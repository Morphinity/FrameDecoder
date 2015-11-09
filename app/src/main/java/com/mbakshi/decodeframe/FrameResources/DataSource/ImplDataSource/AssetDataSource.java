package com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource;

import android.content.Context;
import android.content.res.AssetManager;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.DataSource.TransferListener;
import com.mbakshi.decodeframe.FrameResources.DataSource.UriDataSource;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by mbakshi on 19/08/15.
 */
public final class AssetDataSource implements UriDataSource {
    /**
     * Thrown when an {@link IOException} is encountered reading a local asset.
     */
    public static final class AssetDataSourceException extends IOException {

        public AssetDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private final AssetManager assetManager;
    private final TransferListener listener;

    private String uriString;
    private InputStream inputStream;
    private long bytesRemaining;
    private boolean opened;


    public AssetDataSource(Context context) {
        this(context, null);
    }

    public AssetDataSource(Context context, TransferListener listener) {
        this.assetManager = context.getAssets();
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws AssetDataSourceException {
        try {
            uriString = dataSpec.uri.toString();
            String path = dataSpec.uri.getPath();
            if (path.startsWith("/android_asset/")) {
                path = path.substring(15);
            } else if (path.startsWith("/")) {
                path = path.substring(1);
            }
            uriString = dataSpec.uri.toString();
            inputStream = assetManager.open(path, AssetManager.ACCESS_RANDOM);
            long skipped = inputStream.skip(dataSpec.position);
            Assertions.checkState(skipped == dataSpec.position);
            bytesRemaining = dataSpec.length == Constants.LENGTH_UNBOUNDED ? inputStream.available()
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            throw new AssetDataSourceException(e);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart();
        }
        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws AssetDataSourceException {
        if (bytesRemaining == 0) {
            return -1;
        } else {
            int bytesRead = 0;
            try {
                bytesRead = inputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
            } catch (IOException e) {
                throw new AssetDataSourceException(e);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                if (listener != null) {
                    listener.onBytesTransferred(bytesRead);
                }
            }

            return bytesRead;
        }
    }

    @Override
    public String getUri() {
        return uriString;
    }

    @Override
    public void close() throws AssetDataSourceException {
        uriString = null;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new AssetDataSourceException(e);
            } finally {
                inputStream = null;
                if (opened) {
                    opened = false;
                    if (listener != null) {
                        listener.onTransferEnd();
                    }
                }
            }
        }
    }

}
