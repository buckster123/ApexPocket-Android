package com.apexaurum.pocket.widget

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apexaurum.pocket.MainActivity
import com.apexaurum.pocket.R
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.data.SoulRepository
import kotlinx.coroutines.flow.first

class NudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "soul_whispers"
        private const val NOTIFICATION_ID = 42
    }

    override suspend fun doWork(): Result {
        // Check notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
        }

        // Read device token — can't call API without it
        val repo = SoulRepository(applicationContext)
        val token = repo.tokenFlow.first() ?: return Result.success()

        // Ask backend for a nudge (it handles rate-limiting and context)
        val nudge = try {
            val api = CloudClient.create(token)
            api.getNudge().nudge
        } catch (_: Exception) {
            null
        }

        if (nudge == null) return Result.success()

        // Build tap intent → opens app
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(nudge.agentId.lowercase().replaceFirstChar { it.uppercase() })
            .setContentText(nudge.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        return Result.success()
    }
}
