package com.mbakshi.decodeframe.FrameResources.SampleLoader;

import android.net.Uri;
import android.util.Log;

import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSource;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.Util.Allocate.Allocator;
import com.mbakshi.decodeframe.FrameResources.Util.PositionHolder;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.CustomExtractorInput;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.ExtractorInput;
import com.mbakshi.decodeframe.FrameResources.extractor.Parser;

import java.io.IOException;

/**
 * Created by mbakshi on 27/08/15.
 */
public class Loadable implements Runnable {
    private long offset;
    private DataSource dataSource;
    private Uri sourceUri;
    private Allocator allocator;
    private Parser parser;
    private SampleLoader loader;

    private int trackIndex;
    private String trackType;

    private boolean pendingLoadCancel;
    private boolean loadCancelled;
    private boolean loadFinished;

    public Loadable(SampleLoader loader, long offset, DataSource dataSource, Uri sourceUri, Allocator allocator, Parser parser, int trackIndex, String trackType) {
        this.loader = loader;
        this.offset = offset;
        this.dataSource = dataSource;
        this.sourceUri = sourceUri;
        this.allocator = allocator;
        this.parser = parser;

        this.trackIndex = trackIndex;
        this.trackType = trackType;

        pendingLoadCancel = false;
        loadCancelled = false;
        loadFinished = false;
    }

    public void cancelLoading() {
        Log.i("LoaderThread", "Cancel Loading");
        pendingLoadCancel = true;
    }

    public boolean isLoadFinished() {
        return loadFinished;
    }

    public void setOffset(long newOffset) {
        this.offset = newOffset;
    }

    public boolean isLoadCancelled() {
        return loadCancelled;
    }

    public long runSync() {
        PositionHolder positionHolder = new PositionHolder();
        positionHolder.position = offset;
        int result = Parser.RESULT_CONTINUE;
        Log.i("LoaderThread", "Sync LoadSampleStarted " + trackType);
        while (result == Parser.RESULT_CONTINUE && !pendingLoadCancel) {
            ExtractorInput input = null;
            try {
                long position = positionHolder.position;
                long length = dataSource.open(new DataSpec(sourceUri, position, Constants.LENGTH_UNBOUNDED, null));
                if (length != Constants.LENGTH_UNBOUNDED) {
                    length += position;
                }
                input = new CustomExtractorInput(dataSource, position, length);
                while (result == Parser.RESULT_CONTINUE && !pendingLoadCancel) {
                    boolean exceeds = allocator.totalBytesAllocatedExceeds(5);
                    if(exceeds) {
                        dataSource.close();
                        positionHolder.position = input.getPosition();
                        loadFinished = false;
                        Log.i("LoaderThread", "Sync LoadSampleBlocked " + trackType);
                        return positionHolder.position;
                    }
                    result = parser.readSampleData(input, positionHolder, trackIndex);
                }
            } catch (IOException ioexc) {
                Log.e("LoaderThread", "IOexc: " + ioexc);
            } catch (InterruptedException exc) {
                Log.e("LoaderThread", "Interrupt exc " + exc);
            } finally {
                if (result == Parser.RESULT_SEEK) {
                    result = Parser.RESULT_CONTINUE;
                } else if (input != null) {
                    positionHolder.position = input.getPosition();
                }
                try {
                    dataSource.close();
                }
                catch (IOException ex) {
                    Log.e("LoaderThread", "datasource close exc " + ex);
                }
            }
        }
        Log.i("LoaderThread", "LoadSample Sync Complete " + trackType);
        if(result == Parser.RESULT_END_OF_INPUT) {
            loadFinished = true;
        }
        return positionHolder.position;
    }

    @Override
    public synchronized void run() {
        Log.i("LoaderThread", "Async loadsample started " + trackType);
        PositionHolder positionHolder = new PositionHolder();
        positionHolder.position = offset;

        int result = Parser.RESULT_CONTINUE;
        while (result == Parser.RESULT_CONTINUE && !pendingLoadCancel) {
            ExtractorInput input = null;
            try {
                long position = positionHolder.position;
                long length = dataSource.open(new DataSpec(sourceUri, position, Constants.LENGTH_UNBOUNDED, null));
                if (length != Constants.LENGTH_UNBOUNDED) {
                    length += position;
                }
                input = new CustomExtractorInput(dataSource, position, length);

                while (result == Parser.RESULT_CONTINUE && !pendingLoadCancel) {
                    allocator.blockWhileTotalBytesAllocatedExceeds(5);
                    result = parser.readSampleData(input, positionHolder, trackIndex);
                    //Log.i("LoaderThread", "LoadSample Async " + trackType);
                }

            } catch (IOException exc) {
                Log.e("LoaderThread", "IO exception: " + exc);
                try {
                    dataSource.close();
                }
                catch (IOException io) {
                    Log.i("LoaderThread", "IO exception while closing data source");
                }
                return;
            } catch (InterruptedException exc) {
                Log.e("LoaderThread", "Interrupt exc " + exc + " loadCancel " + pendingLoadCancel);
            } finally {
                if (result == Parser.RESULT_SEEK) {
                    result = Parser.RESULT_CONTINUE;
                } else if (input != null) {
                    positionHolder.position = input.getPosition();
                }
                try {
                    dataSource.close();
                }
                catch (IOException ex) {
                    Log.e("LoaderThread", "datasource close exc " + ex);
                }
            }
        }
        if(result == Parser.RESULT_END_OF_INPUT) {
            Log.i("LoaderThread", "Async: End of input");
            loadFinished = true;
        }
        else if(pendingLoadCancel && loader != null) {
            synchronized (loader) {
                loadCancelled = true;
                Log.i("LoaderThread", "Notifying for cancel");
                loader.notify();
                Log.i("LoaderThread", "Notified");
            }
        }
    }
}
