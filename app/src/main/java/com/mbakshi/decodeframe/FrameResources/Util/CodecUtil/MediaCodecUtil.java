package com.mbakshi.decodeframe.FrameResources.Util.CodecUtil;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.mbakshi.decodeframe.FrameResources.Util.Media.MimeTypes;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;

import java.util.HashMap;

/**
 * Created by mbakshi on 15/09/15.
 */
public class MediaCodecUtil {
    public final static class DecoderInfo {
        public final String name;
        public final boolean adaptive;

        DecoderInfo(String name, boolean adaptive) {
            this.name = name;
            this.adaptive = adaptive;
        }
    }

    /**
     * Thrown when an error occurs querying the device for its underlying media capabilities.
     * <p>
     * Such failures are not expected in normal operation and are normally temporary (e.g. if the
     * mediaserver process has crashed and is yet to restart).
     */
    public static class DecoderQueryException extends Exception {

        private DecoderQueryException(Throwable cause) {
            super("Failed to query underlying media codecs", cause);
        }

    }

    private static final String TAG = "MediaCodecUtil";

    private static final HashMap<CodecKey, Pair<String, MediaCodecInfo.CodecCapabilities>> codecs =
            new HashMap<>();

    /**
     * Get information about the decoder that will be used for a given mime type.
     *
     * @param mimeType The mime type.
     * @param secure Whether the decoder is required to support secure decryption. Always pass false
     *     unless secure decryption really is required.
     * @return Information about the decoder that will be used, or null if no decoder exists.
     */
    public static DecoderInfo getDecoderInfo(String mimeType, boolean secure, boolean software)
            throws DecoderQueryException {
        Pair<String, MediaCodecInfo.CodecCapabilities> info = getMediaCodecInfo(mimeType, secure, software);
        if (info == null) {
            return null;
        }
        return new DecoderInfo(info.first, isAdaptive(info.second));
    }

    /**
     * Optional call to warm the codec cache for a given mime type.
     * <p>
     * Calling this method may speed up subsequent calls to {@link #getDecoderInfo(String, boolean, boolean)}.
     *
     * @param mimeType The mime type.
     * @param secure Whether the decoder is required to support secure decryption. Always pass false
     *     unless secure decryption really is required.
     */
    public static synchronized void warmCodec(String mimeType, boolean secure, boolean software) {
        try {
            getMediaCodecInfo(mimeType, secure, software);
        } catch (DecoderQueryException e) {
            // Codec warming is best effort, so we can swallow the exception.
            Log.e(TAG, "Codec warming failed", e);
        }
    }

    /**
     * Returns the name of the best decoder and its capabilities for the given mimeType.
     */
    private static synchronized Pair<String, MediaCodecInfo.CodecCapabilities> getMediaCodecInfo(
            String mimeType, boolean secure, boolean software) throws DecoderQueryException {
        CodecKey key = new CodecKey(mimeType, secure, software);
        if (codecs.containsKey(key)) {
            return codecs.get(key);
        }
        MediaCodecListCompat mediaCodecList = Utilities.SDK_INT >= 21
                ? new MediaCodecListCompatV21(secure) : new MediaCodecListCompatV16();
        Pair<String, MediaCodecInfo.CodecCapabilities> codecInfo = getMediaCodecInfo(key, mediaCodecList, software);
        // TODO: Verify this cannot occur on v22, and change >= to == [Internal: b/18678462].
        if (secure && codecInfo == null && Utilities.SDK_INT >= 21) {
            // Some devices don't list secure decoders on API level 21. Try the legacy path.
            mediaCodecList = new MediaCodecListCompatV16();
            codecInfo = getMediaCodecInfo(key, mediaCodecList, software);
            if (codecInfo != null) {
                Log.w(TAG, "MediaCodecList API didn't list secure decoder for: " + mimeType
                        + ". Assuming: " + codecInfo.first);
            }
        }
        return codecInfo;
    }

    private static Pair<String, MediaCodecInfo.CodecCapabilities> getMediaCodecInfo(CodecKey key,
                                                                                    MediaCodecListCompat mediaCodecList,
                                                                                    boolean software) throws DecoderQueryException {
        try {
            return getMediaCodecInfoInternal(key, mediaCodecList, software);
        } catch (Exception e) {
            // If the underlying mediaserver is in a bad state, we may catch an IllegalStateException
            // or an IllegalArgumentException here.
            throw new DecoderQueryException(e);
        }
    }

    private static Pair<String, MediaCodecInfo.CodecCapabilities> getMediaCodecInfoInternal(CodecKey key,
                                                                                            MediaCodecListCompat mediaCodecList,
                                                                                            boolean software) {
        String mimeType = key.mimeType;
        int numberOfCodecs = mediaCodecList.getCodecCount();
        boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
        // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
        for (int i = 0; i < numberOfCodecs; i++) {
            MediaCodecInfo info = mediaCodecList.getCodecInfoAt(i);
            String codecName = info.getName();
            if (!info.isEncoder() && codecName.startsWith("OMX.")
                    && (secureDecodersExplicit || !codecName.endsWith(".secure")) &&
                    (!software ||  codecName.contains(".sw."))) {
                String[] supportedTypes = info.getSupportedTypes();
                for (String supportedType : supportedTypes) {
                    if (supportedType.equalsIgnoreCase(mimeType)) {
                        MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(supportedType);
                        boolean secure = mediaCodecList.isSecurePlaybackSupported(key.mimeType, capabilities);
                        if (!secureDecodersExplicit) {
                            // Cache variants for both insecure and (if we think it's supported) secure playback.
                            codecs.put(key.secure ? new CodecKey(mimeType, false, software) : key,
                                    Pair.create(codecName, capabilities));
                            if (secure) {
                                codecs.put(key.secure ? key : new CodecKey(mimeType, true, software),
                                        Pair.create(codecName + ".secure", capabilities));
                            }
                        } else {
                            // Only cache this variant. If both insecure and secure decoders are available, they
                            // should both be listed separately.
                            codecs.put(key.secure == secure ? key : new CodecKey(mimeType, secure, software),
                                    Pair.create(codecName, capabilities));
                        }
                        if (codecs.containsKey(key)) {
                            return codecs.get(key);
                        }
                    }
                }
            }
        }
        return null;
    }



    private static boolean isAdaptive(MediaCodecInfo.CodecCapabilities capabilities) {
        return Utilities.SDK_INT >= 19 && isAdaptiveV19(capabilities);
    }

    @TargetApi(19)
    private static boolean isAdaptiveV19(MediaCodecInfo.CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback);
    }

    /**
     * @param profile An AVC profile constant from {@link MediaCodecInfo.CodecProfileLevel}.
     * @param level An AVC profile level from {@link MediaCodecInfo.CodecProfileLevel}.
     * @return Whether the specified profile is supported at the specified level.
     */
    public static boolean isH264ProfileSupported(int profile, int level)
            throws DecoderQueryException {
        Pair<String, MediaCodecInfo.CodecCapabilities> info = getMediaCodecInfo(MimeTypes.VIDEO_H264, false, false);
        if (info == null) {
            return false;
        }

        MediaCodecInfo.CodecCapabilities capabilities = info.second;
        for (int i = 0; i < capabilities.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel profileLevel = capabilities.profileLevels[i];
            if (profileLevel.profile == profile && profileLevel.level >= level) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the maximum frame size for an H264 stream that can be decoded on the device.
     */
    public static int maxH264DecodableFrameSize() throws DecoderQueryException {
        Pair<String, MediaCodecInfo.CodecCapabilities> info = getMediaCodecInfo(MimeTypes.VIDEO_H264, false, false);
        if (info == null) {
            return 0;
        }

        int maxH264DecodableFrameSize = 0;
        MediaCodecInfo.CodecCapabilities capabilities = info.second;
        for (int i = 0; i < capabilities.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel profileLevel = capabilities.profileLevels[i];
            maxH264DecodableFrameSize = Math.max(
                    avcLevelToMaxFrameSize(profileLevel.level), maxH264DecodableFrameSize);
        }

        return maxH264DecodableFrameSize;
    }

    /**
     * Conversion values taken from: https://en.wikipedia.org/wiki/H.264/MPEG-4_AVC.
     *
     * @param avcLevel one of CodecProfileLevel.AVCLevel* constants.
     * @return maximum frame size that can be decoded by a decoder with the specified avc level
     *      (or {@code -1} if the level is not recognized)
     */
    private static int avcLevelToMaxFrameSize(int avcLevel) {
        switch (avcLevel) {
            case MediaCodecInfo.CodecProfileLevel.AVCLevel1: return 25344;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel1b: return 25344;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel12: return 101376;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel13: return 101376;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel2: return 101376;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel21: return 202752;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel22: return 414720;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel3: return 414720;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel31: return 921600;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel32: return 1310720;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel4: return 2097152;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel41: return 2097152;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel42: return 2228224;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel5: return 5652480;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel51: return 9437184;
            default: return -1;
        }
    }

    private interface MediaCodecListCompat {

        /**
         * The number of codecs in the list.
         */
        int getCodecCount();

        /**
         * The info at the specified index in the list.
         *
         * @param index The index.
         */
        MediaCodecInfo getCodecInfoAt(int index);

        /**
         * @return Returns whether secure decoders are explicitly listed, if present.
         */
        boolean secureDecodersExplicit();

        /**
         * Whether secure playback is supported for the given {@link MediaCodecInfo.CodecCapabilities}, which should
         * have been obtained from a {@link MediaCodecInfo} obtained from this list.
         */
        boolean isSecurePlaybackSupported(String mimeType, MediaCodecInfo.CodecCapabilities capabilities);

    }

    @TargetApi(21)
    private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

        private final int codecKind;

        private MediaCodecInfo[] mediaCodecInfos;

        public MediaCodecListCompatV21(boolean includeSecure) {
            codecKind = includeSecure ? MediaCodecList.ALL_CODECS : MediaCodecList.REGULAR_CODECS;
        }

        @Override
        public int getCodecCount() {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos.length;
        }

        @Override
        public MediaCodecInfo getCodecInfoAt(int index) {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos[index];
        }

        @Override
        public boolean secureDecodersExplicit() {
            return true;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType, MediaCodecInfo.CodecCapabilities capabilities) {
            return capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback);
        }

        private void ensureMediaCodecInfosInitialized() {
            if (mediaCodecInfos == null) {
                mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
            }
        }

    }

    @SuppressWarnings("deprecation")
    private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

        @Override
        public int getCodecCount() {
            return MediaCodecList.getCodecCount();
        }

        @Override
        public MediaCodecInfo getCodecInfoAt(int index) {
            return MediaCodecList.getCodecInfoAt(index);
        }

        @Override
        public boolean secureDecodersExplicit() {
            return false;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType, MediaCodecInfo.CodecCapabilities capabilities) {
            // Secure decoders weren't explicitly listed prior to API level 21. We assume that a secure
            // H264 decoder exists.
            return MimeTypes.VIDEO_H264.equals(mimeType);
        }

    }

    private static final class CodecKey {

        public final String mimeType;
        public final boolean secure;
        public final boolean software;

        public CodecKey(String mimeType, boolean secure, boolean software) {
            this.mimeType = mimeType;
            this.secure = secure;
            this.software = software;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mimeType == null) ? 0 : mimeType.hashCode());
            result = prime * result + (secure ? 1231 : 1237);
            result = prime * result + (software ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || obj.getClass() != CodecKey.class) {
                return false;
            }
            CodecKey other = (CodecKey) obj;
            return TextUtils.equals(mimeType, other.mimeType) && secure == other.secure;
        }

    }
}
