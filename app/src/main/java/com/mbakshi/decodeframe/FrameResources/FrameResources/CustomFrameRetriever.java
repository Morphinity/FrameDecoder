package com.mbakshi.decodeframe.FrameResources.FrameResources;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;


import com.mbakshi.decodeframe.ClipExtractor;
import com.mbakshi.decodeframe.FrameResources.Util.CodecUtil.MediaCodecUtil;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MimeTypes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Created by mbakshi on 11/09/15.
 */
public final class CustomFrameRetriever {
    private static final String TAG = "CustomFrameRetriever";
    private static int TARGET_WIDTH;
    private static int TARGET_HEIGHT;

    private static CustomFrameRetriever instance;

    private MediaCodec mediaCodec;
    private boolean decoderInit;
    private ClipExtractor extractor;
    private boolean extractorInit;

    private android.media.MediaFormat format;
    private android.media.MediaFormat outputFormat;
    private MediaFormat customFormat;
    private boolean frameBuffered;

    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private boolean endOfInputStream;
    private boolean endOfOutputStream;

    private Bitmap thumbnail;
    int rotation;

    private int decodeTryCount;
    //private final int MAX_DECODE_COUNT = 1000;

    public static CustomFrameRetriever getInstance() {
        if(instance == null) {
            instance = new CustomFrameRetriever();
        }
        return instance;
    }

    private int inputColorFormat;

    private CustomFrameRetriever() {

    }

    public boolean prepare(long time, ClipExtractor extractor, int targetWidth, int targetHeight) {
        this.extractor = extractor;
        this.TARGET_HEIGHT = targetHeight;
        this.TARGET_WIDTH = targetWidth;
        decoderInit = false;
        frameBuffered = false;
        thumbnail = null;

        // assume the extractor has been prepared but track has not been selected
        Log.i("ThumbLog", "CustomFrameRet:prepare " + time);
        Log.i("ThumbLog", "CustomFrameRet:targetH " + targetHeight + " targetW " + targetWidth);
        extractorInit = initExtractor();
        if(extractorInit) {
            Log.i("ThumbLog", "Prepare:ExtactorInit done");
        }

        decoderInit = initDecoder(true);
        if(!decoderInit) {
            decoderInit = initDecoder(false);
        }
        if(decoderInit) {
            Log.i("ThumbLog", "DecoderInit true");
            seekTo(time);
            frameBuffered = false;
            decodeTryCount = 0;
            while(!frameBuffered && !endOfOutputStream) { //&& (decodeTryCount < MAX_DECODE_COUNT)
                feedInput();
                getDecodedOutput();
            }
            if(endOfOutputStream) {
                Log.i("ThumbLog", "EOS");
            }/*
            if(decodeTryCount >= MAX_DECODE_COUNT) {
                Log.i("ThumbLog", "DecodeCount exc limit");
            }*/
        }
        if(decoderInit && frameBuffered) {
            Log.i("ThumbLog", "Releasing decoder");
            releaseInternal();
        }
        return decoderInit;
    }

    public int getRotation() {
        return rotation;
    }

    public void release() {
        if(thumbnail != null) {
            thumbnail.recycle();
            thumbnail = null;
        }
        releaseInternal();
    }

    private void releaseInternal() {
        releaseDecoder();
    }

    private boolean initExtractor() {
        // assume extractor setDataSource has been done
        int trackCount = extractor.getTrackCount();
        int trackIndex = -1;
        for(int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            if(format == null) {
                continue;
            }
            if(MimeTypes.isVideo(format.mimeType)) {
                trackIndex = i;
                this.format = format.getFrameworkMediaFormatV16();
                this.customFormat = format;
                break;
            }
        }
        if(trackIndex != -1) {
            extractor.selectTrack(trackIndex);
            return true;
        }
        else {
            Log.e(TAG, "Could not find the video track");
            return false;
        }
    }

    private void codecConfigure(MediaCodec mediaCodec, android.media.MediaFormat format, boolean software) {
        if(false) {
            int height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT);
            int width = format.getInteger(android.media.MediaFormat.KEY_WIDTH);
            Log.i("ThumbLog", "configure w " + width + " h " + height);
            while((height >> 1) > TARGET_HEIGHT || (width >> 1) > TARGET_WIDTH) {
                height = height >> 1;
                width = width >> 1;
            }
            Log.i("ThumbLog", "configure new w " + width + " h" + height);
            format.setInteger(android.media.MediaFormat.KEY_WIDTH, width);
            format.setInteger(android.media.MediaFormat.KEY_HEIGHT, height);
        }
        mediaCodec.configure(format, null, null, 0);
    }

    private boolean initDecoder(boolean software) {
        Log.i("ThumbLog", "Initializing decoder " + software);
        boolean error = false;
        if(!extractorInit || format == null) {
            return false;
        }

        MediaCodecUtil.DecoderInfo decoderInfo = null;

        String mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
        try {
            decoderInfo = MediaCodecUtil.getDecoderInfo(mimeType, false, software);
        } catch (MediaCodecUtil.DecoderQueryException e) {
            Log.e(TAG, "DecoderQueryExc " + e);
            error = true;
        }

        if (decoderInfo == null) {
            error = true;
        }

        if(error) {
            Log.e(TAG, "Could not create decoderInfo for mime " + mimeType + " software " + software);
            return false;
        }

        Log.i("ThumbLog", "initDecoder:mimetype " + mimeType);
        String decoderName = decoderInfo.name;
        try {
            mediaCodec = MediaCodec.createByCodecName(decoderName);
            /*
            android.media.MediaFormat outputFormat = android.media.MediaFormat.createVideoFormat(format.getString(android.media.MediaFormat.KEY_MIME),
                                                                                                        format.getInteger(android.media.MediaFormat.KEY_WIDTH),
                                                                                                        format.getInteger(android.media.MediaFormat.KEY_HEIGHT));
            */
            MediaCodecInfo info = mediaCodec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mimeType);
            int[] colorFormats = capabilities.colorFormats;
            inputColorFormat = -1;
            for(int i = 0; i < colorFormats.length; i++) {
                if(colorFormats[i] == 19) {
                    Log.i("ThumbLog", "" + colorFormats[i]);
                    inputColorFormat = 19;
                    break;
                }
                else if(colorFormats[i] == 21) {
                    Log.i("ThumbLog", "" + colorFormats[i]);
                    inputColorFormat = 21;
                    break;
                }
            }
            if(inputColorFormat != -1) {
                format.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, inputColorFormat);
            }

            //format.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, 21);

            //outputFormat = null;
            codecConfigure(mediaCodec, format, software);
            mediaCodec.start();
            //outputFormat = mediaCodec.getOutputFormat();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // no need to get ByteBuffer list...has direct API to get the utput buffer
            } else {
                inputBuffers = mediaCodec.getInputBuffers();
                outputBuffers = mediaCodec.getOutputBuffers();
            }
        }
        catch (Exception ioexc) {
            Log.e("ThumbLog", "initDecoder:Could not create decoder (mimeType) " + mimeType + " software " + software + ioexc);
            error = true;
        }

        if(error) {
            return false;
        }

        endOfInputStream = false;
        endOfOutputStream = false;
        return true;
    }

    private void feedInput() {
        if(endOfInputStream) {
            return;
        }
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(100);
        if(inputBufferIndex >= 0) {
            ByteBuffer buf;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                buf = getInputBufferSDK21(inputBufferIndex);
            } else {
                buf = inputBuffers[inputBufferIndex];
            }

            int sampleSize = extractor.readSampleData(buf, 0);
            Log.i("ThumbLog", "Flag - " + (extractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_SYNC_FRAME));
            long presentationTime = 0;
            if(sampleSize == -2) {
                Log.i("ThumbLog", "FeedData:SampleNotFound");
                sampleSize = 0;
            }
            else if(sampleSize == - 1) {
                Log.i("ThumbLog", "FeedData:Endofstream");
                endOfInputStream = true;
                sampleSize = 0;
            }
            else {
                presentationTime = extractor.getSampleTime();
            }
            Log.i("ThumbLog", "Sample size " + sampleSize + " presenetationTime " + presentationTime);

            mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, endOfInputStream ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

            if(!endOfInputStream) {
                extractor.advance();
            }
        }
    }

    private void getDecodedOutput() {
        if(endOfOutputStream || mediaCodec == null) {
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = mediaCodec.dequeueOutputBuffer(info, 0);
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i("ThumbLog", "Output format changed");
            outputFormat = mediaCodec.getOutputFormat();
            outputIndex = -1;
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            Log.i("ThumbLog", "output buffers changed");
            outputBuffers = mediaCodec.getOutputBuffers();
            outputIndex = -1;
        } else if (outputIndex < 0) {
            //Log.i("ThumbLog", "Decode: outputIndex " + outputIndex);
            decodeTryCount++;
        }

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i("ThumbLog", "decode:endofoutput");
            endOfOutputStream = true;
        }

        if(outputIndex >= 0) {
            ByteBuffer decodedBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                decodedBuffer = getOutputBufferSDK21(outputIndex);
            }
            else {
                decodedBuffer = outputBuffers[outputIndex];
            }

            Log.i("ThumbLog", "Decoded at " + info.presentationTimeUs);
            if(!frameBuffered) {
                decodedBuffer.position(info.offset);
                decodedBuffer.limit(info.size);

                getBitmapFromBuffer(decodedBuffer);
                frameBuffered = true;
            }

            mediaCodec.releaseOutputBuffer(outputIndex, false);

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                this.endOfOutputStream = true;
                releaseDecoder();
            }
        }
    }

    private void releaseDecoder() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }

    private void seekTo(long time) {
        endOfOutputStream = false;
        endOfInputStream = false;
        //mediaCodec.flush();
        extractor.seekTo(time);
    }

    private void getBitmapFromBuffer(ByteBuffer decodedBuffer) {
        Log.i("ThumbLog", "Decodedbuffer size " + decodedBuffer.remaining());
        byte[] byteArray = new byte[decodedBuffer.remaining()];
        decodedBuffer.get(byteArray);
        int inputWidth = format.getInteger(android.media.MediaFormat.KEY_WIDTH);
        int inputHeight = format.getInteger(android.media.MediaFormat.KEY_HEIGHT);
        int outputWidth = outputFormat.getInteger(android.media.MediaFormat.KEY_WIDTH);
        int outputHeight = outputFormat.getInteger(android.media.MediaFormat.KEY_HEIGHT);
        rotation = customFormat.rotation;
        int stride = outputFormat.getInteger("stride");
        int slice = outputFormat.getInteger("slice-height");

        Log.i("ThumbLog", " stride" + stride + " slice " + slice);
        Log.i("ThumbLog", "OutWidth " + outputWidth + " OutHeight " + outputHeight);
        Log.i("ThumbLog", "InputWidth " + inputWidth + " InputHeight " + inputHeight + " rotation " + rotation);
        //width = stride;
        //height = slice;

        // check output color format and convert to rgb pixel array
        int[] pixels = null;
        boolean colorFormatNotFound = false;
        if(outputFormat != null) {
            Log.i("ThumbLog", "OutputFormat found");
            int colorFormat = outputFormat.getInteger(android.media.MediaFormat.KEY_COLOR_FORMAT);
            if(colorFormat != inputColorFormat) {
                Log.w("ThumbLog", "Configure colorFormat not same as output color format");
            }
            switch(colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar :
                    Log.i("ThumbLog", "ColorFornat planar");
                    pixels = convertYUV420PlanarToARGB(byteArray, inputWidth, inputHeight, stride, slice);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar :
                    Log.i("ThumbLog", "ColorFomrat semiplanar");
                    pixels = convertYUV420SemiPlanarToARGB(byteArray, inputWidth, inputHeight, stride, slice);
                    break;
                default :
                    colorFormatNotFound = true;
                    Log.i("ThumbLog", "Color format not found " + colorFormat);
            }
        }
        else { //assume YUV420SemiPlanar
            pixels = convertYUV420SemiPlanarToARGB(byteArray, inputWidth, inputHeight, stride, slice);
        }
        if(colorFormatNotFound) {
            pixels = convertYUV420SemiPlanarToARGB(byteArray, inputWidth, inputHeight, stride, slice);
        }
        Log.i("ThumbLog", "Received Pixels " + pixels.length);
        Bitmap bmp = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        pixels = null;
        if (bmp != null) {
            thumbnail = bmp;
            Log.i("ThumbLog", "Created thumbnail");
        }
        else if (bmp == null) {
            Log.e("ThumbLog", "Could not create bitmap");
        }
        //saveBitmapToFile();
    }

    private void saveBitmapToFile() {
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/thumb.png";
        File file = new File(filePath);
        try {
            FileOutputStream fout = new FileOutputStream(file);
            thumbnail.compress(Bitmap.CompressFormat.PNG, 85, fout);
        }
        catch (FileNotFoundException ex) {
            Log.e("ThumbLog", "SaveBitmap fail " + ex);
        }
    }

    private int[] getPixelsFromByteBuffer(byte[] buffer) {
        IntBuffer intBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        int[] array = new int[intBuffer.remaining()];
        intBuffer.get(array);
        return array;
    }

    @TargetApi(21)
    private ByteBuffer getInputBufferSDK21(int bufferIndex) {
        return mediaCodec.getInputBuffer(bufferIndex);
    }

    @TargetApi(21)
    private ByteBuffer getOutputBufferSDK21(int bufferIndex) {
        return mediaCodec.getOutputBuffer(bufferIndex);
    }

    public Bitmap getFrame() {
        return thumbnail;
    }

    public static int[] convertYUV420PlanarToARGB(byte[] data, int width, int height) {
        int size = width * height;
        int offset = size;
        int[] pixels = new int[size];
        int u, v, y1, y2, y3, y4;
        for(int i=0, k=0; i < size; i+=2, k+=1) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[width+i] & 0xff;
            y4 = data[width+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+(size/4)] & 0xff;
            u = u - 128;
            v = v - 128;

            pixels[i] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

            if (i!=0 && (i+2) % width == 0)
                i += width;
        }

        return pixels;

    }

    public static int[] convertYUV420PlanarToARGB(byte[] data, int width, int height, int stride, int slice) {
        int size = stride * height;
        int offset = stride * slice;
        int[] pixels = new int[width * height];
        Log.i("ThumbLog", "Conversion start");
        int u, v, y1, y2, y3, y4, row;
        row = 0;
        for(int i=0, k=0; i < size; i+=2, k+=1) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[stride+i] & 0xff;
            y4 = data[stride+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+(offset/4)] & 0xff;
            u = u - 128;
            v = v - 128;

            pixels[i] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

            if(i != 0 && (i - (row * stride) + 2) % width == 0) {
                i += 2 * stride - width;
                row += 2;
                k += (stride - width) / 2;
                //Log.i("ThumbLog", "i " + i + " k " + k + " size " + size + " offset " + offset);
            }
        }
        Log.i("ThumbLog", "Conversion end");
        return pixels;
    }

    public static int[] convertYUV420SemiPlanarToARGB(byte [] data, int width, int height) {
        int size = width * height;
        int offset = size;
        int[] pixels = new int[size];
        int u, v, y1, y2, y3, y4;

        // i percorre os Y and the final pixels
        // k percorre os pixles U e V
        for(int i=0, k=0; i < size; i+=2, k+=2) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[width+i] & 0xff;
            y4 = data[width+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+1] & 0xff;
            u = u - 128;
            v = v - 128;

            pixels[i] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

            if (i!=0 && (i+2) % width == 0)
                i += width;
        }

        return pixels;
    }

    public static int[] convertYUV420SemiPlanarToARGB(byte[] data, int width, int height, int stride, int slice) {
        int size = stride * height;
        int offset = slice * stride;
        int[] pixels = new int[width * height];
        int u,v,y1,y2,y3,y4,row;
        Log.i("ThumbLog", "Conversion start");
        row = 0;
        for(int i = 0, k = 0; i < size; i += 2, k += 2) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[stride+i] & 0xff;
            y4 = data[stride+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+1] & 0xff;
            u = u - 128;
            v = v - 128;

            pixels[i] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

            if(i != 0 && (i - (row * stride) + 2) % width == 0) {
                i += 2 * stride - width;
                row += 2;
                k += (stride - width);
                //Log.i("ThumbLog", "i " + i + " k " + k + " size " + size + " offset " + offset);
            }
        }
        Log.i("ThumbLog", "Conversion Complete");
        return pixels;
    }

    public static byte[] convertYUV420PlanarToARGBByte(byte[] data, int width, int height) {
        int size = width * height;
        int offset = size;
        byte[] bytepixels = new byte[size * 4];

        int u, v, y1, y2, y3, y4;
        for(int i=0, k=0; i < size; i+=2, k+=1) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[width+i] & 0xff;
            y4 = data[width+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+(size/4)] & 0xff;
            u = u - 128;
            v = v - 128;

            convertYUVtoRGBByte(y1, u, v, bytepixels, i*4); // pixels[i] =
            convertYUVtoRGBByte(y2, u, v, bytepixels, (i+1)*4); // pixels[i+1] =
            convertYUVtoRGBByte(y3, u, v, bytepixels, (width + i)*4); // pixels[width+i] =
            convertYUVtoRGBByte(y4, u, v, bytepixels, (width+i+1)*4); // pixels[width+i+1] =

            if (i!=0 && (i+2) % width == 0)
                i += width;
        }

        return bytepixels;

    }

    public static byte[] convertYUV420SemiPlanarToARGBByte(byte[] data, int width, int height) {
        int size = width * height;
        int offset = size;
        byte[] pixels = new byte[size*4];
        int u, v, y1, y2, y3, y4;

        // i percorre os Y and the final pixels
        // k percorre os pixles U e V
        for(int i=0, k=0; i < size; i+=2, k+=2) {
            y1 = data[i] & 0xff;
            y2 = data[i+1] & 0xff;
            y3 = data[width+i] & 0xff;
            y4 = data[width+i+1] & 0xff;

            u = data[offset+k] & 0xff;
            v = data[offset+k+1] & 0xff;
            u = u - 128;
            v = v - 128;

            convertYUVtoRGBByte(y1, u, v, pixels, i*4); // pixels[i] =
            convertYUVtoRGBByte(y2, u, v, pixels, (i + 1) * 4); // pixels[i+1] =
            convertYUVtoRGBByte(y3, u, v, pixels, (width+i)*4); // pixels[width+i] =
            convertYUVtoRGBByte(y4, u, v, pixels, (width+i+1)*4); // pixels[width+i+1] =

            if (i!=0 && (i+2) % width == 0)
                i += width;
        }
        return pixels;
    }

    private static void convertYUVtoRGBByte(int y, int u, int v, byte[] byteArr, int position) {
        int r,g,b;
        r = y + (int) 1.402f * v;
        g = y - (int) (0.344f * u + 0.714f * v);
        b = y + (int) 1.772f * u;
        r = r > 255 ? 255 : r < 0 ? 0 : r;
        g = g > 255 ? 255 : g < 0 ? 0 : g;
        b = b > 255 ? 255 : b < 0 ? 0 : b;

        byteArr[position] = (byte)0xff;
        byteArr[position+1] = (byte) r;
        byteArr[position+2] = (byte) g;
        byteArr[position+3] = (byte) b;
    }

    private static int convertYUVtoRGB(int y, int u, int v) {
        int r,g,b;
        r = y + (int) 1.402f * v;
        g = y - (int) (0.344f * u + 0.714f * v);
        b = y + (int) 1.772f * u;
        r = r > 255 ? 255 : r < 0 ? 0 : r;
        g = g > 255 ? 255 : g < 0 ? 0 : g;
        b = b > 255 ? 255 : b < 0 ? 0 : b;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static Bitmap handleRotation(Bitmap sourceBitmap, int rotation) {
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(sourceBitmap, 0, 0, width, height, matrix, true);
    }
}
