package com.mbakshi.decodeframe.FrameResources.Tracks;


import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;
import com.mbakshi.decodeframe.FrameResources.Util.ParsableByteArray;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.ExtractorInput;

import java.io.IOException;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface TrackOutput {

    void format(MediaFormat format);

    /**
     * Invoked to write sample data to the output.
     *
     * @param input An {@link ExtractorInput} from which to read the sample data.
     * @param length The maximum length to read from the input.
     * @return The number of bytes appended.
     * @throws IOException If an error occurred reading from the input.
     * @throws InterruptedException If the thread was interrupted.
     */
    int sampleData(ExtractorInput input, int length) throws IOException, InterruptedException;

    /**
     * Invoked to write sample data to the output.
     *
     * @param data A {@link ParsableByteArray} from which to read the sample data.
     * @param length The number of bytes to read.
     */
    void sampleData(ParsableByteArray data, int length);

    /**
     * Invoked when metadata associated with a sample has been extracted from the stream.
     * <p>
     * The corresponding sample data will have already been passed to the output via calls to
     * {@link #sampleData(ExtractorInput, int)} or {@link #sampleData(ParsableByteArray, int)}.
     *
     * @param timeUs The media timestamp associated with the sample, in microseconds.
     * @param flags Flags associated with the sample. See {@link com.mbakshi.decodeframe.FrameResources.Util.SampleHolder#flags}.
     * @param size The size of the sample data, in bytes.
     * @param offset The number of bytes that have been passed to
     *     {@link #sampleData(ExtractorInput, int)} or {@link #sampleData(ParsableByteArray, int)}
     *     since the last byte belonging to the sample whose metadata is being passed.
     * @param encryptionKey The encryption key associated with the sample. May be null.
     */
    void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey);
}
