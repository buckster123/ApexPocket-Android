package com.apexaurum.pocket.sentinel

/**
 * Configuration for Pocket Sentinel detection modes.
 * Persisted in DataStore, read by the foreground service on arm.
 */
data class PocketSentinelConfig(
    val cameraEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val motionEnabled: Boolean = false,
    val cameraThreshold: Int = 25,        // pixel luminance delta (0-255)
    val cameraMinPixels: Int = 50,        // out of 4800 (80x60 grid)
    val soundThresholdDb: Float = -30f,   // dB threshold
    val motionThresholdG: Float = 0.5f,   // g-force delta from baseline
    val cooldownSeconds: Int = 30,
    val maxAlertsPerHour: Int = 20,
    val useBackCamera: Boolean = true,
)

/** Local event from a detection trigger (before cloud post). */
data class PocketSentinelEvent(
    val mode: String,           // "camera" | "sound" | "motion"
    val magnitude: Float,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val snapshotJpeg: ByteArray? = null,
)

enum class DetectionMode { CAMERA, SOUND, MOTION }
