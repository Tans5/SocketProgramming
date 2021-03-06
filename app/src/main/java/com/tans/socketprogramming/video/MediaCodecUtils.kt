package com.tans.socketprogramming.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.tans.socketprogramming.blockToSuspend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

const val VIDEO_WITH = 640
const val VIDEO_HEIGHT = 480
const val VIDEO_FORMAT = MediaFormat.MIMETYPE_VIDEO_AVC
// 2Mbps
const val VIDEO_BIT_RATE = 2 * 1024 * 1024
const val VIDEO_FRAME_RATE = 30
// 5s
const val VIDEO_KEY_FRAME_INTERVAL = 5
const val VIDEO_BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR

const val VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface

fun createDefaultEncodeMediaFormat(): MediaFormat {
    return MediaFormat.createVideoFormat(VIDEO_FORMAT, VIDEO_WITH, VIDEO_HEIGHT)
        .apply {
            setString(MediaFormat.KEY_MIME, VIDEO_FORMAT)
            setInteger(MediaFormat.KEY_WIDTH, VIDEO_WITH)
            setInteger(MediaFormat.KEY_HEIGHT, VIDEO_HEIGHT)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
//            setInteger(MediaFormat.KEY_CAPTURE_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, VIDEO_COLOR_FORMAT)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_KEY_FRAME_INTERVAL)
//            setInteger(MediaFormat.KEY_BITRATE_MODE, VIDEO_BITRATE_MODE)
        }
}

fun createDefaultDecodeMediaFormat(): MediaFormat = MediaFormat.createVideoFormat(VIDEO_FORMAT, VIDEO_WITH, VIDEO_HEIGHT)
    .apply {
        setString(MediaFormat.KEY_MIME, VIDEO_FORMAT)
        setInteger(MediaFormat.KEY_WIDTH, VIDEO_WITH)
        setInteger(MediaFormat.KEY_HEIGHT, VIDEO_HEIGHT)
    }


fun createDefaultEncodeMediaCodec(): MediaCodec = MediaCodec.createEncoderByType(VIDEO_FORMAT).apply {
    configure(createDefaultEncodeMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
}

fun createDefaultDecodeMediaCodec(outputSurface: Surface): MediaCodec = MediaCodec.createDecoderByType(VIDEO_FORMAT).apply {
    configure(createDefaultDecodeMediaFormat(), outputSurface, null, 0)
}

fun MediaCodec.callback(inputAvailable: (mc: MediaCodec, inputBufferId: Int) -> Unit,
                        outputAvailable: (mc: MediaCodec, outputBufferId: Int) -> Unit ) {

    setCallback(object : MediaCodec.Callback() {

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            inputAvailable(codec, index)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            outputAvailable(codec, index)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}

    })
}

suspend fun MediaCodec.dequeueOutputBufferSuspend(
    bufferInfo: MediaCodec.BufferInfo,
    timeout: Long,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    dequeueOutputBuffer(bufferInfo, timeout)
}

suspend fun MediaCodec.dequeueInputBufferSuspend(
    timeout: Long,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    dequeueInputBuffer(timeout)
}

suspend fun MediaCodec.queueInputBufferSuspend(
    inputIndex: Int,
    offset: Int = 0,
    size: Int,
    timeUs: Long = 0,
    flags: Int = 0,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    queueInputBuffer(inputIndex,
        offset,
        size,
        timeUs,
        flags)
}

suspend fun MediaCodec.getOutputBufferSuspend(
    outputIndex: Int,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    getOutputBuffer(outputIndex)
}

suspend fun MediaCodec.getInputBufferSuspend(
    outputIndex: Int,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    getInputBuffer(outputIndex)
}

suspend fun MediaCodec.releaseOutputBufferSuspend(
    outputIndex: Int,
    render: Boolean,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) {
    releaseOutputBuffer(outputIndex, render)
}