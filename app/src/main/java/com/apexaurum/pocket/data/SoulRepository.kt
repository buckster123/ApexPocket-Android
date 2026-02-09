package com.apexaurum.pocket.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.apexaurum.pocket.soul.Personality
import com.apexaurum.pocket.soul.SoulData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_soul")

/**
 * Local persistence for soul state + device token.
 * Uses DataStore (modern SharedPreferences replacement).
 */
class SoulRepository(private val context: Context) {

    private object Keys {
        val SOUL_E = floatPreferencesKey("soul_e")
        val SOUL_E_FLOOR = floatPreferencesKey("soul_e_floor")
        val SOUL_E_PEAK = floatPreferencesKey("soul_e_peak")
        val INTERACTIONS = longPreferencesKey("interactions")
        val TOTAL_CARE = floatPreferencesKey("total_care")
        val CURIOSITY = floatPreferencesKey("personality_curiosity")
        val PLAYFULNESS = floatPreferencesKey("personality_playfulness")
        val WISDOM = floatPreferencesKey("personality_wisdom")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        val AUTO_READ_ENABLED = booleanPreferencesKey("auto_read_enabled")
    }

    /** Observe soul state as a Flow (reactive). */
    val soulFlow: Flow<SoulData> = context.dataStore.data.map { prefs ->
        SoulData(
            e = prefs[Keys.SOUL_E] ?: 1.0f,
            eFloor = prefs[Keys.SOUL_E_FLOOR] ?: 0.5f,
            ePeak = prefs[Keys.SOUL_E_PEAK] ?: 1.0f,
            interactions = prefs[Keys.INTERACTIONS] ?: 0L,
            totalCare = prefs[Keys.TOTAL_CARE] ?: 0.0f,
            personality = Personality(
                curiosity = prefs[Keys.CURIOSITY] ?: 0.3f,
                playfulness = prefs[Keys.PLAYFULNESS] ?: 0.3f,
                wisdom = prefs[Keys.WISDOM] ?: 0.1f,
            ),
            selectedAgentId = prefs[Keys.SELECTED_AGENT] ?: "AZOTH",
            deviceName = prefs[Keys.DEVICE_NAME] ?: "ApexPocket App",
        )
    }

    /** Save soul state to local storage. */
    suspend fun saveSoul(soul: SoulData) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUL_E] = soul.e
            prefs[Keys.SOUL_E_FLOOR] = soul.eFloor
            prefs[Keys.SOUL_E_PEAK] = soul.ePeak
            prefs[Keys.INTERACTIONS] = soul.interactions
            prefs[Keys.TOTAL_CARE] = soul.totalCare
            prefs[Keys.CURIOSITY] = soul.personality.curiosity
            prefs[Keys.PLAYFULNESS] = soul.personality.playfulness
            prefs[Keys.WISDOM] = soul.personality.wisdom
            prefs[Keys.SELECTED_AGENT] = soul.selectedAgentId
            prefs[Keys.DEVICE_NAME] = soul.deviceName
        }
    }

    /** Get device token (nullable — not paired until set). */
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEVICE_TOKEN]
    }

    /** Store device token after pairing. */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_TOKEN] = token
        }
    }

    /** Clear token (un-pair). */
    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.DEVICE_TOKEN)
        }
    }

    /** Last sync timestamp. */
    val lastSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_MS] ?: 0L
    }

    suspend fun updateLastSync() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_MS] = System.currentTimeMillis()
        }
    }

    /** Auto-read (TTS) preference as a Flow. */
    val autoReadFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_READ_ENABLED] ?: false
    }

    /** Save auto-read toggle state. */
    suspend fun saveAutoRead(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_READ_ENABLED] = enabled
        }
    }
}
