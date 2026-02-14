package com.apexaurum.pocket.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached chat message — persists across app restarts. */
@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val expression: String = "NEUTRAL",
    val type: String = "message",
    val toolResultsJson: String? = null,
    val pending: Boolean = false,
)

/** Cached agent info — avoids re-fetching on every app open. */
@Entity(tableName = "cached_agents")
data class CachedAgent(
    @PrimaryKey val name: String,
    val description: String = "",
    val lastFetched: Long = System.currentTimeMillis(),
)

/** Cached agent memory — offline-readable copy. */
@Entity(tableName = "cached_memories")
data class CachedMemory(
    @PrimaryKey val id: String,
    val agentId: String,
    val memoryType: String = "fact",
    val key: String,
    val value: String,
    val confidence: Float = 0.8f,
)

/** Cached CerebroCortex memory — rich semantic memory with layers and types. */
@Entity(tableName = "cached_cortex_memories")
data class CachedCortexMemory(
    @PrimaryKey val id: String,
    val content: String,
    val agentId: String = "AZOTH",
    val layer: String = "working",
    val memoryType: String = "semantic",
    val salience: Float = 0.5f,
    val valence: String = "neutral",
    val accessCount: Int = 0,
    val tags: String = "",
    val concepts: String = "",
    val linkCount: Int = 0,
    val createdAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)

/** Queued action for offline replay. */
@Entity(tableName = "offline_actions")
data class OfflineAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retries: Int = 0,
)
