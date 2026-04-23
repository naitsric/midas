package com.midas.data.api

import com.midas.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ApiError(val statusCode: Int, message: String) : Exception(message)

class MidasApiClient(
    private val baseUrl: String = "http://localhost:8005",
    private val apiKeyProvider: suspend () -> String?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@MidasApiClient.json)
        }
        install(WebSockets)
    }

    private suspend fun getApiKey(): String? = apiKeyProvider()

    private suspend inline fun <reified T> get(path: String): T {
        val key = getApiKey()
        val response = client.get("$baseUrl$path") {
            key?.let { header("X-API-Key", it) }
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error ${response.status.value}")
        }
        return response.body()
    }

    private suspend inline fun <reified T, reified R> post(path: String, body: T): R {
        val key = getApiKey()
        val response = client.post("$baseUrl$path") {
            key?.let { header("X-API-Key", it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error ${response.status.value}")
        }
        return response.body()
    }

    // --- Advisor ---

    suspend fun getAdvisorProfile(): Advisor = get("/api/advisors/me")

    suspend fun registerAdvisor(request: RegisterAdvisorRequest): RegisterAdvisorResponse {
        val response = client.post("$baseUrl/api/advisors") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error al registrar")
        }
        return response.body()
    }

    // --- Conversations ---

    suspend fun listConversations(): List<ConversationSummary> = get("/api/conversations")

    suspend fun getConversation(id: String): Conversation = get("/api/conversations/$id")

    // --- Applications ---

    suspend fun listApplications(): List<CreditApplication> = get("/api/applications")

    suspend fun getApplication(id: String): CreditApplication = get("/api/applications/$id")

    // --- Calls ---

    suspend fun startCall(clientName: String): CallDetail =
        post("/api/calls", StartCallRequest(clientName))

    suspend fun listCalls(): List<CallSummary> = get("/api/calls")

    suspend fun getCall(id: String): CallDetail = get("/api/calls/$id")

    suspend fun endCall(id: String): CallDetail {
        val key = getApiKey()
        val response = client.post("$baseUrl/api/calls/$id/end") {
            key?.let { header("X-API-Key", it) }
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error al finalizar llamada")
        }
        return response.body()
    }

    suspend fun generateApplicationFromCall(callId: String): CreditApplication {
        val key = getApiKey()
        val response = client.post("$baseUrl/api/calls/$callId/generate-application") {
            key?.let { header("X-API-Key", it) }
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error al generar solicitud")
        }
        return response.body()
    }

    suspend fun detectIntentFromCall(callId: String): IntentResult {
        val key = getApiKey()
        val response = client.post("$baseUrl/api/calls/$callId/detect-intent") {
            key?.let { header("X-API-Key", it) }
        }
        if (response.status.value >= 400) {
            throw ApiError(response.status.value, "Error al detectar intención")
        }
        return response.body()
    }

    // --- Copilot (SSE stream) ---

    /**
     * Envía un mensaje al Copilot y emite cada evento del stream SSE como
     * [CopilotEvent]. El Flow se cierra cuando el backend manda `event: done`
     * o cuando el body del response se agota.
     *
     * El backend es stateless — pasamos el historial completo en cada call.
     */
    fun streamCopilot(history: List<CopilotHistoryItem>, message: String): Flow<CopilotEvent> = channelFlow {
        // channelFlow (no flow) — Ktor's `execute` runs the body in Dispatchers.IO,
        // so emissions cross the collector's coroutine context. channelFlow is the
        // context-preserving builder that handles that safely.
        val key = getApiKey()
        val request = CopilotMessageRequest(history = history, message = message)
        client.preparePost("$baseUrl/api/copilot/messages") {
            key?.let { header("X-API-Key", it) }
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(request)
        }.execute { response ->
            if (response.status.value >= 400) {
                send(CopilotEvent.Error("HTTP ${response.status.value}"))
                return@execute
            }
            val channel: ByteReadChannel = response.bodyAsChannel()
            var eventName = ""
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> {
                        eventName = ""  // separador entre eventos
                    }
                    line.startsWith("event:") -> {
                        eventName = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isNotEmpty()) {
                            val parsed = parseCopilotEvent(eventName, payload)
                            if (parsed != null) send(parsed)
                            if (parsed is CopilotEvent.Done) return@execute
                        }
                    }
                }
            }
        }
        awaitClose { /* ktor request finishes when execute returns */ }
    }

    private fun parseCopilotEvent(eventName: String, dataJson: String): CopilotEvent? {
        return try {
            val obj: JsonObject = json.parseToJsonElement(dataJson).jsonObject
            when (eventName) {
                "token" -> CopilotEvent.Token(text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "tool_call" -> CopilotEvent.ToolCall(name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "tool_result" -> {
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val source = obj["source"]?.jsonObject
                    CopilotEvent.ToolResult(
                        name = name,
                        sourceType = source?.get("type")?.jsonPrimitive?.contentOrNull,
                        sourceLabel = source?.get("label")?.jsonPrimitive?.contentOrNull,
                    )
                }
                "done" -> CopilotEvent.Done(
                    elapsedMs = obj["elapsed_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                )
                "error" -> CopilotEvent.Error(
                    message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- WebSocket ---

    data class WebSocketInfo(val host: String, val port: Int, val path: String, val secure: Boolean)

    fun getWebSocketInfo(callId: String, apiKey: String): WebSocketInfo {
        val isSecure = baseUrl.startsWith("https://")
        val withoutScheme = baseUrl.removePrefix("http://").removePrefix("https://")
        val parts = withoutScheme.split(":", limit = 2)
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: if (isSecure) 443 else 80 else if (isSecure) 443 else 80
        return WebSocketInfo(host, port, "/api/calls/$callId/stream?api_key=$apiKey", isSecure)
    }

    fun close() {
        client.close()
    }
}
