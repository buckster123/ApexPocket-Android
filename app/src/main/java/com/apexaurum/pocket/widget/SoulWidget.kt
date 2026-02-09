package com.apexaurum.pocket.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.apexaurum.pocket.MainActivity
import com.apexaurum.pocket.data.dataStore
import kotlinx.coroutines.flow.first

class SoulWidget : GlanceAppWidget() {

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(SoulWidget::class.java)
            ids.forEach { id -> SoulWidget().update(context, id) }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val e = prefs[floatPreferencesKey("soul_e")] ?: 1.0f
        val agent = prefs[stringPreferencesKey("selected_agent")] ?: "AZOTH"
        val stateName = deriveState(e)

        provideContent {
            WidgetContent(stateName, e, agent)
        }
    }

    private fun deriveState(e: Float): String = when {
        e >= 30.0f -> "TRANSCENDENT"
        e >= 12.0f -> "RADIANT"
        e >= 5.0f -> "FLOURISHING"
        e >= 2.0f -> "WARM"
        e >= 1.0f -> "TENDER"
        e >= 0.5f -> "GUARDED"
        else -> "PROTECTING"
    }
}

@Composable
private fun WidgetContent(state: String, e: Float, agent: String) {
    val stateColor = when (state) {
        "PROTECTING" -> ColorProvider(Color(0xFF4A4A5A))
        "GUARDED" -> ColorProvider(Color(0xFF6B7B9B))
        "TENDER" -> ColorProvider(Color(0xFF8BC34A))
        "WARM" -> ColorProvider(Color(0xFFFFB74D))
        "FLOURISHING" -> ColorProvider(Color(0xFF4FC3F7))
        "RADIANT" -> ColorProvider(Color(0xFFFFD700))
        "TRANSCENDENT" -> ColorProvider(Color(0xFFE8B4FF))
        else -> ColorProvider(Color(0xFFFFD700))
    }
    val goldColor = ColorProvider(Color(0xFFFFD700))
    val mutedColor = ColorProvider(Color(0xFF616161))
    val bgColor = ColorProvider(Color(0xFF0A0A0F))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = state,
            style = TextStyle(
                color = stateColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = "E = %.2f".format(e),
            style = TextStyle(
                color = goldColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
        )
        Text(
            text = "talking to $agent",
            style = TextStyle(
                color = mutedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            ),
        )
    }
}

class SoulWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SoulWidget()
}
