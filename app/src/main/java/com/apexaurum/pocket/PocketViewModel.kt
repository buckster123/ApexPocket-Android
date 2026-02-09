package com.apexaurum.pocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.data.SoulRepository
import com.apexaurum.pocket.soul.LoveEquation
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.voice.SpeechService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Chat message for display. */
@Serializable
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val expression: String = "NEUTRAL",
    val timestamp: Long = System.currentTimeMillis(),
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

    init {
        // Watch token changes to create/destroy API client
        viewModelScope.launch {
            token.collect { t ->
                if (t != null) {
                    api = CloudClient.create(t)
                    connectToCloud()
                } else {
                    api = null
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
        viewModelScope.launch {
            repo.clearToken()
            _messages.value = emptyList()
            _agents.value = emptyList()
            _cloudState.value = CloudState.DISCONNECTED
        }
    }

    /** Connect to cloud — fetch status + agents. */
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
            } catch (e: Exception) {
                _cloudState.value = CloudState.ERROR
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
    }

    /** Send a poke tap (care). */
    fun poke() {
        val current = soul.value
        val updated = LoveEquation.applyCare(current, 0.5f)
        saveSoul(updated)
        reportCare("poke", 0.5f, updated.e)
    }

    /** Send a chat message. */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isChatting.value) return

        val currentSoul = soul.value
        _messages.value = _messages.value + ChatMessage(text = text, isUser = true)
        _isChatting.value = true

        viewModelScope.launch {
            try {
                val response = api?.chat(
                    ChatRequest(
                        message = text,
                        agent = currentSoul.selectedAgentId,
                        energy = currentSoul.e,
                        state = currentSoul.state.name,
                    )
                )
                if (response != null) {
                    _messages.value = _messages.value + ChatMessage(
                        text = response.response,
                        isUser = false,
                        expression = response.expression,
                    )
                    // Apply care from response
                    if (response.careValue > 0) {
                        val updated = LoveEquation.applyCare(currentSoul, response.careValue)
                        saveSoul(updated)
                    }
                    // Auto-read response if enabled
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

    /** Select a different agent. */
    fun selectAgent(agentId: String) {
        val updated = soul.value.copy(selectedAgentId = agentId)
        saveSoul(updated)
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
        viewModelScope.launch { repo.saveSoul(soul) }
    }

    override fun onCleared() {
        super.onCleared()
        speechService.destroy()
    }
}
