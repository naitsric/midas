package com.midas.voip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoipCallState { CONNECTING, RINGING, CONNECTED, ENDED, FAILED }

data class VoipCall(
    val callId: String,
    val remoteNumber: String,
    val displayName: String?,
    val state: VoipCallState,
    val isOutgoing: Boolean,
    val muted: Boolean = false,
)

interface VoipCallDispatcher {
    fun dial(toNumber: String, clientName: String?)
    fun answer(callId: String)
    fun hangup(callId: String)
    fun setMuted(muted: Boolean)
}

class VoipCallManager(private val dispatcher: VoipCallDispatcher) {
    private val _activeCall = MutableStateFlow<VoipCall?>(null)
    val activeCall: StateFlow<VoipCall?> = _activeCall.asStateFlow()

    fun dial(toNumber: String, clientName: String?) {
        _activeCall.value = VoipCall(
            callId = "",
            remoteNumber = toNumber,
            displayName = clientName,
            state = VoipCallState.CONNECTING,
            isOutgoing = true,
        )
        dispatcher.dial(toNumber, clientName)
    }

    /** Actualiza el callId real tras respuesta del backend, sin cambiar el estado UI. */
    fun onDialed(callId: String) {
        _activeCall.value = _activeCall.value?.copy(callId = callId)
    }

    fun answer(callId: String) {
        _activeCall.value = _activeCall.value?.copy(state = VoipCallState.CONNECTING)
        dispatcher.answer(callId)
    }

    fun hangup() {
        _activeCall.value?.callId?.let { dispatcher.hangup(it) }
    }

    fun setMuted(muted: Boolean) {
        _activeCall.value = _activeCall.value?.copy(muted = muted)
        dispatcher.setMuted(muted)
    }

    fun onIncomingCall(callId: String, callerNumber: String, callerName: String?) {
        _activeCall.value = VoipCall(
            callId = callId,
            remoteNumber = callerNumber,
            displayName = callerName,
            state = VoipCallState.RINGING,
            isOutgoing = false,
        )
    }

    fun onCallConnected(callId: String) {
        _activeCall.value = _activeCall.value?.copy(callId = callId, state = VoipCallState.CONNECTED)
    }

    fun onCallEnded(failed: Boolean = false) {
        _activeCall.value = _activeCall.value?.copy(
            state = if (failed) VoipCallState.FAILED else VoipCallState.ENDED,
        )
        _activeCall.value = null
    }
}
