package com.tans.socketprogramming.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat

const val VIDEO_WITH = 640
const val VIDEO_HEIGHT = 480
const val VIDEO_FORMAT = MediaFormat.MIMETYPE_VIDEO_AVC
// 2 Mbps
const val VIDEO_BIT_RATE = 2 * 1024 * 1024
const val VIDEO_FRAME_RATE = 30
const val VIDEO_KEY_FRAME_INTERVAL = 300
// NV12/NV21
const val VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

fun createDefaultEncodeMediaFormat(): MediaFormat {
    return MediaFormat.createVideoFormat(VIDEO_FORMAT, VIDEO_WITH, VIDEO_HEIGHT)
        .apply {
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, VIDEO_COLOR_FORMAT)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_KEY_FRAME_INTERVAL)
        }
}

fun createDefaultDecodeMediaFormat(): MediaFormat = MediaFormat.createVideoFormat(VIDEO_FORMAT, VIDEO_WITH, VIDEO_HEIGHT)


fun createDefaultEncodeMediaCodec(): MediaCodec = MediaCodec.createEncoderByType(VIDEO_FORMAT).apply {
    configure(createDefaultEncodeMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
}