package com.midas.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

actual class AudioRecorder actual constructor() {
    private var audioRecord: AudioRecord? = null
    private var recording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    actual fun startRecording(): Flow<ByteArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Cannot get min buffer size for AudioRecord")
        }
        val bufferSize = maxOf(minBuffer * 2, 4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize. Check RECORD_AUDIO permission.")
        }

        audioRecord = record
        recording = true
        record.startRecording()

        val buffer = ByteArray(bufferSize)
        try {
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    emit(buffer.copyOf(read))
                }
            }
        } finally {
            record.stop()
            record.release()
            audioRecord = null
        }
    }.flowOn(Dispatchers.IO)

    actual fun stopRecording() {
        recording = false
    }

    actual fun isRecording(): Boolean = recording
}
