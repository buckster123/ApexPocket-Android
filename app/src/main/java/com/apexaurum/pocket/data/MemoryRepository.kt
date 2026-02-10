package com.apexaurum.pocket.data

import com.apexaurum.pocket.cloud.AgentMemoryItem
import com.apexaurum.pocket.cloud.PocketApi
import com.apexaurum.pocket.cloud.SaveMemoryRequest
import com.apexaurum.pocket.data.db.ApexDatabase
import com.apexaurum.pocket.data.db.CachedMemory
import com.apexaurum.pocket.data.db.OfflineAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Memory repository — Room cache + offline queuing for save/delete.
 */
class MemoryRepository(private val db: ApexDatabase) {

    private val memoryDao = db.memoryDao()
    private val actionDao = db.offlineActionDao()
    private val json = Json { ignoreUnknownKeys = true }

    /** Observe cached memories for an agent (reactive). */
    fun memoriesForAgent(agentId: String): Flow<List<AgentMemoryItem>> {
        return memoryDao.getByAgent(agentId).map { cached ->
            cached.map { it.toAgentMemoryItem() }
        }
    }

    /** Refresh memories from API and update cache. */
    suspend fun refreshFromApi(api: PocketApi, agentId: String): List<AgentMemoryItem> {
        val resp = api.getAgentMemories(agentId)
        memoryDao.clearByAgent(agentId)
        memoryDao.insertAll(resp.memories.map { it.toCachedMemory() })
        return resp.memories
    }

    /** Save a memory. Online → API + cache. Offline → queue action. */
    suspend fun save(api: PocketApi?, agentId: String, key: String, value: String, type: String, isOnline: Boolean) {
        if (isOnline && api != null) {
            api.saveMemory(SaveMemoryRequest(agent = agentId, memoryType = type, key = key, value = value))
        } else {
            val payload = json.encodeToString(
                mapOf("agent" to agentId, "memory_type" to type, "key" to key, "value" to value)
            )
            actionDao.insert(OfflineAction(actionType = "memory_save", payloadJson = payload))
        }
    }

    /** Delete a memory. Online → API + cache. Offline → queue action. */
    suspend fun delete(api: PocketApi?, memoryId: String, isOnline: Boolean) {
        memoryDao.deleteById(memoryId)
        if (isOnline && api != null) {
            api.deleteMemory(memoryId)
        } else {
            val payload = json.encodeToString(mapOf("memory_id" to memoryId))
            actionDao.insert(OfflineAction(actionType = "memory_delete", payloadJson = payload))
        }
    }

    /** Clear all cached memories. */
    suspend fun clearAll() {
        memoryDao.clearAll()
    }
}

private fun CachedMemory.toAgentMemoryItem(): AgentMemoryItem = AgentMemoryItem(
    id = id,
    agentId = agentId,
    memoryType = memoryType,
    key = key,
    value = value,
    confidence = confidence,
)

private fun AgentMemoryItem.toCachedMemory(): CachedMemory = CachedMemory(
    id = id,
    agentId = agentId,
    memoryType = memoryType,
    key = key,
    value = value,
    confidence = confidence,
)
