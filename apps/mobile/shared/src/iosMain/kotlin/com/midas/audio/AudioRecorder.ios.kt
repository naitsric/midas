package com.midas.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.*
import platform.Foundation.NSError

@OptIn(ExperimentalForeignApi::class)
actual class AudioRecorder actual constructor() {
    private var audioEngine: AVAudioEngine? = null
    private var recording = false

    actual fun startRecording(): Flow<ByteArray> = callbackFlow {
        val engine = AVAudioEngine()
        audioEngine = engine

        val inputNode = engine.inputNode
        val format = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = 16000.0,
            channels = 1u,
            interleaved = false
        )

        val bus: UInt = 0u
        inputNode.installTapOnBus(bus, bufferSize = 4096u, format = format) { buffer, _ ->
            buffer?.let {
                val data = it.int16ChannelData?.get(0)
                if (data != null) {
                    val frameLength = it.frameLength.toInt()
                    val bytes = ByteArray(frameLength * 2)
                    for (i in 0 until frameLength) {
                        val sample = data[i]
                        bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
                    }
                    trySend(bytes)
                }
            }
        }

        engine.prepare()
        recording = true

        val error: NSError? = null
        engine.startAndReturnError(null)

        awaitClose {
            recording = false
            inputNode.removeTapOnBus(bus)
            engine.stop()
            audioEngine = null
        }
    }

    actual fun stopRecording() {
        recording = false
        audioEngine?.stop()
        audioEngine = null
    }

    actual fun isRecording(): Boolean = recording
}
