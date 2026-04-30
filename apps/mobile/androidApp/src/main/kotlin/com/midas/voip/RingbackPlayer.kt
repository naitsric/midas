package com.midas.voip

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.midas.R

/**
 * Reproduce el ringback (tono de timbrado) en loop durante el dial outbound,
 * hasta que la otra parte conteste. Equivalente del `RingbackPlayer.swift` iOS;
 * usa el mismo asset (`R.raw.ringback`).
 *
 * Se reproduce con `USAGE_VOICE_COMMUNICATION` para que no compita con el
 * audio de Chime — Android lo rutea por el earpiece/altavoz de llamada.
 */
class RingbackPlayer(private val appContext: Context) {

    private var player: MediaPlayer? = null

    fun start() {
        if (player != null) return
        try {
            val mp = MediaPlayer.create(appContext, R.raw.ringback) ?: run {
                Log.w(TAG, "MediaPlayer.create returned null for ringback")
                return
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.isLooping = true
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.w(TAG, "ringback start() falló: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
    }

    private companion object {
        const val TAG = "MidasRingback"
    }
}
