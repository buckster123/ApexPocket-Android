package com.apexaurum.pocket.cloud

import android.util.Log
import com.apexaurum.pocket.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for the Village real-time event feed.
 *
 * Connects to /ws/village with JWT auth (obtained via POST /pocket/ws-token).
 * Emits VillageEvent via SharedFlow. Auto-reconnects with exponential backoff.
 */
class VillageWsClient {

    private val TAG = "VillageWS"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _events = MutableSharedFlow<VillageEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VillageEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = 3000L
    private var shouldReconnect = true
    private var currentToken: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(jwtToken: String, externalScope: CoroutineScope) {
        disconnect()
        currentToken = jwtToken
        scope = externalScope
        shouldReconnect = true
        reconnectDelayMs = 3000L
        openWebSocket(jwtToken)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connected.value = false
        scope = null
        currentToken = null
    }

    private fun openWebSocket(token: String) {
        val baseUrl = BuildConfig.CLOUD_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val wsUrl = "$baseUrl/ws/village?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Village WS connected")
                _connected.value = true
                reconnectDelayMs = 3000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = json.decodeFromString<VillageEvent>(text)
                    _events.tryEmit(event)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse village event: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Village WS closed: $code")
                _connected.value = false
                if (code == 1008) {
                    Log.w(TAG, "Auth failure — not reconnecting")
                    shouldReconnect = false
                }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Village WS failure: ${t.message}")
                _connected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val token = currentToken ?: return
        val s = scope ?: return
        reconnectJob?.cancel()
        reconnectJob = s.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30_000L)
            openWebSocket(token)
        }
    }
}
