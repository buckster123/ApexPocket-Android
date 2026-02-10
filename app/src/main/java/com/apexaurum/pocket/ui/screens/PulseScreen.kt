package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.VillageEvent
import com.apexaurum.pocket.ui.theme.*

@Composable
fun PulseScreen(
    events: List<VillageEvent>,
    isConnected: Boolean,
    onCouncilsClick: () -> Unit = {},
    onMusicClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Village Pulse",
                color = Gold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) StateFlourishing else TextMuted),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isConnected) "live" else "offline",
                color = if (isConnected) StateFlourishing else TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Gold.copy(alpha = 0.15f),
                modifier = Modifier.clickable(onClick = onMusicClick),
            ) {
                Text(
                    "Music",
                    color = Gold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ElysianViolet.copy(alpha = 0.15f),
                modifier = Modifier.clickable(onClick = onCouncilsClick),
            ) {
                Text(
                    "Councils",
                    color = ElysianViolet,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Village is quiet...",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(events, key = { "${it.timestamp}_${it.type}_${it.tool}" }) { event ->
                    PulseEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun PulseEventCard(event: VillageEvent) {
    val zoneColor = zoneColor(event.zone)
    val agentColor = agentColor(event.agentId)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zone badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = zoneColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = event.zone.replace("_", " "),
                        color = zoneColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.agentId,
                    color = agentColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (event.success != null) {
                    Text(
                        text = if (event.success) "OK" else "ERR",
                        color = if (event.success) StateFlourishing else StateTender,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = relativeTime(event.timestamp),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = eventDescription(event),
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )

            val detail = event.resultPreview ?: event.error ?: event.message
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                )
            }

            if (event.durationMs != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${event.durationMs}ms",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun eventDescription(event: VillageEvent): String = when (event.type) {
    "tool_start" -> "using ${event.tool ?: "unknown tool"}"
    "tool_complete" -> "finished ${event.tool ?: "tool"}"
    "tool_error" -> "failed ${event.tool ?: "tool"}"
    "music_complete" -> "music ready"
    "agent_thinking" -> "thinking..."
    "approval_needed" -> "needs approval"
    "input_needed" -> "needs input"
    else -> event.type
}

private fun zoneColor(zone: String): androidx.compose.ui.graphics.Color = when (zone) {
    "dj_booth" -> Gold
    "workshop" -> StateWarm
    "watchtower" -> StateFlourishing
    "library" -> VajraBlue
    "memory_garden" -> ElysianViolet
    "file_shed" -> GoldDark
    "bridge_portal" -> ElysianViolet
    else -> TextMuted
}

private fun agentColor(agentId: String): androidx.compose.ui.graphics.Color = when (agentId.uppercase()) {
    "AZOTH" -> Gold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextMuted
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 5_000 -> "now"
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}
