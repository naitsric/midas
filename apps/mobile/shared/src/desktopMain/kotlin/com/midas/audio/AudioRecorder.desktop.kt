package com.midas.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.sound.sampled.*

actual class AudioRecorder actual constructor() {
    private var targetLine: TargetDataLine? = null
    private var recording = false

    private val sampleRate = 16000f
    private val sampleSizeInBits = 16
    private val channels = 1
    private val signed = true
    private val bigEndian = false
    private val bufferSize = 4096

    actual fun startRecording(): Flow<ByteArray> = flow {
        val format = AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as TargetDataLine

        line.open(format, bufferSize * 2)
        line.start()
        targetLine = line
        recording = true

        val buffer = ByteArray(bufferSize)
        try {
            while (recording) {
                val read = line.read(buffer, 0, buffer.size)
                if (read > 0) {
                    emit(buffer.copyOf(read))
                }
            }
        } finally {
            line.stop()
            line.close()
            targetLine = null
        }
    }.flowOn(Dispatchers.IO)

    actual fun stopRecording() {
        recording = false
        targetLine?.stop()
    }

    actual fun isRecording(): Boolean = recording
}
