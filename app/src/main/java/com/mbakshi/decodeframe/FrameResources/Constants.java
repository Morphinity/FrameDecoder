package com.mbakshi.decodeframe.FrameResources;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;

/**
 * Created by mbakshi on 19/08/15.
 */
public final class Constants {
    /**
     * Represents an unknown microsecond time or duration.
     */
    public static final long UNKNOWN_TIME_US = -1L;

    /**
     * The number of microseconds in one second.
     */
    public static final long MICROS_PER_SECOND = 1000000L;

    /**
     * Represents an unbounded length of data.
     */
    public static final int LENGTH_UNBOUNDED = -1;

    /**
     * The name of the UTF-8 charset.
     */
    public static final String UTF8_NAME = "UTF-8";

    /**
     * @see MediaCodec#CRYPTO_MODE_AES_CTR
     */
    @SuppressWarnings("InlinedApi")
    public static final int CRYPTO_MODE_AES_CTR = MediaCodec.CRYPTO_MODE_AES_CTR;

    /**
     * @see AudioFormat#ENCODING_AC3
     */
    @SuppressWarnings("InlinedApi")
    public static final int ENCODING_AC3 = AudioFormat.ENCODING_AC3;

    /**
     * @see AudioFormat#ENCODING_E_AC3
     */
    @SuppressWarnings("InlinedApi")
    public static final int ENCODING_E_AC3 = AudioFormat.ENCODING_E_AC3;

    /**
     * @see MediaExtractor#SAMPLE_FLAG_SYNC
     */
    @SuppressWarnings("InlinedApi")
    public static final int SAMPLE_FLAG_SYNC = MediaExtractor.SAMPLE_FLAG_SYNC;

    /**
     * @see MediaExtractor#SAMPLE_FLAG_ENCRYPTED
     */
    @SuppressWarnings("InlinedApi")
    public static final int SAMPLE_FLAG_ENCRYPTED = MediaExtractor.SAMPLE_FLAG_ENCRYPTED;

    /**
     * Indicates that a sample should be decoded but not rendered.
     */
    public static final int SAMPLE_FLAG_DECODE_ONLY = 0x8000000;

    /**
     * A return value for methods where the end of an input was encountered.
     */
    public static final int RESULT_END_OF_INPUT = -1;

    public static final String VERSION = "1.3.3";

    /**
     * The version of the library, expressed as an integer.
     * <p>
     * Three digits are used for each component of {@link #VERSION}. For example "1.2.3" has the
     * corresponding integer version 001002003.
     */
    public static final int VERSION_INT = 001003003;

    /**
     * Whether the library was compiled with {@link Assertions}
     * checks enabled.
     */
    public static final boolean ASSERTIONS_ENABLED = true;

    /**
     * Whether the library was compiled with
     * trace enabled.
     */
    public static final boolean TRACE_ENABLED = true;



    private Constants() {}
}
