package com.apexaurum.pocket.dream

import android.graphics.Color
import android.graphics.Typeface
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.apexaurum.pocket.R
import com.apexaurum.pocket.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ambient mode screensaver — shows the soul face with current state on a dark screen.
 * Reads from DataStore (same pattern as the widget). No Compose — pure View-based.
 */
class SoulDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        val prefs = runBlocking { dataStore.data.first() }
        val e = prefs[floatPreferencesKey("soul_e")] ?: 1.0f
        val agent = prefs[stringPreferencesKey("selected_agent")] ?: "AZOTH"
        val expression = prefs[stringPreferencesKey("widget_expression")] ?: deriveExpression(e)
        val stateName = deriveState(e)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        setContentView(buildLayout(expression, stateName, e, agent, time))
    }

    private fun buildLayout(
        expression: String,
        stateName: String,
        e: Float,
        agent: String,
        time: String,
    ): FrameLayout {
        val dp = { value: Int -> TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt() }

        val sp = { value: Float -> TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
        ) }

        val gold = Color.parseColor("#FFD700")
        val goldDim = Color.parseColor("#B8960F")
        val background = Color.parseColor("#0A0A0F")

        val container = FrameLayout(this).apply {
            setBackgroundColor(background)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }

        // Soul face
        column.addView(ImageView(this).apply {
            setImageResource(faceDrawable(expression))
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                bottomMargin = dp(16)
            }
        })

        // State name
        column.addView(TextView(this).apply {
            text = stateName
            setTextColor(stateColor(stateName))
            textSize = 20f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        // E value + agent
        column.addView(TextView(this).apply {
            text = "E = %.2f  |  $agent".format(e)
            setTextColor(goldDim)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        })

        // Clock
        column.addView(TextView(this).apply {
            text = time
            setTextColor(gold)
            textSize = 48f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(24) }
        })

        container.addView(column)
        return container
    }

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

    private fun stateColor(state: String): Int = when (state) {
        "PROTECTING" -> Color.parseColor("#4A4A5A")
        "GUARDED" -> Color.parseColor("#6B7B9B")
        "TENDER" -> Color.parseColor("#8BC34A")
        "WARM" -> Color.parseColor("#FFB74D")
        "FLOURISHING" -> Color.parseColor("#4FC3F7")
        "RADIANT" -> Color.parseColor("#FFD700")
        "TRANSCENDENT" -> Color.parseColor("#E8B4FF")
        else -> Color.parseColor("#FFD700")
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
