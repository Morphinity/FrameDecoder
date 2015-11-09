package com.mbakshi.decodeframe.FrameResources.DataSource;

import java.io.IOException;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface DataSource {
    /**
     * Opens the {@link DataSource} to read the specified data. Calls to {@link #open(DataSpec)} and
     * {@link #close()} must be balanced.
     * <p>
     * Note: If {@link #open(DataSpec)} throws an {@link IOException}, callers must still call
     * {@link #close()} to ensure that any partial effects of the {@link #open(DataSpec)} invocation
     * are cleaned up. Implementations of this class can assume that callers will call
     * {@link #close()} in this case.
     *
     * @param dataSpec Defines the data to be read.
     * @throws IOException If an error occurs opening the source.
     * @return The number of bytes that can be read from the opened source. For unbounded requests
     *     (i.e. requests where {@link DataSpec#length} equals {@link com.mbakshi.decodeframe.FrameResources.Constants#LENGTH_UNBOUNDED}) this value
     *     is the resolved length of the request, or {@link com.mbakshi.decodeframe.FrameResources.Constants#LENGTH_UNBOUNDED} if the length is still
     *     unresolved. For all other requests, the value returned will be equal to the request's
     *     {@link DataSpec#length}.
     */
    long open(DataSpec dataSpec) throws IOException;

    /**
     * Closes the {@link DataSource}.
     * <p>
     * Note: This method will be called even if the corresponding call to {@link #open(DataSpec)}
     * threw an {@link IOException}. See {@link #open(DataSpec)} for more details.
     *
     * @throws IOException If an error occurs closing the source.
     */
    void close() throws IOException;

    /**
     * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
     * index {@code offset}.
     * <p>
     * This method blocks until at least one byte of data can be read, the end of the opened range is
     * detected, or an exception is thrown.
     *
     * @param buffer The buffer into which the read data should be stored.
     * @param offset The start offset into {@code buffer} at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or {@link com.mbakshi.decodeframe.FrameResources.Constants#RESULT_END_OF_INPUT} if the end of the opened
     *     range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    int read(byte[] buffer, int offset, int readLength) throws IOException;
}
