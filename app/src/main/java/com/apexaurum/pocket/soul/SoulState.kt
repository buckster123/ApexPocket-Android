package com.apexaurum.pocket.soul

import kotlinx.serialization.Serializable

/**
 * The Seven Affective States — mirrors the ESP32 firmware exactly.
 * Each state is determined by the current Love-energy (E) value.
 */
enum class AffectiveState(val minE: Float, val expression: Expression) {
    PROTECTING(0.0f, Expression.SLEEPING),
    GUARDED(0.5f, Expression.SAD),
    TENDER(1.0f, Expression.CURIOUS),
    WARM(2.0f, Expression.NEUTRAL),
    FLOURISHING(5.0f, Expression.HAPPY),
    RADIANT(12.0f, Expression.EXCITED),
    TRANSCENDENT(30.0f, Expression.LOVE);

    companion object {
        fun fromE(e: Float): AffectiveState =
            entries.lastOrNull { e >= it.minE } ?: PROTECTING
    }
}

/** Visual expressions for the animated face. */
enum class Expression {
    NEUTRAL, HAPPY, SAD, EXCITED, CURIOUS, LOVE, THINKING, SLEEPING
}

/** Personality traits that evolve over time. */
@Serializable
data class Personality(
    val curiosity: Float = 0.3f,
    val playfulness: Float = 0.3f,
    val wisdom: Float = 0.1f,
)

/** The full soul state — persisted locally and synced to cloud. */
@Serializable
data class SoulData(
    val e: Float = 1.0f,
    val eFloor: Float = 0.5f,
    val ePeak: Float = 1.0f,
    val interactions: Long = 0,
    val totalCare: Float = 0.0f,
    val personality: Personality = Personality(),
    val selectedAgentId: String = "AZOTH",
    val deviceName: String = "ApexPocket App",
) {
    val state: AffectiveState get() = AffectiveState.fromE(e)
    val expression: Expression get() = state.expression
}
