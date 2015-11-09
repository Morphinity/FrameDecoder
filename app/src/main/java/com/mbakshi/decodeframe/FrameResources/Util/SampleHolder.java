package com.mbakshi.decodeframe.FrameResources.Util;

import com.mbakshi.decodeframe.FrameResources.Constants;

import java.nio.ByteBuffer;

/**
 * Created by mbakshi on 19/08/15.
 */
public final class SampleHolder {
    /**
     * Disallows buffer replacement.
     */
    public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;

    /**
     * Allows buffer replacement using {@link ByteBuffer#allocate(int)}.
     */
    public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;

    /**
     * Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}.
     */
    public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

    public final CryptoInfo cryptoInfo;

    /**
     * A buffer holding the sample data.
     */
    public ByteBuffer data;

    /**
     * The size of the sample in bytes.
     */
    public int size;

    public boolean decodeOnly;

    /**
     * Flags that accompany the sample. A combination of {@link com.mbakshi.decodeframe.FrameResources.Constants#SAMPLE_FLAG_SYNC},
     * {@link com.mbakshi.decodeframe.FrameResources.Constants#SAMPLE_FLAG_ENCRYPTED} and {@link com.mbakshi.decodeframe.FrameResources.Constants#SAMPLE_FLAG_DECODE_ONLY}.
     */
    public int flags;

    /**
     * The time at which the sample should be presented.
     */
    public long timeUs;

    private final int bufferReplacementMode;

    /**
     * @param bufferReplacementMode Determines the behavior of {@link #replaceBuffer(int)}. One of
     *     {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
     *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
     */
    public SampleHolder(int bufferReplacementMode) {
        this.cryptoInfo = new CryptoInfo();
        this.bufferReplacementMode = bufferReplacementMode;
    }

    /**
     * Attempts to replace {@link #data} with a {@link ByteBuffer} of the specified capacity.
     *
     * @param capacity The capacity of the replacement buffer, in bytes.
     * @return True if the buffer was replaced. False otherwise.
     */
    public boolean replaceBuffer(int capacity) {
        switch (bufferReplacementMode) {
            case BUFFER_REPLACEMENT_MODE_NORMAL:
                data = ByteBuffer.allocate(capacity);
                return true;
            case BUFFER_REPLACEMENT_MODE_DIRECT:
                data = ByteBuffer.allocateDirect(capacity);
                return true;
        }
        return false;
    }


    public boolean isEncrypted() {
        return (flags & Constants.SAMPLE_FLAG_ENCRYPTED) != 0;
    }

    public boolean isDecodeOnly() {
        return (flags & Constants.SAMPLE_FLAG_DECODE_ONLY) != 0;
    }

    public boolean isSyncFrame() {
        return (flags & Constants.SAMPLE_FLAG_SYNC) != 0;
    }

    /**
     * Clears {@link #data}. Does nothing if {@link #data} is null.
     */
    public void clearData() {
        if (data != null) {
            data.clear();
        }
    }
}
