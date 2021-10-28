/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package com.mako.videocompressor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;


import com.mako.videocompressor.utils.BuildVars;
import com.mako.videocompressor.utils.CacheUtils;
import com.mako.videocompressor.utils.FileLog;
import com.mako.videocompressor.utils.PhotoFilterView;
import com.mako.videocompressor.utils.VideoEditedInfo;
import com.mako.videocompressor.video.MediaCodecVideoConvertor;

import java.io.File;

public class MediaController {

    public final static String VIDEO_MIME_TYPE = "video/avc";
    public final static String AUIDO_MIME_TYPE = "audio/mp4a-latm";

    public static class SavedFilterState {
        public float enhanceValue;
        public float softenSkinValue;
        public float exposureValue;
        public float contrastValue;
        public float warmthValue;
        public float saturationValue;
        public float fadeValue;
        public int tintShadowsColor;
        public int tintHighlightsColor;
        public float highlightsValue;
        public float shadowsValue;
        public float vignetteValue;
        public float grainValue;
        public int blurType;
        public PhotoFilterView.CurvesToolValue curvesToolValue = new PhotoFilterView.CurvesToolValue();
        public float sharpenValue;
        public float blurExcludeSize;
        public Point blurExcludePoint;
        public float blurExcludeBlurSize;
        public float blurAngle;
    }

    public static class CropState {
        public float cropPx;
        public float cropPy;
        public float cropScale = 1;
        public float cropRotate;
        public float cropPw = 1;
        public float cropPh = 1;
        public int transformWidth;
        public int transformHeight;
        public int transformRotation;
        public boolean mirrored;

        public float stateScale;
        public float scale;
        public Matrix matrix;
        public int width;
        public int height;
        public boolean freeform;
        public float lockedAspectRatio;

        public boolean initied;
    }

    public static VideoEditedInfo createCompressionSettings(String videoPath) {
//        int[] params = new int[AnimatedFileDrawable.PARAM_NUM_COUNT];
//        AnimatedFileDrawable.getVideoInfo(videoPath, params);
//
//        if (params[AnimatedFileDrawable.PARAM_NUM_SUPPORTED_VIDEO_CODEC] == 0) {
//            if (BuildVars.LOGS_ENABLED) {
//                FileLog.d("video hasn't avc1 atom");
//            }
//            return null;
//        }

        int originalBitrate = MediaController.getVideoBitrate(videoPath);
//        if (originalBitrate == -1) {
//            originalBitrate = params[AnimatedFileDrawable.PARAM_NUM_BITRATE];
//        }
        int bitrate = originalBitrate;
        float videoDuration = MediaController.getVideoDuration(videoPath);
//        long videoFramesSize = params[AnimatedFileDrawable.PARAM_NUM_VIDEO_FRAME_SIZE];
//        long audioFramesSize = params[AnimatedFileDrawable.PARAM_NUM_AUDIO_FRAME_SIZE];
        int videoFramerate = MediaController.getVideoFramerate(videoPath);
        int videoWidth = MediaController.getVideoWidth(videoPath);
        int videoHeight = MediaController.getVideoHeight(videoPath);
        int videoRotation = MediaController.getVideoRotation(videoPath);

        if (Build.VERSION.SDK_INT < 18) {
            try {
                MediaCodecInfo codecInfo = MediaController.selectCodec(MediaController.VIDEO_MIME_TYPE);
                if (codecInfo == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("no codec info for " + MediaController.VIDEO_MIME_TYPE);
                    }
                    return null;
                } else {
                    String name = codecInfo.getName();
                    if (name.equals("OMX.google.h264.encoder") ||
                            name.equals("OMX.ST.VFM.H264Enc") ||
                            name.equals("OMX.Exynos.avc.enc") ||
                            name.equals("OMX.MARVELL.VIDEO.HW.CODA7542ENCODER") ||
                            name.equals("OMX.MARVELL.VIDEO.H264ENCODER") ||
                            name.equals("OMX.k3.video.encoder.avc") ||
                            name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("unsupported encoder = " + name);
                        }
                        return null;
                    } else {
                        if (MediaController.selectColorFormat(codecInfo, MediaController.VIDEO_MIME_TYPE) == 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("no color format for " + MediaController.VIDEO_MIME_TYPE);
                            }
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }

        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = -1;
        videoEditedInfo.endTime = -1;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.originalPath = videoPath;
        videoEditedInfo.framerate = videoFramerate;
        videoEditedInfo.estimatedDuration = (long) Math.ceil(videoDuration);
        videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = videoWidth;
        videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = videoHeight;
        videoEditedInfo.rotationValue = videoRotation;
        videoEditedInfo.originalDuration = (long) (videoDuration * 1000);

        int compressionsCount;

        float maxSize = Math.max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight);
        if (maxSize > 1280) {
            compressionsCount = 4;
        } else if (maxSize > 854) {
            compressionsCount = 3;
        } else if (maxSize > 640) {
            compressionsCount = 2;
        } else {
            compressionsCount = 1;
        }

//        int selectedCompression = Math.round(DownloadController.getInstance(UserConfig.selectedAccount).getMaxVideoBitrate() / (100f / compressionsCount));
        int selectedCompression = Math.round(100 / (100f / compressionsCount));

        if (selectedCompression > compressionsCount) {
            selectedCompression = compressionsCount;
        }
        boolean needCompress = false;
        if (selectedCompression != compressionsCount - 1 || Math.max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight) > 1280) {
            needCompress = true;
            switch (selectedCompression) {
                case 1:
                    maxSize = 432.0f;
                    break;
                case 2:
                    maxSize = 640.0f;
                    break;
                case 3:
                    maxSize = 848.0f;
                    break;
                default:
                    maxSize = 1280.0f;
                    break;
            }
            float scale = videoEditedInfo.originalWidth > videoEditedInfo.originalHeight ? maxSize / videoEditedInfo.originalWidth : maxSize / videoEditedInfo.originalHeight;
            videoEditedInfo.resultWidth = Math.round(videoEditedInfo.originalWidth * scale / 2) * 2;
            videoEditedInfo.resultHeight = Math.round(videoEditedInfo.originalHeight * scale / 2) * 2;
        }
        bitrate = MediaController.makeVideoBitrate(
                videoEditedInfo.originalHeight, videoEditedInfo.originalWidth,
                originalBitrate,
                videoEditedInfo.resultHeight, videoEditedInfo.resultWidth
        );

        if (!needCompress) {
            videoEditedInfo.resultWidth = videoEditedInfo.originalWidth;
            videoEditedInfo.resultHeight = videoEditedInfo.originalHeight;
            videoEditedInfo.bitrate = bitrate;
//            videoEditedInfo.estimatedSize = (int) (audioFramesSize + videoDuration / 1000.0f * bitrate / 8);
        } else {
            videoEditedInfo.bitrate = bitrate;
//            videoEditedInfo.estimatedSize = (int) (audioFramesSize + videoFramesSize);
//            videoEditedInfo.estimatedSize += videoEditedInfo.estimatedSize / (32 * 1024) * 16;
        }
//        if (videoEditedInfo.estimatedSize == 0) {
//            videoEditedInfo.estimatedSize = 1;
//        }

        return videoEditedInfo;
    }

    public static int findTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    public static boolean convertVideo(final VideoEditedInfo info, VideoCompressorListener compressorListener) {
//        MessageObject messageObject = convertMessage.messageObject;
//        VideoEditedInfo info = convertMessage.videoEditedInfo;
        if (info == null) {
            return false;
        }
        String videoPath = info.originalPath;
        long startTime = info.startTime;
        long avatarStartTime = info.avatarStartTime;
        long endTime = info.endTime;
        int resultWidth = info.resultWidth;
        int resultHeight = info.resultHeight;
        int rotationValue = info.rotationValue;
        int originalWidth = info.originalWidth;
        int originalHeight = info.originalHeight;
        int framerate = info.framerate;
        int bitrate = info.bitrate;
        int originalBitrate = info.originalBitrate;
//        boolean isSecret = DialogObject.isEncryptedDialog(messageObject.getDialogId());
        String fileName = "Video_Compressor_" + System.currentTimeMillis() + ".mp4";
        final File cacheFile = new File(CacheUtils.getVideoFilePath(ApplicationLoader.applicationContext), fileName);
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("begin convert " + videoPath + " startTime = " + startTime + " avatarStartTime = " + avatarStartTime + " endTime " + endTime + " rWidth = " + resultWidth + " rHeight = " + resultHeight + " rotation = " + rotationValue + " oWidth = " + originalWidth + " oHeight = " + originalHeight + " framerate = " + framerate + " bitrate = " + bitrate + " originalBitrate = " + originalBitrate);
        }

        if (videoPath == null) {
            videoPath = "";
        }

        long duration;
        if (startTime > 0 && endTime > 0) {
            duration = endTime - startTime;
        } else if (endTime > 0) {
            duration = endTime;
        } else if (startTime > 0) {
            duration = info.originalDuration - startTime;
        } else {
            duration = info.originalDuration;
        }

        if (framerate == 0) {
            framerate = 25;
        }/* else if (framerate > 59) {
            framerate = 59;
        }*/

        if (rotationValue == 90 || rotationValue == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
        }

        boolean needCompress = avatarStartTime != -1 || info.cropState != null || info.mediaEntities != null || info.paintPath != null || info.filterState != null ||
                resultWidth != originalWidth || resultHeight != originalHeight || rotationValue != 0 || info.roundVideo || startTime != -1;


        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("videoconvert", Activity.MODE_PRIVATE);

//        long time = System.currentTimeMillis();

        VideoConvertorListener callback = new VideoConvertorListener() {

            private long lastAvailableSize = 0;

            @Override
            public boolean checkConversionCanceled() {
                return info.canceled;
            }

            @Override
            public void didWriteData(long availableSize, float progress) {
                if (info.canceled) {
                    return;
                }
                if (availableSize < 0) {
                    availableSize = cacheFile.length();
                }

                if (!info.needUpdateProgress && lastAvailableSize == availableSize) {
                    return;
                }

                lastAvailableSize = availableSize;
                MediaController.didWriteData(cacheFile, false, 0, availableSize, false, progress);

                compressorListener.compressor(cacheFile.toString(),availableSize, progress, info);
            }
        };

//        info.videoConvertFirstWrite = true;

        MediaCodecVideoConvertor videoConvertor = new MediaCodecVideoConvertor();
        boolean error = videoConvertor.convertVideo(videoPath, cacheFile,
                rotationValue,
                resultWidth, resultHeight,
                framerate, bitrate, originalBitrate,
                startTime, endTime, avatarStartTime,
                needCompress, duration,
                info.filterState,
                info.paintPath,
                info.mediaEntities,
                info.isPhoto,
                info.cropState,
                callback);


//        boolean canceled = info.canceled;
//        if (!canceled) {
//            synchronized (videoConvertSync) {
//                canceled = info.canceled;
//            }
//        }

//        if (BuildVars.LOGS_ENABLED) {
//            FileLog.d("time=" + (System.currentTimeMillis() - time) + " canceled=" + canceled);
//        }

        preferences.edit().putBoolean("isPreviousOk", true).apply();
        didWriteData(cacheFile, true, videoConvertor.getLastFrameTimestamp(), cacheFile.length(), error, 1f);
        compressorListener.compressor(cacheFile.toString(),cacheFile.length(), 1f, info);
        return true;
    }

    public static int getVideoBitrate(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int bitrate = 0;
        try {
            retriever.setDataSource(path);
            bitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return bitrate;
    }

    public static float getVideoDuration(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        float duration = 0;
        try {
            retriever.setDataSource(path);
            duration = Float.parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return duration;
    }

    public static int getVideoFramerate(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int framerate = 0;
        try {
            retriever.setDataSource(path);
            framerate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return framerate;
    }

    public static int getVideoWidth(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int width = 0;
        try {
            retriever.setDataSource(path);
            width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return width;
    }

    public static int getVideoHeight(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int height = 0;
        try {
            retriever.setDataSource(path);
            height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return height;
    }

    public static int getVideoRotation(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int rotation = 0;
        try {
            retriever.setDataSource(path);
            rotation = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        } catch (Exception e) {
            FileLog.e(e);
        }

        retriever.release();
        return rotation;
    }

    public static int makeVideoBitrate(int originalHeight, int originalWidth, int originalBitrate, int height, int width) {
        float compressFactor;
        float minCompressFactor;
        int maxBitrate;
        if (Math.min(height, width) >= 1080) {
            maxBitrate = 6800_000;
            compressFactor = 1f;
            minCompressFactor = 1f;
        } else if (Math.min(height, width) >= 720) {
            maxBitrate = 3200_000;
            compressFactor = 1f;
            minCompressFactor = 1f;
        } else if (Math.min(height, width) >= 480) {
            maxBitrate = 1000_000;
            compressFactor = 0.8f;
            minCompressFactor = 0.9f;
        } else {
            maxBitrate = 750_000;
            compressFactor = 0.6f;
            minCompressFactor = 0.7f;
        }
        int remeasuredBitrate = (int) (originalBitrate / (Math.min(originalHeight / (float) (height), originalWidth / (float) (width))));
        remeasuredBitrate *= compressFactor;
        int minBitrate = (int) (getVideoBitrateWithFactor(minCompressFactor) / (1280f * 720f / (width * height)));
        if (originalBitrate < minBitrate) {
            return remeasuredBitrate;
        }
        if (remeasuredBitrate > maxBitrate) {
            return maxBitrate;
        }
        return Math.max(remeasuredBitrate, minBitrate);
    }

    private static int getVideoBitrateWithFactor(float f) {
        return (int) (f * 2000f * 1000f * 1.13f);
    }

    public interface VideoConvertorListener {
        boolean checkConversionCanceled();

        void didWriteData(long availableSize, float progress);
    }

    public interface VideoCompressorListener {

        void compressor(String cacheFile, long availableSize, float progress, VideoEditedInfo info);

    }

    @SuppressLint("NewApi")
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    String name = lastCodecInfo.getName();
                    if (name != null) {
                        if (!name.equals("OMX.SEC.avc.enc")) {
                            return lastCodecInfo;
                        } else if (name.equals("OMX.SEC.AVC.Encoder")) {
                            return lastCodecInfo;
                        }
                    }
                }
            }
        }
        return lastCodecInfo;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    @SuppressLint("NewApi")
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private static void didWriteData(final File file, final boolean last, final long lastFrameTimestamp, long availableSize, final boolean error, final float progress) {

        FileLog.d("didWriteData file" + file + " last = " + last + " lastFrameTimestamp = " + lastFrameTimestamp + " availableSize " + availableSize + " error = " + error + " progress = " + progress);


//        final boolean firstWrite = message.videoEditedInfo.videoConvertFirstWrite;
//        if (firstWrite) {
//            message.videoEditedInfo.videoConvertFirstWrite = false;
//        }
//        AndroidUtilities.runOnUIThread(() -> {
//            if (error || last) {
//                synchronized (videoConvertSync) {
//                    message.videoEditedInfo.canceled = false;
//                }
//                videoConvertQueue.remove(message);
//                startVideoConvertFromQueue();
//            }
//            if (error) {
//                NotificationCenter.getInstance(message.currentAccount).postNotificationName(NotificationCenter.filePreparingFailed, message.messageObject, file.toString(), progress, lastFrameTimestamp);
//            } else {
//                if (firstWrite) {
//                    NotificationCenter.getInstance(message.currentAccount).postNotificationName(NotificationCenter.filePreparingStarted, message.messageObject, file.toString(), progress, lastFrameTimestamp);
//                }
//                NotificationCenter.getInstance(message.currentAccount).postNotificationName(NotificationCenter.fileNewChunkAvailable, message.messageObject, file.toString(), availableSize, last ? file.length() : 0, progress, lastFrameTimestamp);
//            }
//        });
    }
}
