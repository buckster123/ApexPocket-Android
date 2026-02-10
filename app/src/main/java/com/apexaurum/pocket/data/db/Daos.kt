package com.apexaurum.pocket.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages WHERE agentId = :agentId ORDER BY timestamp ASC")
    fun getByAgent(agentId: String): Flow<List<CachedMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CachedMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CachedMessage>)

    @Query("DELETE FROM cached_messages WHERE agentId = :agentId")
    suspend fun clearByAgent(agentId: String)

    @Query("DELETE FROM cached_messages")
    suspend fun clearAll()

    @Query("UPDATE cached_messages SET pending = 0 WHERE id = :id")
    suspend fun markSent(id: Long)
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM cached_agents ORDER BY name ASC")
    suspend fun getAll(): List<CachedAgent>

    @Query("SELECT * FROM cached_agents ORDER BY name ASC")
    fun getAllFlow(): Flow<List<CachedAgent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agents: List<CachedAgent>)

    @Query("SELECT lastFetched FROM cached_agents LIMIT 1")
    suspend fun getLastFetched(): Long?

    @Query("DELETE FROM cached_agents")
    suspend fun clearAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM cached_memories WHERE agentId = :agentId ORDER BY key ASC")
    fun getByAgent(agentId: String): Flow<List<CachedMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<CachedMemory>)

    @Query("DELETE FROM cached_memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_memories WHERE agentId = :agentId")
    suspend fun clearByAgent(agentId: String)

    @Query("DELETE FROM cached_memories")
    suspend fun clearAll()
}

@Dao
interface OfflineActionDao {
    @Query("SELECT * FROM offline_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<OfflineAction>

    @Insert
    suspend fun insert(action: OfflineAction): Long

    @Query("DELETE FROM offline_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE offline_actions SET retries = retries + 1 WHERE id = :id")
    suspend fun incrementRetries(id: Long)

    @Query("DELETE FROM offline_actions WHERE retries >= :maxRetries")
    suspend fun pruneExhausted(maxRetries: Int = 3)

    @Query("SELECT COUNT(*) FROM offline_actions")
    suspend fun count(): Int
}
