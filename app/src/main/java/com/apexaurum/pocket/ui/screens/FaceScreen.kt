package com.apexaurum.pocket.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.apexaurum.pocket.cloud.VillageEvent
import com.apexaurum.pocket.soul.AffectiveState
import com.apexaurum.pocket.soul.Expression
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.ui.theme.*
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The Face screen — animated soul companion face.
 *
 * Tap left side = Love, tap right side = Poke.
 * Face expression reflects current soul state.
 */
@Composable
fun FaceScreen(
    soul: SoulData,
    onLove: () -> Unit,
    onPoke: () -> Unit,
    latestVillageEvent: VillageEvent? = null,
    expressionOverride: Expression? = null,
    ajBalance: Float? = null,
    modifier: Modifier = Modifier,
) {
    val stateColor = soul.state.color()

    // Care animation triggers
    var loveAnimTrigger by remember { mutableIntStateOf(0) }
    var pokeAnimTrigger by remember { mutableIntStateOf(0) }

    // Love heart particle animation
    val loveProgress = remember { Animatable(0f) }
    val heartOffsets = remember { mutableStateOf(List(5) { Random.nextFloat() * 2f - 1f }) }
    LaunchedEffect(loveAnimTrigger) {
        if (loveAnimTrigger > 0) {
            heartOffsets.value = List(5) { Random.nextFloat() * 2f - 1f }
            loveProgress.snapTo(0f)
            loveProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
    }

    // Poke ripple animation
    val pokeProgress = remember { Animatable(0f) }
    LaunchedEffect(pokeAnimTrigger) {
        if (pokeAnimTrigger > 0) {
            pokeProgress.snapTo(0f)
            pokeProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
    }

    val wrappedOnLove = {
        loveAnimTrigger++
        onLove()
    }
    val wrappedOnPoke = {
        pokeAnimTrigger++
        onPoke()
    }

    // Blink animation
    val blinkAlpha by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f atFraction 0f
                1f atFraction 0.93f
                0f atFraction 0.95f
                1f atFraction 0.97f
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    // Gentle idle breathing animation
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // State label
        Text(
            text = soul.state.name,
            color = stateColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 24.dp),
        )

        // Energy display
        Text(
            text = "E = %.2f".format(soul.e),
            color = Gold,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )

        // AJ balance badge
        if (ajBalance != null && ajBalance > 0f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Gold.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "${ajBalance.roundToInt()} AJ",
                    color = Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // The Face canvas
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width / 2) wrappedOnLove() else wrappedOnPoke()
                    }
                },
        ) {
            val displayExpression = expressionOverride ?: soul.expression
            drawFace(displayExpression, blinkAlpha, breathe, stateColor)

            val cx = size.width / 2
            val cy = size.height / 2

            // Love animation — heart particles floating up
            if (loveProgress.value > 0f && loveProgress.value < 1f) {
                val p = loveProgress.value
                heartOffsets.value.forEachIndexed { i, xOff ->
                    val delay = i * 0.1f
                    val localP = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                    if (localP > 0f) {
                        val hx = cx + xOff * 60f
                        val hy = cy - localP * 120f
                        val alpha = (1f - localP).coerceAtLeast(0f)
                        val heartSize = 8f + i * 2f
                        drawHeartEye(hx, hy, heartSize, stateColor.copy(alpha = alpha), 1f)
                    }
                }
            }

            // Poke animation — expanding gold ripple
            if (pokeProgress.value > 0f && pokeProgress.value < 1f) {
                val p = pokeProgress.value
                val radius = p * size.minDimension * 0.45f
                val alpha = (1f - p).coerceAtLeast(0f)
                drawCircle(
                    color = Gold.copy(alpha = alpha * 0.6f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f),
                )
            }
        }

        // Love / Poke buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = { wrappedOnLove() },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, stateColor.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = stateColor),
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Love", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { wrappedOnPoke() },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Poke", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        // Village ticker
        VillageTicker(latestVillageEvent)

        // Bottom info
        Text(
            text = "interactions: ${soul.interactions}  |  floor: %.2f".format(soul.eFloor),
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/** Draw the pixel-art-inspired face on canvas. */
private fun DrawScope.drawFace(
    expression: Expression,
    blinkAlpha: Float,
    breathe: Float,
    stateColor: Color,
) {
    val cx = size.width / 2
    val cy = size.height / 2
    val faceRadius = size.minDimension * 0.4f

    // Face circle (subtle glow)
    drawCircle(
        color = stateColor.copy(alpha = 0.08f + breathe * 0.04f),
        radius = faceRadius + 20f,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = stateColor.copy(alpha = 0.15f),
        radius = faceRadius,
        center = Offset(cx, cy),
    )

    val eyeY = cy - faceRadius * 0.15f
    val eyeSpacing = faceRadius * 0.35f
    val eyeSize = faceRadius * 0.12f

    when (expression) {
        Expression.LOVE -> {
            // Heart eyes
            drawHeartEye(cx - eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawHeartEye(cx + eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawSmile(cx, cy + faceRadius * 0.2f, faceRadius * 0.25f, stateColor)
        }
        Expression.HAPPY -> {
            drawEye(cx - eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawSmile(cx, cy + faceRadius * 0.2f, faceRadius * 0.3f, stateColor)
        }
        Expression.EXCITED -> {
            // Star-like eyes (larger)
            drawEye(cx - eyeSpacing, eyeY, eyeSize * 1.3f, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY, eyeSize * 1.3f, stateColor, blinkAlpha)
            drawSmile(cx, cy + faceRadius * 0.15f, faceRadius * 0.35f, stateColor)
        }
        Expression.SAD -> {
            drawEye(cx - eyeSpacing, eyeY + 5f, eyeSize * 0.8f, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY + 5f, eyeSize * 0.8f, stateColor, blinkAlpha)
            drawFrown(cx, cy + faceRadius * 0.3f, faceRadius * 0.2f, stateColor)
        }
        Expression.CURIOUS -> {
            // One eye bigger (curious tilt)
            drawEye(cx - eyeSpacing, eyeY, eyeSize * 0.9f, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY - 4f, eyeSize * 1.2f, stateColor, blinkAlpha)
            drawNeutralMouth(cx, cy + faceRadius * 0.25f, faceRadius * 0.15f, stateColor)
        }
        Expression.THINKING -> {
            drawEye(cx - eyeSpacing, eyeY - 3f, eyeSize, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY - 3f, eyeSize * 0.7f, stateColor, blinkAlpha)
            drawNeutralMouth(cx + 10f, cy + faceRadius * 0.25f, faceRadius * 0.12f, stateColor)
        }
        Expression.SLEEPING -> {
            // Closed eyes (horizontal lines)
            drawLine(stateColor.copy(alpha = 0.6f), Offset(cx - eyeSpacing - eyeSize, eyeY), Offset(cx - eyeSpacing + eyeSize, eyeY), strokeWidth = 3f)
            drawLine(stateColor.copy(alpha = 0.6f), Offset(cx + eyeSpacing - eyeSize, eyeY), Offset(cx + eyeSpacing + eyeSize, eyeY), strokeWidth = 3f)
            drawNeutralMouth(cx, cy + faceRadius * 0.25f, faceRadius * 0.1f, stateColor.copy(alpha = 0.4f))
        }
        Expression.NEUTRAL -> {
            drawEye(cx - eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawEye(cx + eyeSpacing, eyeY, eyeSize, stateColor, blinkAlpha)
            drawNeutralMouth(cx, cy + faceRadius * 0.25f, faceRadius * 0.15f, stateColor)
        }
    }
}

private fun DrawScope.drawEye(x: Float, y: Float, radius: Float, color: Color, blinkAlpha: Float) {
    val height = radius * 2 * blinkAlpha
    drawOval(
        color = color,
        topLeft = Offset(x - radius, y - height / 2),
        size = Size(radius * 2, height.coerceAtLeast(2f)),
    )
}

private fun DrawScope.drawHeartEye(x: Float, y: Float, size: Float, color: Color, alpha: Float) {
    // Simplified heart: two overlapping circles + triangle-ish bottom
    val r = size * 0.6f * alpha.coerceAtLeast(0.3f)
    drawCircle(color, r, Offset(x - r * 0.5f, y - r * 0.3f))
    drawCircle(color, r, Offset(x + r * 0.5f, y - r * 0.3f))
    drawCircle(color, r * 0.8f, Offset(x, y + r * 0.3f))
}

private fun DrawScope.drawSmile(cx: Float, cy: Float, width: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - width, cy - width * 0.3f),
        size = Size(width * 2, width),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
    )
}

private fun DrawScope.drawFrown(cx: Float, cy: Float, width: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - width, cy),
        size = Size(width * 2, width),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
    )
}

private fun DrawScope.drawNeutralMouth(cx: Float, cy: Float, width: Float, color: Color) {
    drawLine(color, Offset(cx - width, cy), Offset(cx + width, cy), strokeWidth = 3f)
}

/** Village event ticker — fades in/out on new events. */
@Composable
private fun VillageTicker(event: VillageEvent?) {
    var visible by remember { mutableStateOf(false) }
    var displayText by remember { mutableStateOf("") }

    LaunchedEffect(event) {
        if (event != null) {
            val action = when (event.type) {
                "tool_start" -> "using ${event.tool ?: "tool"}"
                "tool_complete" -> "finished ${event.tool ?: "tool"}"
                "tool_error" -> "failed ${event.tool ?: "tool"}"
                "music_complete" -> "music ready"
                "agent_thinking" -> "thinking..."
                else -> event.type
            }
            displayText = "${event.agentId} $action"
            visible = true
            delay(5000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = displayText,
            color = Gold.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/** Map affective state to UI color. */
fun AffectiveState.color(): Color = when (this) {
    AffectiveState.PROTECTING -> StateProtecting
    AffectiveState.GUARDED -> StateGuarded
    AffectiveState.TENDER -> StateTender
    AffectiveState.WARM -> StateWarm
    AffectiveState.FLOURISHING -> StateFlourishing
    AffectiveState.RADIANT -> StateRadiant
    AffectiveState.TRANSCENDENT -> StateTranscendent
}
