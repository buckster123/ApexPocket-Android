package com.apexaurum.pocket.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

/** Cloud API client — mirrors the backend pocket.py endpoints exactly. */
interface PocketApi {

    @GET("api/v1/pocket/status")
    suspend fun getStatus(): StatusResponse

    @POST("api/v1/pocket/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("api/v1/pocket/care")
    suspend fun care(@Body request: CareRequest): CareResponse

    @POST("api/v1/pocket/sync")
    suspend fun sync(@Body request: SyncRequest): SyncResponse

    @GET("api/v1/pocket/agents")
    suspend fun getAgents(): AgentsResponse

    @GET("api/v1/pocket/memories")
    suspend fun getMemories(): MemoriesResponse

    @GET("api/v1/pocket/memories")
    suspend fun getAgentMemories(
        @Query("agent") agent: String,
        @Query("limit") limit: Int = 20,
    ): AgentMemoriesResponse

    @POST("api/v1/pocket/memories")
    suspend fun saveMemory(@Body request: SaveMemoryRequest): SaveMemoryResponse

    @DELETE("api/v1/pocket/memories/{id}")
    suspend fun deleteMemory(@Path("id") id: String): DeleteMemoryResponse

    @GET("api/v1/pocket/history")
    suspend fun getHistory(
        @Query("agent") agent: String,
        @Query("limit") limit: Int = 50,
    ): HistoryResponse
}

// ─── Request Models (match backend Pydantic schemas exactly) ─────

@Serializable
data class ChatRequest(
    val message: String,
    val agent: String = "AZOTH",
    @SerialName("E") val energy: Float = 1.0f,
    val state: String = "WARM",
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
)

@Serializable
data class CareRequest(
    @SerialName("care_type") val careType: String,  // love, poke, pet, talk
    val intensity: Float = 1.0f,
    @SerialName("E") val energy: Float = 1.0f,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class SyncRequest(
    @SerialName("E") val energy: Float,
    @SerialName("E_floor") val energyFloor: Float,
    @SerialName("E_peak") val energyPeak: Float? = null,
    val interactions: Long,
    @SerialName("total_care") val totalCare: Float,
    val state: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = "app-1.0.0",
)

// ─── Response Models (match backend return dicts exactly) ────────

@Serializable
data class StatusResponse(
    @SerialName("village_online") val villageOnline: Boolean = true,
    @SerialName("agents_active") val agentsActive: Int = 0,
    @SerialName("tools_available") val toolsAvailable: Int = 0,
    @SerialName("message_of_the_day") val motd: String = "",
)

@Serializable
data class ChatResponse(
    val response: String,
    val expression: String = "NEUTRAL",
    @SerialName("care_value") val careValue: Float = 0.0f,
    val agent: String = "AZOTH",
    @SerialName("tools_used") val toolsUsed: List<String> = emptyList(),
    @SerialName("conversation_id") val conversationId: String? = null,
)

@Serializable
data class CareResponse(
    val success: Boolean = true,
    val response: String = "",
    @SerialName("care_value") val careValue: Float = 0.0f,
    @SerialName("new_E_estimate") val newEEstimate: Float = 0.0f,
)

@Serializable
data class SyncResponse(
    val success: Boolean = true,
    @SerialName("synced_at") val syncedAt: Double = 0.0,
    @SerialName("village_acknowledged") val villageAcknowledged: Boolean = true,
    val message: String = "",
    val memories: List<MemoryItem> = emptyList(),
)

@Serializable
data class MemoryItem(
    val content: String,
    val type: String = "fact",
    val agent: String = "AZOTH",
    val age: String = "recently",
)

@Serializable
data class AgentsResponse(
    val agents: List<AgentInfo>,
    val default: String = "AZOTH",
)

@Serializable
data class AgentInfo(
    val name: String,
    val description: String = "",
)

@Serializable
data class MemoriesResponse(
    val memories: List<MemoryItem> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class HistoryResponse(
    @SerialName("conversation_id") val conversationId: String? = null,
    val messages: List<HistoryMessage> = emptyList(),
)

@Serializable
data class HistoryMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = 0,
)

// ─── Memory CRUD Models ──────────────────────────────────────────

@Serializable
data class AgentMemoryItem(
    val id: String,
    @SerialName("agent_id") val agentId: String,
    @SerialName("memory_type") val memoryType: String = "fact",
    val key: String,
    val value: String,
    val confidence: Float = 0.8f,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_accessed") val lastAccessed: String? = null,
    @SerialName("access_count") val accessCount: Int = 0,
)

@Serializable
data class AgentMemoriesResponse(
    val memories: List<AgentMemoryItem> = emptyList(),
    val count: Int = 0,
    val agent: String = "",
)

@Serializable
data class SaveMemoryRequest(
    val agent: String = "AZOTH",
    @SerialName("memory_type") val memoryType: String = "fact",
    val key: String,
    val value: String,
    val confidence: Float = 0.8f,
)

@Serializable
data class SaveMemoryResponse(
    val id: String = "",
    val message: String = "",
    val key: String = "",
    val agent: String = "",
)

@Serializable
data class DeleteMemoryResponse(
    val message: String = "",
    val id: String = "",
)
