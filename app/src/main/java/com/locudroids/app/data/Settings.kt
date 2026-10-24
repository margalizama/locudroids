package com.locudroids.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "locudroids_settings")

data class DjSettings(
    val djEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_MODEL,
    val listenerName: String = "Margaro",
    val djName: String = "Locudroid",
    val city: String = "",
    val songsBetweenBreaks: Int = 2,
    val speechRate: Float = 1.0f,
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val djEnabled = booleanPreferencesKey("dj_enabled")
        val apiKey = stringPreferencesKey("gemini_api_key")
        val model = stringPreferencesKey("gemini_model")
        val listener = stringPreferencesKey("listener_name")
        val djName = stringPreferencesKey("dj_name")
        val city = stringPreferencesKey("city")
        val interval = intPreferencesKey("songs_between_breaks")
        val rate = floatPreferencesKey("speech_rate")
    }

    val flow: Flow<DjSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun current(): DjSettings = flow.first()

    suspend fun update(transform: (DjSettings) -> DjSettings) {
        context.settingsStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.djEnabled] = s.djEnabled
            prefs[Keys.apiKey] = s.geminiApiKey
            prefs[Keys.model] = s.geminiModel
            prefs[Keys.listener] = s.listenerName
            prefs[Keys.djName] = s.djName
            prefs[Keys.city] = s.city
            prefs[Keys.interval] = s.songsBetweenBreaks
            prefs[Keys.rate] = s.speechRate
        }
    }

    private fun Preferences.toSettings(): DjSettings {
        val d = DjSettings()
        return DjSettings(
            djEnabled = this[Keys.djEnabled] ?: d.djEnabled,
            geminiApiKey = this[Keys.apiKey] ?: d.geminiApiKey,
            geminiModel = this[Keys.model] ?: d.geminiModel,
            listenerName = this[Keys.listener] ?: d.listenerName,
            djName = this[Keys.djName] ?: d.djName,
            city = this[Keys.city] ?: d.city,
            songsBetweenBreaks = this[Keys.interval] ?: d.songsBetweenBreaks,
            speechRate = this[Keys.rate] ?: d.speechRate,
        )
    }
}
