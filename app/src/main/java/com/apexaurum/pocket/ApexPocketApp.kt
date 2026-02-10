package com.apexaurum.pocket

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.*
import com.apexaurum.pocket.widget.NudgeWorker
import java.util.concurrent.TimeUnit

class ApexPocketApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for soul nudges
        val channel = NotificationChannel(
            NudgeWorker.CHANNEL_ID,
            "Soul Whispers",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Gentle nudges when your soul companion misses you"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        // Schedule periodic nudge worker (every 1 hour — backend controls rate-limiting)
        val nudgeRequest = PeriodicWorkRequestBuilder<NudgeWorker>(
            1, TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "soul_nudge",
            ExistingPeriodicWorkPolicy.KEEP,
            nudgeRequest,
        )
    }
}
