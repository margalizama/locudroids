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
    val stationName: String = "Locudroids FM",
    /** Nombres a los que el locutor manda saludos, separados por comas. */
    val greetingsTo: String = "",
    val city: String = "",
    /** 1 = un corte cada 2 canciones, 2 = un corte al inicio de cada canción, 3 = inicio y final de cada canción. */
    val intensity: Int = 2,
    val allowTrivia: Boolean = true,
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
        val station = stringPreferencesKey("station_name")
        val greetings = stringPreferencesKey("greetings_to")
        val city = stringPreferencesKey("city")
        val intensity = intPreferencesKey("dj_intensity")
        val trivia = booleanPreferencesKey("allow_trivia")
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
            prefs[Keys.station] = s.stationName
            prefs[Keys.greetings] = s.greetingsTo
            prefs[Keys.city] = s.city
            prefs[Keys.intensity] = s.intensity
            prefs[Keys.trivia] = s.allowTrivia
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
            stationName = this[Keys.station] ?: d.stationName,
            greetingsTo = this[Keys.greetings] ?: d.greetingsTo,
            city = this[Keys.city] ?: d.city,
            intensity = this[Keys.intensity] ?: d.intensity,
            allowTrivia = this[Keys.trivia] ?: d.allowTrivia,
            speechRate = this[Keys.rate] ?: d.speechRate,
        )
    }
}
