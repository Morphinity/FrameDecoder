package com.mbakshi.decodeframe.FrameResources.Util.Media;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;

import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by mbakshi on 20/08/15.
 */
public class MediaFormat {
    private static final String KEY_PIXEL_WIDTH_HEIGHT_RATIO =
            "com.google.android.videos.pixelWidthHeightRatio";

    public static final int NO_VALUE = -1;

    public final String mimeType;
    public final int maxInputSize;

    public final long durationUs;

    public final int width;
    public final int height;
    public int rotation;
    public final float pixelWidthHeightRatio;

    public final int channelCount;
    public final int sampleRate;

    public final List<byte[]> initializationData;

    private int maxWidth;
    private int maxHeight;

    // Lazy-initialized hashcode.
    private int hashCode;
    // Possibly-lazy-initialized framework media format.
    private android.media.MediaFormat frameworkMediaFormat;

    @TargetApi(16)
    public static MediaFormat createFromFrameworkMediaFormatV16(android.media.MediaFormat format) {
        return new MediaFormat(format);
    }

    public static MediaFormat createVideoFormat(String mimeType, int maxInputSize, int width,
                                                int height, List<byte[]> initializationData) {
        return createVideoFormat(
                mimeType, maxInputSize, Constants.UNKNOWN_TIME_US, width, height, 0, initializationData);
    }

    public static MediaFormat createVideoFormat(String mimeType, int maxInputSize, long durationUs,
                                                int width, int height, int rotation, List<byte[]> initializationData) {
        return createVideoFormat(
                mimeType, maxInputSize, durationUs, width, height, rotation, 1, initializationData);
    }

    public static MediaFormat createVideoFormat(String mimeType, int maxInputSize, long durationUs,
                                                int width, int height, int rotation, float pixelWidthHeightRatio, List<byte[]> initializationData) {
        return new MediaFormat(mimeType, maxInputSize, durationUs, width, height, rotation, pixelWidthHeightRatio,
                NO_VALUE, NO_VALUE, initializationData);
    }

    public static MediaFormat createAudioFormat(String mimeType, int maxInputSize, int channelCount,
                                                int sampleRate, List<byte[]> initializationData) {
        return createAudioFormat(
                mimeType, maxInputSize, Constants.UNKNOWN_TIME_US, channelCount, sampleRate, initializationData);
    }

    public static MediaFormat createAudioFormat(String mimeType, int maxInputSize, long durationUs,
                                                int channelCount, int sampleRate, List<byte[]> initializationData) {
        return new MediaFormat(mimeType, maxInputSize, durationUs, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                channelCount, sampleRate, initializationData);
    }

    public static MediaFormat createId3Format() {
        return createFormatForMimeType(MimeTypes.APPLICATION_ID3);
    }

    public static MediaFormat createEia608Format() {
        return createFormatForMimeType(MimeTypes.APPLICATION_EIA608);
    }

    public static MediaFormat createTtmlFormat() {
        return createFormatForMimeType(MimeTypes.APPLICATION_TTML);
    }

    public static MediaFormat createFormatForMimeType(String mimeType) {
        return new MediaFormat(mimeType, NO_VALUE, Constants.UNKNOWN_TIME_US, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, null);
    }

    @TargetApi(16)
    private MediaFormat(android.media.MediaFormat format) {
        this.frameworkMediaFormat = format;
        mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
        maxInputSize = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE);
        width = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_WIDTH);
        height = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT);
        rotation = 0; //TODO if API >= 23 then use KEY_ROTATION
        channelCount = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT);
        sampleRate = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE);
        pixelWidthHeightRatio = getOptionalFloatV16(format, KEY_PIXEL_WIDTH_HEIGHT_RATIO);
        initializationData = new ArrayList<byte[]>();
        for (int i = 0; format.containsKey("csd-" + i); i++) {
            ByteBuffer buffer = format.getByteBuffer("csd-" + i);
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            initializationData.add(data);
            buffer.flip();
        }
        durationUs = format.containsKey(android.media.MediaFormat.KEY_DURATION)
                ? format.getLong(android.media.MediaFormat.KEY_DURATION) : Constants.UNKNOWN_TIME_US;
        maxWidth = NO_VALUE;
        maxHeight = NO_VALUE;
    }

    private MediaFormat(String mimeType, int maxInputSize, long durationUs, int width, int height, int rotation,
                        float pixelWidthHeightRatio, int channelCount, int sampleRate,
                        List<byte[]> initializationData) {
        this.mimeType = mimeType;
        this.maxInputSize = maxInputSize;
        this.durationUs = durationUs;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.pixelWidthHeightRatio = pixelWidthHeightRatio;
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;
        this.initializationData = initializationData == null ? Collections.<byte[]>emptyList()
                : initializationData;
        maxWidth = NO_VALUE;
        maxHeight = NO_VALUE;
    }

    public void setMaxVideoDimensions(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        if (frameworkMediaFormat != null) {
            maybeSetMaxDimensionsV16(frameworkMediaFormat);
        }
    }

    public int getMaxVideoWidth() {
        return maxWidth;
    }

    public int getMaxVideoHeight() {
        return maxHeight;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = 17;
            result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
            result = 31 * result + maxInputSize;
            result = 31 * result + width;
            result = 31 * result + height;
            result = 31 * result + Float.floatToRawIntBits(pixelWidthHeightRatio);
            result = 31 * result + (int) durationUs;
            result = 31 * result + maxWidth;
            result = 31 * result + maxHeight;
            result = 31 * result + channelCount;
            result = 31 * result + sampleRate;
            for (int i = 0; i < initializationData.size(); i++) {
                result = 31 * result + Arrays.hashCode(initializationData.get(i));
            }
            hashCode = result;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return equalsInternal((MediaFormat) obj, false);
    }

    public boolean equals(MediaFormat other, boolean ignoreMaxDimensions) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        return equalsInternal(other, ignoreMaxDimensions);
    }

    private boolean equalsInternal(MediaFormat other, boolean ignoreMaxDimensions) {
        if (maxInputSize != other.maxInputSize || width != other.width || height != other.height
                || pixelWidthHeightRatio != other.pixelWidthHeightRatio
                || (!ignoreMaxDimensions && (maxWidth != other.maxWidth || maxHeight != other.maxHeight))
                || channelCount != other.channelCount || sampleRate != other.sampleRate
                || !Utilities.areEqual(mimeType, other.mimeType)
                || initializationData.size() != other.initializationData.size()) {
            return false;
        }
        for (int i = 0; i < initializationData.size(); i++) {
            if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MediaFormat(" + mimeType + ", " + maxInputSize + ", " + width + ", " + height + ", "
                + pixelWidthHeightRatio + ", " + channelCount + ", " + sampleRate + ", " + durationUs + ", "
                + maxWidth + ", " + maxHeight + ")";
    }

    /**
     * @return A {@link MediaFormat} representation of this format.
     */
    @TargetApi(16)
    public final android.media.MediaFormat getFrameworkMediaFormatV16() {
        if (frameworkMediaFormat == null) {
            android.media.MediaFormat format = new android.media.MediaFormat();
            format.setString(android.media.MediaFormat.KEY_MIME, mimeType);
            maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
            maybeSetIntegerV16(format, android.media.MediaFormat.KEY_WIDTH, width);
            maybeSetIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT, height);
            maybeSetIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT, channelCount);
            maybeSetIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            maybeSetFloatV16(format, KEY_PIXEL_WIDTH_HEIGHT_RATIO, pixelWidthHeightRatio);
            for (int i = 0; i < initializationData.size(); i++) {
                format.setByteBuffer("csd-" + i, ByteBuffer.wrap(initializationData.get(i)));
            }
            if (durationUs != Constants.UNKNOWN_TIME_US) {
                format.setLong(android.media.MediaFormat.KEY_DURATION, durationUs);
            }
            maybeSetMaxDimensionsV16(format);
            frameworkMediaFormat = format;
        }
        return frameworkMediaFormat;
    }

    @SuppressLint("InlinedApi")
    @TargetApi(16)
    private final void maybeSetMaxDimensionsV16(android.media.MediaFormat format) {
        maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_WIDTH, maxWidth);
        maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_HEIGHT, maxHeight);
    }

    @TargetApi(16)
    private static final void maybeSetIntegerV16(android.media.MediaFormat format, String key,
                                                 int value) {
        if (value != NO_VALUE) {
            format.setInteger(key, value);
        }
    }

    @TargetApi(16)
    private static final void maybeSetFloatV16(android.media.MediaFormat format, String key,
                                               float value) {
        if (value != NO_VALUE) {
            format.setFloat(key, value);
        }
    }

    @TargetApi(16)
    private static final int getOptionalIntegerV16(android.media.MediaFormat format, String key) {
        return format.containsKey(key) ? format.getInteger(key) : NO_VALUE;
    }

    @TargetApi(16)
    private static final float getOptionalFloatV16(android.media.MediaFormat format, String key) {
        return format.containsKey(key) ? format.getFloat(key) : NO_VALUE;
    }
}
