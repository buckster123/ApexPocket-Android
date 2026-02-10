package com.apexaurum.pocket.data

import com.apexaurum.pocket.cloud.AgentInfo
import com.apexaurum.pocket.cloud.PocketApi
import com.apexaurum.pocket.data.db.ApexDatabase
import com.apexaurum.pocket.data.db.CachedAgent

/**
 * Agent list repository — Room cache with 1-hour freshness.
 *
 * Returns cached agents immediately; refreshes from cloud in background.
 */
class AgentRepository(private val db: ApexDatabase) {

    private val agentDao = db.agentDao()

    companion object {
        private const val FRESHNESS_MS = 60 * 60 * 1000L // 1 hour
    }

    /** Get cached agents (may be stale). */
    suspend fun getCached(): List<AgentInfo> {
        return agentDao.getAll().map { it.toAgentInfo() }
    }

    /** Whether the cache is still fresh. */
    suspend fun isFresh(): Boolean {
        val lastFetched = agentDao.getLastFetched() ?: return false
        return System.currentTimeMillis() - lastFetched < FRESHNESS_MS
    }

    /** Refresh agents from API and update cache. Returns fresh list. */
    suspend fun refreshFromApi(api: PocketApi): List<AgentInfo> {
        val resp = api.getAgents()
        val now = System.currentTimeMillis()
        agentDao.clearAll()
        agentDao.insertAll(resp.agents.map { CachedAgent(it.name, it.description, now) })
        return resp.agents
    }

    /** Clear agent cache. */
    suspend fun clearAll() {
        agentDao.clearAll()
    }
}

private fun CachedAgent.toAgentInfo(): AgentInfo = AgentInfo(
    name = name,
    description = description,
)
