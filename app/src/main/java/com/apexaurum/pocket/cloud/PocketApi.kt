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

    // ─── Village Pulse ────────────────────────────────────────────────

    @POST("api/v1/pocket/ws-token")
    suspend fun getWsToken(): WsTokenResponse

    @GET("api/v1/pocket/village-pulse")
    suspend fun getVillagePulse(): VillagePulseResponse

    // ─── Agora Feed ──────────────────────────────────────────────────

    @GET("api/v1/pocket/agora")
    suspend fun getAgoraFeed(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Query("content_type") contentType: String? = null,
    ): AgoraFeedResponse

    @POST("api/v1/pocket/agora/{postId}/react")
    suspend fun reactToPost(
        @Path("postId") postId: String,
        @Body request: ReactRequest,
    ): ReactResponse

    // ─── Smart Nudges ──────────────────────────────────────────────

    @GET("api/v1/pocket/nudge")
    suspend fun getNudge(): NudgeResponse

    // ─── Morning Briefing ──────────────────────────────────────────

    @GET("api/v1/pocket/briefing")
    suspend fun getBriefing(
        @Query("local_time") localTime: String? = null,
    ): BriefingResponse

    // ─── Pending Messages ──────────────────────────────────────────

    @GET("api/v1/pocket/pending-messages")
    suspend fun getPendingMessages(): PendingMessagesResponse

    // ─── SensorHead Dashboard ────────────────────────────────────

    @GET("api/v1/pocket/sensors")
    suspend fun getSensorStatus(): SensorStatusResponse

    @POST("api/v1/pocket/sensors/environment")
    suspend fun readEnvironment(): SensorEnvironmentResponse

    @POST("api/v1/pocket/sensors/capture/{camera}")
    suspend fun captureCamera(@Path("camera") camera: String): SensorCaptureResponse

    @POST("api/v1/pocket/sensors/thermal")
    suspend fun captureThermal(): SensorCaptureResponse

    @POST("api/v1/pocket/sensors/snapshot")
    suspend fun sensorSnapshot(): SensorSnapshotResponse

    // ─── Sentinel (autonomous motion detection) ────────────────────

    @GET("api/v1/pocket/sentinel/status")
    suspend fun getSentinelStatus(): SentinelStatusResponse

    @POST("api/v1/pocket/sentinel/arm")
    suspend fun sentinelArm(): SentinelActionResponse

    @POST("api/v1/pocket/sentinel/disarm")
    suspend fun sentinelDisarm(): SentinelActionResponse

    @POST("api/v1/pocket/sentinel/configure")
    suspend fun sentinelConfigure(@Body config: Map<String, @Serializable Any>): SentinelActionResponse

    @POST("api/v1/pocket/sentinel/presets/{name}/load")
    suspend fun sentinelLoadPreset(@Path("name") name: String): SentinelActionResponse

    @GET("api/v1/pocket/sentinel/events")
    suspend fun getSentinelEvents(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): SentinelEventsResponse

    @POST("api/v1/pocket/sentinel/events/{id}/ack")
    suspend fun sentinelAckEvent(@Path("id") id: String): SentinelAckResponse

    @POST("api/v1/pocket/sentinel/events/ack-all")
    suspend fun sentinelAckAll(): SentinelAckResponse

    @GET("api/v1/pocket/sentinel/events/{id}/snapshot")
    suspend fun getSentinelSnapshot(@Path("id") id: String): SentinelSnapshotResponse

    @POST("api/v1/pocket/sentinel/pocket-alert")
    suspend fun postPocketAlert(@Body body: PocketAlertRequest): PocketAlertResponse
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
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("local_time") val localTime: String? = null,
    val timezone: String? = null,
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

// ─── Agora Models ───────────────────────────────────────────────

@Serializable
data class AgoraFeedResponse(
    val posts: List<AgoraPostItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class AgoraPostItem(
    val id: String,
    @SerialName("content_type") val contentType: String = "user_post",
    val title: String? = null,
    val body: String = "",
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("my_reactions") val myReactions: List<String> = emptyList(),
)

@Serializable
data class ReactRequest(
    @SerialName("reaction_type") val reactionType: String,
)

@Serializable
data class ReactResponse(
    val action: String = "",
    @SerialName("reaction_type") val reactionType: String = "",
    @SerialName("reaction_count") val reactionCount: Int = 0,
)

// ─── Pending Messages Models ────────────────────────────────────

@Serializable
data class PendingMessagesResponse(
    val messages: List<PendingMessageItem> = emptyList(),
)

@Serializable
data class PendingMessageItem(
    val id: String,
    @SerialName("agent_id") val agentId: String = "AZOTH",
    val text: String = "",
    @SerialName("event_type") val eventType: String = "general",
    @SerialName("created_at") val createdAt: String? = null,
)

// ─── Morning Briefing Models ────────────────────────────────────

@Serializable
data class BriefingResponse(
    val briefing: BriefingData? = null,
)

@Serializable
data class BriefingData(
    val greeting: String = "Welcome back",
    val highlights: List<BriefingHighlight> = emptyList(),
    val milestone: String? = null,
    @SerialName("agent_id") val agentId: String = "AZOTH",
)

@Serializable
data class BriefingHighlight(
    val type: String = "general",  // council, music, agora
    val text: String = "",
)

// ─── Smart Nudge Models ─────────────────────────────────────────

@Serializable
data class NudgeResponse(
    val nudge: NudgeItem? = null,
)

@Serializable
data class NudgeItem(
    @SerialName("agent_id") val agentId: String = "AZOTH",
    val text: String = "",
    @SerialName("event_type") val eventType: String = "general",
)

// ─── Village Pulse Models ───────────────────────────────────────

@Serializable
data class WsTokenResponse(
    val token: String,
    @SerialName("expires_in") val expiresIn: Int = 3600,
)

// ─── Village Pulse REST Models ─────────────────────────────────

@Serializable
data class VillagePulseResponse(
    val councils: List<VillagePulseCouncil> = emptyList(),
    val music: List<VillagePulseMusic> = emptyList(),
    val agora: List<VillagePulseAgora> = emptyList(),
)

@Serializable
data class VillagePulseCouncil(
    val topic: String = "",
    val state: String = "",
    val round: Int = 0,
    val age: String = "recently",
)

@Serializable
data class VillagePulseMusic(
    val title: String? = null,
    val agent: String? = null,
    val status: String = "",
    val age: String = "recently",
)

@Serializable
data class VillagePulseAgora(
    @SerialName("content_type") val contentType: String = "",
    val title: String? = null,
    val agent: String? = null,
    val age: String = "recently",
)

@Serializable
data class VillageEvent(
    val type: String,
    @SerialName("agent_id") val agentId: String = "SYSTEM",
    val tool: String? = null,
    val zone: String = "village_square",
    @SerialName("result_preview") val resultPreview: String? = null,
    val success: Boolean? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

// ─── SensorHead Models ─────────────────────────────────────────

@Serializable
data class SensorTelemetryReadings(
    @SerialName("temperature_c") val temperatureC: Float? = null,
    @SerialName("humidity_pct") val humidityPct: Float? = null,
    @SerialName("pressure_hpa") val pressureHpa: Float? = null,
    @SerialName("co2_ppm") val co2Ppm: Float? = null,
    val iaq: Float? = null,
    @SerialName("iaq_accuracy") val iaqAccuracy: Int? = null,
    @SerialName("voc_ppm") val vocPpm: Float? = null,
    @SerialName("thermal_min_c") val thermalMinC: Float? = null,
    @SerialName("thermal_max_c") val thermalMaxC: Float? = null,
    @SerialName("thermal_avg_c") val thermalAvgC: Float? = null,
)

@Serializable
data class SensorTelemetry(
    val readings: SensorTelemetryReadings? = null,
    val timestamp: Double? = null,
    @SerialName("age_s") val ageS: Float? = null,
    val source: String? = null,
)

@Serializable
data class SensorStatusResponse(
    val online: Boolean = false,
    @SerialName("device_name") val deviceName: String = "SensorHead",
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("connected_at") val connectedAt: Double? = null,
    @SerialName("uptime_s") val uptimeS: Float? = null,
    val telemetry: SensorTelemetry? = null,
)

@Serializable
data class SensorEnvironmentResponse(
    val data: SensorTelemetryReadings? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("device_name") val deviceName: String = "",
)

@Serializable
data class SensorCaptureResponse(
    @SerialName("image_base64") val imageBase64: String? = null,
    val camera: String? = null,
    val sensor: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("device_name") val deviceName: String = "",
)

@Serializable
data class SensorSnapshotResponse(
    val environment: SensorTelemetryReadings? = null,
    @SerialName("visual_base64") val visualBase64: String? = null,
    @SerialName("night_base64") val nightBase64: String? = null,
    @SerialName("thermal_base64") val thermalBase64: String? = null,
    val errors: List<String> = emptyList(),
    @SerialName("total_duration_ms") val totalDurationMs: Int = 0,
    @SerialName("device_name") val deviceName: String = "",
)

// ─── Sentinel Models ─────────────────────────────────────────────

@Serializable
data class SentinelConfig(
    @SerialName("thermal_threshold_c") val thermalThresholdC: Float = 2.0f,
    @SerialName("min_changed_pixels") val minChangedPixels: Int = 10,
    @SerialName("scan_interval") val scanInterval: Float = 0.5f,
    @SerialName("ai_confirm") val aiConfirm: Boolean = true,
    @SerialName("ai_confidence") val aiConfidence: Float = 0.3f,
    @SerialName("ai_labels") val aiLabels: List<String> = listOf("person", "cat", "dog", "bird"),
    @SerialName("cooldown_s") val cooldownS: Float = 30f,
    @SerialName("max_alerts_per_hour") val maxAlertsPerHour: Int = 20,
    @SerialName("include_snapshot") val includeSnapshot: Boolean = true,
    @SerialName("active_start") val activeStart: String = "",
    @SerialName("active_end") val activeEnd: String = "",
)

@Serializable
data class SentinelStats(
    @SerialName("scan_count") val scanCount: Int = 0,
    @SerialName("trigger_count") val triggerCount: Int = 0,
    @SerialName("alert_count") val alertCount: Int = 0,
    @SerialName("alerts_this_hour") val alertsThisHour: Int = 0,
    @SerialName("last_scan_ms") val lastScanMs: Float = 0f,
    @SerialName("last_alert_time") val lastAlertTime: Double = 0.0,
)

@Serializable
data class SentinelStatusResponse(
    val online: Boolean = false,
    val armed: Boolean = false,
    val running: Boolean = false,
    val config: SentinelConfig? = null,
    val presets: List<String> = emptyList(),
    val stats: SentinelStats? = null,
)

@Serializable
data class SentinelActionResponse(
    val action: String = "",
    val armed: Boolean = false,
    val running: Boolean = false,
    val config: SentinelConfig? = null,
    val presets: List<String> = emptyList(),
    val stats: SentinelStats? = null,
    val preset: String? = null,
)

@Serializable
data class SentinelEventData(
    @SerialName("event_type") val eventType: String = "motion",
    val timestamp: Double = 0.0,
    @SerialName("thermal_delta") val thermalDelta: Float = 0f,
    @SerialName("changed_pixels") val changedPixels: Int = 0,
    @SerialName("thermal_min_c") val thermalMinC: Float = 0f,
    @SerialName("thermal_max_c") val thermalMaxC: Float = 0f,
    @SerialName("thermal_avg_c") val thermalAvgC: Float = 0f,
    @SerialName("ai_detections") val aiDetections: List<SentinelDetection> = emptyList(),
)

@Serializable
data class SentinelDetection(
    val label: String = "",
    val confidence: Float = 0f,
    @SerialName("class_id") val classId: Int = 0,
)

@Serializable
data class SentinelEvent(
    val id: String = "",
    val type: String = "motion",
    val data: SentinelEventData? = null,
    val acknowledged: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
)

@Serializable
data class SentinelEventsResponse(
    val events: List<SentinelEvent> = emptyList(),
    @SerialName("unacked_count") val unackedCount: Int = 0,
)

@Serializable
data class SentinelAckResponse(
    val action: String = "",
    @SerialName("event_id") val eventId: String? = null,
    val count: Int? = null,
)

@Serializable
data class SentinelSnapshotResponse(
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("event_id") val eventId: String? = null,
)

// ─── Pocket Sentinel (phone as guardian) ────────────────────────────

@Serializable
data class PocketAlertRequest(
    @SerialName("alert_type") val alertType: String,
    @SerialName("snapshot_b64") val snapshotB64: String? = null,
    @SerialName("detection_mode") val detectionMode: String,
    val magnitude: Float,
    val detail: String,
)

@Serializable
data class PocketAlertResponse(
    val id: String = "",
    @SerialName("alert_type") val alertType: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)
