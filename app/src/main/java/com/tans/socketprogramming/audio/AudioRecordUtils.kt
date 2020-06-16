package com.tans.socketprogramming.audio

import android.media.*

const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
// 44100 Hz
const val AUDIO_SAMPLE_RATE = 44100
const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO
const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT
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