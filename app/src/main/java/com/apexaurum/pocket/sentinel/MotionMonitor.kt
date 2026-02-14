package com.apexaurum.pocket.sentinel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Accelerometer tamper detection.
 * Calibrates baseline gravity over 2s, then triggers on significant deviation.
 */
class MotionMonitor(
    private val context: Context,
    private val config: PocketSentinelConfig,
    private val onTrigger: (magnitude: Float) -> Unit,
) : SensorEventListener {

    companion object {
        private const val TAG = "MotionMonitor"
        private const val CALIBRATION_SAMPLES = 40  // ~2s at SENSOR_DELAY_NORMAL
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var baseline = 9.81f
    private var calibrationSamples = mutableListOf<Float>()
    private var calibrated = false
    private var lastTriggerTime = 0L

    fun start() {
        calibrated = false
        calibrationSamples.clear()
        lastTriggerTime = 0L

        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer sensor available")
            return
        }

        sensorManager.registerListener(
            this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL,
        )
        Log.i(TAG, "Started — calibrating baseline...")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "Stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        if (!calibrated) {
            calibrationSamples.add(magnitude)
            if (calibrationSamples.size >= CALIBRATION_SAMPLES) {
                baseline = calibrationSamples.average().toFloat()
                calibrated = true
                Log.i(TAG, "Calibrated — baseline: %.2f m/s²".format(baseline))
            }
            return
        }

        val delta = abs(magnitude - baseline)
        if (delta > config.motionThresholdG) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime >= config.cooldownSeconds * 1000L) {
                lastTriggerTime = now
                Log.i(TAG, "Tamper detected — delta: %.2f g".format(delta))
                onTrigger(delta)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
