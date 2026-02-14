package com.apexaurum.pocket.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.data.db.ApexDatabase
import kotlinx.coroutines.flow.first

/**
 * Background worker that keeps cortex memory cache fresh.
 *
 * Runs every 15 minutes via WorkManager (requires network).
 * 1. Processes any pending cortex offline actions.
 * 2. Refreshes the local cortex memory cache from cloud.
 */
class CortexSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CortexSyncWorker"
    }

    override suspend fun doWork(): Result {
        val repo = SoulRepository(applicationContext)
        val token = repo.tokenFlow.first() ?: return Result.success()

        val api = CloudClient.create(token)
        val db = ApexDatabase.getInstance(applicationContext)
        val syncManager = SyncManager(db)
        val cortexRepo = CortexRepository(db)

        // 1. Replay any pending offline cortex actions
        try {
            syncManager.processQueue(api)
        } catch (e: Exception) {
            Log.w(TAG, "Queue processing failed: ${e.message}")
        }

        // 2. Refresh cortex memory cache
        try {
            cortexRepo.refreshFromApi(api)
            Log.d(TAG, "Cortex cache refreshed")
        } catch (e: Exception) {
            Log.w(TAG, "Cache refresh failed: ${e.message}")
        }

        return Result.success()
    }
}
