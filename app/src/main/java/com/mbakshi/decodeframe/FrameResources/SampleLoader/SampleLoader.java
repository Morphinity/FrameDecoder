package com.mbakshi.decodeframe.FrameResources.SampleLoader;

import android.net.Uri;
import android.util.Log;

import com.mbakshi.decodeframe.FrameResources.DataSource.DataSource;
import com.mbakshi.decodeframe.FrameResources.Util.Allocate.Allocator;
import com.mbakshi.decodeframe.FrameResources.Util.Allocate.CustomCountAllocator;
import com.mbakshi.decodeframe.FrameResources.extractor.Parser;

/**
 * Created by mbakshi on 27/08/15.
 */
public class SampleLoader {
    private static final String TAG = "SampleLoader";

    public interface LoaderCallback {
        void onLoaderRelease();
    }

    private DataSource dataSource;
    private Uri sourceUri;
    private Allocator allocator;
    private Parser parser;

    private Thread currentThread;
    private Loadable currentAsyncLoadable;

    private LoaderCallback callback;

    private String trackType;

    public SampleLoader(LoaderCallback callback, DataSource dataSource, Uri uri, CustomCountAllocator allocator, Parser parser, String trackType) {
        this.callback = callback;
        this.dataSource = dataSource;
        this.sourceUri = uri;
        this.allocator = allocator;
        this.parser = parser;
        this.trackType = trackType;
    }

    public void release() {
        Log.i(TAG, "Release");
        stopLoading();
        callback.onLoaderRelease();
    }

    public void startLoadingAtOffset(long offset, int trackIndex) {
        Log.i(TAG, "StartLoading:offset " + offset);
        currentAsyncLoadable = new Loadable(this, offset, dataSource, sourceUri, allocator, parser, trackIndex, trackType);
        offset = currentAsyncLoadable.runSync();
        if(!currentAsyncLoadable.isLoadFinished()) {
            Log.i(TAG, "Async Load start at "  + offset);
            currentAsyncLoadable.setOffset(offset);
            currentThread = new Thread(currentAsyncLoadable);
            currentThread.start();
        }
    }

    public synchronized void stopLoading() {
        Log.i(TAG, "StopLoading");
        if(currentAsyncLoadable != null && !currentAsyncLoadable.isLoadFinished()) {
            try {
                Log.i(TAG, "StopLoading:Async Loadable found");
                currentAsyncLoadable.cancelLoading();
                if(currentThread != null) {
                    currentThread.interrupt();
                }
                Log.i(TAG, "StopLoading:GoingToWait");
                while(!currentAsyncLoadable.isLoadCancelled()) {
                    wait();
                }
                Log.i(TAG, "StopLoading:Notified");

            }
            catch (InterruptedException interrupt) {
                Log.i(TAG, "StopLoadingWaitReceivedInterrupt " + interrupt);
            }
        }
        reset();
    }

    public boolean isLoadingFinished() {
        if(currentAsyncLoadable != null) {
            return currentAsyncLoadable.isLoadFinished();
        }
        return true;
    }

    public boolean isLoading() {
        if(currentAsyncLoadable != null) {
            return !currentAsyncLoadable.isLoadFinished();
        }
        return false;
    }

    private void reset() {
        Log.i(TAG, "Resetting");
        currentAsyncLoadable = null;
        currentThread = null;
    }
}
