package com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.DataSource.TransferListener;
import com.mbakshi.decodeframe.FrameResources.DataSource.UriDataSource;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by mbakshi on 19/08/15.
 */
public final class ContentDataSource implements UriDataSource {
    public static class ContentDataSourceException extends IOException {

        public ContentDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private final ContentResolver resolver;
    private final TransferListener listener;

    private InputStream inputStream;
    private String uriString;
    private long bytesRemaining;
    private boolean opened;

    /**
     * Constructs a new {@link com.mbakshi.decodeframe.FrameResources.DataSource.DataSource} that retrieves data from a content provider.
     */
    public ContentDataSource(Context context) {
        this(context, null);
    }

    /**
     * Constructs a new {@link com.mbakshi.decodeframe.FrameResources.DataSource.DataSource} that retrieves data from a content provider.
     *
     * @param listener An optional listener. Specify {@code null} for no listener.
     */
    public ContentDataSource(Context context, TransferListener listener) {
        this.resolver = context.getContentResolver();
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws ContentDataSourceException {
        try {
            uriString = dataSpec.uri.toString();
            AssetFileDescriptor assetFd = resolver.openAssetFileDescriptor(dataSpec.uri, "r");
            inputStream = new FileInputStream(assetFd.getFileDescriptor());
            long skipped = inputStream.skip(dataSpec.position);
            Assertions.checkState(skipped == dataSpec.position);
            bytesRemaining = dataSpec.length == Constants.LENGTH_UNBOUNDED ? inputStream.available()
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            throw new ContentDataSourceException(e);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart();
        }

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws ContentDataSourceException {
        if (bytesRemaining == 0) {
            return -1;
        } else {
            int bytesRead = 0;
            try {
                bytesRead = inputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
            } catch (IOException e) {
                throw new ContentDataSourceException(e);
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
    public void close() throws ContentDataSourceException {
        uriString = null;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new ContentDataSourceException(e);
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
