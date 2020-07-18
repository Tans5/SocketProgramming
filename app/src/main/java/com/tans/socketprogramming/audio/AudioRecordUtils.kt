package com.tans.socketprogramming.audio

import android.media.*
import com.tans.socketprogramming.blockToSuspend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
// 44100 Hz
const val AUDIO_SAMPLE_RATE = 44100
const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
val AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_IN, AUDIO_FORMAT)

fun createDefaultAudioRecord(): AudioRecord = AudioRecord(AUDIO_SOURCE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_IN, AUDIO_FORMAT, AUDIO_BUFFER_SIZE)

fun createDefaultAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

fun createDefaultAudioFormat(): AudioFormat = AudioFormat.Builder()
    .setEncoding(AUDIO_FORMAT)
    .setChannelMask(AUDIO_CHANNEL_OUT)
    .setSampleRate(AUDIO_SAMPLE_RATE)
    .build()

fun createDefaultAudioTrack(): AudioTrack = AudioTrack(createDefaultAudioAttributes(), createDefaultAudioFormat(),
    AUDIO_BUFFER_SIZE, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)

fun AudioRecord.readWithoutRemain(bytes: ByteArray, offset: Int = 0, len: Int = bytes.size) {
    val readCount = read(bytes, offset, len)
    if (readCount in 0 until len) {
        val needRead = len - readCount
        readWithoutRemain(bytes, offset + readCount, needRead)
    } else {
        if (readCount < 0) {
            println("AudioRecord Read Error: $readCount")
        }
        return
    }
}

suspend fun AudioTrack.writeSuspend(
    bytes: ByteArray,
    offset: Int = 0,
    size: Int = bytes.size,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) { write(bytes, offset, size) }

suspend fun AudioRecord.readWithoutRemainSuspend(
    bytes: ByteArray,
    offset: Int = 0,
    len: Int = bytes.size,
    context: CoroutineContext = Dispatchers.IO
) = blockToSuspend(context, { this.stop(); this.release() }) { readWithoutRemain(bytes, offset, len) }