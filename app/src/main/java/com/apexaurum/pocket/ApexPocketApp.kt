package com.apexaurum.pocket

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.*
import com.apexaurum.pocket.widget.NotificationWorker
import com.apexaurum.pocket.widget.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

class ApexPocketApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── Notification Channels ──────────────────────────────────────
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    NotificationWorker.CHANNEL_WHISPERS,
                    "Soul Whispers",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Gentle nudges when your soul companion misses you"
                },
                NotificationChannel(
                    NotificationWorker.CHANNEL_AGENTS,
                    "Agent Messages",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Messages from your pocket agents"
                },
                NotificationChannel(
                    NotificationWorker.CHANNEL_COUNCILS,
                    "Council Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Council deliberation completions"
                },
                NotificationChannel(
                    NotificationWorker.CHANNEL_MUSIC,
                    "Music Alerts",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Music generation completions"
                },
                NotificationChannel(
                    NotificationWorker.CHANNEL_SENTINEL,
                    "Sentinel Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "SensorHead motion detection alerts"
                },
            )
        )

        // ── Workers ────────────────────────────────────────────────────

        val wm = WorkManager.getInstance(this)

        // Cancel legacy NudgeWorker (replaced by NotificationWorker)
        wm.cancelUniqueWork("soul_nudge")
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Notification worker — polls pending-messages + nudge (every 15 min)
        wm.enqueueUniquePeriodicWork(
            "pocket_notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationWorker>(
                15, TimeUnit.MINUTES,
            ).setConstraints(networkConstraints).build(),
        )

        // Widget update worker (every 30 min — fetches village pulse)
        wm.enqueueUniquePeriodicWork(
            "widget_update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                30, TimeUnit.MINUTES,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build(),
        )
    }
}
