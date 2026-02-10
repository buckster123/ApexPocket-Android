package com.apexaurum.pocket.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.apexaurum.pocket.MainActivity
import com.apexaurum.pocket.cloud.CareRequest
import com.apexaurum.pocket.cloud.CloudClient
import com.apexaurum.pocket.data.SoulRepository
import com.apexaurum.pocket.soul.LoveEquation
import kotlinx.coroutines.flow.first

/**
 * Love tap from widget — sends care to cloud + updates local E.
 * Runs in WorkManager context, no ViewModel needed.
 */
class LoveTapAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repo = SoulRepository(context)
        val token = repo.tokenFlow.first() ?: return
        val soul = repo.soulFlow.first()

        // Apply care locally (mirrors PocketViewModel.love())
        val updated = LoveEquation.applyCare(soul, 1.5f)
        val evolved = LoveEquation.evolvePersonality(updated)
        repo.saveSoul(evolved)
        repo.updateLastInteraction()
        repo.saveWidgetExpression("LOVE")

        // Report to cloud (fire-and-forget)
        try {
            val api = CloudClient.create(token)
            api.care(CareRequest(careType = "love", intensity = 1.5f, energy = evolved.e))
        } catch (_: Exception) {}

        // Refresh widget immediately
        SoulWidget.refreshAll(context)
    }
}

/**
 * Toggle music play/pause from widget via broadcast to app process.
 */
class TogglePlayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = Intent("com.apexaurum.pocket.TOGGLE_PLAY").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}

/**
 * Open a specific tab in the app from widget.
 */
class OpenTabAction : ActionCallback {
    companion object {
        val TAB_KEY = ActionParameters.Key<String>("tab")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val tab = parameters[TAB_KEY] ?: "face"
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("tab", tab)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}

/**
 * Receives TOGGLE_PLAY broadcast in the app process.
 * Calls MusicToggleBridge which routes to ViewModel's ExoPlayer.
 */
class MusicToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        MusicToggleBridge.toggle()
    }
}

/**
 * Simple callback bridge so the widget broadcast can reach the ViewModel's player.
 * ViewModel sets toggleCallback in init, clears in onCleared.
 */
object MusicToggleBridge {
    var toggleCallback: (() -> Unit)? = null
    fun toggle() { toggleCallback?.invoke() }
}
