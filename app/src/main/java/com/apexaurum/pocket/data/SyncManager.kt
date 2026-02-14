package com.apexaurum.pocket.data

import android.util.Log
import com.apexaurum.pocket.cloud.CareRequest
import com.apexaurum.pocket.cloud.CortexRememberRequest
import com.apexaurum.pocket.cloud.PocketApi
import com.apexaurum.pocket.cloud.SaveMemoryRequest
import com.apexaurum.pocket.data.db.ApexDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Processes the offline action queue when connectivity resumes.
 *
 * Actions are replayed FIFO. Failed actions get retried up to 3 times,
 * then discarded with a log warning.
 */
class SyncManager(private val db: ApexDatabase) {

    private val actionDao = db.offlineActionDao()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_RETRIES = 3
    }

    /** Process all queued offline actions. Call when network comes back. */
    suspend fun processQueue(api: PocketApi) {
        // Prune actions that already exceeded max retries
        actionDao.pruneExhausted(MAX_RETRIES)

        val actions = actionDao.getAll()
        if (actions.isEmpty()) return

        Log.d(TAG, "Processing ${actions.size} queued offline actions")

        for (action in actions) {
            try {
                when (action.actionType) {
                    "care" -> replayCare(api, action.payloadJson)
                    "memory_save" -> replayMemorySave(api, action.payloadJson)
                    "memory_delete" -> replayMemoryDelete(api, action.payloadJson)
                    "cortex_remember" -> replayCortexRemember(api, action.payloadJson)
                    "cortex_delete" -> replayCortexDelete(api, action.payloadJson)
                    else -> Log.w(TAG, "Unknown action type: ${action.actionType}")
                }
                // Success — remove from queue
                actionDao.deleteById(action.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replay action ${action.id} (${action.actionType}): ${e.message}")
                actionDao.incrementRetries(action.id)
            }
        }

        // Final prune
        actionDao.pruneExhausted(MAX_RETRIES)
    }

    /** How many actions are still queued. */
    suspend fun pendingCount(): Int = actionDao.count()

    private suspend fun replayCare(api: PocketApi, payloadJson: String) {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        val careType = obj["care_type"]?.jsonPrimitive?.content ?: "love"
        val intensity = obj["intensity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
        val energy = obj["energy"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
        api.care(CareRequest(careType = careType, intensity = intensity, energy = energy))
    }

    private suspend fun replayMemorySave(api: PocketApi, payloadJson: String) {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        api.saveMemory(
            SaveMemoryRequest(
                agent = obj["agent"]?.jsonPrimitive?.content ?: "AZOTH",
                memoryType = obj["memory_type"]?.jsonPrimitive?.content ?: "fact",
                key = obj["key"]?.jsonPrimitive?.content ?: "",
                value = obj["value"]?.jsonPrimitive?.content ?: "",
            )
        )
    }

    private suspend fun replayMemoryDelete(api: PocketApi, payloadJson: String) {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        val id = obj["memory_id"]?.jsonPrimitive?.content ?: return
        api.deleteMemory(id)
    }

    private suspend fun replayCortexRemember(api: PocketApi, payloadJson: String) {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        val content = obj["content"]?.jsonPrimitive?.content ?: return
        val agentId = obj["agent_id"]?.jsonPrimitive?.content ?: "AZOTH"
        val memoryType = obj["memory_type"]?.jsonPrimitive?.content
        val tagsRaw = obj["tags"]?.jsonPrimitive?.content
        val tags = tagsRaw?.split(",")?.filter { it.isNotBlank() }
        val salience = obj["salience"]?.jsonPrimitive?.content?.toFloatOrNull()
        api.createCortexMemory(
            CortexRememberRequest(
                content = content,
                agentId = agentId,
                memoryType = memoryType,
                tags = tags,
                salience = salience,
            )
        )
    }

    private suspend fun replayCortexDelete(api: PocketApi, payloadJson: String) {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        val id = obj["memory_id"]?.jsonPrimitive?.content ?: return
        api.deleteCortexMemory(id)
    }
}
