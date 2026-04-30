package com.midas.audioplayer

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * MediaPlayer wrapper que cumple `AudioPlayerBridge` declarado en KMP.
 *
 * Equivalente Android del `AudioPlayerBridgeImpl.swift` (AVPlayer). MediaPlayer
 * maneja URLs HTTPS pre-firmadas de S3 sin configuración extra.
 *
 * Comportamiento:
 * - `load`: reset previo, configura datasource, `prepareAsync` (no bloquea el
 *   main thread mientras bufferea HTTPS). En onPrepared callback con duración
 *   y arranca el polling de progress.
 * - `play` / `pause`: control directo.
 * - `seek(seconds)`: convierte a millis (MediaPlayer.seekTo trabaja en ms).
 * - `release`: cancela polling y libera el player.
 *
 * Progress polling se hace con un `Handler` en main thread cada 100ms — no
 * vale la pena hilar coroutines para algo tan pequeño y mantenemos la UI
 * actualizada en el frame correcto.
 */
class AudioPlayerBridgeImpl : AudioPlayerBridge {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressTick: Runnable? = null

    private var onReady: ((Double) -> Unit)? = null
    private var onProgress: ((Double) -> Unit)? = null
    private var onCompleted: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun load(
        url: String,
        onReady: (Double) -> Unit,
        onProgress: (Double) -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        release()
        this.onReady = onReady
        this.onProgress = onProgress
        this.onCompleted = onCompleted
        this.onError = onError

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { prepared ->
                    val seconds = prepared.duration.takeIf { it > 0 }?.let { it / 1000.0 } ?: 0.0
                    this@AudioPlayerBridgeImpl.onReady?.invoke(seconds)
                }
                setOnCompletionListener {
                    stopProgressUpdates()
                    this@AudioPlayerBridgeImpl.onCompleted?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    this@AudioPlayerBridgeImpl.onError?.invoke("Error de reproducción ($what/$extra)")
                    true
                }
                prepareAsync()
            }
            player = mp
        } catch (e: Exception) {
            Log.w(TAG, "load() falló: ${e.message}", e)
            onError(e.message ?: "No se pudo iniciar el reproductor")
        }
    }

    override fun play() {
        val p = player ?: return
        try {
            p.start()
            startProgressUpdates()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "play() en estado inválido: ${e.message}")
        }
    }

    override fun pause() {
        val p = player ?: return
        try {
            if (p.isPlaying) p.pause()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "pause() en estado inválido: ${e.message}")
        }
        stopProgressUpdates()
    }

    override fun seek(positionSeconds: Double) {
        val p = player ?: return
        try {
            p.seekTo((positionSeconds * 1000).toInt())
        } catch (e: IllegalStateException) {
            Log.w(TAG, "seek() en estado inválido: ${e.message}")
        }
    }

    override fun release() {
        stopProgressUpdates()
        try {
            player?.reset()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        onReady = null
        onProgress = null
        onCompleted = null
        onError = null
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return
                val playing = try { p.isPlaying } catch (_: IllegalStateException) { false }
                if (playing) {
                    onProgress?.invoke(p.currentPosition / 1000.0)
                    handler.postDelayed(this, 100L)
                }
            }
        }
        progressTick = tick
        handler.post(tick)
    }

    private fun stopProgressUpdates() {
        progressTick?.let { handler.removeCallbacks(it) }
        progressTick = null
    }

    private companion object {
        const val TAG = "AudioPlayerBridge"
    }
}
