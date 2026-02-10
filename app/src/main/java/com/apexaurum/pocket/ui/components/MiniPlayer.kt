package com.apexaurum.pocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.MusicTrack
import com.apexaurum.pocket.cloud.PlayerState
import com.apexaurum.pocket.ui.theme.*

@Composable
fun MiniPlayer(
    track: MusicTrack,
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionText = formatMs(playerState.positionMs)
    val durationText = formatMs(playerState.durationMs)
    val progress = if (playerState.durationMs > 0) {
        playerState.positionMs.toFloat() / playerState.durationMs.toFloat()
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ApexDarkSurface),
    ) {
        // Progress bar (top, spanning full width)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Gold,
            trackColor = ApexBorder,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/pause button
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(40.dp),
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        color = Gold,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Gold,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Track info + time
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = track.title ?: "Untitled",
                    color = Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = "$positionText / $durationText",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Close/stop button
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Stop",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${"%02d".format(sec)}"
}
