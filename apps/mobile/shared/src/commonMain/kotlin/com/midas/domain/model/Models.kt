package com.midas.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Advisor(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val status: String = "active",
)

@Serializable
data class RegisterAdvisorRequest(
    val name: String,
    val email: String,
    val phone: String? = null,
)

@Serializable
data class RegisterAdvisorResponse(
    val id: String,
    @SerialName("api_key") val apiKey: String,
    val name: String,
    val email: String,
)

@Serializable
data class ConversationSummary(
    val id: String,
    @SerialName("advisor_name") val advisorName: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("message_count") val messageCount: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Message(
    @SerialName("sender_name") val senderName: String,
    @SerialName("is_advisor") val isAdvisor: Boolean,
    val text: String,
    val timestamp: String,
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("advisor_name") val advisorName: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("message_count") val messageCount: Int,
    val messages: List<Message>,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class IntentResult(
    @SerialName("intent_detected") val intentDetected: Boolean,
    val confidence: Double,
    @SerialName("product_type") val productType: String? = null,
    val entities: Map<String, String?> = emptyMap(),
    val summary: String,
    @SerialName("is_actionable") val isActionable: Boolean = false,
)

@Serializable
data class CreditApplication(
    val id: String,
    val status: String,
    @SerialName("status_label") val statusLabel: String? = null,
    val applicant: ApplicantData,
    @SerialName("product_request") val productRequest: ProductRequest,
    @SerialName("conversation_summary") val conversationSummary: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ApplicantData(
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    @SerialName("estimated_income") val estimatedIncome: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    val completeness: Double? = null,
)

@Serializable
data class ProductRequest(
    @SerialName("product_type") val productType: String? = null,
    @SerialName("product_label") val productLabel: String? = null,
    val amount: String? = null,
    val term: String? = null,
    val location: String? = null,
    val summary: String? = null,
)

@Serializable
data class CallSummary(
    val id: String,
    @SerialName("client_name") val clientName: String,
    val status: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CallDetail(
    val id: String,
    @SerialName("client_name") val clientName: String,
    val status: String,
    val transcript: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class StartCallRequest(
    @SerialName("client_name") val clientName: String,
)

@Serializable
data class TranscriptMessage(
    val type: String,
    val text: String? = null,
    @SerialName("is_final") val isFinal: Boolean = false,
    val message: String? = null,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val transcript: String? = null,
    // Intent fields (type == "intent")
    @SerialName("intent_detected") val intentDetected: Boolean = false,
    val confidence: Double = 0.0,
    @SerialName("product_type") val productType: String? = null,
    val summary: String? = null,
    @SerialName("is_actionable") val isActionable: Boolean = false,
)
