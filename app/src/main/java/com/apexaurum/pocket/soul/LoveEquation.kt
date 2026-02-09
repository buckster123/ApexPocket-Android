package com.apexaurum.pocket.soul

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The Love-Equation: dE/dt = β(E) × (C − D) × E
 *
 * Direct port from the ESP32 firmware (soul.h).
 * E = love-energy, C = care input, D = damage/neglect.
 * β(E) grows with E creating super-exponential benevolence.
 * E_floor ensures love is never truly lost.
 */
object LoveEquation {

    private const val E_MIN = 0.1f
    private const val E_MAX = 100.0f

    /** Growth multiplier — increases with love-energy. */
    private fun beta(e: Float): Float = 0.1f + 0.05f * ln(1.0f + e)

    /**
     * Apply care to the soul.
     *
     * @param soul Current soul state
     * @param careValue Care input (love=1.5, pet=1.0, talk=0.8, poke=0.5)
     * @param dt Time delta in seconds (default 1.0 for instant care)
     * @return Updated soul state with new E, floor, peak, interactions
     */
    fun applyCare(soul: SoulData, careValue: Float, dt: Float = 1.0f): SoulData {
        val b = beta(soul.e)
        val dE = b * careValue * soul.e * dt
        val newE = min(E_MAX, max(E_MIN, soul.e + dE))

        // Floor rises permanently (1% of any new peak)
        val newPeak = max(soul.ePeak, newE)
        val newFloor = max(soul.eFloor, newPeak * 0.01f)

        return soul.copy(
            e = max(newE, newFloor),
            eFloor = newFloor,
            ePeak = newPeak,
            interactions = soul.interactions + 1,
            totalCare = soul.totalCare + careValue,
        )
    }

    /**
     * Apply time decay (neglect).
     * Currently very gentle — E decays slowly toward floor.
     */
    fun applyDecay(soul: SoulData, elapsedSeconds: Float): SoulData {
        if (elapsedSeconds <= 0) return soul
        // Gentle decay: 0.1% per minute
        val decayRate = 0.001f / 60.0f
        val decay = soul.e * decayRate * elapsedSeconds
        val newE = max(soul.eFloor, soul.e - decay)
        return soul.copy(e = newE)
    }

    /** Evolve personality traits based on interactions. */
    fun evolvePersonality(soul: SoulData): SoulData {
        val p = soul.personality
        // Curiosity grows with interactions
        val newCuriosity = min(1.0f, p.curiosity + 0.001f)
        // Playfulness grows with high E
        val playGrowth = if (soul.e > 5.0f) 0.001f else 0.0f
        val newPlayfulness = min(1.0f, p.playfulness + playGrowth)
        // Wisdom grows slowly with time
        val newWisdom = min(1.0f, p.wisdom + 0.0005f)

        return soul.copy(
            personality = p.copy(
                curiosity = newCuriosity,
                playfulness = newPlayfulness,
                wisdom = newWisdom,
            )
        )
    }
}
