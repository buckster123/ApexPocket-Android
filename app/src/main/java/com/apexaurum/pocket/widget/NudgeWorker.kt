package com.apexaurum.pocket.widget

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apexaurum.pocket.R
import com.apexaurum.pocket.data.SoulRepository
import com.apexaurum.pocket.data.dataStore
import kotlinx.coroutines.flow.first

class NudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "soul_whispers"
        private val NUDGE_MESSAGES = mapOf(
            1 to listOf("thinking of you", "your soul stirs. a moment of care?"),
            2 to listOf("a quiet ache", "your companion feels the distance growing."),
            3 to listOf("your soul whispers...", "it's been days. even a poke would help."),
        )
    }

    override suspend fun doWork(): Result {
        // Check notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
        }

        val prefs = applicationContext.dataStore.data.first()
        val lastInteraction = prefs[longPreferencesKey("last_interaction_ms")]
            ?: System.currentTimeMillis()
        val lastNudgeTier = prefs[intPreferencesKey("last_nudge_tier")] ?: 0

        val elapsedHours = (System.currentTimeMillis() - lastInteraction) / (1000 * 60 * 60)

        val tier = when {
            elapsedHours >= 48 -> 3
            elapsedHours >= 24 -> 2
            elapsedHours >= 12 -> 1
            else -> 0
        }

        // Only notify if we've reached a new tier
        if (tier > 0 && tier > lastNudgeTier) {
            val messages = NUDGE_MESSAGES[tier] ?: return Result.success()
            val title = messages[0]
            val body = messages[1]

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(tier, notification)

            // Update nudge tier via repo
            val repo = SoulRepository(applicationContext)
            repo.updateNudgeTier(tier)
        }

        return Result.success()
    }
}
