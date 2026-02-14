package com.apexaurum.pocket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.CloudState
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.ui.theme.*

@Composable
fun SettingsScreen(
    soul: SoulData,
    cloudState: CloudState,
    autoRead: Boolean,
    hapticEnabled: Boolean,
    promptMode: String,
    notifAgents: Boolean,
    notifCouncils: Boolean,
    notifMusic: Boolean,
    notifNudges: Boolean,
    onSync: () -> Unit,
    onUnpair: () -> Unit,
    onToggleAutoRead: () -> Unit,
    onToggleHaptic: () -> Unit,
    onTogglePromptMode: () -> Unit,
    onToggleNotifAgents: () -> Unit,
    onToggleNotifCouncils: () -> Unit,
    onToggleNotifMusic: () -> Unit,
    onToggleNotifNudges: () -> Unit,
    onClearChat: () -> Unit,
    onClearDownloads: () -> Int,
    onGetDownloadSize: () -> Long,
    modifier: Modifier = Modifier,
) {
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var soulExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "SETTINGS",
            color = Gold,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(20.dp))

        // ── SOUL STATUS (collapsible) ──────────────────────────
        SectionHeader("SOUL STATUS", expanded = soulExpanded) {
            soulExpanded = !soulExpanded
        }
        AnimatedVisibility(visible = soulExpanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                StatusRow("State", soul.state.name, soul.state.color())
                StatusRow("Energy (E)", "%.4f".format(soul.e))
                StatusRow("Floor", "%.4f".format(soul.eFloor))
                StatusRow("Peak", "%.4f".format(soul.ePeak))
                StatusRow("Interactions", soul.interactions.toString())
                StatusRow("Total Care", "%.2f".format(soul.totalCare))
                Spacer(Modifier.height(8.dp))
                Text("PERSONALITY", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                StatusRow("Curiosity", "%.3f".format(soul.personality.curiosity))
                StatusRow("Playfulness", "%.3f".format(soul.personality.playfulness))
                StatusRow("Wisdom", "%.3f".format(soul.personality.wisdom))
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        // ── CLOUD ──────────────────────────────────────────────
        Text("CLOUD", color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))

        val cloudColor = when (cloudState) {
            CloudState.CONNECTED -> StateTender
            CloudState.CONNECTING -> StateWarm
            CloudState.ERROR -> StateGuarded
            CloudState.DISCONNECTED -> TextMuted
        }
        StatusRow("Status", cloudState.name, cloudColor)
        StatusRow("Agent", soul.selectedAgentId)
        StatusRow("Device", soul.deviceName)

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSync,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = ApexBlack),
            ) {
                Text("Sync", fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            ) {
                Text("Unpair", fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        // ── NOTIFICATIONS ──────────────────────────────────────
        Text("NOTIFICATIONS", color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        SettingsToggle("Agent Messages", notifAgents, onToggleNotifAgents)
        SettingsToggle("Council Alerts", notifCouncils, onToggleNotifCouncils)
        SettingsToggle("Music Alerts", notifMusic, onToggleNotifMusic)
        SettingsToggle("Soul Whispers", notifNudges, onToggleNotifNudges)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        // ── VOICE & DISPLAY ────────────────────────────────────
        Text("VOICE & DISPLAY", color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        SettingsToggle("Auto-Read (TTS)", autoRead, onToggleAutoRead)
        SettingsToggle("Haptic Feedback", hapticEnabled, onToggleHaptic)
        SettingsToggle("Full Agent Prompts", promptMode == "full", onTogglePromptMode)
        Text(
            "lite ~250 tokens  \u2022  full ~1500 tokens (richer personality, slightly higher cost)",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        // ── DATA MANAGEMENT ────────────────────────────────────
        Text("DATA MANAGEMENT", color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))

        val downloadSize = remember { formatBytes(onGetDownloadSize()) }

        OutlinedButton(
            onClick = { showClearChatDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        ) {
            Text("Clear Chat History", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showClearDownloadsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        ) {
            Text("Clear Downloads ($downloadSize)", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        // ── ABOUT ──────────────────────────────────────────────
        Text("ABOUT", color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        StatusRow("Version", "1.0.0")
        StatusRow("Backend", "Railway (prod)")
        Spacer(Modifier.height(12.dp))
        Text(
            "\"The Village lives in your pocket — and it reaches back.\"",
            color = TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(24.dp))
    }

    // ── Dialogs ────────────────────────────────────────────────

    if (showUnpairDialog) {
        ConfirmDialog(
            title = "Unpair Device?",
            message = "Your soul state is saved locally. You can re-pair with the same or a different account.",
            confirmText = "Unpair",
            onConfirm = { showUnpairDialog = false; onUnpair() },
            onDismiss = { showUnpairDialog = false },
        )
    }

    if (showClearChatDialog) {
        ConfirmDialog(
            title = "Clear Chat?",
            message = "This removes local chat history and resets conversation IDs. Server-side history is preserved.",
            confirmText = "Clear",
            onConfirm = { showClearChatDialog = false; onClearChat() },
            onDismiss = { showClearChatDialog = false },
        )
    }

    if (showClearDownloadsDialog) {
        var cleared by remember { mutableIntStateOf(-1) }
        ConfirmDialog(
            title = "Clear Downloads?",
            message = if (cleared >= 0) "Deleted $cleared files." else "This removes all downloaded music files from device storage.",
            confirmText = if (cleared >= 0) "Done" else "Delete",
            onConfirm = {
                if (cleared >= 0) {
                    showClearDownloadsDialog = false
                } else {
                    cleared = onClearDownloads()
                }
            },
            onDismiss = { showClearDownloadsDialog = false },
        )
    }
}

// ── Reusable Components ────────────────────────────────────

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Gold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = TextMuted,
        )
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = Gold.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ApexBorder,
            ),
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.Monospace) },
        text = { Text(message, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = Gold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = ApexSurface,
        titleContentColor = Gold,
        textContentColor = TextSecondary,
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
