package com.apexaurum.pocket.sentinel

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.apexaurum.pocket.MainActivity
import com.apexaurum.pocket.R
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.data.SoulRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service managing pocket sentinel detection engines.
 *
 * Communicates with UI via companion StateFlows (no binding needed).
 * Starts/stops via Intent actions. Each detection engine calls back
 * on trigger; the service rate-limits and posts alerts to cloud.
 */
class PocketSentinelService : LifecycleService() {

    companion object {
        private const val TAG = "PocketSentinel"
        const val CHANNEL_SERVICE = "pocket_sentinel_service"
        private const val NOTIFICATION_ID = 500

        const val ACTION_START = "com.apexaurum.pocket.sentinel.START"
        const val ACTION_STOP = "com.apexaurum.pocket.sentinel.STOP"

        // Observable state for UI (no binding required)
        val isRunning = MutableStateFlow(false)
        val activeMode = MutableStateFlow<Set<DetectionMode>>(emptySet())
        val lastEvent = MutableStateFlow<PocketSentinelEvent?>(null)
        val eventCount = MutableStateFlow(0)

        fun start(context: Context, config: PocketSentinelConfig) {
            val intent = Intent(context, PocketSentinelService::class.java).apply {
                action = ACTION_START
                putExtra("camera", config.cameraEnabled)
                putExtra("sound", config.soundEnabled)
                putExtra("motion", config.motionEnabled)
                putExtra("cam_threshold", config.cameraThreshold)
                putExtra("cam_min_pixels", config.cameraMinPixels)
                putExtra("sound_db", config.soundThresholdDb)
                putExtra("motion_g", config.motionThresholdG)
                putExtra("cooldown", config.cooldownSeconds)
                putExtra("max_alerts", config.maxAlertsPerHour)
                putExtra("back_camera", config.useBackCamera)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PocketSentinelService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var frameAnalyzer: FrameAnalyzer? = null
    private var soundMonitor: SoundMonitor? = null
    private var motionMonitor: MotionMonitor? = null
    private var config = PocketSentinelConfig()

    // Rate limiting
    private var lastAlertTime = 0L
    private val alertsThisHour = AtomicInteger(0)
    private var hourStart = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                config = PocketSentinelConfig(
                    cameraEnabled = intent.getBooleanExtra("camera", true),
                    soundEnabled = intent.getBooleanExtra("sound", false),
                    motionEnabled = intent.getBooleanExtra("motion", false),
                    cameraThreshold = intent.getIntExtra("cam_threshold", 25),
                    cameraMinPixels = intent.getIntExtra("cam_min_pixels", 50),
                    soundThresholdDb = intent.getFloatExtra("sound_db", -30f),
                    motionThresholdG = intent.getFloatExtra("motion_g", 0.5f),
                    cooldownSeconds = intent.getIntExtra("cooldown", 30),
                    maxAlertsPerHour = intent.getIntExtra("max_alerts", 20),
                    useBackCamera = intent.getBooleanExtra("back_camera", true),
                )
                startSentinel()
            }
            ACTION_STOP -> {
                stopSentinel()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        stopSentinel()
        super.onDestroy()
    }

    private fun startSentinel() {
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))
        hourStart = System.currentTimeMillis()
        alertsThisHour.set(0)

        val modes = mutableSetOf<DetectionMode>()

        // Camera detection
        if (config.cameraEnabled) {
            frameAnalyzer = FrameAnalyzer(this, this, config) { changedPixels, snapshot ->
                handleTrigger(
                    mode = "camera",
                    magnitude = changedPixels.toFloat(),
                    detail = "$changedPixels pixels changed",
                    snapshot = snapshot,
                )
            }
            frameAnalyzer?.start()
            modes.add(DetectionMode.CAMERA)
        }

        // Sound detection
        if (config.soundEnabled) {
            soundMonitor = SoundMonitor(this, config) { db ->
                handleTrigger(
                    mode = "sound",
                    magnitude = db,
                    detail = "%.1f dB".format(db),
                    snapshot = null,
                )
            }
            soundMonitor?.start()
            modes.add(DetectionMode.SOUND)
        }

        // Motion/tamper detection
        if (config.motionEnabled) {
            motionMonitor = MotionMonitor(this, config) { delta ->
                handleTrigger(
                    mode = "motion",
                    magnitude = delta,
                    detail = "%.2f g deviation".format(delta),
                    snapshot = null,
                )
            }
            motionMonitor?.start()
            modes.add(DetectionMode.MOTION)
        }

        activeMode.value = modes
        isRunning.value = true
        eventCount.value = 0

        val modeText = modes.joinToString(", ") { it.name.lowercase() }
        updateNotification("Active — $modeText")
        Log.i(TAG, "Armed — modes: $modeText")
    }

    private fun stopSentinel() {
        frameAnalyzer?.stop()
        frameAnalyzer = null
        soundMonitor?.stop()
        soundMonitor = null
        motionMonitor?.stop()
        motionMonitor = null

        isRunning.value = false
        activeMode.value = emptySet()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Disarmed")
    }

    private fun handleTrigger(
        mode: String,
        magnitude: Float,
        detail: String,
        snapshot: ByteArray?,
    ) {
        // Hourly rate limit reset
        val now = System.currentTimeMillis()
        if (now - hourStart >= 3_600_000L) {
            alertsThisHour.set(0)
            hourStart = now
        }
        if (alertsThisHour.get() >= config.maxAlertsPerHour) {
            Log.w(TAG, "Rate limit reached ($config.maxAlertsPerHour/hr)")
            return
        }

        // Global cooldown across all modes
        if (now - lastAlertTime < config.cooldownSeconds * 1000L) return
        lastAlertTime = now
        alertsThisHour.incrementAndGet()

        val event = PocketSentinelEvent(
            mode = mode,
            magnitude = magnitude,
            detail = detail,
            snapshotJpeg = snapshot,
        )
        lastEvent.value = event
        eventCount.value = eventCount.value + 1

        // Update notification
        updateNotification("Alert: $detail")

        // Post to cloud
        lifecycleScope.launch {
            postAlertToCloud(event)
        }
    }

    private suspend fun postAlertToCloud(event: PocketSentinelEvent) {
        try {
            val repo = SoulRepository(applicationContext)
            val token = repo.tokenFlow.first() ?: return
            val api = CloudClient.create(token)

            val alertType = "pocket_${event.mode}"
            val snapshotB64 = event.snapshotJpeg?.let {
                Base64.encodeToString(it, Base64.NO_WRAP)
            }

            api.postPocketAlert(
                com.apexaurum.pocket.cloud.PocketAlertRequest(
                    alertType = alertType,
                    snapshotB64 = snapshotB64,
                    detectionMode = event.mode,
                    magnitude = event.magnitude,
                    detail = event.detail,
                )
            )
            Log.i(TAG, "Alert posted to cloud: $alertType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post alert: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab", "sensors")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pocket Sentinel")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setColor(0xFFD4AF37.toInt())
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
