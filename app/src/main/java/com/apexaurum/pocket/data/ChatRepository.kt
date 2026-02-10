package com.apexaurum.pocket.data

import com.apexaurum.pocket.ChatMessage
import com.apexaurum.pocket.ToolInfo
import com.apexaurum.pocket.data.db.ApexDatabase
import com.apexaurum.pocket.data.db.CachedMessage
import com.apexaurum.pocket.data.db.OfflineAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Chat message repository — cache-first reads, offline write queuing.
 *
 * Flow: loadHistory API → save to Room. On send offline → save locally + queue action.
 */
class ChatRepository(private val db: ApexDatabase) {

    private val messageDao = db.messageDao()
    private val actionDao = db.offlineActionDao()
    private val json = Json { ignoreUnknownKeys = true }

    /** Observe cached messages for an agent (reactive). */
    fun messagesForAgent(agentId: String): Flow<List<ChatMessage>> {
        return messageDao.getByAgent(agentId).map { cached ->
            cached.map { it.toChatMessage() }
        }
    }

    /** Replace local cache with cloud history for an agent. */
    suspend fun replaceFromHistory(agentId: String, messages: List<ChatMessage>) {
        messageDao.clearByAgent(agentId)
        messageDao.insertAll(messages.map { it.toCachedMessage(agentId) })
    }

    /** Append a single message to the local cache. Returns the DB row ID. */
    suspend fun appendMessage(agentId: String, message: ChatMessage, pending: Boolean = false): Long {
        return messageDao.insert(message.toCachedMessage(agentId, pending))
    }

    /** Mark a pending message as sent. */
    suspend fun markSent(rowId: Long) {
        messageDao.markSent(rowId)
    }

    /** Queue a chat message for offline replay. */
    suspend fun queueOfflineChat(agentId: String, text: String, requestJson: String) {
        actionDao.insert(
            OfflineAction(
                actionType = "chat",
                payloadJson = requestJson,
            )
        )
    }

    /** Queue a care tap for offline replay. */
    suspend fun queueOfflineCare(careType: String, intensity: Float, energy: Float) {
        val payload = json.encodeToString(
            mapOf("care_type" to careType, "intensity" to intensity.toString(), "energy" to energy.toString())
        )
        actionDao.insert(
            OfflineAction(actionType = "care", payloadJson = payload)
        )
    }

    /** Clear all cached messages. */
    suspend fun clearAll() {
        messageDao.clearAll()
    }

    /** Clear messages for a specific agent. */
    suspend fun clearByAgent(agentId: String) {
        messageDao.clearByAgent(agentId)
    }
}

// ── Mapping helpers ────────────────────────────────────────

private val json = Json { ignoreUnknownKeys = true }

private fun CachedMessage.toChatMessage(): ChatMessage = ChatMessage(
    text = text,
    isUser = isUser,
    expression = expression,
    timestamp = timestamp,
    type = type,
    toolResults = toolResultsJson?.let {
        try { json.decodeFromString<List<ToolInfo>>(it) } catch (_: Exception) { emptyList() }
    } ?: emptyList(),
)

private fun ChatMessage.toCachedMessage(agentId: String, pending: Boolean = false): CachedMessage = CachedMessage(
    agentId = agentId,
    text = text,
    isUser = isUser,
    timestamp = timestamp,
    expression = expression,
    type = type,
    toolResultsJson = if (toolResults.isNotEmpty()) json.encodeToString(toolResults) else null,
    pending = pending,
)
