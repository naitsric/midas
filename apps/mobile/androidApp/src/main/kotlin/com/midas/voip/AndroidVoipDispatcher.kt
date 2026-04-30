package com.midas.voip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatus
import com.midas.voip.CallControlClient
import com.midas.voip.DialResponse
import com.midas.voip.VoipCallDispatcher
import com.midas.voip.VoipCallManager
import com.midas.voip.VoipDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Implementación Android del `VoipCallDispatcher` (declarado en :shared/commonMain).
 *
 * Responsabilidades:
 * - Llamar al backend Chime SMA (`CallControlClient.dial/hangup`).
 * - Pedir audio focus + setear `AudioManager.mode = MODE_IN_COMMUNICATION`.
 * - Arrancar el `MidasCallForegroundService` para que Android no mate el
 *   proceso si el usuario manda la app a background.
 * - Conducir el `AndroidChimeMeetingController` y reaccionar a sus callbacks.
 * - Ringback local hasta que entra el participante PSTN.
 * - Emitir cambios de estado al `VoipCallManager` que la UI Compose observa.
 *
 * Diferencias vs iOS (donde CallKit gestiona muchas de estas cosas):
 * - `setMuted` SÍ se implementa en Android (en iOS el dispatcher es no-op
 *   porque CallKit lo dispara via CXSetMutedCallAction).
 * - El AudioFocus + audio mode los gestionamos a mano (en iOS los maneja
 *   AVAudioSession + CallKit).
 * - El scope de coroutines es nuestro (SupervisorJob + IO), NO el del
 *   Activity — las llamadas sobreviven rotación / minimización.
 */
class AndroidVoipDispatcher(
    private val appContext: Context,
    private val callControlClient: CallControlClient,
) : VoipCallDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chime = AndroidChimeMeetingController(appContext)
    private val ringback = RingbackPlayer(appContext)
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var manager: VoipCallManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var currentCallId: String? = null

    /** Llamado tras `createVoipCallManager(dispatcher)` para resolver el cycle. */
    fun attach(manager: VoipCallManager) {
        this.manager = manager
    }

    override fun dial(toNumber: String, clientName: String?) {
        VoipDebugLog.append("[Voip][android] dial start to=$toNumber")

        if (!hasMicPermission()) {
            VoipDebugLog.append("[Voip][android] dial aborted — RECORD_AUDIO not granted")
            manager?.onCallEnded(failed = true)
            return
        }

        scope.launch {
            try {
                MidasCallForegroundService.start(appContext, clientName ?: toNumber)
                acquireAudioForCall()

                val resp: DialResponse = callControlClient.dial(toNumber, clientName)
                VoipDebugLog.append("[Voip][android] dial OK callId=${resp.callId}")
                currentCallId = resp.callId
                manager?.onDialed(callId = resp.callId)

                ringback.start()
                chime.start(resp, chimeListener)
            } catch (e: Exception) {
                VoipDebugLog.append("[Voip][android] dial FAILED: ${e.message}")
                Log.w(TAG, "dial failed", e)
                cleanupAfterCall()
                manager?.onCallEnded(failed = true)
            }
        }
    }

    override fun answer(callId: String) {
        // Phase A: incoming calls not supported on Android (no FCM yet).
        VoipDebugLog.append("[Voip][android] answer ignored — incoming not implemented in phase A")
    }

    override fun hangup(callId: String) {
        VoipDebugLog.append("[Voip][android] hangup callId=$callId")
        scope.launch {
            chime.stop()
            ringback.stop()
            try {
                callControlClient.hangup(callId)
            } catch (e: Exception) {
                Log.w(TAG, "hangup backend call failed (continuing local cleanup)", e)
            }
            cleanupAfterCall()
            manager?.onCallEnded(failed = false)
        }
    }

    override fun setMuted(muted: Boolean) {
        val ok = chime.setMuted(muted)
        VoipDebugLog.append("[Voip][android] setMuted($muted) → $ok")
    }

    fun release() {
        try {
            chime.stop()
            ringback.stop()
        } catch (_: Exception) {
        }
        cleanupAfterCall()
        scope.cancel()
        try {
            callControlClient.close()
        } catch (_: Exception) {
        }
    }

    // ---------- Chime callbacks ----------

    private val chimeListener = object : AndroidChimeMeetingController.Listener {
        override fun onAudioSessionStartedConnecting(reconnecting: Boolean) {
            VoipDebugLog.append("[Voip][android] chime connecting reconnecting=$reconnecting")
        }

        override fun onAudioSessionStarted(reconnecting: Boolean) {
            VoipDebugLog.append("[Voip][android] chime audio session started")
        }

        override fun onAudioSessionStopped(status: MeetingSessionStatus) {
            VoipDebugLog.append("[Voip][android] chime stopped status=${status.statusCode}")
            scope.launch {
                ringback.stop()
                cleanupAfterCall()
                manager?.onCallEnded(failed = false)
            }
        }

        override fun onRemoteAttendeeJoined(attendeeId: String) {
            VoipDebugLog.append("[Voip][android] remote attendee joined $attendeeId")
            ringback.stop()
            currentCallId?.let { manager?.onCallConnected(it) }
        }

        override fun onRemoteAttendeeLeft(attendeeId: String) {
            VoipDebugLog.append("[Voip][android] remote attendee left $attendeeId")
            // Other side hung up. Clean up local audio + service; manager state
            // flips to ENDED via onAudioSessionStopped which fires shortly after.
        }
    }

    // ---------- Audio focus / mode ----------

    private fun acquireAudioForCall() {
        savedAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            )
        }
    }

    private fun cleanupAfterCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
        audioManager.mode = savedAudioMode

        MidasCallForegroundService.stop(appContext)
        currentCallId = null
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MidasVoipDispatcher"
    }
}
