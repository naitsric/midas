package com.midas.voip

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP del Lambda de control de llamadas (Chime SMA).
 *
 * Equivalente cross-platform de `CallControlService.swift`. Apunta al mismo
 * API Gateway URL — ambos clientes coexisten en este PR (iOS sigue usando
 * el Swift). El backend devuelve credenciales de Chime SDK platform-agnostic
 * (mismas para iOS y Android).
 *
 * Auth: header `X-API-Key` con la api key del asesor.
 */
class CallControlClient(
    private val baseUrl: String = defaultCallControlBaseUrl,
    private val apiKeyProvider: suspend () -> String?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@CallControlClient.json)
        }
    }

    suspend fun dial(toNumber: String, clientName: String?): DialResponse {
        val key = apiKeyProvider()
        val response = client.post("$baseUrl/calls/dial") {
            key?.let { header("X-API-Key", it) }
            contentType(ContentType.Application.Json)
            setBody(DialRequest(toNumber = toNumber, clientName = clientName))
        }
        if (response.status.value >= 400) {
            throw CallControlException(response.status.value, "dial failed")
        }
        return response.body()
    }

    suspend fun answer(callId: String) {
        val key = apiKeyProvider()
        val response = client.post("$baseUrl/calls/answer") {
            key?.let { header("X-API-Key", it) }
            contentType(ContentType.Application.Json)
            setBody(CallIdRequest(callId = callId))
        }
        if (response.status.value >= 400) {
            throw CallControlException(response.status.value, "answer failed")
        }
    }

    suspend fun hangup(callId: String) {
        val key = apiKeyProvider()
        val response = client.post("$baseUrl/calls/hangup") {
            key?.let { header("X-API-Key", it) }
            contentType(ContentType.Application.Json)
            setBody(CallIdRequest(callId = callId))
        }
        if (response.status.value >= 400) {
            throw CallControlException(response.status.value, "hangup failed")
        }
    }

    suspend fun getStatus(callId: String): StatusResponse {
        val key = apiKeyProvider()
        val response = client.get("$baseUrl/calls/status") {
            key?.let { header("X-API-Key", it) }
            parameter("callId", callId)
        }
        if (response.status.value >= 400) {
            throw CallControlException(response.status.value, "status failed")
        }
        return response.body()
    }

    fun close() {
        client.close()
    }
}

class CallControlException(val statusCode: Int, message: String) : Exception(message)

@Serializable
private data class DialRequest(
    @SerialName("toNumber") val toNumber: String,
    @SerialName("clientName") val clientName: String?,
)

@Serializable
private data class CallIdRequest(
    @SerialName("callId") val callId: String,
)

@Serializable
data class DialResponse(
    @SerialName("callId") val callId: String,
    @SerialName("meetingId") val meetingId: String,
    @SerialName("joinToken") val joinToken: String,
    @SerialName("attendeeId") val attendeeId: String,
    @SerialName("externalUserId") val externalUserId: String,
    @SerialName("mediaRegion") val mediaRegion: String,
    @SerialName("audioHostUrl") val audioHostUrl: String,
    @SerialName("audioFallbackUrl") val audioFallbackUrl: String,
    @SerialName("signalingUrl") val signalingUrl: String,
    @SerialName("turnControlUrl") val turnControlUrl: String,
)

@Serializable
data class StatusResponse(
    @SerialName("callId") val callId: String,
    @SerialName("status") val status: String,
    @SerialName("meetingId") val meetingId: String? = null,
)
