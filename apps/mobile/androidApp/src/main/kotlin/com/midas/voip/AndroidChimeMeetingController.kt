package com.midas.voip

import android.content.Context
import android.util.Log
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoConfiguration
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.RemoteVideoSource
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver
import com.amazonaws.services.chime.sdk.meetings.session.Attendee
import com.amazonaws.services.chime.sdk.meetings.session.CreateAttendeeResponse
import com.amazonaws.services.chime.sdk.meetings.session.CreateMeetingResponse
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MediaPlacement
import com.amazonaws.services.chime.sdk.meetings.session.Meeting
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionConfiguration
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatus
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel

/**
 * Wrapper sobre `DefaultMeetingSession` de Chime SDK Android. Cumple el rol
 * análogo al `ChimeCallManager.swift` iOS.
 *
 * Responsabilidades:
 * - Construir `MeetingSessionConfiguration` desde el `DialResponse` del backend.
 * - Iniciar / parar el audio session y pasar callbacks útiles al dispatcher.
 * - Filtrar el self-attendee en `onAttendeesJoined` para que el ringback se
 *   detenga sólo cuando entra el otro participante (PSTN), no cuando entramos
 *   nosotros mismos al meeting.
 *
 * Notas:
 * - El SDK exige que `start()` se llame con permiso de RECORD_AUDIO ya
 *   concedido — el dispatcher lo gestiona antes de invocarnos.
 * - `AudioStreamType.VoiceCall` es el default en `AudioVideoConfiguration`.
 *   Ese stream type combinado con `AudioManager.MODE_IN_COMMUNICATION` (que
 *   gestiona el dispatcher) rutea al earpiece/altavoz de llamada y activa
 *   echo cancellation.
 */
class AndroidChimeMeetingController(private val appContext: Context) {

    interface Listener {
        fun onAudioSessionStartedConnecting(reconnecting: Boolean)
        fun onAudioSessionStarted(reconnecting: Boolean)
        fun onAudioSessionStopped(status: MeetingSessionStatus)
        fun onRemoteAttendeeJoined(attendeeId: String)
        fun onRemoteAttendeeLeft(attendeeId: String)
    }

    private var session: MeetingSession? = null
    private var listener: Listener? = null
    private var selfAttendeeId: String? = null

    fun start(creds: DialResponse, listener: Listener) {
        if (session != null) {
            Log.w(TAG, "start() called but a session is already active; stopping previous")
            stop()
        }
        this.listener = listener
        this.selfAttendeeId = creds.attendeeId

        val config = creds.toMeetingSessionConfiguration()
        val logger = ConsoleLogger(LogLevel.INFO)
        val newSession = DefaultMeetingSession(config, logger, appContext)

        newSession.audioVideo.addAudioVideoObserver(audioVideoObserver)
        newSession.audioVideo.addRealtimeObserver(realtimeObserver)

        session = newSession
        // Defaults: AudioMode.Stereo48K, AudioStreamType.VoiceCall, 180s reconnect.
        // VoiceCall stream type ensures Bluetooth headset routing if connected.
        newSession.audioVideo.start(AudioVideoConfiguration())
    }

    fun stop() {
        val s = session ?: return
        try {
            s.audioVideo.removeAudioVideoObserver(audioVideoObserver)
            s.audioVideo.removeRealtimeObserver(realtimeObserver)
            s.audioVideo.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop() error: ${e.message}")
        }
        session = null
        listener = null
        selfAttendeeId = null
    }

    fun setMuted(muted: Boolean): Boolean {
        val av = session?.audioVideo ?: return false
        return if (muted) av.realtimeLocalMute() else av.realtimeLocalUnmute()
    }

    private val audioVideoObserver = object : AudioVideoObserver {
        override fun onAudioSessionStartedConnecting(reconnecting: Boolean) {
            listener?.onAudioSessionStartedConnecting(reconnecting)
        }

        override fun onAudioSessionStarted(reconnecting: Boolean) {
            listener?.onAudioSessionStarted(reconnecting)
        }

        override fun onAudioSessionDropped() {}
        override fun onAudioSessionStopped(sessionStatus: MeetingSessionStatus) {
            listener?.onAudioSessionStopped(sessionStatus)
        }

        override fun onAudioSessionCancelledReconnect() {}
        override fun onConnectionRecovered() {}
        override fun onConnectionBecamePoor() {}
        override fun onVideoSessionStartedConnecting() {}
        override fun onVideoSessionStarted(sessionStatus: MeetingSessionStatus) {}
        override fun onVideoSessionStopped(sessionStatus: MeetingSessionStatus) {}
        override fun onRemoteVideoSourceUnavailable(sources: List<RemoteVideoSource>) {}
        override fun onRemoteVideoSourceAvailable(sources: List<RemoteVideoSource>) {}
        override fun onCameraSendAvailabilityUpdated(available: Boolean) {}
    }

    private val realtimeObserver = object : RealtimeObserver {
        override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { info ->
                if (info.attendeeId != selfAttendeeId) {
                    listener?.onRemoteAttendeeJoined(info.attendeeId)
                }
            }
        }

        override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { info ->
                if (info.attendeeId != selfAttendeeId) {
                    listener?.onRemoteAttendeeLeft(info.attendeeId)
                }
            }
        }

        override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) {
            attendeeInfo.forEach { info ->
                if (info.attendeeId != selfAttendeeId) {
                    listener?.onRemoteAttendeeLeft(info.attendeeId)
                }
            }
        }

        override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {}
        override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {}
        override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) {}
        override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) {}
    }

    private companion object {
        const val TAG = "MidasChimeCtl"
    }
}

private fun DialResponse.toMeetingSessionConfiguration(): MeetingSessionConfiguration {
    val mediaPlacement = MediaPlacement(
        AudioFallbackUrl = audioFallbackUrl,
        AudioHostUrl = audioHostUrl,
        SignalingUrl = signalingUrl,
        TurnControlUrl = turnControlUrl,
    )
    val meeting = Meeting(
        ExternalMeetingId = null,
        MediaPlacement = mediaPlacement,
        MediaRegion = mediaRegion,
        MeetingId = meetingId,
    )
    val attendee = Attendee(
        AttendeeId = attendeeId,
        ExternalUserId = externalUserId,
        JoinToken = joinToken,
    )
    return MeetingSessionConfiguration(
        createMeetingResponse = CreateMeetingResponse(meeting),
        createAttendeeResponse = CreateAttendeeResponse(attendee),
    )
}
