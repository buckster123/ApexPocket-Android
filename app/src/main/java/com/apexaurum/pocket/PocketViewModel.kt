package com.apexaurum.pocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.data.SoulRepository
import com.apexaurum.pocket.soul.LoveEquation
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.voice.SpeechService
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
)

/** Tool execution result for display in chat. */
@Serializable
data class ToolInfo(
    val name: String,
    val result: String,
    val isError: Boolean = false,
)

/** Connection state with the cloud. */
enum class CloudState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class PocketViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SoulRepository(application)

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

    val autoRead: StateFlow<Boolean> = repo.autoReadFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

        // Collect recognized text from STT → set as pending voice text
        viewModelScope.launch {
            speechService.recognizedText.collect { text ->
                _pendingVoiceText.value = text
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
        viewModelScope.launch {
            _cloudState.value = CloudState.CONNECTING
            try {
                val status = api?.getStatus()
                if (status != null) {
                    _motd.value = status.motd
                    _cloudState.value = CloudState.CONNECTED
                }
                // Fetch agents
                val agentsResp = api?.getAgents()
                if (agentsResp != null) {
                    _agents.value = agentsResp.agents
                }
                // Fetch memories
                fetchMemories()
                // Load chat history for current agent
                loadHistory()
                // Load Agora feed
                loadAgoraFeed()
            } catch (e: Exception) {
                _cloudState.value = CloudState.ERROR
            }
        }
    }

    /** Fetch memories from cloud (filtered by current agent). */
    fun fetchMemories() {
        viewModelScope.launch {
            _memoriesLoading.value = true
            try {
                val agent = soul.value.selectedAgentId
                val resp = api?.getAgentMemories(agent)
                if (resp != null) {
                    _memories.value = resp.memories
                }
            } catch (_: Exception) {
                // Silent — memories are best-effort
            } finally {
                _memoriesLoading.value = false
            }
        }
    }

    /** Save a memory to the cloud. */
    fun saveMemory(key: String, value: String, type: String = "fact") {
        viewModelScope.launch {
            try {
                val agent = soul.value.selectedAgentId
                api?.saveMemory(
                    SaveMemoryRequest(
                        agent = agent,
                        memoryType = type,
                        key = key,
                        value = value,
                    )
                )
                fetchMemories()
            } catch (_: Exception) {
                // Silent
            }
        }
    }

    /** Delete a memory from the cloud. */
    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            // Optimistic removal
            _memories.value = _memories.value.filter { it.id != memoryId }
            try {
                api?.deleteMemory(memoryId)
            } catch (_: Exception) {
                // Refresh to restore if failed
                fetchMemories()
            }
        }
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
        expressionOverrideJob = viewModelScope.launch {
            delay(durationMs)
            _expressionOverride.value = null
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

    /** Submit a butt-in message to the active council, then trigger +1 round. */
    fun submitButtIn(sessionId: String, message: String) {
        if (councilWs.connected.value) {
            councilWs.sendButtIn(message)
            councilWs.sendResume(1)
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

    /** Load chat history from cloud for the given (or current) agent. */
    fun loadHistory(agentOverride: String? = null) {
        viewModelScope.launch {
            try {
                val agent = agentOverride ?: soul.value.selectedAgentId
                val resp = api?.getHistory(agent) ?: return@launch
                // Persist conversation ID
                resp.conversationId?.let { id ->
                    repo.saveConversationId(agent, id)
                }
                // Replace messages with history
                _messages.value = resp.messages.map { m ->
                    ChatMessage(
                        text = m.text,
                        isUser = m.isUser,
                        timestamp = m.timestamp,
                    )
                }
            } catch (_: Exception) {
                // Silent — history loading is best-effort
            }
        }
    }

    /** Send a love tap (care). */
    fun love() {
        val current = soul.value
        val updated = LoveEquation.applyCare(current, 1.5f)
        val evolved = LoveEquation.evolvePersonality(updated)
        saveSoul(evolved)
        reportCare("love", 1.5f, evolved.e)
        viewModelScope.launch { repo.updateLastInteraction() }
    }

    /** Send a poke tap (care). */
    fun poke() {
        val current = soul.value
        val updated = LoveEquation.applyCare(current, 0.5f)
        saveSoul(updated)
        reportCare("poke", 0.5f, updated.e)
        viewModelScope.launch { repo.updateLastInteraction() }
    }

    /** Send a chat message — tries streaming first, falls back to non-streaming. */
    fun sendMessage(text: String) = sendMessageWithImage(text, null)

    /** Send a chat message with optional image attachment. */
    fun sendMessageWithImage(text: String, imageBase64: String? = null) {
        if (text.isBlank() || _isChatting.value) return

        val currentSoul = soul.value
        _messages.value = _messages.value + ChatMessage(
            text = text,
            isUser = true,
            hasImage = imageBase64 != null,
        )
        _isChatting.value = true
        viewModelScope.launch { repo.updateLastInteraction() }

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
                    _messages.value = _messages.value + ChatMessage(
                        text = response.response,
                        isUser = false,
                        expression = response.expression,
                    )
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
                    toolResults.add(ToolInfo(event.name, event.result, event.isError))
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

        // Auto-read the complete response
        if (autoRead.value && accumulated.isNotBlank()) {
            speechService.speak(accumulated, currentSoul.selectedAgentId)
        }
    }

    /** Select a different agent. */
    fun selectAgent(agentId: String) {
        val updated = soul.value.copy(selectedAgentId = agentId)
        saveSoul(updated)
        _messages.value = emptyList()
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
        speechService.destroy()
    }
}
