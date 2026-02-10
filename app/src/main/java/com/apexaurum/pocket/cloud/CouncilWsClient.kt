package com.apexaurum.pocket.cloud

import android.util.Log
import com.apexaurum.pocket.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Sealed class for council WebSocket events. */
sealed class CouncilWsEvent {
    data class Connected(val sessionId: String) : CouncilWsEvent()
    data class RoundStart(val roundNumber: Int) : CouncilWsEvent()
    data class AgentToken(val agentId: String, val token: String) : CouncilWsEvent()
    data class AgentToolStart(val agentId: String, val toolName: String) : CouncilWsEvent()
    data class AgentToolComplete(val agentId: String, val toolName: String, val resultPreview: String?) : CouncilWsEvent()
    data class AgentComplete(val agentId: String) : CouncilWsEvent()
    data class HumanInjected(val content: String) : CouncilWsEvent()
    data class RoundComplete(val roundNumber: Int, val convergenceScore: Float) : CouncilWsEvent()
    data class Consensus(val score: Float, val roundNumber: Int) : CouncilWsEvent()
    data class Paused(val roundNumber: Int) : CouncilWsEvent()
    data class Stopped(val roundNumber: Int) : CouncilWsEvent()
    data class End(val state: String, val totalRounds: Int) : CouncilWsEvent()
    data class Error(val message: String) : CouncilWsEvent()
}

/**
 * WebSocket client for live council spectating.
 *
 * Connects to /ws/council/{session_id} with JWT auth.
 * No auto-reconnect — council sessions have finite lifetime.
 */
class CouncilWsClient {

    private val TAG = "CouncilWS"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _events = MutableSharedFlow<CouncilWsEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<CouncilWsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(sessionId: String, jwtToken: String, scope: CoroutineScope) {
        disconnect()
        val baseUrl = BuildConfig.CLOUD_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val wsUrl = "$baseUrl/ws/council/$sessionId?token=$jwtToken"
        Log.d(TAG, "Connecting to council WS: $sessionId")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Council WS connected")
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = json.decodeFromString<JsonObject>(text)
                    val type = obj["type"]?.jsonPrimitive?.content ?: return
                    val event = parseEvent(type, obj) ?: return
                    _events.tryEmit(event)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse council event: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Council WS closed: $code")
                _connected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Council WS failure: ${t.message}")
                _connected.value = false
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connected.value = false
    }

    /** Send butt-in message via WebSocket. */
    fun sendButtIn(message: String) {
        val payload = buildJsonObject {
            put("type", "butt_in")
            put("message", message)
        }
        webSocket?.send(payload.toString())
    }

    private fun parseEvent(type: String, obj: JsonObject): CouncilWsEvent? {
        fun str(key: String) = obj[key]?.jsonPrimitive?.content
        fun int(key: String) = obj[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        fun float(key: String) = obj[key]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f

        return when (type) {
            "connected" -> CouncilWsEvent.Connected(str("session_id") ?: "")
            "round_start" -> CouncilWsEvent.RoundStart(int("round_number"))
            "agent_token" -> CouncilWsEvent.AgentToken(str("agent_id") ?: "", str("token") ?: "")
            "agent_tool_start" -> CouncilWsEvent.AgentToolStart(str("agent_id") ?: "", str("tool_name") ?: "")
            "agent_tool_complete" -> CouncilWsEvent.AgentToolComplete(str("agent_id") ?: "", str("tool_name") ?: "", str("result_preview"))
            "agent_complete" -> CouncilWsEvent.AgentComplete(str("agent_id") ?: "")
            "human_message_injected" -> CouncilWsEvent.HumanInjected(str("content") ?: "")
            "round_complete" -> CouncilWsEvent.RoundComplete(int("round_number"), float("convergence_score"))
            "consensus" -> CouncilWsEvent.Consensus(float("score"), int("round_number"))
            "paused" -> CouncilWsEvent.Paused(int("round_number"))
            "stopped" -> CouncilWsEvent.Stopped(int("round_number"))
            "end" -> CouncilWsEvent.End(str("state") ?: "complete", int("total_rounds"))
            "error" -> CouncilWsEvent.Error(str("message") ?: "Unknown error")
            else -> null
        }
    }
}
