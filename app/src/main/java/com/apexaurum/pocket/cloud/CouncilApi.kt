package com.apexaurum.pocket.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

/** Council API client — JWT-authenticated for spectating council deliberations. */
interface CouncilApi {

    @GET("api/v1/council/sessions")
    suspend fun getSessions(@Query("limit") limit: Int = 20): List<CouncilSession>

    @GET("api/v1/council/sessions/{id}")
    suspend fun getSession(@Path("id") id: String): CouncilSessionDetail

    @POST("api/v1/council/sessions/{id}/butt-in")
    suspend fun buttIn(@Path("id") id: String, @Body request: ButtInRequest): ButtInResponse
}

// ─── Council Models ─────────────────────────────────────────────

@Serializable
data class CouncilAgentInfo(
    @SerialName("agent_id") val agentId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class CouncilToolCall(
    val name: String,
    val result: String? = null,
)

@Serializable
data class CouncilMessage(
    val id: String = "",
    val role: String = "agent",
    @SerialName("agent_id") val agentId: String? = null,
    val content: String = "",
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("tool_calls") val toolCalls: List<CouncilToolCall>? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CouncilRound(
    @SerialName("round_number") val roundNumber: Int,
    @SerialName("human_message") val humanMessage: String? = null,
    @SerialName("convergence_score") val convergenceScore: Float = 0f,
    val messages: List<CouncilMessage> = emptyList(),
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class CouncilSession(
    val id: String,
    val topic: String,
    val state: String = "pending",
    val mode: String = "auto",
    val model: String = "",
    @SerialName("current_round") val currentRound: Int = 0,
    @SerialName("max_rounds") val maxRounds: Int = 10,
    @SerialName("convergence_score") val convergenceScore: Float = 0f,
    val agents: List<CouncilAgentInfo> = emptyList(),
    @SerialName("tool_categories") val toolCategories: List<String>? = null,
    @SerialName("total_cost_usd") val totalCostUsd: Float = 0f,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CouncilSessionDetail(
    val id: String,
    val topic: String,
    val state: String = "pending",
    val mode: String = "auto",
    val model: String = "",
    @SerialName("current_round") val currentRound: Int = 0,
    @SerialName("max_rounds") val maxRounds: Int = 10,
    @SerialName("convergence_score") val convergenceScore: Float = 0f,
    val agents: List<CouncilAgentInfo> = emptyList(),
    @SerialName("tool_categories") val toolCategories: List<String>? = null,
    @SerialName("total_cost_usd") val totalCostUsd: Float = 0f,
    @SerialName("created_at") val createdAt: String? = null,
    val rounds: List<CouncilRound> = emptyList(),
)

@Serializable
data class ButtInRequest(val message: String)

@Serializable
data class ButtInResponse(
    val status: String = "",
    val message: String = "",
    @SerialName("will_apply_to_round") val willApplyToRound: Int? = null,
)
