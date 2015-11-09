package com.mbakshi.decodeframe.FrameResources.Util.Media;

import android.media.AudioFormat;

import com.mbakshi.decodeframe.FrameResources.Constants;

/**
 * Created by mbakshi on 20/08/15.
 */
public class MimeTypes {
    public static final String BASE_TYPE_VIDEO = "video";
    public static final String BASE_TYPE_AUDIO = "audio";
    public static final String BASE_TYPE_TEXT = "text";
    public static final String BASE_TYPE_APPLICATION = "application";

    public static final String VIDEO_MP4 = BASE_TYPE_VIDEO + "/mp4";
    public static final String VIDEO_WEBM = BASE_TYPE_VIDEO + "/webm";
    public static final String VIDEO_H264 = BASE_TYPE_VIDEO + "/avc";
    public static final String VIDEO_VP8 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp8";
    public static final String VIDEO_VP9 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9";
    public static final String VIDEO_MP4V = BASE_TYPE_VIDEO + "/mp4v-es";

    public static final String AUDIO_MP4 = BASE_TYPE_AUDIO + "/mp4";
    public static final String AUDIO_AAC = BASE_TYPE_AUDIO + "/mp4a-latm";
    public static final String AUDIO_WEBM = BASE_TYPE_AUDIO + "/webm";
    public static final String AUDIO_MPEG = BASE_TYPE_AUDIO + "/mpeg";
    public static final String AUDIO_MPEG_L1 = BASE_TYPE_AUDIO + "/mpeg-L1";
    public static final String AUDIO_MPEG_L2 = BASE_TYPE_AUDIO + "/mpeg-L2";

    public static final String AUDIO_RAW = BASE_TYPE_AUDIO + "/raw";
    public static final String AUDIO_AC3 = BASE_TYPE_AUDIO + "/ac3";
    public static final String AUDIO_EC3 = BASE_TYPE_AUDIO + "/eac3";

    public static final String AUDIO_VORBIS = BASE_TYPE_AUDIO + "/vorbis";
    public static final String AUDIO_OPUS = BASE_TYPE_AUDIO + "/opus";

    public static final String TEXT_VTT = BASE_TYPE_TEXT + "/vtt";

    public static final String APPLICATION_ID3 = BASE_TYPE_APPLICATION + "/id3";
    public static final String APPLICATION_EIA608 = BASE_TYPE_APPLICATION + "/eia-608";
    public static final String APPLICATION_TTML = BASE_TYPE_APPLICATION + "/ttml+xml";
    public static final String APPLICATION_M3U8 = BASE_TYPE_APPLICATION + "/x-mpegURL";

    private MimeTypes() {}

    /**
     * Returns the top-level type of {@code mimeType}.
     *
     * @param mimeType The mimeType whose top-level type is required.
     * @return The top-level type.
     */
    public static String getTopLevelType(String mimeType) {
        int indexOfSlash = mimeType.indexOf('/');
        if (indexOfSlash == -1) {
            throw new IllegalArgumentException("Invalid mime type: " + mimeType);
        }
        return mimeType.substring(0, indexOfSlash);
    }

    /**
     * Whether the top-level type of {@code mimeType} is audio.
     *
     * @param mimeType The mimeType to test.
     * @return Whether the top level type is audio.
     */
    public static boolean isAudio(String mimeType) {
        return getTopLevelType(mimeType).equals(BASE_TYPE_AUDIO);
    }

    /**
     * Whether the top-level type of {@code mimeType} is video.
     *
     * @param mimeType The mimeType to test.
     * @return Whether the top level type is video.
     */
    public static boolean isVideo(String mimeType) {
        return getTopLevelType(mimeType).equals(BASE_TYPE_VIDEO);
    }

    /**
     * Whether the top-level type of {@code mimeType} is text.
     *
     * @param mimeType The mimeType to test.
     * @return Whether the top level type is text.
     */
    public static boolean isText(String mimeType) {
        return getTopLevelType(mimeType).equals(BASE_TYPE_TEXT);
    }

    /**
     * Whether the top-level type of {@code mimeType} is application.
     *
     * @param mimeType The mimeType to test.
     * @return Whether the top level type is application.
     */
    public static boolean isApplication(String mimeType) {
        return getTopLevelType(mimeType).equals(BASE_TYPE_APPLICATION);
    }

    /**
     * Whether the mimeType is {@link #APPLICATION_TTML}.
     *
     * @param mimeType The mimeType to test.
     * @return Whether the mimeType is {@link #APPLICATION_TTML}.
     */
    public static boolean isTtml(String mimeType) {
        return mimeType.equals(APPLICATION_TTML);
    }

    /**
     * Returns the output audio encoding that will result from processing input in {@code mimeType}.
     * For non-passthrough audio formats, this is always {@link AudioFormat#ENCODING_PCM_16BIT}. For
     * passthrough formats it will be one of {@link AudioFormat}'s other {@code ENCODING_*} constants.
     * For non-audio formats, {@link AudioFormat#ENCODING_INVALID} will be returned.
     *
     * @param mimeType The MIME type of media that will be decoded (or passed through).
     * @return The corresponding {@link AudioFormat} encoding.
     */
    public static int getEncodingForMimeType(String mimeType) {
        if (AUDIO_AC3.equals(mimeType)) {
            return Constants.ENCODING_AC3;
        }
        if (AUDIO_EC3.equals(mimeType)) {
            return Constants.ENCODING_E_AC3;
        }

        // All other audio formats will be decoded to 16-bit PCM.
        return isAudio(mimeType) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_INVALID;
    }


    public static boolean isPassthroughAudio(String mimeType) {
        return AUDIO_AC3.equals(mimeType) || AUDIO_EC3.equals(mimeType);
    }
}
