package com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource;

import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.DataSource.TransferListener;
import com.mbakshi.decodeframe.FrameResources.DataSource.UriDataSource;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by mbakshi on 19/08/15.
 */
public final class FileDataSource implements UriDataSource {
    /**
     * Thrown when IOException is encountered during local file read operation.
     */
    public static class FileDataSourceException extends IOException {

        public FileDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private final TransferListener listener;

    private RandomAccessFile file;
    private String uriString;
    private long bytesRemaining;
    private boolean opened;


    public FileDataSource() {
        this(null);
    }


    public FileDataSource(TransferListener listener) {
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws FileDataSourceException {
        try {
            uriString = dataSpec.uri.toString();
            file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
            file.seek(dataSpec.position);
            bytesRemaining = dataSpec.length == Constants.LENGTH_UNBOUNDED ? file.length() - dataSpec.position
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            throw new FileDataSourceException(e);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart();
        }

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
        if (bytesRemaining == 0) {
            return -1;
        } else {
            int bytesRead = 0;
            try {
                bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
            } catch (IOException e) {
                throw new FileDataSourceException(e);
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
    public void close() throws FileDataSourceException {
        uriString = null;
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                throw new FileDataSourceException(e);
            } finally {
                file = null;
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
