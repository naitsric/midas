package com.midas.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.*
import platform.Foundation.NSError
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.ObjCObjectVar

@OptIn(ExperimentalForeignApi::class)
actual class AudioRecorder actual constructor() {
    private var audioEngine: AVAudioEngine? = null
    private var recording = false

    actual fun startRecording(): Flow<ByteArray> = callbackFlow {
        // Configure audio session for recording alongside phone calls
        val session = AVAudioSession.sharedInstance()
        try {
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
            session.setMode(AVAudioSessionModeDefault, error = null)
            session.setActive(true, error = null)
        } catch (_: Exception) {
            // Session config failed, continue anyway
        }

        val engine = AVAudioEngine()
        audioEngine = engine

        val inputNode = engine.inputNode
        val nativeFormat = inputNode.outputFormatForBus(0u)

        // Guard against invalid format (e.g. during active phone call)
        val sampleRate = nativeFormat.sampleRate.toInt()
        if (sampleRate <= 0) {
            close(IllegalStateException("Audio input not available"))
            return@callbackFlow
        }

        val bus: ULong = 0u
        val ratio = maxOf(sampleRate / 16000, 1)

        inputNode.installTapOnBus(bus, bufferSize = 4096u, format = nativeFormat) { buffer, _ ->
            buffer?.let { buf ->
                val floatData = buf.floatChannelData
                if (floatData != null) {
                    val samples = floatData[0] ?: return@let
                    val frameLength = buf.frameLength.toInt()
                    val outLength = frameLength / ratio
                    if (outLength <= 0) return@let
                    val bytes = ByteArray(outLength * 2)
                    for (i in 0 until outLength) {
                        val floatSample = samples[i * ratio]
                        val intSample = (floatSample * 32767.0f).toInt().coerceIn(-32768, 32767)
                        bytes[i * 2] = (intSample and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((intSample shr 8) and 0xFF).toByte()
                    }
                    trySend(bytes)
                }
            }
        }

        engine.prepare()
        recording = true

        // Start engine with error handling
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val started = engine.startAndReturnError(errorPtr.ptr)
            if (!started) {
                recording = false
                inputNode.removeTapOnBus(bus)
                close(IllegalStateException("Failed to start audio engine: ${errorPtr.value?.localizedDescription}"))
                return@callbackFlow
            }
        }

        awaitClose {
            recording = false
            inputNode.removeTapOnBus(bus)
            engine.stop()
            session.setActive(false, error = null)
            audioEngine = null
        }
    }

    actual fun stopRecording() {
        recording = false
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        AVAudioSession.sharedInstance().setActive(false, error = null)
        audioEngine = null
    }

    actual fun isRecording(): Boolean = recording
}
