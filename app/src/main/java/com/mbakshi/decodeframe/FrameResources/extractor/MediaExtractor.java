package com.mbakshi.decodeframe.FrameResources.extractor;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by mbakshi on 19/08/15.
 */
public class MediaExtractor {
    public static final int END_OF_STREAM = -1;
    public static final int SAMPLE_NOT_FOUND = -2;

    public static final int SEEK_TO_PREV_SYNC = 1;
    public static final int SEEK_TO_NEXT_SYNC = 2;
    public static final int SEEK_TO_NEXT_CLOSEST_FRAME = 3;

    private static final String TAG = "MediaExtractor";
    private String filePath;
    private Uri fileUri;
    private CustomSource source;

    public MediaExtractor(Context context) {
        source = new CustomSource(context);
    }

    public void release() {
        if(source != null) {
            source.release();
        }
    }

    public void setTrackType(String trackType) {
        source.setTrackType(trackType);
    }
    /******************* Set Data Sources *******************/

    public boolean setDataSource(Context context, Uri fileUri, Map<String, String> headers) throws IOException{
        source = new CustomSource(context);
        return setDataSource(fileUri);
    }

    public boolean setDataSource(String filePath) throws IOException {
        return setDataSource(filePath, true);
    }

    public boolean setDataSource(Uri fileUri) throws IOException {
        return setDataSource(fileUri, true);
    }

    public boolean setDataSource(String filePath, boolean metaDataOnly) throws IOException {
        this.filePath = filePath;
        //this.fileUri = Uri.parse(filePath);
        this.fileUri = Uri.fromFile(new File(filePath));
        return setDataSourceInternal(metaDataOnly);
    }

    public boolean setDataSource(Uri uri, boolean metaDataOnly) throws IOException {
        this.fileUri = uri;
        filePath = fileUri.getPath();
        return setDataSourceInternal(metaDataOnly);
    }

    private boolean setDataSourceInternal(boolean metaDataOnly) throws IOException {
        source.setSource(fileUri);
        return source.prepare(0, metaDataOnly);
    }

    /************************ Tracks ************************/

    public int getTrackCount() {
        return source.getTrackCount();
    }

    public com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat getTrackFormat(int i) {
        return source.getTrackFormat(i);
    }

    public MediaFormat getNativeTrackFormat(int i) {
        return source.getNativeTrackFormat(i);
    }

    public void selectTrack(int i) {
        source.selectTrack(i);
    }

    public void unselectTrack(int i) {
        source.deselectTrack(i);
    }

    /***************** Samples **********************/

    public long getSampleTime() {
        return source.getSampleTime();
    }

    public int getSampleFlags() {
        return source.getSampleFlags();
    }

    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        return source.getSampleCryptoInfo(info);
    }

    public int readSampleData(ByteBuffer buffer, int offset) {
        try {
            int length = source.readSampleData(buffer, offset);
            return length;
        }
        catch (IOException ex) {
            Log.e(TAG, "Could not read sample " + ex);
        }
        catch (InterruptedException exception) {
            Log.e(TAG, "Could not read sample " + exception);
        }
        return END_OF_STREAM;
    }

    public void seekTo(long positionUs, int seekType) {
        try {
            source.seekTo(positionUs, seekType);
        }
        catch (IOException ioexc) {
            Log.e(TAG, "Seek failed " + ioexc);
        }

    }

    public void advance() {
        source.advance();
    }

    public boolean hasCacheReachedEndOfStream() {
        return true;
    }
}
