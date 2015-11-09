package com.mbakshi.decodeframe.FrameResources.extractor.Input;

import java.io.EOFException;
import java.io.IOException;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface ExtractorInput {

    int read(byte[] target, int offset, int length) throws IOException, InterruptedException;

    boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
            throws IOException, InterruptedException;

    /**
     * Equivalent to {@code readFully(target, offset, length, false)}.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to read from the input.
     * @throws EOFException If the end of input was encountered.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread is interrupted.
     */
    void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException;


    void skipFully(int length) throws IOException, InterruptedException;


    long getPosition();


    long getLength();

}
