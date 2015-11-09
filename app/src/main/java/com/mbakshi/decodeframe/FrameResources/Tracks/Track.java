package com.mbakshi.decodeframe.FrameResources.Tracks;


import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class Track {
    /**
     * Type of a video track.
     */
    public static final int TYPE_VIDEO = 0x76696465;
    /**
     * Type of an audio track.
     */
    public static final int TYPE_AUDIO = 0x736F756E;
    /**
     * Type of a text track.
     */
    public static final int TYPE_TEXT = 0x74657874;
    /**
     * Type of a hint track.
     */
    public static final int TYPE_HINT = 0x68696E74;
    /**
     * Type of a meta track.
     */
    public static final int TYPE_META = 0x6D657461;
    /**
     * Type of a time-code track.
     */
    public static final int TYPE_TIME_CODE = 0x746D6364;

    /**
     * The track identifier.
     */
    public final int id;

    /**
     * One of {@link #TYPE_VIDEO}, {@link #TYPE_AUDIO}, {@link #TYPE_HINT}, {@link #TYPE_META} and
     * {@link #TYPE_TIME_CODE}.
     */
    public final int type;

    /**
     * The track timescale, defined as the number of time units that pass in one second.
     */
    public final long timescale;

    public final long durationUs;

    /**
     * The format if {@link #type} is {@link #TYPE_VIDEO} or {@link #TYPE_AUDIO}. Null otherwise.
     */
    public final MediaFormat mediaFormat;

    /**
     * Track encryption boxes for the different track sample descriptions. Entries may be null.
     */
    public final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

    /**
     * For H264 video tracks, the length in bytes of the NALUnitLength field in each sample. -1 for
     * other track types.
     */
    public final int nalUnitLengthFieldLength;

    public Track(int id, int type, long timescale, long durationUs, MediaFormat mediaFormat,
                 TrackEncryptionBox[] sampleDescriptionEncryptionBoxes, int nalUnitLengthFieldLength) {
        this.id = id;
        this.type = type;
        this.timescale = timescale;
        this.durationUs = durationUs;
        this.mediaFormat = mediaFormat;
        this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
        this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    }
}
