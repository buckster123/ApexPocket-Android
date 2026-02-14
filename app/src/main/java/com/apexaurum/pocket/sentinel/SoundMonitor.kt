package com.apexaurum.pocket.sentinel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * AudioRecord amplitude monitoring.
 * Reads mic in a coroutine loop, calculates RMS → dB, triggers on threshold.
 */
class SoundMonitor(
    private val context: Context,
    private val config: PocketSentinelConfig,
    private val onTrigger: (amplitudeDb: Float) -> Unit,
) {
    companion object {
        private const val TAG = "SoundMonitor"
        private const val SAMPLE_RATE = 8000
        private const val CHECK_INTERVAL_MS = 200L
    }

    private var scope: CoroutineScope? = null
    private var audioRecord: AudioRecord? = null
    private var lastTriggerTime = 0L

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        lastTriggerTime = 0L
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        audioRecord?.startRecording()

        scope?.launch {
            Log.i(TAG, "Started — threshold: ${config.soundThresholdDb} dB")
            val buffer = ShortArray(bufferSize / 2)

            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read <= 0) {
                    delay(CHECK_INTERVAL_MS)
                    continue
                }

                // Calculate RMS amplitude
                var sumSquares = 0.0
                for (i in 0 until read) {
                    val sample = buffer[i].toDouble()
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / read)

                // Convert to dB (relative to max 16-bit value)
                val db = if (rms > 0) (20.0 * log10(rms / Short.MAX_VALUE)).toFloat() else -100f

                if (db > config.soundThresholdDb) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerTime >= config.cooldownSeconds * 1000L) {
                        lastTriggerTime = now
                        Log.i(TAG, "Sound trigger — %.1f dB".format(db))
                        withContext(Dispatchers.Main) {
                            onTrigger(db)
                        }
                    }
                }

                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Stopped")
    }
}
