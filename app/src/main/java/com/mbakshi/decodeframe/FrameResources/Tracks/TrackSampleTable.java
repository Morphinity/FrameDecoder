package com.mbakshi.decodeframe.FrameResources.Tracks;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class TrackSampleTable {
    /** Sample index when no sample is available. */
    public static final int NO_SAMPLE = -1;

    /** Number of samples. */
    public final int sampleCount;
    /** Sample offsets in bytes. */
    public final long[] offsets;
    /** Sample sizes in bytes. */
    public final int[] sizes;
    /** Sample timestamps in microseconds. */
    public final long[] timestampsUs;
    /** Sample flags. */
    public final int[] flags;

    public TrackSampleTable(
            long[] offsets, int[] sizes, long[] timestampsUs, int[] flags) {
        Assertions.checkArgument(sizes.length == timestampsUs.length);
        Assertions.checkArgument(offsets.length == timestampsUs.length);
        Assertions.checkArgument(flags.length == timestampsUs.length);

        this.offsets = offsets;
        this.sizes = sizes;
        this.timestampsUs = timestampsUs;
        this.flags = flags;
        sampleCount = offsets.length;
    }

    /**
     * Returns the sample index of the closest synchronization sample at or before the given
     * timestamp, if one is available.
     *
     * @param timeUs Timestamp adjacent to which to find a synchronization sample.
     * @return Index of the synchronization sample, or {@link #NO_SAMPLE} if none.
     */
    public int getIndexOfEarlierOrEqualSynchronizationSample(long timeUs) {
        int startIndex = Utilities.binarySearchFloor(timestampsUs, timeUs, true, false);
        for (int i = startIndex; i >= 0; i--) {
            if (timestampsUs[i] <= timeUs && (flags[i] & Constants.SAMPLE_FLAG_SYNC) != 0) {
                return i;
            }
        }
        return NO_SAMPLE;
    }

    /**
     * Returns the sample index of the closest synchronization sample at or after the given timestamp,
     * if one is available.
     *
     * @param timeUs Timestamp adjacent to which to find a synchronization sample.
     * @return index Index of the synchronization sample, or {@link #NO_SAMPLE} if none.
     */
    public int getIndexOfLaterOrEqualSynchronizationSample(long timeUs) {
        int startIndex = Utilities.binarySearchCeil(timestampsUs, timeUs, true, false);
        for (int i = startIndex; i < timestampsUs.length; i++) {
            if (timestampsUs[i] >= timeUs && (flags[i] & Constants.SAMPLE_FLAG_SYNC) != 0) {
                return i;
            }
        }
        return NO_SAMPLE;
    }

    public int getIndexOfLaterOrEqualClosestSample(long timeUs) {
        int startIndex = Utilities.binarySearchCeil(timestampsUs, timeUs, true, false);
        if(startIndex == timestampsUs.length) {
            return NO_SAMPLE;
        }
        return startIndex;
    }
}
