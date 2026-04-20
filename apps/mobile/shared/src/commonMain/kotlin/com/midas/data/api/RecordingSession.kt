package com.midas.data.api

import com.midas.audio.AudioRecorder
import com.midas.data.repository.SettingsRepository
import com.midas.domain.model.TranscriptMessage
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

data class IntentInfo(
    val intentDetected: Boolean,
    val confidence: Double,
    val productType: String?,
    val summary: String?,
    val isActionable: Boolean,
    val callId: String?,
)

class RecordingSession(
    private val apiClient: MidasApiClient,
    private val settings: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    var callId: String? = null
        private set

    suspend fun start(
        clientName: String,
        onTranscript: (text: String, isFinal: Boolean) -> Unit,
        onIntent: (IntentInfo) -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
        isRecording: () -> Boolean,
    ) {
        val call = apiClient.startCall(clientName)
        callId = call.id
        val apiKey = settings.getApiKey() ?: throw Exception("No API key")
        val wsInfo = apiClient.getWebSocketInfo(call.id, apiKey)

        val recorder = AudioRecorder()
        val client = HttpClient { install(WebSockets) }
        try {
            client.webSocket(
                method = HttpMethod.Get,
                host = wsInfo.host,
                port = wsInfo.port,
                path = wsInfo.path,
            ) {
                val sendJob = launch {
                    recorder.startRecording().collect { chunk ->
                        if (!isRecording()) {
                            recorder.stopRecording()
                            return@collect
                        }
                        send(Frame.Binary(true, chunk))
                    }
                    send(Frame.Text("""{"action":"end"}"""))
                }

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val msg = json.decodeFromString<TranscriptMessage>(text)
                            when (msg.type) {
                                "transcript" -> {
                                    msg.text?.let { onTranscript(it, msg.isFinal) }
                                }
                                "intent" -> {
                                    onIntent(
                                        IntentInfo(
                                            intentDetected = msg.intentDetected,
                                            confidence = msg.confidence,
                                            productType = msg.productType,
                                            summary = msg.summary,
                                            isActionable = msg.isActionable,
                                            callId = msg.callId,
                                        )
                                    )
                                }
                                "completed" -> {
                                    onCompleted()
                                    break
                                }
                                "error" -> {
                                    onError(msg.message ?: "Error desconocido")
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                sendJob.cancelAndJoin()
            }
        } finally {
            recorder.stopRecording()
            client.close()
        }
    }
}
