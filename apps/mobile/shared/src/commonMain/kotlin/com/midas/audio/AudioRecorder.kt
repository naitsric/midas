package com.midas.audio

import kotlinx.coroutines.flow.Flow

/**
 * Grabador de audio multiplataforma.
 * Captura audio PCM 16kHz 16-bit mono y emite chunks via Flow.
 */
expect class AudioRecorder() {
    /** Inicia la captura de audio. Emite chunks de ByteArray (PCM). */
    fun startRecording(): Flow<ByteArray>

    /** Detiene la captura. */
    fun stopRecording()

    /** Indica si está grabando. */
    fun isRecording(): Boolean
}
