package com.mbakshi.decodeframe.FrameResources.Tracks;


import com.mbakshi.decodeframe.FrameResources.DataSource.DataSource;
import com.mbakshi.decodeframe.FrameResources.Util.Allocate.Allocator;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;
import com.mbakshi.decodeframe.FrameResources.Util.ParsableByteArray;
import com.mbakshi.decodeframe.FrameResources.Util.RollingSampleBuffer;
import com.mbakshi.decodeframe.FrameResources.Util.SampleHolder;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.ExtractorInput;

import java.io.IOException;

/**
 * Created by mbakshi on 20/08/15.
 */
public class CustomTrackOutput implements TrackOutput {
    private final RollingSampleBuffer rollingBuffer;
    private final SampleHolder sampleInfoHolder;

    // Accessed only by the consuming thread.
    private boolean needKeyframe;
    private long lastReadTimeUs;
    private long spliceOutTimeUs;

    // Accessed by both the loading and consuming threads.
    private volatile long largestParsedTimestampUs;
    private volatile MediaFormat format;

    /**
     * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
     */
    public CustomTrackOutput(Allocator allocator) {
        rollingBuffer = new RollingSampleBuffer(allocator);
        sampleInfoHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
        needKeyframe = true;
        lastReadTimeUs = Long.MIN_VALUE;
        spliceOutTimeUs = Long.MIN_VALUE;
        largestParsedTimestampUs = Long.MIN_VALUE;
    }

    // Called by the consuming thread, but only when there is no loading thread.

    /**
     * Clears the queue, returning all allocations to the allocator.
     */
    public void clear() {
        rollingBuffer.clear();
        needKeyframe = true;
        lastReadTimeUs = Long.MIN_VALUE;
        spliceOutTimeUs = Long.MIN_VALUE;
        largestParsedTimestampUs = Long.MIN_VALUE;
    }

    /**
     * Returns the current absolute write index.
     */
    public int getWriteIndex() {
        return rollingBuffer.getWriteIndex();
    }

    /**
     * Discards samples from the write side of the queue.
     *
     * @param discardFromIndex The absolute index of the first sample to be discarded.
     */
    public void discardUpstreamSamples(int discardFromIndex) {
        rollingBuffer.discardUpstreamSamples(discardFromIndex);
        largestParsedTimestampUs = rollingBuffer.peekSample(sampleInfoHolder) ? sampleInfoHolder.timeUs
                : Long.MIN_VALUE;
    }

    // Called by the consuming thread.

    /**
     * Returns the current absolute read index.
     */
    public int getReadIndex() {
        return rollingBuffer.getReadIndex();
    }

    /**
     * True if the output has received a format. False otherwise.
     */
    public boolean hasFormat() {
        return format != null;
    }

    /**
     * The format most recently received by the output, or null if a format has yet to be received.
     */
    public MediaFormat getFormat() {
        return format;
    }

    /**
     * The largest timestamp of any sample received by the output, or {@link Long#MIN_VALUE} if a
     * sample has yet to be received.
     */
    public long getLargestParsedTimestampUs() {
        return largestParsedTimestampUs;
    }

    /**
     * True if at least one sample can be read from the queue. False otherwise.
     */
    public boolean isEmpty() {
        return !advanceToEligibleSample();
    }

    /**
     * Removes the next sample from the head of the queue, writing it into the provided holder.
     * <p>
     * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
     * queued prior to the first keyframe are discarded.
     *
     * @param holder A {@link SampleHolder} into which the sample should be read.
     * @return True if a sample was read. False otherwise.
     */
    public boolean getSample(SampleHolder holder) {
        boolean foundEligibleSample = advanceToEligibleSample();
        if (!foundEligibleSample) {
            return false;
        }
        // Write the sample into the holder.
        rollingBuffer.readSample(holder);
        needKeyframe = false;
        lastReadTimeUs = holder.timeUs;
        return true;
    }

    /**
     * Discards samples from the queue up to the specified time.
     *
     * @param timeUs The time up to which samples should be discarded, in microseconds.
     */
    public void discardUntil(long timeUs) {
        while (rollingBuffer.peekSample(sampleInfoHolder) && sampleInfoHolder.timeUs < timeUs) {
            rollingBuffer.skipSample();
            // We're discarding one or more samples. A subsequent read will need to start at a keyframe.
            needKeyframe = true;
        }
        lastReadTimeUs = Long.MIN_VALUE;
    }

    /**
     * Attempts to skip to the keyframe before the specified time, if it's present in the buffer.
     *
     * @param timeUs The seek time.
     * @return True if the skip was successful. False otherwise.
     */
    public boolean skipToKeyframeBefore(long timeUs) {
        return rollingBuffer.skipToKeyframeBefore(timeUs);
    }

    /**
     * Attempts to configure a splice from this queue to the next.
     *
     * @param nextQueue The queue being spliced to.
     * @return Whether the splice was configured successfully.
     */
    public boolean configureSpliceTo(CustomTrackOutput nextQueue) {
        if (spliceOutTimeUs != Long.MIN_VALUE) {
            // We've already configured the splice.
            return true;
        }
        long firstPossibleSpliceTime;
        if (rollingBuffer.peekSample(sampleInfoHolder)) {
            firstPossibleSpliceTime = sampleInfoHolder.timeUs;
        } else {
            firstPossibleSpliceTime = lastReadTimeUs + 1;
        }
        RollingSampleBuffer nextRollingBuffer = nextQueue.rollingBuffer;
        while (nextRollingBuffer.peekSample(sampleInfoHolder)
                && (sampleInfoHolder.timeUs < firstPossibleSpliceTime || !sampleInfoHolder.isSyncFrame())) {
            // Discard samples from the next queue for as long as they are before the earliest possible
            // splice time, or not keyframes.
            nextRollingBuffer.skipSample();
        }
        if (nextRollingBuffer.peekSample(sampleInfoHolder)) {
            // We've found a keyframe in the next queue that can serve as the splice point. Set the
            // splice point now.
            spliceOutTimeUs = sampleInfoHolder.timeUs;
            return true;
        }
        return false;
    }

    /**
     * Advances the underlying buffer to the next sample that is eligible to be returned.
     *
     * @boolean True if an eligible sample was found. False otherwise, in which case the underlying
     *     buffer has been emptied.
     */
    private boolean advanceToEligibleSample() {
        boolean haveNext = rollingBuffer.peekSample(sampleInfoHolder);
        if (needKeyframe) {
            while (haveNext && !sampleInfoHolder.isSyncFrame()) {
                rollingBuffer.skipSample();
                haveNext = rollingBuffer.peekSample(sampleInfoHolder);
            }
        }
        if (!haveNext) {
            return false;
        }
        if (spliceOutTimeUs != Long.MIN_VALUE && sampleInfoHolder.timeUs >= spliceOutTimeUs) {
            return false;
        }
        return true;
    }

    // Called by the loading thread.

    public int sampleData(DataSource dataSource, int length) throws IOException {
        return rollingBuffer.appendData(dataSource, length);
    }

    // TrackOutput implementation. Called by the loading thread.

    @Override
    public void format(MediaFormat format) {
        this.format = format;
    }

    @Override
    public int sampleData(ExtractorInput input, int length) throws IOException, InterruptedException {
        return rollingBuffer.appendData(input, length);
    }

    @Override
    public void sampleData(ParsableByteArray buffer, int length) {
        rollingBuffer.appendData(buffer, length);
    }

    @Override
    public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs, timeUs);
        rollingBuffer.commitSample(timeUs, flags, rollingBuffer.getWritePosition() - size - offset,
                size, encryptionKey);
    }
}
