package com.apexaurum.pocket.sentinel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * CameraX frame differencing for motion detection.
 *
 * Downsamples each frame to an 80x60 luminance grid, compares with the
 * previous frame pixel-by-pixel. When enough pixels change beyond the
 * threshold, captures a full-res snapshot and fires the trigger callback.
 */
class FrameAnalyzer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val config: PocketSentinelConfig,
    private val onTrigger: (changedPixels: Int, snapshot: ByteArray?) -> Unit,
) {
    companion object {
        private const val TAG = "FrameAnalyzer"
        private const val GRID_W = 80
        private const val GRID_H = 60
        private const val GRID_SIZE = GRID_W * GRID_H  // 4800 pixels
        private const val SNAPSHOT_MAX_DIM = 640
        private const val SNAPSHOT_QUALITY = 60
        // Skip first N frames to let auto-exposure stabilize
        private const val WARMUP_FRAMES = 10
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var previousFrame: ByteArray? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lastTriggerTime = 0L
    private var frameCount = 0

    fun start() {
        frameCount = 0
        previousFrame = null
        lastTriggerTime = 0L

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val cameraSelector = if (config.useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { proxy ->
                analyzeFrame(proxy)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis,
                    imageCapture,
                )
                Log.i(TAG, "Started — threshold: ${config.cameraThreshold}, minPixels: ${config.cameraMinPixels}")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        imageCapture = null
        previousFrame = null
        Log.i(TAG, "Stopped")
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            frameCount++
            val image = proxy.image ?: return

            // Extract Y (luminance) plane and downsample to 80x60
            val yBuffer = image.planes[0].buffer
            val yRowStride = image.planes[0].rowStride
            val imgW = image.width
            val imgH = image.height

            val grid = ByteArray(GRID_SIZE)
            val strideX = imgW / GRID_W
            val strideY = imgH / GRID_H

            for (gy in 0 until GRID_H) {
                val srcRow = gy * strideY
                for (gx in 0 until GRID_W) {
                    val srcCol = gx * strideX
                    val pos = srcRow * yRowStride + srcCol
                    grid[gy * GRID_W + gx] = if (pos < yBuffer.capacity()) {
                        yBuffer.get(pos)
                    } else {
                        0
                    }
                }
            }

            // Skip warmup frames (auto-exposure stabilization)
            if (frameCount <= WARMUP_FRAMES) {
                previousFrame = grid
                return
            }

            val prev = previousFrame
            previousFrame = grid

            if (prev == null) return

            // Count changed pixels
            var changed = 0
            for (i in 0 until GRID_SIZE) {
                val delta = abs((grid[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                if (delta > config.cameraThreshold) {
                    changed++
                }
            }

            if (changed >= config.cameraMinPixels) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime >= config.cooldownSeconds * 1000L) {
                    lastTriggerTime = now
                    Log.i(TAG, "Motion detected — $changed pixels changed")
                    captureSnapshot { snapshot ->
                        onTrigger(changed, snapshot)
                    }
                }
            }
        } finally {
            proxy.close()
        }
    }

    private fun captureSnapshot(onCaptured: (ByteArray?) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onCaptured(null)
            return
        }

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                @androidx.annotation.OptIn(ExperimentalGetImage::class)
                override fun onCaptureSuccess(proxy: ImageProxy) {
                    try {
                        val buffer = proxy.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        // Decode, resize, re-encode as compact JPEG
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val scaled = scaleDown(bitmap, SNAPSHOT_MAX_DIM)
                            val out = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, out)
                            if (scaled !== bitmap) scaled.recycle()
                            bitmap.recycle()
                            onCaptured(out.toByteArray())
                        } else {
                            onCaptured(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Snapshot processing failed", e)
                        onCaptured(null)
                    } finally {
                        proxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Snapshot capture failed", exception)
                    onCaptured(null)
                }
            },
        )
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
    }
}
