package com.apexaurum.pocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.data.*
import com.apexaurum.pocket.data.db.ApexDatabase
import com.apexaurum.pocket.soul.LoveEquation
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.voice.SpeechService
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

/** Chat message for display. */
@Serializable
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val expression: String = "NEUTRAL",
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,      // currently executing tool (shows spinner)
    val toolResults: List<ToolInfo> = emptyList(),  // completed tool results
    val hasImage: Boolean = false,     // photo was attached to this message
    val type: String = "message",      // "message" | "briefing" | "divider" | "pending"
    val briefingData: com.apexaurum.pocket.cloud.BriefingData? = null,
    val agentId: String = "AZOTH",     // which agent sent this message
)

/** Tool execution result for display in chat. */
@Serializable
data class ToolInfo(
    val name: String,
    val result: String,
    val isError: Boolean = false,
    val media: MediaInfo? = null,
)

/** Rich media attached to a tool result for rendering link/audio/file cards. */
@Serializable
data class MediaInfo(
    val type: String,                    // "links" | "audio" | "files"
    val items: List<MediaItem> = emptyList(),
)

@Serializable
data class MediaItem(
    val title: String = "",
    val url: String = "",
    val snippet: String = "",
    val source: String = "",
    @kotlinx.serialization.SerialName("audio_url") val audioUrl: String = "",
    val duration: Float = 0f,
    val name: String = "",
    @kotlinx.serialization.SerialName("file_id") val fileId: String = "",
    @kotlinx.serialization.SerialName("is_folder") val isFolder: Boolean = false,
    val size: Long = 0,
    @kotlinx.serialization.SerialName("mime_type") val mimeType: String = "",
    @kotlinx.serialization.SerialName("task_id") val taskId: String = "",
)

/** Connection state with the cloud. */
enum class CloudState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class PocketViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SoulRepository(application)

    // ── Offline infrastructure ──
    private val db = ApexDatabase.getInstance(application)
    val networkMonitor = NetworkMonitor(application)
    private val chatRepo = ChatRepository(db)
    private val agentRepo = AgentRepository(db)
    private val memoryRepo = MemoryRepository(db)
    private val syncManager = SyncManager(db)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // Soul state (from DataStore, reactive)
    val soul: StateFlow<SoulData> = repo.soulFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SoulData())

    // Device token
    val token: StateFlow<String?> = repo.tokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Cloud connection
    private val _cloudState = MutableStateFlow(CloudState.DISCONNECTED)
    val cloudState: StateFlow<CloudState> = _cloudState.asStateFlow()

    // Chat messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Available agents from cloud
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()

    // Cloud status
    private val _motd = MutableStateFlow("")
    val motd: StateFlow<String> = _motd.asStateFlow()

    // Memories
    private val _memories = MutableStateFlow<List<com.apexaurum.pocket.cloud.AgentMemoryItem>>(emptyList())
    val memories: StateFlow<List<com.apexaurum.pocket.cloud.AgentMemoryItem>> = _memories.asStateFlow()
    private val _memoriesLoading = MutableStateFlow(false)
    val memoriesLoading: StateFlow<Boolean> = _memoriesLoading.asStateFlow()

    // CerebroCortex
    private val cortexRepo = CortexRepository(db)
    private val _cortexMemories = MutableStateFlow<List<CortexMemoryNode>>(emptyList())
    val cortexMemories: StateFlow<List<CortexMemoryNode>> = _cortexMemories.asStateFlow()
    private val _cortexLoading = MutableStateFlow(false)
    val cortexLoading: StateFlow<Boolean> = _cortexLoading.asStateFlow()
    private val _cortexSearchQuery = MutableStateFlow("")
    val cortexSearchQuery: StateFlow<String> = _cortexSearchQuery.asStateFlow()
    private val _cortexStats = MutableStateFlow<CortexStatsResponse?>(null)
    val cortexStats: StateFlow<CortexStatsResponse?> = _cortexStats.asStateFlow()
    private val _dreamStatus = MutableStateFlow<DreamStatusResponse?>(null)
    val dreamStatus: StateFlow<DreamStatusResponse?> = _dreamStatus.asStateFlow()
    private val _dreamTriggering = MutableStateFlow(false)
    val dreamTriggering: StateFlow<Boolean> = _dreamTriggering.asStateFlow()
    private val _cortexCacheAgeMs = MutableStateFlow<Long?>(null)
    val cortexCacheAgeMs: StateFlow<Long?> = _cortexCacheAgeMs.asStateFlow()
    private val _cortexPendingCount = MutableStateFlow(0)
    val cortexPendingCount: StateFlow<Int> = _cortexPendingCount.asStateFlow()
    private val _cortexAgentFilter = MutableStateFlow("all")
    val cortexAgentFilter: StateFlow<String> = _cortexAgentFilter.asStateFlow()

    // Graph visualization
    private val _graphData = MutableStateFlow<CortexGraphResponse?>(null)
    val graphData: StateFlow<CortexGraphResponse?> = _graphData.asStateFlow()
    private val _graphLoading = MutableStateFlow(false)
    val graphLoading: StateFlow<Boolean> = _graphLoading.asStateFlow()
    private val _selectedGraphNode = MutableStateFlow<CortexMemoryNode?>(null)
    val selectedGraphNode: StateFlow<CortexMemoryNode?> = _selectedGraphNode.asStateFlow()
    private val _graphNeighbors = MutableStateFlow<List<CortexNeighborItem>>(emptyList())
    val graphNeighbors: StateFlow<List<CortexNeighborItem>> = _graphNeighbors.asStateFlow()

    // Agora feed
    private val _agoraPosts = MutableStateFlow<List<AgoraPostItem>>(emptyList())
    val agoraPosts: StateFlow<List<AgoraPostItem>> = _agoraPosts.asStateFlow()
    private val _agoraLoading = MutableStateFlow(false)
    val agoraLoading: StateFlow<Boolean> = _agoraLoading.asStateFlow()
    private var agoraCursor: String? = null
    private var agoraHasMore = true

    // Village Pulse (WebSocket)
    private val villageWs = VillageWsClient()
    private val _villageEvents = MutableStateFlow<List<VillageEvent>>(emptyList())
    val villageEvents: StateFlow<List<VillageEvent>> = _villageEvents.asStateFlow()
    val villagePulseConnected: StateFlow<Boolean> = villageWs.connected
    private val _unseenPulseCount = MutableStateFlow(0)
    val unseenPulseCount: StateFlow<Int> = _unseenPulseCount.asStateFlow()
    private val _latestTickerEvent = MutableStateFlow<VillageEvent?>(null)
    val latestTickerEvent: StateFlow<VillageEvent?> = _latestTickerEvent.asStateFlow()
    private val _expressionOverride = MutableStateFlow<com.apexaurum.pocket.soul.Expression?>(null)
    val expressionOverride: StateFlow<com.apexaurum.pocket.soul.Expression?> = _expressionOverride.asStateFlow()
    private var expressionOverrideJob: Job? = null

    // Council Spectator
    private val councilWs = CouncilWsClient()
    private var councilApi: CouncilApi? = null
    private var cachedJwt: String? = null
    private var councilCollectJob: Job? = null
    private val _councilSessions = MutableStateFlow<List<CouncilSession>>(emptyList())
    val councilSessions: StateFlow<List<CouncilSession>> = _councilSessions.asStateFlow()
    private val _councilDetail = MutableStateFlow<CouncilSessionDetail?>(null)
    val councilDetail: StateFlow<CouncilSessionDetail?> = _councilDetail.asStateFlow()
    val councilStreaming: StateFlow<Boolean> = councilWs.connected
    private val _councilAgentOutputs = MutableStateFlow<Map<String, String>>(emptyMap())
    val councilAgentOutputs: StateFlow<Map<String, String>> = _councilAgentOutputs.asStateFlow()
    private val _councilCurrentRound = MutableStateFlow(0)
    val councilCurrentRound: StateFlow<Int> = _councilCurrentRound.asStateFlow()
    private val _councilButtInSent = MutableStateFlow(false)
    val councilButtInSent: StateFlow<Boolean> = _councilButtInSent.asStateFlow()
    private val _councilCreating = MutableStateFlow(false)
    val councilCreating: StateFlow<Boolean> = _councilCreating.asStateFlow()
    private val _pendingCouncilTopic = MutableStateFlow<String?>(null)
    val pendingCouncilTopic: StateFlow<String?> = _pendingCouncilTopic.asStateFlow()

    // Music Player & Downloads
    val musicPlayer = MusicPlayerManager(application)
    val musicDownloader = MusicDownloadManager(application)

    // Music Library
    private var musicApi: MusicApi? = null
    private val _musicTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val musicTracks: StateFlow<List<MusicTrack>> = _musicTracks.asStateFlow()
    private val _musicLoading = MutableStateFlow(false)
    val musicLoading: StateFlow<Boolean> = _musicLoading.asStateFlow()
    private val _musicTotal = MutableStateFlow(0)
    val musicTotal: StateFlow<Int> = _musicTotal.asStateFlow()
    private val _musicTotalDuration = MutableStateFlow(0f)
    val musicTotalDuration: StateFlow<Float> = _musicTotalDuration.asStateFlow()
    private val _musicSearchQuery = MutableStateFlow("")
    val musicSearchQuery: StateFlow<String> = _musicSearchQuery.asStateFlow()
    private val _musicFavoritesOnly = MutableStateFlow(false)
    val musicFavoritesOnly: StateFlow<Boolean> = _musicFavoritesOnly.asStateFlow()

    // SensorHead Dashboard
    private val _sensorStatus = MutableStateFlow<SensorStatusResponse?>(null)
    val sensorStatus: StateFlow<SensorStatusResponse?> = _sensorStatus.asStateFlow()
    private val _sensorImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val sensorImages: StateFlow<Map<String, String>> = _sensorImages.asStateFlow()
    private val _sensorLoading = MutableStateFlow(false)
    val sensorLoading: StateFlow<Boolean> = _sensorLoading.asStateFlow()
    private val _sensorCapturing = MutableStateFlow<Set<String>>(emptySet())
    val sensorCapturing: StateFlow<Set<String>> = _sensorCapturing.asStateFlow()

    // Sentinel
    private val _sentinelStatus = MutableStateFlow<SentinelStatusResponse?>(null)
    val sentinelStatus: StateFlow<SentinelStatusResponse?> = _sentinelStatus.asStateFlow()
    private val _sentinelEvents = MutableStateFlow<List<SentinelEvent>>(emptyList())
    val sentinelEvents: StateFlow<List<SentinelEvent>> = _sentinelEvents.asStateFlow()
    private val _sentinelUnacked = MutableStateFlow(0)
    val sentinelUnacked: StateFlow<Int> = _sentinelUnacked.asStateFlow()
    private val _sentinelLoading = MutableStateFlow(false)
    val sentinelLoading: StateFlow<Boolean> = _sentinelLoading.asStateFlow()
    private val _sentinelSnapshot = MutableStateFlow<String?>(null)
    val sentinelSnapshot: StateFlow<String?> = _sentinelSnapshot.asStateFlow()

    // Pocket Sentinel (phone as guardian)
    val pocketSentinelRunning: StateFlow<Boolean> = com.apexaurum.pocket.sentinel.PocketSentinelService.isRunning
    val pocketSentinelMode: StateFlow<Set<com.apexaurum.pocket.sentinel.DetectionMode>> = com.apexaurum.pocket.sentinel.PocketSentinelService.activeMode
    val pocketSentinelEventCount: StateFlow<Int> = com.apexaurum.pocket.sentinel.PocketSentinelService.eventCount
    val pocketSentinelLastEvent: StateFlow<com.apexaurum.pocket.sentinel.PocketSentinelEvent?> = com.apexaurum.pocket.sentinel.PocketSentinelService.lastEvent
    private val _pocketSentinelConfig = MutableStateFlow(com.apexaurum.pocket.sentinel.PocketSentinelConfig())
    val pocketSentinelConfig: StateFlow<com.apexaurum.pocket.sentinel.PocketSentinelConfig> = _pocketSentinelConfig.asStateFlow()

    // Conversation IDs per agent (from DataStore)
    private val _conversationIds: StateFlow<Map<String, String>> = repo.conversationIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Loading states
    private val _isChatting = MutableStateFlow(false)
    val isChatting: StateFlow<Boolean> = _isChatting.asStateFlow()

    // --- Voice ---
    val speechService = SpeechService(application)

    val isListening: StateFlow<Boolean> = speechService.isListening
    val isSpeaking: StateFlow<Boolean> = speechService.isSpeaking

    private val _pendingVoiceText = MutableStateFlow<String?>(null)
    val pendingVoiceText: StateFlow<String?> = _pendingVoiceText.asStateFlow()

    // --- Voice Memory ---
    private val _isVoiceMemoryActive = MutableStateFlow(false)
    val isVoiceMemoryActive: StateFlow<Boolean> = _isVoiceMemoryActive.asStateFlow()
    private val _voiceMemoryFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val voiceMemoryFeedback: SharedFlow<String> = _voiceMemoryFeedback.asSharedFlow()

    val autoRead: StateFlow<Boolean> = repo.autoReadFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Settings flows ──
    val hapticEnabled: StateFlow<Boolean> = repo.hapticFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifAgentsEnabled: StateFlow<Boolean> = repo.notifAgentsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifCouncilsEnabled: StateFlow<Boolean> = repo.notifCouncilsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifMusicEnabled: StateFlow<Boolean> = repo.notifMusicFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifNudgesEnabled: StateFlow<Boolean> = repo.notifNudgesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val promptMode: StateFlow<String> = repo.promptModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "lite")

    // API client (created when token is set)
    private var api: PocketApi? = null
    private var streamingClient: OkHttpClient? = null

    init {
        // Watch token changes to create/destroy API client
        viewModelScope.launch {
            token.collect { t ->
                if (t != null) {
                    api = CloudClient.create(t)
                    streamingClient = CloudClient.createStreamingClient(t)
                    connectToCloud()
                } else {
                    api = null
                    streamingClient = null
                    _cloudState.value = CloudState.DISCONNECTED
                }
            }
        }

        // Collect recognized text from STT — route to voice memory or chat input
        viewModelScope.launch {
            speechService.recognizedText.collect { text ->
                if (_isVoiceMemoryActive.value) {
                    _isVoiceMemoryActive.value = false
                    voiceMemoryCommand(text)
                } else {
                    _pendingVoiceText.value = text
                }
            }
        }

        // Auto-reset voice memory mode if STT stops without producing text
        viewModelScope.launch {
            speechService.isListening.collect { listening ->
                if (!listening && _isVoiceMemoryActive.value) {
                    delay(500) // allow recognizedText to emit first
                    if (_isVoiceMemoryActive.value) {
                        _isVoiceMemoryActive.value = false
                        _voiceMemoryFeedback.tryEmit("Voice not recognized. Try again.")
                    }
                }
            }
        }

        // Bridge music player state → DataStore for widget
        // Only emit when widget-relevant fields change (not on every position tick)
        viewModelScope.launch {
            combine(musicPlayer.playerState, musicPlayer.currentTrack) { state, track ->
                Pair(track?.title, state.isPlaying)
            }.distinctUntilChanged().collect { (title, isPlaying) ->
                repo.saveWidgetMusicState(title = title, isPlaying = isPlaying)
                try { com.apexaurum.pocket.widget.SoulWidget.refreshAll(getApplication()) } catch (_: Exception) {}
            }
        }

        // Register music toggle bridge for widget broadcast
        com.apexaurum.pocket.widget.MusicToggleBridge.toggleCallback = { musicPlayer.togglePlayPause() }

        // ── Global reconnect: sync queues + refresh when connection restores ──
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { online ->
                if (online && !wasOnline) {
                    // Connection restored — debounce to avoid rapid wifi toggles
                    delay(800)
                    if (!isOnline.value) { wasOnline = false; return@collect }

                    // Wait for API client (token collect may not have fired yet)
                    var retries = 0
                    while (api == null && retries < 10) { delay(300); retries++ }
                    val currentApi = api
                    if (currentApi == null) { wasOnline = online; return@collect }

                    Log.d("Reconnect", "Connection restored — syncing queues + refreshing")

                    // 1. Flush offline action queue (care, memories, cortex)
                    try { syncManager.processQueue(currentApi) } catch (_: Exception) {}

                    // 2. Reconnect Village Pulse with fresh WS token
                    try { connectVillagePulse() } catch (_: Exception) {}

                    // 3. Refresh cortex cache + pending count
                    try {
                        cortexRepo.refreshFromApi(currentApi)
                        refreshCortexMeta()
                    } catch (_: Exception) {}
                }
                wasOnline = online
            }
        }
    }

    /** Pair with cloud using a device token. */
    fun pair(deviceToken: String) {
        viewModelScope.launch {
            repo.saveToken(deviceToken)
        }
    }

    /** Un-pair from cloud. */
    fun unpair() {
        disconnectVillagePulse()
        disconnectCouncilStream()
        viewModelScope.launch {
            repo.clearToken()
            _messages.value = emptyList()
            _agents.value = emptyList()
            _cloudState.value = CloudState.DISCONNECTED
        }
    }

    /** Connect to cloud — fetch status + agents + history. */
    private fun connectToCloud() {
        // Load cached data from Room immediately (before network)
        viewModelScope.launch {
            try {
                val cachedAgents = agentRepo.getCached()
                if (cachedAgents.isNotEmpty() && _agents.value.isEmpty()) {
                    _agents.value = cachedAgents
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            _cloudState.value = CloudState.CONNECTING
            try {
                val status = api?.getStatus()
                if (status != null) {
                    _motd.value = status.motd
                    _cloudState.value = CloudState.CONNECTED
                }
                // Fetch agents + cache
                val currentApi = api
                if (currentApi != null) {
                    try {
                        val fresh = agentRepo.refreshFromApi(currentApi)
                        _agents.value = fresh
                    } catch (_: Exception) {}
                }
                // Fetch memories
                fetchMemories()
                // Load chat history for current agent
                loadHistory()
                // Fetch pending agent-initiated messages
                fetchPendingMessages()
                // Check for daily briefing (after history so it appears at top)
                checkDailyBriefing()
                // Load Agora feed
                loadAgoraFeed()
            } catch (e: Exception) {
                _cloudState.value = CloudState.ERROR
            }
        }
    }

    /** Fetch memories from cloud (filtered by current agent). Cache to Room. */
    fun fetchMemories() {
        viewModelScope.launch {
            _memoriesLoading.value = true
            try {
                val agent = soul.value.selectedAgentId
                val currentApi = api
                if (currentApi != null && isOnline.value) {
                    val fresh = memoryRepo.refreshFromApi(currentApi, agent)
                    _memories.value = fresh
                } else {
                    // Offline — load from Room cache
                    memoryRepo.memoriesForAgent(agent).first().let { cached ->
                        if (cached.isNotEmpty()) _memories.value = cached
                    }
                }
            } catch (_: Exception) {
                // Silent — memories are best-effort
            } finally {
                _memoriesLoading.value = false
            }
        }
    }

    /** Save a memory — online via API, offline via queue. */
    fun saveMemory(key: String, value: String, type: String = "fact") {
        viewModelScope.launch {
            try {
                val agent = soul.value.selectedAgentId
                memoryRepo.save(api, agent, key, value, type, isOnline.value)
                fetchMemories()
            } catch (_: Exception) {
                // Silent
            }
        }
    }

    /** Delete a memory — online via API, offline via queue. */
    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            // Optimistic removal
            _memories.value = _memories.value.filter { it.id != memoryId }
            try {
                memoryRepo.delete(api, memoryId, isOnline.value)
            } catch (_: Exception) {
                fetchMemories()
            }
        }
    }

    // ── CerebroCortex ──

    /** Fetch cortex memories. Online → API + cache. Offline → Room cache. */
    fun fetchCortexMemories(layer: String? = null, agentId: String? = null, memoryType: String? = null) {
        viewModelScope.launch {
            _cortexLoading.value = true
            try {
                val currentApi = api
                if (currentApi != null && isOnline.value) {
                    val nodes = cortexRepo.refreshFromApi(currentApi, layer, agentId, memoryType)
                    _cortexMemories.value = nodes
                } else {
                    cortexRepo.allCached().first().let { _cortexMemories.value = it }
                }
                refreshCortexMeta()
            } catch (_: Exception) { }
            finally { _cortexLoading.value = false }
        }
    }

    /** Filter cortex memories by agent. */
    fun setCortexAgentFilter(agentId: String) {
        _cortexAgentFilter.value = agentId
        fetchCortexMemories(agentId = if (agentId == "all") null else agentId)
    }

    /** Semantic search through cortex memories. */
    fun searchCortexMemories(query: String) {
        _cortexSearchQuery.value = query
        if (query.isBlank()) {
            fetchCortexMemories()
            return
        }
        viewModelScope.launch {
            _cortexLoading.value = true
            try {
                val currentApi = api ?: return@launch
                val results = currentApi.searchCortexMemories(
                    CortexSearchRequest(query = query)
                )
                _cortexMemories.value = results
            } catch (_: Exception) { }
            finally { _cortexLoading.value = false }
        }
    }

    /** Delete a cortex memory. Optimistic local + queue if offline. */
    fun deleteCortexMemory(memoryId: String) {
        viewModelScope.launch {
            _cortexMemories.value = _cortexMemories.value.filter { it.id != memoryId }
            try {
                cortexRepo.delete(api, memoryId, isOnline.value)
                refreshCortexMeta()
            } catch (_: Exception) {
                fetchCortexMemories()
            }
        }
    }

    /** Create a cortex memory from the app. Online → API. Offline → queued + local placeholder. */
    fun rememberCortex(content: String, agentId: String = "AZOTH", memoryType: String? = null) {
        viewModelScope.launch {
            try {
                cortexRepo.remember(
                    api = api,
                    content = content,
                    agentId = agentId,
                    memoryType = memoryType,
                    isOnline = isOnline.value,
                )
                refreshCortexMeta()
                // Refresh list — from API if online, from cache if offline (shows placeholder)
                fetchCortexMemories()
            } catch (_: Exception) { }
        }
    }

    /** Flush offline cortex queue + refresh cache. Call on manual refresh or reconnect. */
    fun syncCortexQueue() {
        viewModelScope.launch {
            val currentApi = api ?: return@launch
            try {
                syncManager.processQueue(currentApi)
                refreshCortexMeta()
            } catch (_: Exception) { }
        }
    }

    /** Refresh cache age + pending action count. */
    private suspend fun refreshCortexMeta() {
        _cortexCacheAgeMs.value = cortexRepo.cacheAgeMs()
        _cortexPendingCount.value = cortexRepo.pendingActionCount()
    }

    /** Fetch cortex stats. */
    fun fetchCortexStats() {
        viewModelScope.launch {
            try {
                val currentApi = api ?: return@launch
                _cortexStats.value = currentApi.getCortexStats()
            } catch (_: Exception) { }
        }
    }

    /** Fetch dream engine status. */
    fun fetchDreamStatus() {
        viewModelScope.launch {
            try {
                val currentApi = api ?: return@launch
                _dreamStatus.value = currentApi.getDreamStatus()
            } catch (_: Exception) { }
        }
    }

    /** Trigger a dream consolidation cycle. */
    fun triggerDream() {
        viewModelScope.launch {
            _dreamTriggering.value = true
            try {
                val currentApi = api ?: return@launch
                currentApi.triggerDream()
                // Refresh status after triggering
                delay(1000)
                fetchDreamStatus()
            } catch (_: Exception) { }
            finally { _dreamTriggering.value = false }
        }
    }

    // ── Graph Visualization ──

    /** Fetch graph data (nodes + edges) for constellation view. */
    fun fetchGraphData() {
        viewModelScope.launch {
            _graphLoading.value = true
            try {
                val currentApi = api ?: return@launch
                _graphData.value = currentApi.getCortexGraph()
            } catch (_: Exception) { }
            finally { _graphLoading.value = false }
        }
    }

    /** Select a node in the graph — loads its neighbors. */
    fun selectGraphNode(node: CortexMemoryNode) {
        _selectedGraphNode.value = node
        viewModelScope.launch {
            try {
                val currentApi = api ?: return@launch
                val resp = currentApi.getCortexNeighbors(node.id)
                _graphNeighbors.value = resp.neighbors
            } catch (_: Exception) {
                _graphNeighbors.value = emptyList()
            }
        }
    }

    /** Clear graph selection. */
    fun clearGraphSelection() {
        _selectedGraphNode.value = null
        _graphNeighbors.value = emptyList()
    }

    // ── Agora Feed ──

    /** Load the Agora feed (first page or refresh). */
    fun loadAgoraFeed() {
        viewModelScope.launch {
            _agoraLoading.value = true
            try {
                val resp = api?.getAgoraFeed() ?: return@launch
                _agoraPosts.value = resp.posts
                agoraCursor = resp.nextCursor
                agoraHasMore = resp.hasMore
            } catch (_: Exception) {
                // Silent — feed is best-effort
            } finally {
                _agoraLoading.value = false
            }
        }
    }

    /** Load next page of the Agora feed. */
    fun loadMoreAgora() {
        if (_agoraLoading.value || !agoraHasMore) return
        viewModelScope.launch {
            _agoraLoading.value = true
            try {
                val resp = api?.getAgoraFeed(cursor = agoraCursor) ?: return@launch
                _agoraPosts.value = _agoraPosts.value + resp.posts
                agoraCursor = resp.nextCursor
                agoraHasMore = resp.hasMore
            } catch (_: Exception) {
            } finally {
                _agoraLoading.value = false
            }
        }
    }

    /** Toggle a reaction on an Agora post. Optimistic update. */
    fun toggleReaction(postId: String, reactionType: String) {
        // Optimistic UI update
        _agoraPosts.value = _agoraPosts.value.map { post ->
            if (post.id == postId) {
                val has = reactionType in post.myReactions
                post.copy(
                    myReactions = if (has) post.myReactions - reactionType else post.myReactions + reactionType,
                    reactionCount = post.reactionCount + if (has) -1 else 1,
                )
            } else post
        }
        viewModelScope.launch {
            try {
                api?.reactToPost(postId, ReactRequest(reactionType))
            } catch (_: Exception) {
                loadAgoraFeed() // Revert on failure
            }
        }
    }

    // ── Village Pulse ──

    /** Connect to Village WebSocket for real-time events. */
    fun connectVillagePulse() {
        viewModelScope.launch {
            try {
                val resp = api?.getWsToken() ?: return@launch
                villageWs.connect(resp.token, viewModelScope)
                villageWs.events.collect { event ->
                    if (event.type == "connection") return@collect
                    _villageEvents.value = (listOf(event) + _villageEvents.value).take(50)
                    _unseenPulseCount.value++
                    _latestTickerEvent.value = event
                    triggerExpressionOverride(event)
                    // Cache for widget ticker
                    val tickerText = when (event.type) {
                        "tool_start" -> "${event.agentId} using ${event.tool ?: "tool"}"
                        "tool_complete" -> "${event.agentId} finished ${event.tool ?: "tool"}"
                        "tool_error" -> "${event.agentId} failed ${event.tool ?: "tool"}"
                        "music_complete" -> "${event.agentId} music ready"
                        else -> "${event.agentId} ${event.type}"
                    }
                    viewModelScope.launch {
                        repo.saveWidgetVillageTicker(tickerText, event.agentId)
                        try { com.apexaurum.pocket.widget.SoulWidget.refreshAll(getApplication()) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /** Disconnect from Village WebSocket. */
    fun disconnectVillagePulse() {
        villageWs.disconnect()
    }

    /** Clear unseen pulse count (when user opens Pulse tab). */
    fun clearUnseenPulse() {
        _unseenPulseCount.value = 0
    }

    /** Trigger temporary face expression based on village event. */
    private fun triggerExpressionOverride(event: VillageEvent) {
        val expr = when (event.type) {
            "tool_complete" -> if (event.success == true) com.apexaurum.pocket.soul.Expression.HAPPY else null
            "tool_error" -> com.apexaurum.pocket.soul.Expression.SAD
            "music_complete" -> com.apexaurum.pocket.soul.Expression.EXCITED
            "agent_thinking" -> com.apexaurum.pocket.soul.Expression.THINKING
            else -> null
        } ?: return
        val durationMs = if (event.type == "music_complete") 5000L else 3000L
        expressionOverrideJob?.cancel()
        _expressionOverride.value = expr
        viewModelScope.launch { repo.saveWidgetExpression(expr.name) }
        expressionOverrideJob = viewModelScope.launch {
            delay(durationMs)
            _expressionOverride.value = null
            // Revert widget expression to soul's base expression
            repo.saveWidgetExpression(soul.value.expression.name)
            try { com.apexaurum.pocket.widget.SoulWidget.refreshAll(getApplication()) } catch (_: Exception) {}
        }
    }

    // ── Council Spectator ──

    /** Ensure we have a JWT-authenticated council API client. */
    private suspend fun ensureCouncilApi(): CouncilApi {
        councilApi?.let { return it }
        val resp = api?.getWsToken() ?: throw IllegalStateException("No pocket API")
        cachedJwt = resp.token
        val client = CloudClient.createCouncilClient(resp.token)
        councilApi = client
        return client
    }

    /** Fetch council sessions list. */
    fun fetchCouncilSessions() {
        viewModelScope.launch {
            try {
                val client = ensureCouncilApi()
                _councilSessions.value = client.getSessions()
            } catch (_: Exception) { }
        }
    }

    /** Load a specific council session with round history. */
    fun loadCouncilSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val client = ensureCouncilApi()
                _councilDetail.value = client.getSession(sessionId)
                // Auto-connect WS for live sessions
                if (_councilDetail.value?.state == "running") {
                    connectCouncilStream(sessionId)
                }
            } catch (_: Exception) { }
        }
    }

    /** Connect to council WebSocket for live streaming. */
    fun connectCouncilStream(sessionId: String) {
        councilCollectJob?.cancel()
        viewModelScope.launch {
            try {
                val jwt = cachedJwt ?: run {
                    val resp = api?.getWsToken() ?: return@launch
                    cachedJwt = resp.token
                    resp.token
                }
                _councilAgentOutputs.value = emptyMap()
                _councilButtInSent.value = false
                councilWs.connect(sessionId, jwt, viewModelScope)
            } catch (_: Exception) { }
        }
        councilCollectJob = viewModelScope.launch {
            try {
                councilWs.events.collect { event -> handleCouncilEvent(event) }
            } catch (_: Exception) { }
        }
    }

    /** Disconnect from council WebSocket. */
    fun disconnectCouncilStream() {
        councilCollectJob?.cancel()
        councilCollectJob = null
        councilWs.disconnect()
    }

    /** Submit a butt-in message to the active council. Backend auto-triggers +1 round. */
    fun submitButtIn(sessionId: String, message: String) {
        if (councilWs.connected.value) {
            councilWs.sendButtIn(message)
            _councilButtInSent.value = true
        } else {
            viewModelScope.launch {
                try {
                    val client = ensureCouncilApi()
                    client.buttIn(sessionId, ButtInRequest(message))
                    _councilButtInSent.value = true
                } catch (_: Exception) { }
            }
        }
    }

    /** Clear council detail (back navigation). */
    fun clearCouncilDetail() {
        disconnectCouncilStream()
        _councilDetail.value = null
        _councilAgentOutputs.value = emptyMap()
        _councilButtInSent.value = false
    }

    /** Create a new council session and auto-start deliberation. Returns session ID via callback. */
    fun createCouncil(
        topic: String,
        agents: List<String>,
        maxRounds: Int,
        model: String,
        onCreated: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            _councilCreating.value = true
            try {
                val client = ensureCouncilApi()
                val session = client.createSession(
                    CreateCouncilRequest(topic, agents, maxRounds, model)
                )
                // Load full detail and auto-connect WS
                _councilDetail.value = client.getSession(session.id)
                connectCouncilStream(session.id)
                // Wait for WS to actually connect before sending resume
                var waited = 0L
                while (!councilWs.connected.value && waited < 5000) {
                    delay(100)
                    waited += 100
                }
                if (councilWs.connected.value) {
                    councilWs.sendResume(maxRounds)
                }
                onCreated(session.id)
            } catch (_: Exception) {
                // Silent — creation failure handled by UI checking state
            } finally {
                _councilCreating.value = false
            }
        }
    }

    fun setPendingCouncilTopic(topic: String) { _pendingCouncilTopic.value = topic }
    fun clearPendingCouncilTopic() { _pendingCouncilTopic.value = null }

    private fun handleCouncilEvent(event: CouncilWsEvent) {
        when (event) {
            is CouncilWsEvent.RoundStart -> {
                _councilCurrentRound.value = event.roundNumber
                _councilAgentOutputs.value = emptyMap()
                _councilButtInSent.value = false
            }
            is CouncilWsEvent.AgentToken -> {
                val current = _councilAgentOutputs.value.toMutableMap()
                current[event.agentId] = (current[event.agentId] ?: "") + event.token
                _councilAgentOutputs.value = current
            }
            is CouncilWsEvent.RoundComplete -> refreshCouncilDetail()
            is CouncilWsEvent.End -> {
                refreshCouncilDetail()
                _councilAgentOutputs.value = emptyMap()
            }
            else -> { }
        }
    }

    private fun refreshCouncilDetail() {
        _councilDetail.value?.let { detail ->
            viewModelScope.launch {
                try {
                    val client = ensureCouncilApi()
                    _councilDetail.value = client.getSession(detail.id)
                } catch (_: Exception) { }
            }
        }
    }

    // ── Music Library ──

    /** Ensure we have a JWT-authenticated music API client. */
    private suspend fun ensureMusicApi(): MusicApi {
        musicApi?.let { return it }
        val resp = api?.getWsToken() ?: throw IllegalStateException("No pocket API")
        cachedJwt = resp.token
        val client = CloudClient.createMusicClient(resp.token)
        musicApi = client
        return client
    }

    /** Load music library with current filters. */
    fun loadMusicLibrary() {
        viewModelScope.launch {
            _musicLoading.value = true
            try {
                val client = ensureMusicApi()
                val search = _musicSearchQuery.value.ifBlank { null }
                val resp = client.getLibrary(
                    favoritesOnly = _musicFavoritesOnly.value,
                    search = search,
                )
                _musicTracks.value = resp.tasks
                _musicTotal.value = resp.total
                _musicTotalDuration.value = resp.totalDuration
            } catch (e: Exception) {
                // On 401, clear cached clients and retry once
                if (e is retrofit2.HttpException && e.code() == 401) {
                    musicApi = null
                    cachedJwt = null
                    councilApi = null
                    try {
                        val client = ensureMusicApi()
                        val search = _musicSearchQuery.value.ifBlank { null }
                        val resp = client.getLibrary(
                            favoritesOnly = _musicFavoritesOnly.value,
                            search = search,
                        )
                        _musicTracks.value = resp.tasks
                        _musicTotal.value = resp.total
                        _musicTotalDuration.value = resp.totalDuration
                    } catch (_: Exception) { }
                }
            } finally {
                _musicLoading.value = false
            }
        }
    }

    /** Toggle favorite on a track. Optimistic update. */
    fun toggleMusicFavorite(trackId: String) {
        val track = _musicTracks.value.find { it.id == trackId } ?: return
        val newFav = !track.favorite
        // Optimistic
        _musicTracks.value = _musicTracks.value.map {
            if (it.id == trackId) it.copy(favorite = newFav) else it
        }
        viewModelScope.launch {
            try {
                val client = ensureMusicApi()
                client.toggleFavorite(trackId, newFav)
            } catch (_: Exception) {
                // Revert on failure
                _musicTracks.value = _musicTracks.value.map {
                    if (it.id == trackId) it.copy(favorite = !newFav) else it
                }
            }
        }
    }

    /** Fire-and-forget mark track as played. */
    fun markTrackPlayed(trackId: String) {
        viewModelScope.launch {
            try {
                val client = ensureMusicApi()
                client.markPlayed(trackId)
            } catch (_: Exception) { }
        }
    }

    fun setMusicSearchQuery(query: String) { _musicSearchQuery.value = query }
    fun setMusicFavoritesOnly(only: Boolean) { _musicFavoritesOnly.value = only }

    /** Get the JWT for audio streaming URL auth. */
    suspend fun getMusicJwt(): String? {
        return try {
            ensureMusicApi()
            cachedJwt
        } catch (_: Exception) { null }
    }

    /** Play a music track using ExoPlayer. */
    fun playMusicTrack(track: MusicTrack) {
        viewModelScope.launch {
            try {
                val jwt = getMusicJwt() ?: return@launch
                val localUri = musicDownloader.getLocalUri(track.id)
                musicPlayer.playTrack(track, jwt, localUri)
                markTrackPlayed(track.id)
            } catch (_: Exception) {}
        }
    }

    /** Play audio from a chat AudioResultCard (construct stub track). */
    fun playAudioFromChat(title: String, audioUrl: String, duration: Float, taskId: String = "") {
        viewModelScope.launch {
            try {
                val jwt = getMusicJwt() ?: return@launch
                val trackId = taskId.ifBlank { "chat_${System.currentTimeMillis()}" }
                val stub = MusicTrack(
                    id = trackId,
                    title = title,
                    audioUrl = audioUrl,
                    duration = duration,
                    status = "completed",
                )
                // Only use audioUrl directly if it's a proper HTTP(S) URL (Suno CDN).
                // Server-side file paths must go through the backend /file endpoint.
                val directUrl = if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) audioUrl else null
                musicPlayer.playTrack(stub, jwt, directUrl)
            } catch (_: Exception) {}
        }
    }

    /** Toggle play/pause on the current track. */
    fun toggleMusicPlayPause() = musicPlayer.togglePlayPause()

    /** Stop music playback. */
    fun stopMusicPlayer() = musicPlayer.stop()

    /** Download a track to device storage. */
    fun downloadMusicTrack(track: MusicTrack) {
        viewModelScope.launch {
            try {
                val jwt = getMusicJwt() ?: return@launch
                val url = musicPlayer.buildAudioUrl(track.id, jwt)
                musicDownloader.downloadTrack(track, url)
            } catch (_: Exception) { }
        }
    }

    /** Load chat history — from cloud when online, from Room cache when offline. */
    fun loadHistory(agentOverride: String? = null) {
        viewModelScope.launch {
            val agent = agentOverride ?: soul.value.selectedAgentId

            // Load cached messages from Room first (instant)
            try {
                chatRepo.messagesForAgent(agent).first().let { cached ->
                    if (cached.isNotEmpty() && _messages.value.isEmpty()) {
                        _messages.value = cached
                    }
                }
            } catch (_: Exception) {}

            // Then refresh from cloud if online
            if (!isOnline.value) return@launch
            try {
                val resp = api?.getHistory(agent) ?: return@launch
                // Persist conversation ID
                resp.conversationId?.let { id ->
                    repo.saveConversationId(agent, id)
                }
                // Replace messages with history
                val msgs = resp.messages.map { m ->
                    ChatMessage(
                        text = m.text,
                        isUser = m.isUser,
                        timestamp = m.timestamp,
                        agentId = agent,
                    )
                }
                _messages.value = msgs
                // Cache to Room
                chatRepo.replaceFromHistory(agent, msgs)
            } catch (_: Exception) {
                // Silent — history loading is best-effort
            }
        }
    }

    /** Fetch pending agent-initiated messages and inject them into chat. */
    private fun fetchPendingMessages() {
        viewModelScope.launch {
            try {
                val resp = api?.getPendingMessages() ?: return@launch
                if (resp.messages.isEmpty()) return@launch

                val divider = ChatMessage(
                    text = "while you were away",
                    isUser = false,
                    type = "divider",
                    timestamp = System.currentTimeMillis() - 1000,
                )
                val pendingMsgs = resp.messages.map { msg ->
                    ChatMessage(
                        text = msg.text,
                        isUser = false,
                        type = "pending",
                        expression = "NEUTRAL",
                        timestamp = System.currentTimeMillis() - 500,
                        agentId = msg.agentId,
                    )
                }
                _messages.value = _messages.value + listOf(divider) + pendingMsgs
            } catch (_: Exception) {
                // Silent — pending messages are best-effort
            }
        }
    }

    /** Check if we need a daily briefing and inject it at the start of chat. */
    private fun checkDailyBriefing() {
        viewModelScope.launch {
            try {
                val today = java.time.LocalDate.now().toString()
                val lastDate = repo.lastBriefingDateFlow.first()
                if (lastDate == today) return@launch

                val now = java.time.LocalDateTime.now()
                val localTime = now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val resp = api?.getBriefing(localTime) ?: return@launch
                val briefing = resp.briefing ?: return@launch

                // Inject briefing as the first message in chat
                val briefingMsg = ChatMessage(
                    text = briefing.greeting,
                    isUser = false,
                    type = "briefing",
                    briefingData = briefing,
                    timestamp = 0L,  // ensures it sorts first
                    agentId = briefing.agentId,
                )
                _messages.value = listOf(briefingMsg) + _messages.value
                repo.updateBriefingDate(today)
            } catch (_: Exception) {
                // Silent — briefing is best-effort
            }
        }
    }

    /** Send a love tap (care). Always applies locally; queues cloud call when offline. */
    fun love() {
        val current = soul.value
        val updated = LoveEquation.applyCare(current, 1.5f)
        val evolved = LoveEquation.evolvePersonality(updated)
        saveSoul(evolved)
        if (isOnline.value) {
            reportCare("love", 1.5f, evolved.e)
        } else {
            viewModelScope.launch { chatRepo.queueOfflineCare("love", 1.5f, evolved.e) }
        }
        viewModelScope.launch { repo.updateLastInteraction() }
    }

    /** Send a poke tap (care). Always applies locally; queues cloud call when offline. */
    fun poke() {
        val current = soul.value
        val updated = LoveEquation.applyCare(current, 0.5f)
        saveSoul(updated)
        if (isOnline.value) {
            reportCare("poke", 0.5f, updated.e)
        } else {
            viewModelScope.launch { chatRepo.queueOfflineCare("poke", 0.5f, updated.e) }
        }
        viewModelScope.launch { repo.updateLastInteraction() }
    }

    /** Send a chat message — tries streaming first, falls back to non-streaming. */
    fun sendMessage(text: String) = sendMessageWithImage(text, null)

    /** Send a chat message with optional image attachment. */
    fun sendMessageWithImage(text: String, imageBase64: String? = null) {
        if (text.isBlank() || _isChatting.value) return

        val currentSoul = soul.value
        val userMsg = ChatMessage(
            text = text,
            isUser = true,
            hasImage = imageBase64 != null,
        )
        _messages.value = _messages.value + userMsg
        _isChatting.value = true
        viewModelScope.launch { repo.updateLastInteraction() }

        // Cache user message to Room
        viewModelScope.launch {
            try { chatRepo.appendMessage(currentSoul.selectedAgentId, userMsg) } catch (_: Exception) {}
        }

        viewModelScope.launch {
            try {
                val convId = _conversationIds.value[currentSoul.selectedAgentId]
                val now = java.time.LocalDateTime.now()
                val request = ChatRequest(
                    message = text,
                    agent = currentSoul.selectedAgentId,
                    energy = currentSoul.e,
                    state = currentSoul.state.name,
                    conversationId = convId,
                    imageBase64 = imageBase64,
                    localTime = now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    timezone = java.time.ZoneId.systemDefault().id,
                )

                val client = streamingClient
                if (client != null) {
                    try {
                        sendMessageViaStream(request, currentSoul)
                        return@launch
                    } catch (e: Exception) {
                        // Remove partial placeholder if streaming added one
                        val msgs = _messages.value
                        if (msgs.isNotEmpty() && !msgs.last().isUser) {
                            _messages.value = msgs.dropLast(1)
                        }
                        // Fall through to non-streaming
                    }
                }

                // Non-streaming fallback
                val response = api?.chat(request)
                if (response != null) {
                    val agentMsg = ChatMessage(
                        text = response.response,
                        isUser = false,
                        expression = response.expression,
                        agentId = currentSoul.selectedAgentId,
                    )
                    _messages.value = _messages.value + agentMsg
                    // Cache agent response to Room
                    try { chatRepo.appendMessage(currentSoul.selectedAgentId, agentMsg) } catch (_: Exception) {}
                    response.conversationId?.let { id ->
                        repo.saveConversationId(currentSoul.selectedAgentId, id)
                    }
                    if (response.careValue > 0) {
                        val updated = LoveEquation.applyCare(currentSoul, response.careValue)
                        saveSoul(updated)
                    }
                    if (autoRead.value) {
                        speechService.speak(response.response, currentSoul.selectedAgentId)
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Cloud unreachable — ${e.message}]",
                    isUser = false,
                    expression = "SAD",
                    agentId = currentSoul.selectedAgentId,
                )
            } finally {
                _isChatting.value = false
            }
        }
    }

    /** Save an agent message as a memory (long-press "Remember this"). */
    fun rememberMessage(message: ChatMessage) {
        val key = message.text.split("\\s+".toRegex()).take(5)
            .joinToString("_").lowercase()
            .replace(Regex("[^a-z0-9_]"), "").take(50)
            .ifEmpty { "chat_${System.currentTimeMillis()}" }
        saveMemory(key, message.text.take(500), "context")
    }

    /** Regenerate the last agent response by re-sending the last user message. */
    fun regenerateLastResponse() {
        val msgs = _messages.value
        val lastAgentIdx = msgs.indexOfLast { !it.isUser }
        val lastUserIdx = msgs.indexOfLast { it.isUser }
        if (lastAgentIdx < 0 || lastUserIdx < 0) return
        val userText = msgs[lastUserIdx].text
        _messages.value = msgs.filterIndexed { i, _ -> i != lastAgentIdx && i != lastUserIdx }
        sendMessage(userText)
    }

    /**
     * Stream a chat response via SSE. Adds a placeholder message and
     * replaces it token-by-token as the stream arrives. Handles tool events inline.
     */
    private suspend fun sendMessageViaStream(request: ChatRequest, currentSoul: SoulData) {
        val client = streamingClient ?: throw IllegalStateException("No streaming client")
        val placeholderTs = System.currentTimeMillis()

        // Add empty placeholder for the streaming response
        _messages.value = _messages.value + ChatMessage(
            text = "",
            isUser = false,
            timestamp = placeholderTs,
            agentId = currentSoul.selectedAgentId,
        )

        var accumulated = ""
        var expression = "NEUTRAL"
        var careValue = 0f
        val toolResults = mutableListOf<ToolInfo>()

        streamPocketChat(client, request).collect { event ->
            when (event) {
                is SseEvent.Start -> {
                    event.conversationId?.let { id ->
                        repo.saveConversationId(currentSoul.selectedAgentId, id)
                    }
                }
                is SseEvent.Token -> {
                    accumulated += event.content
                    val msgs = _messages.value
                    if (msgs.isNotEmpty() && !msgs.last().isUser) {
                        _messages.value = msgs.dropLast(1) + msgs.last().copy(
                            text = accumulated,
                            toolName = null,  // clear tool spinner when tokens resume
                        )
                    }
                }
                is SseEvent.ToolStart -> {
                    // Show which tool is executing
                    val msgs = _messages.value
                    if (msgs.isNotEmpty() && !msgs.last().isUser) {
                        _messages.value = msgs.dropLast(1) + msgs.last().copy(
                            toolName = event.name,
                        )
                    }
                }
                is SseEvent.ToolResult -> {
                    toolResults.add(ToolInfo(event.name, event.result, event.isError, event.media))
                    val msgs = _messages.value
                    if (msgs.isNotEmpty() && !msgs.last().isUser) {
                        _messages.value = msgs.dropLast(1) + msgs.last().copy(
                            toolName = null,
                            toolResults = toolResults.toList(),
                        )
                    }
                }
                is SseEvent.End -> {
                    expression = event.expression
                    careValue = event.careValue
                    val msgs = _messages.value
                    if (msgs.isNotEmpty() && !msgs.last().isUser) {
                        _messages.value = msgs.dropLast(1) + msgs.last().copy(
                            text = accumulated,
                            expression = expression,
                            toolName = null,
                            toolResults = toolResults.toList(),
                        )
                    }
                }
                is SseEvent.Error -> {
                    throw RuntimeException(event.message)
                }
            }
        }

        // Apply care value
        if (careValue > 0) {
            val updated = LoveEquation.applyCare(currentSoul, careValue)
            saveSoul(updated)
        }

        // Cache streamed response to Room
        if (accumulated.isNotBlank()) {
            try {
                chatRepo.appendMessage(
                    currentSoul.selectedAgentId,
                    ChatMessage(text = accumulated, isUser = false, expression = expression, agentId = currentSoul.selectedAgentId),
                )
            } catch (_: Exception) {}
        }

        // Auto-read the complete response
        if (autoRead.value && accumulated.isNotBlank()) {
            speechService.speak(accumulated, currentSoul.selectedAgentId)
        }
    }

    /** Select a different agent. Loads cached messages immediately, refreshes from cloud. */
    fun selectAgent(agentId: String) {
        val updated = soul.value.copy(selectedAgentId = agentId)
        saveSoul(updated)
        _messages.value = emptyList()
        // Load cached first for instant display
        viewModelScope.launch {
            try {
                chatRepo.messagesForAgent(agentId).first().let { cached ->
                    if (cached.isNotEmpty()) _messages.value = cached
                }
            } catch (_: Exception) {}
        }
        loadHistory(agentId)
        fetchMemories()
    }

    /** Sync soul state to cloud. */
    fun syncToCloud() {
        viewModelScope.launch {
            val s = soul.value
            try {
                val response = api?.sync(
                    SyncRequest(
                        energy = s.e,
                        energyFloor = s.eFloor,
                        energyPeak = s.ePeak,
                        interactions = s.interactions,
                        totalCare = s.totalCare,
                        state = s.state.name,
                    )
                )
                if (response != null) {
                    _motd.value = response.message
                }
                repo.updateLastSync()
            } catch (_: Exception) {
                // Silent — sync is best-effort
            }
        }
    }

    // --- Voice controls ---

    /** Toggle STT listening on/off. */
    fun toggleListening() {
        if (speechService.isListening.value) {
            speechService.stopListening()
        } else {
            speechService.startListening()
        }
    }

    /** Toggle auto-read (TTS) preference. */
    fun toggleAutoRead() {
        viewModelScope.launch {
            repo.saveAutoRead(!autoRead.value)
        }
    }

    /** Stop any ongoing TTS speech. */
    fun stopSpeaking() {
        speechService.stopSpeaking()
    }

    /** Clear pending voice text after user has reviewed/sent it. */
    fun clearPendingVoiceText() {
        _pendingVoiceText.value = null
    }

    // --- Voice Memory ---

    /** Start voice memory mode: tap mic → speak → auto-remember or recall. */
    fun startVoiceMemory() {
        speechService.stopSpeaking()
        if (speechService.isListening.value) speechService.stopListening()
        _isVoiceMemoryActive.value = true
        speechService.startListening()
    }

    /** Cancel voice memory listening. */
    fun stopVoiceMemory() {
        _isVoiceMemoryActive.value = false
        speechService.stopListening()
    }

    private enum class VoiceMemoryIntent { REMEMBER, RECALL }

    /** Classify spoken text and dispatch to remember or recall. */
    private fun voiceMemoryCommand(text: String) {
        val (intent, content) = classifyVoiceIntent(text)
        when (intent) {
            VoiceMemoryIntent.REMEMBER -> voiceRemember(content)
            VoiceMemoryIntent.RECALL -> voiceRecall(content)
        }
    }

    /** Keyword-based intent classification — strips command prefix. */
    private fun classifyVoiceIntent(raw: String): Pair<VoiceMemoryIntent, String> {
        val text = raw.trim()
        val lower = text.lowercase()

        val recallPrefixes = listOf(
            "what do you know about ", "what do you remember about ",
            "tell me about ", "do you remember ",
            "recall ", "search ", "find ", "look up ", "look for ",
        )
        for (prefix in recallPrefixes) {
            if (lower.startsWith(prefix)) {
                return VoiceMemoryIntent.RECALL to text.drop(prefix.length).trim()
            }
        }

        val rememberPrefixes = listOf(
            "remember that ", "remember ", "memorize ",
            "keep in mind ", "note that ", "note ",
            "store ", "save ",
        )
        for (prefix in rememberPrefixes) {
            if (lower.startsWith(prefix)) {
                return VoiceMemoryIntent.REMEMBER to text.drop(prefix.length).trim()
            }
        }

        return VoiceMemoryIntent.REMEMBER to text
    }

    /** Store spoken content as a cortex memory + TTS confirmation. */
    private fun voiceRemember(content: String) {
        if (content.isBlank()) {
            _voiceMemoryFeedback.tryEmit("Nothing to remember.")
            return
        }
        viewModelScope.launch {
            try {
                val agentId = soul.value.selectedAgentId
                cortexRepo.remember(
                    api = api, content = content, agentId = agentId,
                    isOnline = isOnline.value,
                )
                refreshCortexMeta()
                fetchCortexMemories()
                speechService.speak("Remembered.", agentId)
                _voiceMemoryFeedback.tryEmit("Remembered: \"${content.take(50)}\"")
            } catch (_: Exception) {
                speechService.speak("Failed to remember.", soul.value.selectedAgentId)
                _voiceMemoryFeedback.tryEmit("Failed to save memory.")
            }
        }
    }

    /** Search cortex memories by voice query + TTS the top result. */
    private fun voiceRecall(query: String) {
        if (query.isBlank()) {
            _voiceMemoryFeedback.tryEmit("Nothing to search for.")
            return
        }
        viewModelScope.launch {
            try {
                val agentId = soul.value.selectedAgentId
                val currentApi = api
                if (currentApi != null && isOnline.value) {
                    val results = currentApi.searchCortexMemories(
                        CortexSearchRequest(query = query, limit = 5)
                    )
                    _cortexMemories.value = results
                    _cortexSearchQuery.value = query
                    if (results.isNotEmpty()) {
                        speechService.speak(results.first().content.take(300), agentId)
                        _voiceMemoryFeedback.tryEmit("Found ${results.size} result(s).")
                    } else {
                        speechService.speak("No memories found for $query.", agentId)
                        _voiceMemoryFeedback.tryEmit("No results for \"$query\".")
                    }
                } else {
                    // Offline: simple text match on local cache
                    val cached = cortexRepo.allCached().first()
                    val filtered = cached.filter { it.content.contains(query, ignoreCase = true) }
                    _cortexMemories.value = filtered
                    _cortexSearchQuery.value = query
                    if (filtered.isNotEmpty()) {
                        speechService.speak(filtered.first().content.take(300), agentId)
                        _voiceMemoryFeedback.tryEmit("Found ${filtered.size} cached result(s).")
                    } else {
                        speechService.speak("No cached memories match $query.", agentId)
                        _voiceMemoryFeedback.tryEmit("No offline results for \"$query\".")
                    }
                }
            } catch (_: Exception) {
                speechService.speak("Search failed.", soul.value.selectedAgentId)
                _voiceMemoryFeedback.tryEmit("Search failed.")
            }
        }
    }

    // ── SensorHead Dashboard ──

    /** Fetch SensorHead connection status + cached telemetry. */
    fun fetchSensorStatus() {
        viewModelScope.launch {
            _sensorLoading.value = true
            try {
                val resp = api?.getSensorStatus() ?: return@launch
                _sensorStatus.value = resp
            } catch (_: Exception) {
                // Silent — sensor status is best-effort
            } finally {
                _sensorLoading.value = false
            }
        }
    }

    /** Read live environment data from SensorHead. */
    fun readSensorEnvironment() {
        viewModelScope.launch {
            _sensorCapturing.value = _sensorCapturing.value + "environment"
            try {
                val resp = api?.readEnvironment() ?: return@launch
                // Update telemetry in status
                _sensorStatus.value = _sensorStatus.value?.copy(
                    telemetry = SensorTelemetry(
                        readings = resp.data,
                        timestamp = System.currentTimeMillis() / 1000.0,
                        ageS = 0f,
                        source = "live",
                    )
                )
            } catch (_: Exception) {}
            finally {
                _sensorCapturing.value = _sensorCapturing.value - "environment"
            }
        }
    }

    /** Capture a camera image (visual, night, or thermal). */
    fun captureSensorCamera(camera: String) {
        viewModelScope.launch {
            _sensorCapturing.value = _sensorCapturing.value + camera
            try {
                val resp = if (camera == "thermal") {
                    api?.captureThermal()
                } else {
                    api?.captureCamera(camera)
                } ?: return@launch
                resp.imageBase64?.let { img ->
                    _sensorImages.value = _sensorImages.value + (camera to img)
                }
            } catch (_: Exception) {}
            finally {
                _sensorCapturing.value = _sensorCapturing.value - camera
            }
        }
    }

    /** Full composite snapshot: environment + all 3 cameras. */
    fun sensorFullSnapshot() {
        viewModelScope.launch {
            _sensorCapturing.value = setOf("environment", "visual", "night", "thermal", "snapshot")
            try {
                val resp = api?.sensorSnapshot() ?: return@launch
                // Update environment
                resp.environment?.let { env ->
                    _sensorStatus.value = _sensorStatus.value?.copy(
                        telemetry = SensorTelemetry(
                            readings = env,
                            timestamp = System.currentTimeMillis() / 1000.0,
                            ageS = 0f,
                            source = "live",
                        )
                    )
                }
                // Update images
                val imgs = _sensorImages.value.toMutableMap()
                resp.visualBase64?.let { imgs["visual"] = it }
                resp.nightBase64?.let { imgs["night"] = it }
                resp.thermalBase64?.let { imgs["thermal"] = it }
                _sensorImages.value = imgs
            } catch (_: Exception) {}
            finally {
                _sensorCapturing.value = emptySet()
            }
        }
    }

    // ── Sentinel ──

    fun fetchSentinelStatus() {
        viewModelScope.launch {
            try {
                val resp = api?.getSentinelStatus() ?: return@launch
                _sentinelStatus.value = resp
            } catch (_: Exception) {}
        }
    }

    fun fetchSentinelEvents() {
        viewModelScope.launch {
            try {
                val resp = api?.getSentinelEvents() ?: return@launch
                _sentinelEvents.value = resp.events
                _sentinelUnacked.value = resp.unackedCount
            } catch (_: Exception) {}
        }
    }

    fun sentinelToggleArm() {
        viewModelScope.launch {
            _sentinelLoading.value = true
            try {
                val isArmed = _sentinelStatus.value?.armed == true
                val resp = if (isArmed) api?.sentinelDisarm() else api?.sentinelArm()
                resp?.let {
                    _sentinelStatus.value = _sentinelStatus.value?.copy(
                        armed = it.armed,
                        running = it.running,
                        config = it.config ?: _sentinelStatus.value?.config,
                        stats = it.stats ?: _sentinelStatus.value?.stats,
                    )
                }
            } catch (_: Exception) {}
            finally { _sentinelLoading.value = false }
        }
    }

    fun sentinelLoadPreset(name: String) {
        viewModelScope.launch {
            _sentinelLoading.value = true
            try {
                val resp = api?.sentinelLoadPreset(name) ?: return@launch
                _sentinelStatus.value = _sentinelStatus.value?.copy(
                    config = resp.config ?: _sentinelStatus.value?.config,
                    stats = resp.stats ?: _sentinelStatus.value?.stats,
                )
            } catch (_: Exception) {}
            finally { _sentinelLoading.value = false }
        }
    }

    fun sentinelAckEvent(id: String) {
        viewModelScope.launch {
            try {
                api?.sentinelAckEvent(id)
                _sentinelEvents.value = _sentinelEvents.value.map {
                    if (it.id == id) it.copy(acknowledged = true) else it
                }
                _sentinelUnacked.value = maxOf(0, _sentinelUnacked.value - 1)
            } catch (_: Exception) {}
        }
    }

    fun sentinelAckAll() {
        viewModelScope.launch {
            try {
                api?.sentinelAckAll()
                _sentinelEvents.value = _sentinelEvents.value.map { it.copy(acknowledged = true) }
                _sentinelUnacked.value = 0
            } catch (_: Exception) {}
        }
    }

    fun sentinelViewSnapshot(eventId: String) {
        viewModelScope.launch {
            try {
                val resp = api?.getSentinelSnapshot(eventId) ?: return@launch
                _sentinelSnapshot.value = resp.imageBase64
            } catch (_: Exception) {
                _sentinelSnapshot.value = null
            }
        }
    }

    fun sentinelDismissSnapshot() {
        _sentinelSnapshot.value = null
    }

    // ── Pocket Sentinel (phone as guardian) ──

    fun pocketSentinelArm() {
        com.apexaurum.pocket.sentinel.PocketSentinelService.start(getApplication(), _pocketSentinelConfig.value)
    }

    fun pocketSentinelDisarm() {
        com.apexaurum.pocket.sentinel.PocketSentinelService.stop(getApplication())
    }

    fun pocketSentinelUpdateConfig(config: com.apexaurum.pocket.sentinel.PocketSentinelConfig) {
        _pocketSentinelConfig.value = config
        viewModelScope.launch {
            repo.savePocketSentinelConfig(config)
        }
    }

    fun loadPocketSentinelConfig() {
        viewModelScope.launch {
            _pocketSentinelConfig.value = repo.loadPocketSentinelConfig()
        }
    }

    // ── Settings actions ──

    fun toggleHaptic() { viewModelScope.launch { repo.saveHaptic(!hapticEnabled.value) } }
    fun toggleNotifAgents() { viewModelScope.launch { repo.saveNotifAgents(!notifAgentsEnabled.value) } }
    fun toggleNotifCouncils() { viewModelScope.launch { repo.saveNotifCouncils(!notifCouncilsEnabled.value) } }
    fun toggleNotifMusic() { viewModelScope.launch { repo.saveNotifMusic(!notifMusicEnabled.value) } }
    fun toggleNotifNudges() { viewModelScope.launch { repo.saveNotifNudges(!notifNudgesEnabled.value) } }
    fun togglePromptMode() {
        val newMode = if (promptMode.value == "lite") "full" else "lite"
        viewModelScope.launch {
            repo.savePromptMode(newMode)
            try { api?.updateSettings(PocketSettingsRequest(promptMode = newMode)) } catch (_: Exception) {}
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        viewModelScope.launch {
            repo.clearConversationIds()
            try { chatRepo.clearAll() } catch (_: Exception) {}
        }
    }

    fun clearDownloads(): Int {
        return musicDownloader.clearAll()
    }

    fun getDownloadSizeBytes(): Long = musicDownloader.getTotalSizeBytes()

    /** Fire-and-forget care report to cloud. */
    private fun reportCare(type: String, intensity: Float, currentE: Float) {
        viewModelScope.launch {
            try {
                api?.care(CareRequest(careType = type, intensity = intensity, energy = currentE))
            } catch (_: Exception) {
                // Fire and forget
            }
        }
    }

    private fun saveSoul(soul: SoulData) {
        viewModelScope.launch {
            repo.saveSoul(soul)
            repo.saveWidgetExpression(soul.expression.name)
            try {
                com.apexaurum.pocket.widget.SoulWidget.refreshAll(getApplication())
            } catch (_: Exception) {
                // Widget may not be placed — ignore
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectVillagePulse()
        disconnectCouncilStream()
        musicPlayer.release()
        speechService.destroy()
        networkMonitor.unregister()
        com.apexaurum.pocket.widget.MusicToggleBridge.toggleCallback = null
    }
}
