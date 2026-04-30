package com.midas.audioplayer

/**
 * Reproductor de audio nativo. La impl iOS usa AVPlayer; Android queda
 * como NoOp en v0.1.
 *
 * Usa callbacks (no Flow / suspend) para minimizar fricción del interop
 * KMP↔Swift y poder llamarlo directo desde Compose.
 *
 * Ciclo de vida típico:
 *   load(url, onReady)            → bufferea audio, callback con duración
 *   play() / pause() / seek(sec)  → control
 *   release()                     → libera el AVPlayer al salir de la pantalla
 *
 * `onProgress` se invoca ~10 veces por segundo mientras hay reproducción
 * activa; `onCompleted` cuando llega al final.
 */
interface AudioPlayerBridge {
    fun load(
        url: String,
        onReady: (durationSeconds: Double) -> Unit,
        onProgress: (positionSeconds: Double) -> Unit,
        onCompleted: () -> Unit,
        onError: (message: String) -> Unit,
    )

    fun play()
    fun pause()
    fun seek(positionSeconds: Double)
    fun release()
}
