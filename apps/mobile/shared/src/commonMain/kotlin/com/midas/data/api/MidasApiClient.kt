package com.midas.data.api

import com.midas.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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

    // --- WebSocket ---

    fun getWebSocketUrl(callId: String, apiKey: String): String {
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        return "$wsBase/api/calls/$callId/stream?api_key=$apiKey"
    }

    fun close() {
        client.close()
    }
}
