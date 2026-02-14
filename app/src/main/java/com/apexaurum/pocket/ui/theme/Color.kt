package com.apexaurum.pocket.ui.theme

import androidx.compose.ui.graphics.Color

// ApexAurum brand palette
val Gold = Color(0xFFFFD700)
val GoldDark = Color(0xFFB8960F)
val GoldLight = Color(0xFFFFE44D)

// Background tiers (dark theme)
val ApexBlack = Color(0xFF0A0A0F)
val ApexDarkSurface = Color(0xFF111118)
val ApexSurface = Color(0xFF1A1A24)
val ApexBorder = Color(0xFF2A2A3A)

// Agent colors
val AzothGold = Color(0xFFFFD700)
val ElysianViolet = Color(0xFFE8B4FF)
val VajraBlue = Color(0xFF4FC3F7)
val KetherWhite = Color(0xFFFFFFFF)

// State colors (matching soul states)
val StateProtecting = Color(0xFF4A4A5A)    // Muted gray
val StateGuarded = Color(0xFF6B7B9B)       // Steely blue
val StateTender = Color(0xFF8BC34A)        // Soft green
val StateWarm = Color(0xFFFFB74D)          // Amber
val StateFlourishing = Color(0xFF4FC3F7)   // Sky blue
val StateRadiant = Color(0xFFFFD700)       // Gold
val StateTranscendent = Color(0xFFE8B4FF)  // Violet

// Text
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF9E9E9E)
val TextMuted = Color(0xFF616161)

// ── Canonical agent color mapping ──

/** Agent identity color — single source of truth for all Compose UI. */
fun agentColor(agentId: String): Color = when (agentId.uppercase()) {
    "AZOTH" -> AzothGold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextPrimary
}
