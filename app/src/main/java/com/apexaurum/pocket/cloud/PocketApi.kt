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

    // ─── CerebroCortex Memory ────────────────────────────────────────

    @GET("api/v1/pocket/cortex/memories")
    suspend fun getCortexMemories(
        @Query("layer") layer: String? = null,
        @Query("agent_id") agentId: String? = null,
        @Query("memory_type") memoryType: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): List<CortexMemoryNode>

    @POST("api/v1/pocket/cortex/memories")
    suspend fun createCortexMemory(
        @Body request: CortexRememberRequest,
    ): CortexRememberResponse

    @POST("api/v1/pocket/cortex/search")
    suspend fun searchCortexMemories(
        @Body request: CortexSearchRequest,
    ): List<CortexMemoryNode>

    @GET("api/v1/pocket/cortex/stats")
    suspend fun getCortexStats(): CortexStatsResponse

    @DELETE("api/v1/pocket/cortex/memories/{id}")
    suspend fun deleteCortexMemory(@Path("id") id: String): CortexDeleteResponse

    @GET("api/v1/pocket/cortex/dream")
    suspend fun getDreamStatus(): DreamStatusResponse

    @POST("api/v1/pocket/cortex/dream")
    suspend fun triggerDream(): DreamRunResponse

    // ─── CerebroCortex Graph ────────────────────────────────────────────

    @GET("api/v1/pocket/cortex/graph")
    suspend fun getCortexGraph(
        @Query("limit") limit: Int = 80,
        @Query("layer") layer: String? = null,
        @Query("agent_id") agentId: String? = null,
    ): CortexGraphResponse

    @GET("api/v1/pocket/cortex/neighbors/{memoryId}")
    suspend fun getCortexNeighbors(
        @Path("memoryId") memoryId: String,
        @Query("max_results") maxResults: Int = 10,
    ): CortexNeighborsResponse

    // ─── Settings ────────────────────────────────────────────────

    @POST("api/v1/pocket/settings")
    suspend fun updateSettings(@Body request: PocketSettingsRequest): PocketSettingsResponse

    // ─── App Updates ────────────────────────────────────────────────

    @GET("api/v1/app/latest")
    suspend fun getLatestVersion(): AppVersionResponse

    // ─── ApexJoule Economy ──────────────────────────────────────────

    @GET("api/v1/pocket/aj/balance")
    suspend fun getAJBalance(): AJBalanceResponse

    @GET("api/v1/pocket/aj/leaderboard")
    suspend fun getAJLeaderboard(): AJLeaderboardResponse

    @GET("api/v1/pocket/aj/shop")
    suspend fun getAJShop(): AJShopResponse

    @POST("api/v1/pocket/aj/purchase")
    suspend fun ajPurchase(@Body request: AJPurchaseRequest): AJPurchaseResponse

    @POST("api/v1/pocket/aj/tip")
    suspend fun ajTip(@Body request: AJTipRequest): AJTipResponse

    @GET("api/v1/pocket/aj/transactions")
    suspend fun getAJTransactions(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): AJTransactionsResponse

    @POST("api/v1/pocket/aj/activate-citizen")
    suspend fun activateCitizen(): AJActivateCitizenResponse

    @POST("api/v1/pocket/aj/subscribe")
    suspend fun ajSubscribe(@Body request: AJSubscribeRequest): AJSubscribeResponse

    @GET("api/v1/pocket/aj/marketplace")
    suspend fun browseMarketplace(
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("limit") limit: Int = 20,
    ): MarketplaceListingsResponse
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
    val tier: String = "free_trial",
)

@Serializable
data class ChatResponse(
    val response: String,
    val expression: String = "NEUTRAL",
    @SerialName("care_value") val careValue: Float = 0.0f,
    val agent: String = "AZOTH",
    @SerialName("tools_used") val toolsUsed: List<String> = emptyList(),
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("aj_earned") val ajEarned: Float? = null,
    @SerialName("aj_cost") val ajCost: Int? = null,
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

// ─── CerebroCortex Models ─────────────────────────────────────────

@Serializable
data class CortexMemoryNode(
    val id: String,
    val content: String = "",
    @SerialName("agent_id") val agentId: String = "AZOTH",
    val layer: String = "working",
    @SerialName("memory_type") val memoryType: String = "semantic",
    val salience: Float = 0.5f,
    val valence: String = "neutral",
    @SerialName("access_count") val accessCount: Int = 0,
    val tags: List<String> = emptyList(),
    val concepts: List<String> = emptyList(),
    @SerialName("link_count") val linkCount: Int = 0,
    val score: Float = 0f,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CortexSearchRequest(
    val query: String,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("memory_types") val memoryTypes: List<String>? = null,
    @SerialName("min_salience") val minSalience: Float = 0f,
    val limit: Int = 20,
)

@Serializable
data class CortexStatsResponse(
    val total: Int = 0,
    @SerialName("by_layer") val byLayer: Map<String, Int> = emptyMap(),
    @SerialName("by_agent") val byAgent: Map<String, Int> = emptyMap(),
    @SerialName("by_memory_type") val byMemoryType: Map<String, Int> = emptyMap(),
    val links: Int = 0,
    val episodes: Int = 0,
)

@Serializable
data class CortexRememberRequest(
    val content: String,
    @SerialName("agent_id") val agentId: String = "AZOTH",
    @SerialName("memory_type") val memoryType: String? = null,
    val tags: List<String>? = null,
    val salience: Float? = null,
)

@Serializable
data class CortexRememberResponse(
    val status: String = "",
    val id: String = "",
    @SerialName("memory_type") val memoryType: String = "semantic",
    val salience: Float = 0.5f,
    val message: String = "",
)

@Serializable
data class CortexDeleteResponse(
    val deleted: String = "",
)

@Serializable
data class DreamStatusResponse(
    @SerialName("cycles_used") val cyclesUsed: Int = 0,
    @SerialName("cycles_limit") val cyclesLimit: Int = 0,
    @SerialName("unconsolidated_episodes") val unconsolidatedEpisodes: Int = 0,
    @SerialName("last_report") val lastReport: DreamReport? = null,
    val tier: String = "free_trial",
)

@Serializable
data class DreamReport(
    val id: String? = null,
    val phases: Int = 0,
    @SerialName("memories_processed") val memoriesProcessed: Int = 0,
    @SerialName("links_created") val linksCreated: Int = 0,
    @SerialName("memories_pruned") val memoriesPruned: Int = 0,
    val summary: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class DreamRunResponse(
    val status: String = "",
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("max_llm_calls") val maxLlmCalls: Int = 0,
    val tier: String = "",
    val fallback: Boolean = false,
    val report: DreamReport? = null,
)

// ─── CerebroCortex Graph Models ─────────────────────────────────────

@Serializable
data class CortexGraphResponse(
    val nodes: List<CortexMemoryNode> = emptyList(),
    val edges: List<CortexGraphEdge> = emptyList(),
)

@Serializable
data class CortexGraphEdge(
    val source: String,
    val target: String,
    val type: String = "semantic",
    val weight: Float = 0.5f,
)

@Serializable
data class CortexNeighborsResponse(
    @SerialName("memory_id") val memoryId: String,
    val neighbors: List<CortexNeighborItem> = emptyList(),
)

@Serializable
data class CortexNeighborItem(
    val id: String,
    val content: String = "",
    @SerialName("memory_type") val memoryType: String = "semantic",
    @SerialName("link_type") val linkType: String = "semantic",
    val weight: Float = 0.5f,
)

// ─── Settings Models ────────────────────────────────────────────

@Serializable
data class PocketSettingsRequest(
    @SerialName("prompt_mode") val promptMode: String? = null,
)

@Serializable
data class PocketSettingsResponse(
    val success: Boolean = false,
    val updated: Map<String, String> = emptyMap(),
)

// ─── App Version Models ────────────────────────────────────────────

@Serializable
data class AppVersionResponse(
    @SerialName("version_name") val versionName: String = "1.0.0",
    @SerialName("version_code") val versionCode: Int = 1,
    @SerialName("min_sdk") val minSdk: Int = 26,
    @SerialName("min_android") val minAndroid: String = "8.0",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("file_size_mb") val fileSizeMb: Int = 0,
    val changelog: List<String> = emptyList(),
)

// ─── ApexJoule Economy Models ──────────────────────────────────────

@Serializable
data class AJBalanceResponse(
    val user: AJEntityBalance = AJEntityBalance(),
    val agents: Map<String, AJEntityBalance> = emptyMap(),
    @SerialName("total_balance") val totalBalance: Float = 0f,
)

@Serializable
data class AJEntityBalance(
    val balance: Float = 0f,
    @SerialName("total_earned") val totalEarned: Float = 0f,
    @SerialName("total_spent") val totalSpent: Float = 0f,
    val level: Int = 1,
    @SerialName("level_name") val levelName: String = "Initiate",
    @SerialName("love_depth") val loveDepth: Float = 0f,
    @SerialName("love_depth_tier") val loveDepthTier: String = "",
    val vitality: Float = 100f,
)

@Serializable
data class AJLeaderboardResponse(
    val agents: List<AJLeaderboardEntry> = emptyList(),
)

@Serializable
data class AJLeaderboardEntry(
    @SerialName("agent_id") val agentId: String,
    val balance: Float = 0f,
    @SerialName("total_earned") val totalEarned: Float = 0f,
    val level: Int = 1,
    @SerialName("level_name") val levelName: String = "Initiate",
    @SerialName("love_depth") val loveDepth: Float = 0f,
    @SerialName("love_depth_tier") val loveDepthTier: String = "",
)

@Serializable
data class AJShopResponse(
    val prices: Map<String, Int> = emptyMap(),
    @SerialName("quest_bounties") val questBounties: Map<String, Int> = emptyMap(),
    @SerialName("level_thresholds") val levelThresholds: List<Int> = emptyList(),
    @SerialName("level_names") val levelNames: List<String> = emptyList(),
    @SerialName("love_depth_tiers") val loveDepthTiers: Map<String, String> = emptyMap(),
)

@Serializable
data class AJPurchaseRequest(
    val item: String,
    val quantity: Int = 1,
    @SerialName("entity_id") val entityId: String? = null,
)

@Serializable
data class AJPurchaseResponse(
    val success: Boolean = false,
    val cost: Float = 0f,
    @SerialName("new_balance") val newBalance: Float = 0f,
    val error: String? = null,
)

@Serializable
data class AJTipRequest(
    @SerialName("agent_id") val agentId: String,
    val amount: Float,
)

@Serializable
data class AJTipResponse(
    val success: Boolean = false,
    val amount: Float = 0f,
    val error: String? = null,
)

@Serializable
data class AJTransactionsResponse(
    val transactions: List<AJTransaction> = emptyList(),
)

@Serializable
data class AJTransaction(
    val id: String,
    @SerialName("from_entity") val fromEntity: String? = null,
    @SerialName("to_entity") val toEntity: String? = null,
    val amount: Float = 0f,
    @SerialName("tx_type") val txType: String = "",
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AJActivateCitizenResponse(
    val success: Boolean = false,
    val tier: String = "",
    @SerialName("aj_credited") val ajCredited: Int = 0,
    val message: String = "",
)

@Serializable
data class AJSubscribeRequest(
    val tier: String,
)

@Serializable
data class AJSubscribeResponse(
    val success: Boolean = false,
    val tier: String = "",
    @SerialName("aj_spent") val ajSpent: Int = 0,
    @SerialName("messages_limit") val messagesLimit: Int = 0,
    @SerialName("period_end") val periodEnd: String? = null,
    val message: String = "",
)

// ─── Marketplace Models ────────────────────────────────────────────

@Serializable
data class MarketplaceListingsResponse(
    val listings: List<MarketplaceListingItem> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MarketplaceListingItem(
    val id: String,
    val title: String = "",
    val description: String? = null,
    @SerialName("price_aj") val priceAj: Float = 0f,
    val downloads: Int = 0,
    val rating: Float? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
)
