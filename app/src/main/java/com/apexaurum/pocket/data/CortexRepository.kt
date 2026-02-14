package com.apexaurum.pocket.data

import com.apexaurum.pocket.cloud.CortexMemoryNode
import com.apexaurum.pocket.cloud.CortexRememberRequest
import com.apexaurum.pocket.cloud.PocketApi
import com.apexaurum.pocket.data.db.ApexDatabase
import com.apexaurum.pocket.data.db.CachedCortexMemory
import com.apexaurum.pocket.data.db.OfflineAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * CerebroCortex memory repository — Room cache + offline queuing.
 *
 * Cache strategy:
 * - Memories cached in Room with timestamp.
 * - Stale after CACHE_TTL_MS (5 minutes).
 * - Background refresh via CortexSyncWorker every 15 min.
 *
 * Offline writes:
 * - cortex_remember → queued in offline_actions, replayed by SyncManager.
 * - cortex_delete → optimistic local delete + queued for cloud.
 */
class CortexRepository(private val db: ApexDatabase) {

    private val cortexDao = db.cortexDao()
    private val actionDao = db.offlineActionDao()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // ── Cache reads ──

    /** Observe cached cortex memories (reactive). */
    fun allCached(): Flow<List<CortexMemoryNode>> {
        return cortexDao.getAll().map { cached -> cached.map { it.toNode() } }
    }

    /** Observe cached cortex memories for an agent (reactive). */
    fun cachedByAgent(agentId: String): Flow<List<CortexMemoryNode>> {
        return cortexDao.getByAgent(agentId).map { cached -> cached.map { it.toNode() } }
    }

    /** Check if cache is stale (older than TTL). */
    suspend fun isCacheStale(): Boolean {
        val lastSync = cortexDao.getLastSyncTime() ?: return true
        return (System.currentTimeMillis() - lastSync) > CACHE_TTL_MS
    }

    /** Get cache age in milliseconds (null if never synced). */
    suspend fun cacheAgeMs(): Long? {
        val lastSync = cortexDao.getLastSyncTime() ?: return null
        return System.currentTimeMillis() - lastSync
    }

    // ── Online refresh ──

    /** Refresh cache from API. Returns fresh list. */
    suspend fun refreshFromApi(
        api: PocketApi,
        layer: String? = null,
        agentId: String? = null,
        memoryType: String? = null,
    ): List<CortexMemoryNode> {
        val nodes = api.getCortexMemories(
            layer = layer, agentId = agentId, memoryType = memoryType,
        )
        cortexDao.clearAll()
        cortexDao.insertAll(nodes.map { it.toCached() })
        return nodes
    }

    /** Refresh cache only if stale. Returns cached list. */
    suspend fun refreshIfStale(
        api: PocketApi,
        layer: String? = null,
        agentId: String? = null,
        memoryType: String? = null,
    ): List<CortexMemoryNode> {
        return if (isCacheStale()) {
            refreshFromApi(api, layer, agentId, memoryType)
        } else {
            cortexDao.getAll().first().map { it.toNode() }
        }
    }

    // ── Remember (create) ──

    /** Store a cortex memory. Online → API + cache. Offline → queue. */
    suspend fun remember(
        api: PocketApi?,
        content: String,
        agentId: String = "AZOTH",
        memoryType: String? = null,
        tags: List<String>? = null,
        salience: Float? = null,
        isOnline: Boolean,
    ): String? {
        if (isOnline && api != null) {
            val resp = api.createCortexMemory(
                CortexRememberRequest(
                    content = content,
                    agentId = agentId,
                    memoryType = memoryType,
                    tags = tags,
                    salience = salience,
                )
            )
            return resp.id.ifEmpty { null }
        } else {
            // Queue for offline replay
            val payload = buildMap<String, String> {
                put("content", content)
                put("agent_id", agentId)
                memoryType?.let { put("memory_type", it) }
                tags?.let { put("tags", it.joinToString(",")) }
                salience?.let { put("salience", it.toString()) }
            }
            actionDao.insert(
                OfflineAction(
                    actionType = "cortex_remember",
                    payloadJson = json.encodeToString(payload),
                )
            )
            return null
        }
    }

    // ── Delete ──

    /** Delete a cortex memory. Optimistic local + queue if offline. */
    suspend fun delete(api: PocketApi?, memoryId: String, isOnline: Boolean) {
        cortexDao.deleteById(memoryId)
        if (isOnline && api != null) {
            api.deleteCortexMemory(memoryId)
        } else {
            val payload = json.encodeToString(mapOf("memory_id" to memoryId))
            actionDao.insert(
                OfflineAction(actionType = "cortex_delete", payloadJson = payload)
            )
        }
    }

    /** Clear all cached cortex memories. */
    suspend fun clearAll() {
        cortexDao.clearAll()
    }

    /** How many offline cortex actions are pending. */
    suspend fun pendingActionCount(): Int {
        val all = actionDao.getAll()
        return all.count { it.actionType.startsWith("cortex_") }
    }
}

// ── Mapping helpers ──

private fun CachedCortexMemory.toNode(): CortexMemoryNode = CortexMemoryNode(
    id = id,
    content = content,
    agentId = agentId,
    layer = layer,
    memoryType = memoryType,
    salience = salience,
    valence = valence,
    accessCount = accessCount,
    tags = tags.split(",").filter { it.isNotBlank() },
    concepts = concepts.split(",").filter { it.isNotBlank() },
    linkCount = linkCount,
    createdAt = createdAt,
)

private fun CortexMemoryNode.toCached(): CachedCortexMemory = CachedCortexMemory(
    id = id,
    content = content,
    agentId = agentId,
    layer = layer,
    memoryType = memoryType,
    salience = salience,
    valence = valence,
    accessCount = accessCount,
    tags = tags.joinToString(","),
    concepts = concepts.joinToString(","),
    linkCount = linkCount,
    createdAt = createdAt,
)
