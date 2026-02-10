package com.apexaurum.pocket.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.apexaurum.pocket.R
import com.apexaurum.pocket.data.dataStore
import kotlinx.coroutines.flow.first

class SoulWidget : GlanceAppWidget() {

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(SoulWidget::class.java)
            ids.forEach { id -> SoulWidget().update(context, id) }
        }

        private val COMPACT = DpSize(180.dp, 60.dp)
        private val MEDIUM = DpSize(220.dp, 100.dp)
        private val LARGE = DpSize(280.dp, 180.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()

        val e = prefs[floatPreferencesKey("soul_e")] ?: 1.0f
        val agent = prefs[stringPreferencesKey("selected_agent")] ?: "AZOTH"
        val expression = prefs[stringPreferencesKey("widget_expression")] ?: deriveExpression(e)
        val musicTitle = prefs[stringPreferencesKey("widget_music_title")] ?: ""
        val musicPlaying = prefs[booleanPreferencesKey("widget_music_playing")] ?: false
        val villageTicker = prefs[stringPreferencesKey("widget_village_ticker")] ?: ""
        val villageAgent = prefs[stringPreferencesKey("widget_village_agent")] ?: ""
        val stateName = deriveState(e)

        provideContent {
            val size = LocalSize.current
            when {
                size.width >= LARGE.width && size.height >= LARGE.height ->
                    LargeLayout(stateName, e, agent, expression, musicTitle, musicPlaying, villageTicker, villageAgent)
                size.width >= MEDIUM.width && size.height >= MEDIUM.height ->
                    MediumLayout(stateName, e, agent, expression, musicTitle, musicPlaying)
                else ->
                    CompactLayout(stateName, e, agent)
            }
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

    private fun deriveExpression(e: Float): String = when {
        e >= 30.0f -> "LOVE"
        e >= 12.0f -> "EXCITED"
        e >= 5.0f -> "HAPPY"
        e >= 2.0f -> "NEUTRAL"
        e >= 1.0f -> "CURIOUS"
        e >= 0.5f -> "SAD"
        else -> "SLEEPING"
    }
}

// ── Compact Layout (2×1) ──

@Composable
private fun CompactLayout(state: String, e: Float, agent: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(10.dp)
            .clickable(actionRunCallback<OpenTabAction>(
                actionParametersOf(OpenTabAction.TAB_KEY to "face")
            )),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state,
                style = TextStyle(
                    color = stateColor(state),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
            Text(
                text = "E = %.2f  |  $agent".format(e),
                style = TextStyle(
                    color = GOLD,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

// ── Medium Layout (3×2) ──

@Composable
private fun MediumLayout(
    state: String, e: Float, agent: String,
    expression: String, musicTitle: String, musicPlaying: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(10.dp),
    ) {
        // Top: face + state info
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(faceDrawable(expression)),
                contentDescription = "Soul",
                modifier = GlanceModifier.size(40.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state,
                    style = TextStyle(
                        color = stateColor(state),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
                Text(
                    text = "E = %.2f  |  $agent".format(e),
                    style = TextStyle(
                        color = GOLD,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Music card with play/pause
        if (musicTitle.isNotEmpty()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = "Music",
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = musicTitle,
                    style = TextStyle(
                        color = GOLD,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    provider = ImageProvider(
                        if (musicPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    ),
                    contentDescription = if (musicPlaying) "Pause" else "Play",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<TogglePlayAction>()),
                )
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Quick actions
        QuickActionsRow()
    }
}

// ── Large Layout (4×3) ──

@Composable
private fun LargeLayout(
    state: String, e: Float, agent: String, expression: String,
    musicTitle: String, musicPlaying: Boolean,
    villageTicker: String, villageAgent: String,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(12.dp),
    ) {
        // Top: soul face + state card
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(faceDrawable(expression)),
                contentDescription = "Soul",
                modifier = GlanceModifier.size(56.dp),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state,
                    style = TextStyle(
                        color = stateColor(state),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    ),
                )
                Text(
                    text = "E = %.2f".format(e),
                    style = TextStyle(
                        color = GOLD,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
                Text(
                    text = "with $agent",
                    style = TextStyle(
                        color = agentColor(agent),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Music card
        if (musicTitle.isNotEmpty()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = "Music",
                    modifier = GlanceModifier.size(20.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = musicTitle,
                    style = TextStyle(
                        color = GOLD,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(
                        if (musicPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    ),
                    contentDescription = if (musicPlaying) "Pause" else "Play",
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionRunCallback<TogglePlayAction>()),
                )
            }
        } else {
            Text(
                text = "No music playing",
                style = TextStyle(
                    color = MUTED,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Village ticker
        if (villageTicker.isNotEmpty()) {
            Text(
                text = villageTicker,
                style = TextStyle(
                    color = agentColor(villageAgent),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Village is quiet",
                style = TextStyle(
                    color = MUTED,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Quick actions
        QuickActionsRow()
    }
}

// ── Shared Components ──

@Composable
private fun QuickActionsRow() {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Love tap
        Image(
            provider = ImageProvider(R.drawable.ic_widget_love),
            contentDescription = "Love",
            modifier = GlanceModifier
                .size(32.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<LoveTapAction>()),
        )
        Spacer(modifier = GlanceModifier.width(20.dp))
        // Chat
        Image(
            provider = ImageProvider(R.drawable.ic_widget_chat),
            contentDescription = "Chat",
            modifier = GlanceModifier
                .size(32.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<OpenTabAction>(
                    actionParametersOf(OpenTabAction.TAB_KEY to "chat")
                )),
        )
        Spacer(modifier = GlanceModifier.width(20.dp))
        // Pulse
        Image(
            provider = ImageProvider(R.drawable.ic_widget_pulse),
            contentDescription = "Pulse",
            modifier = GlanceModifier
                .size(32.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<OpenTabAction>(
                    actionParametersOf(OpenTabAction.TAB_KEY to "pulse")
                )),
        )
        Spacer(modifier = GlanceModifier.width(20.dp))
        // Music
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music),
            contentDescription = "Music",
            modifier = GlanceModifier
                .size(32.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<OpenTabAction>(
                    actionParametersOf(OpenTabAction.TAB_KEY to "music")
                )),
        )
    }
}

// ── Helpers ──

private fun faceDrawable(expression: String): Int = when (expression.uppercase()) {
    "LOVE" -> R.drawable.soul_face_love
    "HAPPY" -> R.drawable.soul_face_happy
    "EXCITED" -> R.drawable.soul_face_excited
    "SAD" -> R.drawable.soul_face_sad
    "CURIOUS" -> R.drawable.soul_face_curious
    "THINKING" -> R.drawable.soul_face_thinking
    "SLEEPING" -> R.drawable.soul_face_sleeping
    else -> R.drawable.soul_face_neutral
}

private fun stateColor(state: String): ColorProvider = when (state) {
    "PROTECTING" -> ColorProvider(Color(0xFF4A4A5A))
    "GUARDED" -> ColorProvider(Color(0xFF6B7B9B))
    "TENDER" -> ColorProvider(Color(0xFF8BC34A))
    "WARM" -> ColorProvider(Color(0xFFFFB74D))
    "FLOURISHING" -> ColorProvider(Color(0xFF4FC3F7))
    "RADIANT" -> ColorProvider(Color(0xFFFFD700))
    "TRANSCENDENT" -> ColorProvider(Color(0xFFE8B4FF))
    else -> ColorProvider(Color(0xFFFFD700))
}

private fun agentColor(agent: String): ColorProvider = when (agent.uppercase()) {
    "AZOTH" -> ColorProvider(Color(0xFFFFD700))
    "ELYSIAN" -> ColorProvider(Color(0xFFE8B4FF))
    "VAJRA" -> ColorProvider(Color(0xFF4FC3F7))
    "KETHER" -> ColorProvider(Color(0xFFFFFFFF))
    "COUNCIL" -> ColorProvider(Color(0xFFFFB74D))
    else -> ColorProvider(Color(0xFF616161))
}

private val GOLD = ColorProvider(Color(0xFFFFD700))
private val MUTED = ColorProvider(Color(0xFF616161))

class SoulWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SoulWidget()
}
