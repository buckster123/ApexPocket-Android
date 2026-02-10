package com.apexaurum.pocket.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.apexaurum.pocket.soul.Personality
import com.apexaurum.pocket.soul.SoulData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_soul")

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
        val LAST_INTERACTION_MS = longPreferencesKey("last_interaction_ms")
        val LAST_NUDGE_TIER = intPreferencesKey("last_nudge_tier")
        val CONVERSATION_IDS = stringPreferencesKey("conversation_ids")
        val LAST_BRIEFING_DATE = stringPreferencesKey("last_briefing_date")

        // Widget state keys (written by ViewModel, read by SoulWidget)
        val WIDGET_EXPRESSION = stringPreferencesKey("widget_expression")
        val WIDGET_MUSIC_TITLE = stringPreferencesKey("widget_music_title")
        val WIDGET_MUSIC_PLAYING = booleanPreferencesKey("widget_music_playing")
        val WIDGET_VILLAGE_TICKER = stringPreferencesKey("widget_village_ticker")
        val WIDGET_VILLAGE_AGENT = stringPreferencesKey("widget_village_agent")
        val WIDGET_PULSE_JSON = stringPreferencesKey("widget_pulse_json")
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

    /** Update last interaction timestamp and clear nudge tier. */
    suspend fun updateLastInteraction() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_INTERACTION_MS] = System.currentTimeMillis()
            prefs[Keys.LAST_NUDGE_TIER] = 0
        }
    }

    /** Last interaction timestamp flow. */
    val lastInteractionFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_INTERACTION_MS] ?: System.currentTimeMillis()
    }

    /** Last nudge tier flow. */
    val lastNudgeTierFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_NUDGE_TIER] ?: 0
    }

    /** Update nudge tier after sending a notification. */
    suspend fun updateNudgeTier(tier: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_NUDGE_TIER] = tier
        }
    }

    /** Last briefing date (ISO date string e.g. "2026-02-10"). */
    val lastBriefingDateFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_BRIEFING_DATE]
    }

    /** Mark today as briefed. */
    suspend fun updateBriefingDate(date: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_BRIEFING_DATE] = date
        }
    }

    /** Observe conversation IDs as a map of agent -> conversationId. */
    val conversationIdsFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.CONVERSATION_IDS] ?: "{}"
        try {
            Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Widget State Writers ──

    /** Write music player state for widget consumption. */
    suspend fun saveWidgetMusicState(title: String?, isPlaying: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_MUSIC_TITLE] = title ?: ""
            prefs[Keys.WIDGET_MUSIC_PLAYING] = isPlaying
        }
    }

    /** Write current expression for widget face drawable. */
    suspend fun saveWidgetExpression(expression: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_EXPRESSION] = expression
        }
    }

    /** Write latest village ticker text + agent for widget display. */
    suspend fun saveWidgetVillageTicker(text: String, agentId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_VILLAGE_TICKER] = text
            prefs[Keys.WIDGET_VILLAGE_AGENT] = agentId
        }
    }

    /** Cache village pulse JSON for background worker. */
    suspend fun saveWidgetPulseJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_PULSE_JSON] = json
        }
    }

    /** Save a conversation ID for a specific agent. */
    suspend fun saveConversationId(agent: String, conversationId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.CONVERSATION_IDS] ?: "{}"
            val existing = try {
                Json.parseToJsonElement(raw).jsonObject.toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
            existing[agent] = JsonPrimitive(conversationId)
            prefs[Keys.CONVERSATION_IDS] = JsonObject(existing).toString()
        }
    }
}
