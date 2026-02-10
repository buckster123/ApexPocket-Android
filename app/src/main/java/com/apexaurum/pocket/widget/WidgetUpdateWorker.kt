package com.apexaurum.pocket.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.cloud.VillagePulseResponse
import com.apexaurum.pocket.data.SoulRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Periodic worker that fetches village pulse and caches it in DataStore for the widget.
 * Runs every 30 minutes via WorkManager (scheduled in ApexPocketApp).
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = SoulRepository(applicationContext)
        val token = repo.tokenFlow.first() ?: return Result.success()

        try {
            val api = CloudClient.create(token)
            val pulse = api.getVillagePulse()

            // Build a one-liner ticker from the most recent activity
            val ticker = buildTickerLine(pulse)
            if (ticker != null) {
                repo.saveWidgetVillageTicker(ticker.first, ticker.second)
            }

            // Cache full JSON
            val json = Json { ignoreUnknownKeys = true }
            repo.saveWidgetPulseJson(
                json.encodeToString(VillagePulseResponse.serializer(), pulse)
            )
        } catch (_: Exception) {
            // Silent — widget update is best-effort
        }

        // Refresh all widget instances
        try {
            SoulWidget.refreshAll(applicationContext)
        } catch (_: Exception) {}

        return Result.success()
    }

    /** Pick the most interesting recent item for the widget ticker. */
    private fun buildTickerLine(pulse: VillagePulseResponse): Pair<String, String>? {
        // Priority: running councils > completed music > agora posts
        val runningCouncil = pulse.councils.firstOrNull { it.state == "running" }
        if (runningCouncil != null) {
            return "Council: ${runningCouncil.topic.take(30)}" to "COUNCIL"
        }
        val recentMusic = pulse.music.firstOrNull { it.status == "completed" }
        if (recentMusic != null) {
            return "${recentMusic.agent ?: "Agent"}: ${recentMusic.title?.take(25) ?: "new track"}" to (recentMusic.agent ?: "AZOTH")
        }
        val recentAgora = pulse.agora.firstOrNull()
        if (recentAgora != null) {
            return "${recentAgora.agent ?: "Village"}: ${recentAgora.title?.take(25) ?: "new post"}" to (recentAgora.agent ?: "AZOTH")
        }
        return null
    }
}
