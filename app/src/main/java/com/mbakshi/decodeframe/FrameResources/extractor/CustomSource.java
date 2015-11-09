package com.mbakshi.decodeframe.FrameResources.extractor;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.CustomDataSource;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.SampleLoader.SampleLoader;
import com.mbakshi.decodeframe.FrameResources.Tracks.CustomTrackOutput;
import com.mbakshi.decodeframe.FrameResources.Tracks.TrackOutput;
import com.mbakshi.decodeframe.FrameResources.Util.Allocate.CustomCountAllocator;
import com.mbakshi.decodeframe.FrameResources.Util.SampleHolder;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.CustomExtractorInput;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.ExtractorInput;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by mbakshi on 19/08/15.
 */
public class CustomSource implements ExtractorOutput, SampleLoader.LoaderCallback {
    private static final String TAG = "CustomSource";
    private static final int BUFFER_FRAGMENT_LENGTH = 256 * 1024;
    private static int requestedBufferSize = 100 * 1024 * 1024;
    private static int NO_RESET_PENDING = -1;
    private Context context;

    private String sourcePath;
    private Uri sourceUri;
    private CustomDataSource dataSource;

    private Parser parser;
    private SampleHolder sampleHolder;
    private CustomCountAllocator allocator;

    private boolean prepared;
    private boolean tracksBuilt;

    private int selectedTrackIndex;

    private SparseArray<CustomTrackOutput> sampleQueues;
    private SampleLoader loader;

    private String trackType;

    public CustomSource(Context context) {
        this.context = context;
        this.trackType = "Video";
    }

    public void setTrackType(String trackType) {
        this.trackType = trackType;
    }

    public void release() {
        if(loader != null) {
              loader.release();
        }
    }

    /******************* Set Data Sources *******************/
    public void setSource(Uri uri) {
        sourceUri = uri;
        sourcePath = sourceUri.getPath();
        setSourceInternal();
    }

    public void setSource(String filePath) {
        sourcePath = filePath;
        //sourceUri = Uri.parse(sourcePath);
        sourceUri = Uri.fromFile(new File(sourcePath));
        setSourceInternal();
    }

    private void setSourceInternal() {
        Log.i(TAG, "SetSourceInternal " + trackType);
        dataSource = new CustomDataSource(context, "CustomSource");
        parser = new Parser();
        parser.setExtractorOutput(this);
        sampleQueues = new SparseArray<>();
        allocator = new CustomCountAllocator(BUFFER_FRAGMENT_LENGTH);
        prepared = false;
        tracksBuilt = false;
        sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
        loader = new SampleLoader(this, dataSource, sourceUri, allocator, parser, trackType);
    }

    /*********************** Preparation ************************/
    public boolean prepare(long positionUs) throws IOException {
        return prepare(positionUs, false);
    }

    public boolean prepare(long positionUs, boolean metaDataOnly) throws IOException {
        if(prepared) {
            return true;
        }
        try {
            if(parser != null) {
                parser.seek();
            }
            Log.i(TAG, "Opening data source");
            long length = dataSource.open(new DataSpec(sourceUri, positionUs, Constants.LENGTH_UNBOUNDED, null));
            if(length != Constants.LENGTH_UNBOUNDED) {
                length += positionUs;
            }
            ExtractorInput input = new CustomExtractorInput(dataSource, positionUs, length);
            Log.i(TAG, "Start Preparing parser");
            if(parser.prepare(input, dataSource, sourceUri)) {
                Log.i(TAG, "Prepared parser");
                prepared = true;
                dataSource.close();
                Log.i(TAG, "Close data source");

                if(!metaDataOnly && loader != null) {
                    long offset = parser.getPosition(positionUs, MediaExtractor.SEEK_TO_PREV_SYNC);
                    loader.startLoadingAtOffset(offset, selectedTrackIndex);
                }
            }
            else {
                prepared = false;
                Log.e(TAG, "Could not prepare parser");
            }
        }
        catch (IOException ex) {
            prepared = false;
            Log.e(TAG, "Could not open datasource");
        }
        catch (InterruptedException ex) {
            prepared = false;
            Log.e(TAG, "Could not prepare parser");
        }

        if(!prepared) {
            throwSourceException();
        }
        return prepared;
    }

    /*********************** Track ********************************/

    public void selectTrack(int index) {
        selectedTrackIndex = index;
    }

    public void deselectTrack(int index) {
        if(index == selectedTrackIndex) {
            selectedTrackIndex = -1;
        }
    }

    public int getTrackCount() {
        if(!tracksBuilt) {
            return 0;
        }
        Assertions.checkState(tracksBuilt);
        Assertions.checkState(prepared);

        return sampleQueues.size();

    }

    public com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat getTrackFormat(int index) {
        if(tracksBuilt && parser != null) { // && haveFormatForAllTracks()
            return sampleQueues.get(index).getFormat();
        }
        return null;
    }

    public android.media.MediaFormat getNativeTrackFormat(int index) {
        if(tracksBuilt && parser != null) { //  && haveFormatForAllTracks()
            com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat format = sampleQueues.get(index).getFormat();
            if(format != null) {
                return format.getFrameworkMediaFormatV16();
            }
        }
        return null;
    }

    private boolean haveFormatForAllTracks() {
        for (int i = 0; i < sampleQueues.size(); i++) {
            if (!sampleQueues.valueAt(i).hasFormat()) {
                return false;
            }
        }
        return true;
    }

    /********************** Sample Data **************************/
    public long getSampleTime(){
        return sampleHolder.timeUs;
    }

    public int getSampleFlags() {
        return sampleHolder.flags;
    }

    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        if(sampleHolder.isEncrypted()) {
            info = sampleHolder.cryptoInfo.getFrameworkCryptoInfoV16();
            return true;
        }
        return false;
    }

    public int readSampleData(ByteBuffer buffer, int offset) throws IOException, InterruptedException {
        if(!prepared || !tracksBuilt) {
            prepare(0);
        }
        sampleHolder.data = buffer;
        sampleHolder.data.clear();
        if(sampleQueues.valueAt(selectedTrackIndex).getSample(sampleHolder)) {
            return sampleHolder.size;
        }
        if(loader.isLoadingFinished()) {
            Log.i(TAG, "ReadNextSample loader is Finished, eos");
            return MediaExtractor.END_OF_STREAM;
        }
        Log.i(TAG, "ReadNextSample: loader not finished, snf");
        return MediaExtractor.SAMPLE_NOT_FOUND;
    }

    public void seekTo(long positionUs, int seekType) throws IOException {
        Log.i(TAG, "Seek To " + positionUs);
        if(prepared && tracksBuilt) {
            if(!sampleQueues.valueAt(selectedTrackIndex).skipToKeyframeBefore(positionUs)) {
                loader.stopLoading();
                resetAtPositionUs(positionUs, seekType);
            }
            else {
                Log.i(TAG, "Seek:Skipped to key frame");
            }
        }
    }

    private void resetAtPositionUs(long positionUs, int seekType) {
        parser.seek();
        clearSamples();
        long offset = parser.getPosition(positionUs, seekType);
        if(loader != null) {
            loader.startLoadingAtOffset(offset, selectedTrackIndex);
        }
        else {
            Log.e(TAG, "Reset:LoaderDoesNotExist");
        }
    }

    private void clearSamples() {
        if(sampleQueues != null) {
            for(int i = 0; i < sampleQueues.size(); i++) {
                sampleQueues.valueAt(i).clear();
            }
        }
    }

    public void advance() {

    }

    private void throwSourceException() throws IOException{
        throw new IOException();
    }

    /****************************  SampleLoaderCallback **************************/
    @Override
    public void onLoaderRelease() {
        Log.i(TAG, "onLoaderRelease");
        clearSamples();
        allocator.trim(0);
        prepared = false;
        tracksBuilt = false;
    }

    /****************************  TrackOutput *******************************/
    @Override
    public TrackOutput getTrackOutput(int i) {
        CustomTrackOutput trackOutput = sampleQueues.get(i);
        if(trackOutput == null) {
            trackOutput = new CustomTrackOutput(allocator);
            sampleQueues.put(i, trackOutput);
        }
        return trackOutput;
    }

    @Override
    public void builtTracks() {
        tracksBuilt = true;
    }
}
