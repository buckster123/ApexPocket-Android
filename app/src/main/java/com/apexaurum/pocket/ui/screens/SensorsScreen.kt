package com.apexaurum.pocket.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.SensorStatusResponse
import com.apexaurum.pocket.cloud.SentinelStatusResponse
import com.apexaurum.pocket.cloud.SentinelEvent
import com.apexaurum.pocket.ui.theme.*

@Composable
fun SensorsScreen(
    status: SensorStatusResponse?,
    images: Map<String, String>,
    isLoading: Boolean,
    capturing: Set<String>,
    onRefreshStatus: () -> Unit,
    onReadEnvironment: () -> Unit,
    onCapture: (String) -> Unit,
    onFullSnapshot: () -> Unit,
    // Sentinel
    sentinelStatus: SentinelStatusResponse?,
    sentinelEvents: List<SentinelEvent>,
    sentinelUnacked: Int,
    sentinelLoading: Boolean,
    sentinelSnapshot: String?,
    onSentinelToggleArm: () -> Unit,
    onSentinelLoadPreset: (String) -> Unit,
    onSentinelFetchStatus: () -> Unit,
    onSentinelFetchEvents: () -> Unit,
    onSentinelAck: (String) -> Unit,
    onSentinelAckAll: () -> Unit,
    onSentinelViewSnapshot: (String) -> Unit,
    onSentinelDismissSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = status?.online ?: false
    val readings = status?.telemetry?.readings
    val isSnapshotting = "snapshot" in capturing

    // Fetch status on first display
    LaunchedEffect(Unit) {
        onRefreshStatus()
        onSentinelFetchStatus()
        onSentinelFetchEvents()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ─── Header ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status?.deviceName ?: "SensorHead",
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
                        .background(if (online) StateFlourishing else TextMuted),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (online) "online" else "offline",
                    color = if (online) StateFlourishing else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (online && status?.uptimeS != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatUptime(status.uptimeS),
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.weight(1f))

                // Full Snapshot button
                Button(
                    onClick = onFullSnapshot,
                    enabled = online && !isSnapshotting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = ApexBlack,
                        disabledContainerColor = Gold.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (isSnapshotting) "Capturing..." else "Snapshot",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ─── Loading ────────────────────────────────────────────
        if (isLoading && status == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Connecting to SensorHead...",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            return@LazyColumn
        }

        // ─── Offline stale badge ────────────────────────────────
        if (!online && readings != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StateWarm.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = "SensorHead offline — showing cached data" +
                            (status?.telemetry?.ageS?.let { " (${formatAge(it)})" } ?: ""),
                        color = StateWarm,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ─── Environment Gauges ─────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ENVIRONMENT",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                TextButton(
                    onClick = onReadEnvironment,
                    enabled = online && "environment" !in capturing,
                ) {
                    Text(
                        text = if ("environment" in capturing) "Reading..." else "Refresh",
                        color = if (online) Gold else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (readings != null) {
            // Row 1: Temp, Humidity, Pressure
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GaugeCard(
                        value = readings.temperatureC?.let { "%.1f".format(it) } ?: "—",
                        label = "Temp \u00B0C",
                        modifier = Modifier.weight(1f),
                    )
                    GaugeCard(
                        value = readings.humidityPct?.let { "${it.toInt()}" } ?: "—",
                        label = "Humidity %",
                        modifier = Modifier.weight(1f),
                    )
                    GaugeCard(
                        value = readings.pressureHpa?.let { "${it.toInt()}" } ?: "—",
                        label = "hPa",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Row 2: IAQ, CO2, VOC
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val iaq = readings.iaq
                    GaugeCard(
                        value = iaq?.let { "${it.toInt()}" } ?: "—",
                        label = "IAQ ${iaqLabel(iaq)}",
                        valueColor = iaqColor(iaq),
                        borderColor = iaqBorderColor(iaq),
                        modifier = Modifier.weight(1f),
                    )
                    GaugeCard(
                        value = readings.co2Ppm?.let { "${it.toInt()}" } ?: "—",
                        label = "CO2 ppm",
                        modifier = Modifier.weight(1f),
                    )
                    GaugeCard(
                        value = readings.vocPpm?.let { "%.2f".format(it) } ?: "—",
                        label = "VOC ppm",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Thermal summary (if available)
            if (readings.thermalMinC != null || readings.thermalMaxC != null) {
                item {
                    Text(
                        text = "THERMAL SUMMARY",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GaugeCard(
                            value = readings.thermalMinC?.let { "%.1f".format(it) } ?: "—",
                            label = "Min \u00B0C",
                            valueColor = VajraBlue,
                            borderColor = VajraBlue.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f),
                        )
                        GaugeCard(
                            value = readings.thermalAvgC?.let { "%.1f".format(it) } ?: "—",
                            label = "Avg \u00B0C",
                            valueColor = StateWarm,
                            borderColor = StateWarm.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f),
                        )
                        GaugeCard(
                            value = readings.thermalMaxC?.let { "%.1f".format(it) } ?: "—",
                            label = "Max \u00B0C",
                            valueColor = StateTender,
                            borderColor = Color(0xFFEF4444).copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ApexSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (online) "No telemetry — tap Refresh" else "No telemetry available",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ─── Sentinel ──────────────────────────────────────────────
        item {
            SentinelCard(
                sentinelStatus = sentinelStatus,
                events = sentinelEvents,
                unackedCount = sentinelUnacked,
                isLoading = sentinelLoading,
                online = online,
                onToggleArm = onSentinelToggleArm,
                onLoadPreset = onSentinelLoadPreset,
                onFetchStatus = onSentinelFetchStatus,
                onFetchEvents = onSentinelFetchEvents,
                onAck = onSentinelAck,
                onAckAll = onSentinelAckAll,
                onViewSnapshot = onSentinelViewSnapshot,
            )
        }

        // ─── Camera Panels ──────────────────────────────────────
        item {
            Text(
                text = "CAMERAS",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            CameraPanel(
                title = "Visual",
                subtitle = "IMX500 AI",
                imageBase64 = images["visual"],
                isCapturing = "visual" in capturing,
                enabled = online && !isSnapshotting,
                onCapture = { onCapture("visual") },
            )
        }

        item {
            CameraPanel(
                title = "Night",
                subtitle = "IMX708 NoIR",
                imageBase64 = images["night"],
                isCapturing = "night" in capturing,
                enabled = online && !isSnapshotting,
                onCapture = { onCapture("night") },
            )
        }

        item {
            CameraPanel(
                title = "Thermal",
                subtitle = "MLX90640 IR",
                imageBase64 = images["thermal"],
                isCapturing = "thermal" in capturing,
                enabled = online && !isSnapshotting,
                onCapture = { onCapture("thermal") },
            )
        }
    }

    // Snapshot lightbox
    if (sentinelSnapshot != null) {
        val bitmap = remember(sentinelSnapshot) {
            try {
                val bytes = Base64.decode(sentinelSnapshot, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = onSentinelDismissSnapshot) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Sentinel snapshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

// ─── Composable Helpers ─────────────────────────────────────────────

@Composable
private fun GaugeCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    borderColor: Color = ApexBorder,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CameraPanel(
    title: String,
    subtitle: String,
    imageBase64: String?,
    isCapturing: Boolean,
    enabled: Boolean,
    onCapture: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCapture,
                    enabled = enabled && !isCapturing,
                ) {
                    Text(
                        text = if (isCapturing) "Capturing..." else "Capture",
                        color = if (enabled && !isCapturing) Gold else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(ApexBlack),
                contentAlignment = Alignment.Center,
            ) {
                if (imageBase64 != null) {
                    val bitmap = remember(imageBase64) {
                        try {
                            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "$title camera",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = "Decode error",
                            color = StateTender,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                } else if (isCapturing) {
                    Text(
                        text = "Capturing...",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    Text(
                        text = "No capture yet",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// ─── Sentinel Card ──────────────────────────────────────────────────

@Composable
private fun SentinelCard(
    sentinelStatus: SentinelStatusResponse?,
    events: List<SentinelEvent>,
    unackedCount: Int,
    isLoading: Boolean,
    online: Boolean,
    onToggleArm: () -> Unit,
    onLoadPreset: (String) -> Unit,
    onFetchStatus: () -> Unit,
    onFetchEvents: () -> Unit,
    onAck: (String) -> Unit,
    onAckAll: () -> Unit,
    onViewSnapshot: (String) -> Unit,
) {
    val armed = sentinelStatus?.armed ?: false
    val stats = sentinelStatus?.stats
    var showPresets by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        border = BorderStroke(
            1.dp,
            if (armed) Color(0xFFEF4444).copy(alpha = 0.4f) else ApexBorder.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: shield + title + badge + arm button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (armed) "\uD83D\uDEE1\uFE0F" else "\uD83D\uDEE1",
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SENTINEL",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        if (unackedCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "$unackedCount",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = if (armed) {
                            "Armed" +
                                (stats?.scanCount?.let { " \u00B7 $it scans" } ?: "") +
                                (stats?.alertCount?.let { " \u00B7 $it alerts" } ?: "")
                        } else "Disarmed",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Arm/Disarm button
                Button(
                    onClick = onToggleArm,
                    enabled = online && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (armed) Color(0xFFEF4444).copy(alpha = 0.2f) else StateFlourishing.copy(alpha = 0.2f),
                        contentColor = if (armed) Color(0xFFEF4444) else StateFlourishing,
                        disabledContainerColor = TextMuted.copy(alpha = 0.1f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = when {
                            isLoading -> "..."
                            armed -> "Disarm"
                            else -> "Arm"
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Presets row
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Preset:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                for (preset in listOf("default", "night_watch", "away_mode", "pet_watch")) {
                    Surface(
                        onClick = { onLoadPreset(preset) },
                        enabled = online && !isLoading,
                        shape = RoundedCornerShape(6.dp),
                        color = ApexBlack.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, ApexBorder.copy(alpha = 0.2f)),
                    ) {
                        Text(
                            text = preset.replace("_", " "),
                            color = Gold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Events
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RECENT EVENTS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (unackedCount > 0) {
                        TextButton(onClick = onAckAll) {
                            Text(
                                text = "Ack all",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                // Show last 5 events
                for (event in events.take(5)) {
                    SentinelEventRow(
                        event = event,
                        onAck = { onAck(event.id) },
                        onViewSnapshot = { onViewSnapshot(event.id) },
                    )
                }
            } else if (armed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Monitoring for motion...",
                    color = TextMuted.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SentinelEventRow(
    event: SentinelEvent,
    onAck: () -> Unit,
    onViewSnapshot: () -> Unit,
) {
    val icon = when (event.type) {
        "person" -> "\uD83D\uDEB6"
        "cat" -> "\uD83D\uDC31"
        "dog" -> "\uD83D\uDC36"
        "bird" -> "\uD83D\uDC26"
        else -> "\u26A1"
    }
    val typeColor = when (event.type) {
        "person" -> Color(0xFFEF4444)
        "motion" -> StateWarm
        else -> VajraBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = icon, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.type.replaceFirstChar { it.uppercase() },
                    color = typeColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                event.data?.let { d ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${d.changedPixels}px \u00B7 ${d.thermalDelta}\u00B0C",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            event.createdAt?.let { ts ->
                Text(
                    text = formatSentinelTime(ts),
                    color = TextMuted.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Snapshot button
        if (event.hasSnapshot) {
            TextButton(
                onClick = onViewSnapshot,
                contentPadding = PaddingValues(4.dp),
            ) {
                Text(text = "\uD83D\uDCF7", fontSize = 12.sp)
            }
        }

        // Ack button
        if (!event.acknowledged) {
            TextButton(
                onClick = onAck,
                contentPadding = PaddingValues(4.dp),
            ) {
                Text(
                    text = "\u2713",
                    color = StateFlourishing,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = "\u2713",
                color = TextMuted.copy(alpha = 0.3f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

private fun formatSentinelTime(isoStr: String): String {
    return try {
        val instant = java.time.Instant.parse(isoStr)
        val diff = (System.currentTimeMillis() / 1000) - instant.epochSecond
        when {
            diff < 60 -> "${diff}s ago"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> "${diff / 86400}d ago"
        }
    } catch (_: Exception) { isoStr.take(16) }
}

// ─── Utility functions ──────────────────────────────────────────────

private fun iaqLabel(iaq: Float?): String = when {
    iaq == null -> ""
    iaq <= 50 -> "Excellent"
    iaq <= 100 -> "Good"
    iaq <= 150 -> "Moderate"
    iaq <= 200 -> "Poor"
    iaq <= 300 -> "Bad"
    else -> "Hazardous"
}

private fun iaqColor(iaq: Float?): Color = when {
    iaq == null -> TextMuted
    iaq <= 50 -> StateFlourishing
    iaq <= 100 -> Color(0xFF86EFAC)
    iaq <= 150 -> StateWarm
    iaq <= 200 -> Color(0xFFFB923C)
    else -> Color(0xFFEF4444)
}

private fun iaqBorderColor(iaq: Float?): Color = when {
    iaq == null -> ApexBorder
    iaq <= 50 -> StateFlourishing.copy(alpha = 0.3f)
    iaq <= 100 -> Color(0xFF86EFAC).copy(alpha = 0.3f)
    iaq <= 150 -> StateWarm.copy(alpha = 0.3f)
    iaq <= 200 -> Color(0xFFFB923C).copy(alpha = 0.3f)
    else -> Color(0xFFEF4444).copy(alpha = 0.3f)
}

private fun formatUptime(seconds: Float): String {
    val s = seconds.toLong()
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

private fun formatAge(seconds: Float): String {
    val s = seconds.toLong()
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        else -> "${s / 3600}h ago"
    }
}
