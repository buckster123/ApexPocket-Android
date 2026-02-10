package com.apexaurum.pocket.cloud

import com.apexaurum.pocket.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Server-sent events from the pocket streaming endpoint. */
sealed class SseEvent {
    data class Start(val conversationId: String?) : SseEvent()
    data class Token(val content: String) : SseEvent()
    data class ToolStart(val name: String) : SseEvent()
    data class ToolResult(val name: String, val result: String, val isError: Boolean) : SseEvent()
    data class End(
        val expression: String,
        val careValue: Float,
        val agent: String,
    ) : SseEvent()
    data class Error(val message: String) : SseEvent()
}

/**
 * Stream a pocket chat via SSE.
 *
 * Uses synchronous OkHttp execute() inside a cold Flow on Dispatchers.IO —
 * the correct pattern for line-by-line SSE reading.
 */
fun streamPocketChat(
    client: OkHttpClient,
    request: ChatRequest,
): Flow<SseEvent> = flow {
    val jsonBody = CloudClient.json.encodeToString(ChatRequest.serializer(), request)
    val httpRequest = Request.Builder()
        .url(BuildConfig.CLOUD_URL + "/api/v1/pocket/chat/stream")
        .post(jsonBody.toRequestBody("application/json".toMediaType()))
        .build()

    val response = client.newCall(httpRequest).execute()
    try {
        if (!response.isSuccessful) {
            emit(SseEvent.Error("HTTP ${response.code}"))
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit(SseEvent.Error("Empty response body"))
            return@flow
        }

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break

            // SSE format: "data: {...}" — skip empty keep-alive lines
            if (!line.startsWith("data: ")) continue
            val payload = line.removePrefix("data: ")

            val obj: JsonObject = try {
                CloudClient.json.decodeFromString(JsonObject.serializer(), payload)
            } catch (_: Exception) {
                continue
            }

            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "start" -> emit(
                    SseEvent.Start(obj["conversation_id"]?.jsonPrimitive?.contentOrNull)
                )
                "token" -> {
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (content.isNotEmpty()) emit(SseEvent.Token(content))
                }
                "tool_start" -> emit(
                    SseEvent.ToolStart(obj["name"]?.jsonPrimitive?.contentOrNull ?: "")
                )
                "tool_result" -> emit(
                    SseEvent.ToolResult(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        result = obj["result"]?.jsonPrimitive?.contentOrNull ?: "",
                        isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                )
                "end" -> emit(
                    SseEvent.End(
                        expression = obj["expression"]?.jsonPrimitive?.contentOrNull ?: "NEUTRAL",
                        careValue = obj["care_value"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        agent = obj["agent"]?.jsonPrimitive?.contentOrNull ?: "AZOTH",
                    )
                )
                "error" -> emit(
                    SseEvent.Error(obj["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error")
                )
            }
        }
    } finally {
        response.close()
    }
}.flowOn(Dispatchers.IO)
