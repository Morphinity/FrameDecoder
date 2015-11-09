package com.mbakshi.decodeframe.FrameResources.extractor;

import com.mbakshi.decodeframe.FrameResources.Tracks.TrackOutput;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface ExtractorOutput {
    TrackOutput getTrackOutput(int i);
    void builtTracks();
}
