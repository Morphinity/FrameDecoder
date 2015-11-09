package com.mbakshi.decodeframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.util.Log;

import com.mbakshi.decodeframe.FrameResources.FrameResources.CustomFrameRetriever;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MimeTypes;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;
import com.mbakshi.decodeframe.FrameResources.extractor.MediaExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by mbakshi on 03/09/15.
 */
public final class ClipExtractor {
    private static String TAG = "ClipExtractor";
    private Context context;
    private MediaExtractor mediaExtractor;

    private String filePath;
    private Uri fileUri;

    private class MetadataAdditional {
        public int rotation;
        public Bitmap frame;
    }

    private MetadataAdditional metadataAdditional;

    public ClipExtractor(Context context) {
        this.context = context;
        mediaExtractor = new MediaExtractor(context);
        metadataAdditional = new MetadataAdditional();
    }

    /***************************************************************************** Prepare and Release *****/
    public boolean setDataSource(String filePath) throws IOException{
        this.filePath = filePath;
        fileUri = Uri.fromFile(new File(filePath));
        return setDataSourceInternal();
    }

    public boolean setDataSource(Uri sourceUri) throws IOException {
        this.fileUri = sourceUri;
        filePath = fileUri.getPath();
        return setDataSourceInternal();
    }

    private boolean setDataSourceInternal() throws IOException {
        boolean error = false;
        if(tryCustomExtractor()){
            Log.i(TAG, "Custom extractor success");
        }
        else {
            Log.i(TAG, "Could not set data source");
            error = true;
        }
        if(error) {
            throw new IOException("Could not set data source for extractor");
        }
        return !error;
    }

    private boolean tryCustomExtractor() {
        boolean error = false;
        try {
            mediaExtractor.setDataSource(fileUri);
            if(mediaExtractor.getTrackCount() == 0) {
                error = true;
            }
        }
        catch (IOException ioexc) {
            Log.e(TAG, "could not use custom extractor " + ioexc);
            error = true;
        }
        if(error) {
            Log.i(TAG, "cannot use custom");
        }
        return !error;
    }

    public void release() {
        mediaExtractor.release();
    }

    public Bitmap getFrameAtTime(long timeUs, int maxW, int maxH, boolean scale) {
        Log.i("ThumbLog", "GetFrameAtTime " + timeUs);
        CustomFrameRetriever frameRetriever = CustomFrameRetriever.getInstance();
        if(frameRetriever.prepare(timeUs, this, maxW, maxH)) {
            metadataAdditional.frame = frameRetriever.getFrame();
            if(scale) {
                metadataAdditional.frame = ThumbnailUtils.extractThumbnail(metadataAdditional.frame, maxW, maxH,
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }
            Log.i("ThumbTest", "thumb w " + metadataAdditional.frame.getWidth() + " h " + metadataAdditional.frame.getHeight());
            Bitmap rot = handleRotation(metadataAdditional.frame, frameRetriever.getRotation());
            metadataAdditional.frame.recycle();
            frameRetriever.release();
            return rot;
        }
        return null;
    }

    private Bitmap handleRotation(Bitmap sourceBitmap, int rotation) {
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(sourceBitmap, 0, 0, width, height, matrix, true);
    }

    /**********************************************************************  Track Information ********/
    public void selectTrack(int index) {
        mediaExtractor.selectTrack(index);
    }

    public int getTrackCount() {
        return mediaExtractor.getTrackCount();
    }

    public android.media.MediaFormat getNativeTrackFormat(int trackIndex) {
        return mediaExtractor.getNativeTrackFormat(trackIndex);
    }

    public MediaFormat getTrackFormat(int trackIndex) {
        return mediaExtractor.getTrackFormat(trackIndex);
    }

    public void unselectTrack(int index) {
        mediaExtractor.unselectTrack(index);
    }

    /********************************************************************* Sample APIs *************/
    public void seekTo(long positionUs) {
        mediaExtractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREV_SYNC);
    }

    public int readSampleData(ByteBuffer byteBuffer, int offset) {
        return mediaExtractor.readSampleData(byteBuffer, offset);
    }

    public long getSampleTime() {
        return mediaExtractor.getSampleTime();
    }

    public int getSampleFlags() {
        return mediaExtractor.getSampleFlags();
    }

    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo cryptoInfo) {
        return mediaExtractor.getSampleCryptoInfo(cryptoInfo);
    }

    public void advance() {
        mediaExtractor.advance();
    }
}
