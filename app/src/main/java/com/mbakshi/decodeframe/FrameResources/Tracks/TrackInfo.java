package com.mbakshi.decodeframe.FrameResources.Tracks;

/**
 * Created by mbakshi on 25/08/15.
 */
public final class TrackInfo {
    public final String mimeType;
    public final long durationUs;

    public TrackInfo(String mimeType, long durationUs) {
        this.mimeType = mimeType;
        this.durationUs = durationUs;
    }
}
