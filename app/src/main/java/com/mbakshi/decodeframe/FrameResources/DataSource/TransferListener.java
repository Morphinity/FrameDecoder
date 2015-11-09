package com.mbakshi.decodeframe.FrameResources.DataSource;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface TransferListener {
    void onTransferStart();

    /**
     * Called incrementally during a transfer.
     *
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     *     method (or if the first call, since the transfer was started).
     */
    void onBytesTransferred(int bytesTransferred);

    /**
     * Invoked when a transfer ends.
     */
    void onTransferEnd();
}
