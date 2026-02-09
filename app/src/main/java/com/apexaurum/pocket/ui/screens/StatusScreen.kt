package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.layout.*
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
fun StatusScreen(
    soul: SoulData,
    cloudState: CloudState,
    onSync: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUnpairDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            "SOUL STATUS",
            color = Gold,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(16.dp))

        StatusRow("State", soul.state.name, soul.state.color())
        StatusRow("Energy (E)", "%.4f".format(soul.e))
        StatusRow("Floor", "%.4f".format(soul.eFloor))
        StatusRow("Peak", "%.4f".format(soul.ePeak))
        StatusRow("Interactions", soul.interactions.toString())
        StatusRow("Total Care", "%.2f".format(soul.totalCare))

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        Text(
            "PERSONALITY",
            color = Gold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        StatusRow("Curiosity", "%.3f".format(soul.personality.curiosity))
        StatusRow("Playfulness", "%.3f".format(soul.personality.playfulness))
        StatusRow("Wisdom", "%.3f".format(soul.personality.wisdom))

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ApexBorder)
        Spacer(Modifier.height(16.dp))

        Text(
            "CLOUD",
            color = Gold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
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

        Spacer(Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSync,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = ApexBlack,
                ),
            ) {
                Text("Sync", fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                ),
            ) {
                Text("Unpair", fontFamily = FontFamily.Monospace)
            }
        }
    }

    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Unpair Device?", fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "Your soul state is saved locally. You can re-pair with the same or a different account.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnpairDialog = false; onUnpair() }) {
                    Text("Unpair", color = Gold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Cancel", fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = ApexSurface,
            titleContentColor = Gold,
            textContentColor = TextSecondary,
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
        Text(
            label,
            color = TextMuted,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            value,
            color = valueColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
