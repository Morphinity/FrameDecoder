package com.mbakshi.decodeframe.FrameResources.extractor.Input;


import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSource;

import java.io.EOFException;
import java.io.IOException;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class CustomExtractorInput implements ExtractorInput{
    private static final byte[] SCRATCH_SPACE = new byte[4096];

    private final DataSource dataSource;

    private long position;
    private long length;

    /**
     * @param dataSource The wrapped {@link DataSource}.
     * @param position The initial position in the stream.
     * @param length The length of the stream, or {@link com.mbakshi.decodeframe.FrameResources.Constants#LENGTH_UNBOUNDED} if it is unknown.
     */
    public CustomExtractorInput(DataSource dataSource, long position, long length) {
        this.dataSource = dataSource;
        this.position = position;
        this.length = length;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        int bytesRead = dataSource.read(target, offset, length);
        if (bytesRead == Constants.RESULT_END_OF_INPUT) {
            return Constants.RESULT_END_OF_INPUT;
        }
        position += bytesRead;
        return bytesRead;
    }

    @Override
    public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
            throws IOException, InterruptedException {
        int remaining = length;
        while (remaining > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            int bytesRead = dataSource.read(target, offset, remaining);
            if (bytesRead == Constants.RESULT_END_OF_INPUT) {
                if (allowEndOfInput && remaining == length) {
                    return false;
                }
                throw new EOFException();
            }
            offset += bytesRead;
            remaining -= bytesRead;
        }
        position += length;
        return true;
    }

    @Override
    public void readFully(byte[] target, int offset, int length)
            throws IOException, InterruptedException {
        readFully(target, offset, length, false);
    }

    @Override
    public void skipFully(int length) throws IOException, InterruptedException {
        int remaining = length;
        while (remaining > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            int bytesRead = dataSource.read(SCRATCH_SPACE, 0, Math.min(SCRATCH_SPACE.length, remaining));
            if (bytesRead == Constants.RESULT_END_OF_INPUT) {
                throw new EOFException();
            }
            remaining -= bytesRead;
        }
        position += length;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getLength() {
        return length;
    }


}
