package com.apexaurum.pocket.widget

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apexaurum.pocket.MainActivity
import com.apexaurum.pocket.R
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.cloud.PendingMessageItem
import com.apexaurum.pocket.data.SoulRepository
import kotlinx.coroutines.flow.first

/** Settings-aware notification prefs read from DataStore. */
private data class NotifPrefs(
    val agents: Boolean = true,
    val councils: Boolean = true,
    val music: Boolean = true,
    val nudges: Boolean = true,
)

/**
 * Multi-channel notification worker — polls pending-messages + nudge endpoints
 * and routes notifications to appropriate channels with deep-link intents.
 *
 * Replaces NudgeWorker with richer notification support:
 * - Agent messages → agent_messages channel (grouped by agent)
 * - Council alerts → council_alerts channel
 * - Music alerts → music_alerts channel
 * - Nudges → soul_whispers channel (unchanged)
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        // Channel IDs — created in ApexPocketApp
        const val CHANNEL_WHISPERS = "soul_whispers"
        const val CHANNEL_AGENTS = "agent_messages"
        const val CHANNEL_COUNCILS = "council_alerts"
        const val CHANNEL_MUSIC = "music_alerts"
        const val CHANNEL_SENTINEL = "sentinel_alerts"

        // Notification ID ranges (non-overlapping)
        private const val ID_NUDGE = 42
        private const val ID_AGENT_BASE = 100
        private const val ID_COUNCIL_BASE = 200
        private const val ID_MUSIC_BASE = 300
        private const val ID_SENTINEL_BASE = 400
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

        val repo = SoulRepository(applicationContext)
        val token = repo.tokenFlow.first() ?: return Result.success()
        val api = try {
            CloudClient.create(token)
        } catch (_: Exception) {
            return Result.retry()
        }

        // Read notification toggle preferences
        val prefs = NotifPrefs(
            agents = repo.notifAgentsFlow.first(),
            councils = repo.notifCouncilsFlow.first(),
            music = repo.notifMusicFlow.first(),
            nudges = repo.notifNudgesFlow.first(),
        )

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // ── Poll pending messages ──────────────────────────────
        try {
            val pending = api.getPendingMessages().messages
            if (pending.isNotEmpty()) {
                postPendingNotifications(nm, pending, prefs)
            }
        } catch (_: Exception) {
            // Best-effort — don't fail the whole worker
        }

        // ── Poll nudge ─────────────────────────────────────────
        if (prefs.nudges) {
            try {
                val nudge = api.getNudge().nudge
                if (nudge != null) {
                    val intent = buildDeepLinkIntent("chat")
                    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_WHISPERS)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(formatAgentName(nudge.agentId))
                        .setContentText(nudge.text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setContentIntent(wrapPendingIntent(intent, ID_NUDGE))
                        .setAutoCancel(true)
                        .setColor(agentColor(nudge.agentId))
                        .build()
                    nm.notify(ID_NUDGE, notification)
                }
            } catch (_: Exception) {
                // Best-effort
            }
        }

        return Result.success()
    }

    /**
     * Post notifications for pending messages, grouped by event type into
     * the appropriate channel. Uses InboxStyle when multiple messages
     * come from the same agent.
     */
    private fun postPendingNotifications(
        nm: NotificationManager,
        messages: List<PendingMessageItem>,
        prefs: NotifPrefs,
    ) {
        // Route each message to the right channel (skip if user disabled)
        for (msg in messages) {
            val (channel, idBase, tab) = when (msg.eventType) {
                "council_complete" -> Triple(CHANNEL_COUNCILS, ID_COUNCIL_BASE, "council_list")
                "music_complete" -> Triple(CHANNEL_MUSIC, ID_MUSIC_BASE, "music")
                "sentinel_alert" -> Triple(CHANNEL_SENTINEL, ID_SENTINEL_BASE, "sensors")
                else -> Triple(CHANNEL_AGENTS, ID_AGENT_BASE, "chat")
            }

            // Respect user notification preferences
            val enabled = when (channel) {
                CHANNEL_AGENTS -> prefs.agents
                CHANNEL_COUNCILS -> prefs.councils
                CHANNEL_MUSIC -> prefs.music
                else -> true
            }
            if (!enabled) continue

            val notifId = idBase + (msg.id.hashCode().and(0x7FFFFFFF) % 99)

            val intent = buildDeepLinkIntent(tab, msg.agentId)
            val title = when (msg.eventType) {
                "council_complete" -> "Council Complete"
                "music_complete" -> "Music Ready"
                else -> formatAgentName(msg.agentId)
            }

            val notification = NotificationCompat.Builder(applicationContext, channel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(msg.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg.text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(wrapPendingIntent(intent, notifId))
                .setAutoCancel(true)
                .setColor(agentColor(msg.agentId))
                .setGroup(groupKey(msg.eventType, msg.agentId))
                .build()

            nm.notify(notifId, notification)
        }

        // If 3+ agent messages, add a summary notification (InboxStyle)
        val agentMsgs = messages.filter {
            it.eventType != "council_complete" && it.eventType != "music_complete"
        }
        if (agentMsgs.size >= 3) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("${agentMsgs.size} agent messages")
            for (msg in agentMsgs.take(5)) {
                inbox.addLine("${formatAgentName(msg.agentId)}: ${msg.text.take(50)}")
            }
            if (agentMsgs.size > 5) {
                inbox.setSummaryText("+${agentMsgs.size - 5} more")
            }

            val summary = NotificationCompat.Builder(applicationContext, CHANNEL_AGENTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${agentMsgs.size} agent messages")
                .setStyle(inbox)
                .setGroup("agent_messages_group")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(wrapPendingIntent(buildDeepLinkIntent("chat"), ID_AGENT_BASE - 1))
                .build()
            nm.notify(ID_AGENT_BASE - 1, summary)
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun buildDeepLinkIntent(tab: String, agent: String? = null): Intent {
        return Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab", tab)
            if (agent != null) putExtra("agent", agent)
        }
    }

    private fun wrapPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatAgentName(id: String): String =
        id.lowercase().replaceFirstChar { it.uppercase() }

    private fun agentColor(id: String): Int = when (id.uppercase()) {
        "AZOTH" -> Color.parseColor("#FFD700")
        "ELYSIAN" -> Color.parseColor("#E8B4FF")
        "VAJRA" -> Color.parseColor("#4FC3F7")
        "KETHER" -> Color.parseColor("#FFFFFF")
        else -> Color.parseColor("#FFD700")
    }

    private fun groupKey(eventType: String, agentId: String): String = when (eventType) {
        "council_complete" -> "council_alerts_group"
        "music_complete" -> "music_alerts_group"
        else -> "agent_messages_group"
    }
}
