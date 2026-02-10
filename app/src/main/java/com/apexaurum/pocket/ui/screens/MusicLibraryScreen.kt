package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.MusicTrack
import com.apexaurum.pocket.ui.theme.*

@Composable
fun MusicLibraryScreen(
    tracks: List<MusicTrack>,
    isLoading: Boolean,
    total: Int,
    totalDuration: Float,
    searchQuery: String,
    favoritesOnly: Boolean,
    onSearchChange: (String) -> Unit,
    onFavoritesToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onDownloadTrack: (MusicTrack) -> Unit,
    downloads: Map<String, DownloadState> = emptyMap(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextMuted)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Music",
                color = Gold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Gold)
            }
        }

        // Stats row
        if (total > 0) {
            val durationMin = (totalDuration / 60).toInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = "$total tracks",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (durationMin > 0) {
                    Text(
                        text = " \u00B7 ${durationMin}m total",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = {
                Text("Search tracks...", color = TextMuted, fontFamily = FontFamily.Monospace)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = ApexBorder,
                cursorColor = Gold,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            singleLine = true,
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavoritesToggle(!favoritesOnly) },
                label = {
                    Text(
                        "Favorites",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold.copy(alpha = 0.15f),
                    selectedLabelColor = Gold,
                    selectedLeadingIconColor = Gold,
                    containerColor = ApexSurface,
                    labelColor = TextMuted,
                    iconColor = TextMuted,
                ),
            )
        }

        // Loading
        if (isLoading && tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp))
            }
        } else if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No tracks found" else "No music yet",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    MusicTrackCard(
                        track = track,
                        downloadState = downloads[track.id],
                        onPlay = { onPlayTrack(track) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onDownload = { onDownloadTrack(track) },
                    )
                }
            }
        }
    }
}

enum class DownloadState { DOWNLOADING, COMPLETE, FAILED }

@Composable
private fun MusicTrackCard(
    track: MusicTrack,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    val isCompleted = track.status == "completed" || track.status == "complete"
    val statusColor = when (track.status) {
        "completed", "complete" -> StateFlourishing
        "generating", "processing" -> Gold
        "pending", "queued" -> TextMuted
        "failed", "error" -> MaterialTheme.colorScheme.error
        else -> TextMuted
    }

    val durationText = if (track.duration != null && track.duration > 0) {
        val mins = (track.duration / 60).toInt()
        val secs = (track.duration % 60).toInt()
        "${mins}:${"%02d".format(secs)}"
    } else ""

    val agentColor = when (track.agentId?.uppercase()) {
        "AZOTH" -> Gold
        "ELYSIAN" -> ElysianViolet
        "VAJRA" -> VajraBlue
        "KETHER" -> KetherWhite
        else -> TextMuted
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isCompleted) { onPlay() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCompleted) Gold.copy(alpha = 0.15f) else ApexDarkSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Text(
                        text = "\u25B6",
                        color = Gold,
                        fontSize = 18.sp,
                    )
                } else {
                    when (track.status) {
                        "generating", "processing" -> CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        else -> Text(
                            text = "\u23F8",
                            color = TextMuted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: "Untitled",
                    color = Gold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.agentId != null) {
                        Text(
                            text = track.agentId,
                            color = agentColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = " \u00B7 ",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    if (!isCompleted) {
                        Text(
                            text = track.status,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else if (track.createdAt != null) {
                        Text(
                            text = formatRelativeDate(track.createdAt),
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                if (track.prompt.isNotBlank()) {
                    Text(
                        text = track.prompt,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }

            // Duration
            if (durationText.isNotBlank()) {
                Text(
                    text = durationText,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // Download button (completed tracks only)
            if (isCompleted) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp),
                ) {
                    when (downloadState) {
                        DownloadState.DOWNLOADING -> CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        DownloadState.COMPLETE -> Icon(
                            Icons.Default.CheckCircle, "Downloaded",
                            tint = StateFlourishing,
                            modifier = Modifier.size(18.dp),
                        )
                        DownloadState.FAILED -> Icon(
                            Icons.Default.ErrorOutline, "Download failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        null -> Icon(
                            Icons.Default.Download, "Download",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Favorite star
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (track.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite",
                    tint = if (track.favorite) Gold else TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatRelativeDate(isoDate: String): String {
    return try {
        val instant = java.time.Instant.parse(isoDate)
        val diff = java.time.Duration.between(instant, java.time.Instant.now())
        when {
            diff.toMinutes() < 60 -> "${diff.toMinutes()}m ago"
            diff.toHours() < 24 -> "${diff.toHours()}h ago"
            diff.toDays() < 7 -> "${diff.toDays()}d ago"
            else -> isoDate.take(10)
        }
    } catch (_: Exception) {
        isoDate.take(10)
    }
}
