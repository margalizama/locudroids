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
    val ttsModel: String = DEFAULT_TTS_MODEL,
    /** Qué voz usar: [ENGINE_AUTO] Gemini y, si no puede, la voz local; [ENGINE_LOCAL] solo la local; [ENGINE_GEMINI] solo Gemini. */
    val voiceEngine: Int = ENGINE_AUTO,
    /** Último recurso, si no hay otra voz disponible: true = voz de Android (robótica); false = omitir el corte. */
    val androidFallback: Boolean = false,
    /** Velocidad de las voces locales y de Android (1.0 = normal). */
    val speechRate: Float = 1.0f,

    val listenerName: String = "Margaro",
    val stationName: String = "Locudroids FM",
    /** Nombres a los que se mandan saludos y dedicatorias, separados por comas. */
    val greetingsTo: String = "",
    val city: String = "",

    /** 1 Susurro, 2 Suave, 3 Radio, 4 Intenso, 5 Locura. */
    val level: Int = 3,
    /** 0 picante ligero, 1 doble sentido, 2 albures fuertes. */
    val spice: Int = 2,

    val segAds: Boolean = true,
    val segPromos: Boolean = true,
    val segHoroscope: Boolean = true,
    val segDedications: Boolean = true,
    val allowTrivia: Boolean = true,

    val announcers: List<Announcer> = Announcer.defaults(),
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_TTS_MODEL = "gemini-3.8-flash-tts"
        const val ENGINE_AUTO = 0
        const val ENGINE_LOCAL = 1
        const val ENGINE_GEMINI = 2
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val djEnabled = booleanPreferencesKey("dj_enabled")
        val apiKey = stringPreferencesKey("gemini_api_key")
        val model = stringPreferencesKey("gemini_model")
        val ttsModel = stringPreferencesKey("tts_model")
        val voiceEngine = intPreferencesKey("voice_engine")

        /** Ajuste de la versión anterior: false = sin voz de Gemini, se migra a [voiceEngine]. */
        val legacyGeminiVoice = booleanPreferencesKey("gemini_voice")
        val androidFallback = booleanPreferencesKey("android_fallback")
        val rate = floatPreferencesKey("speech_rate")
        val listener = stringPreferencesKey("listener_name")
        val station = stringPreferencesKey("station_name")
        val greetings = stringPreferencesKey("greetings_to")
        val city = stringPreferencesKey("city")
        val level = intPreferencesKey("dj_level")
        val spice = intPreferencesKey("dj_spice")
        val segAds = booleanPreferencesKey("seg_ads")
        val segPromos = booleanPreferencesKey("seg_promos")
        val segHoroscope = booleanPreferencesKey("seg_horoscope")
        val segDedications = booleanPreferencesKey("seg_dedications")
        val trivia = booleanPreferencesKey("allow_trivia")

        /** Ajuste de la versión anterior (1-3): se migra a [level]. */
        val legacyIntensity = intPreferencesKey("dj_intensity")

        val announcerName = (0 until ANNOUNCERS).map { stringPreferencesKey("a${it}_name") }
        val announcerVoice = (0 until ANNOUNCERS).map { stringPreferencesKey("a${it}_voice") }
        val announcerPersona = (0 until ANNOUNCERS).map { stringPreferencesKey("a${it}_persona") }
        val announcerLocal = (0 until ANNOUNCERS).map { intPreferencesKey("a${it}_local") }
    }

    val flow: Flow<DjSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun current(): DjSettings = flow.first()

    suspend fun update(transform: (DjSettings) -> DjSettings) {
        context.settingsStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.djEnabled] = s.djEnabled
            prefs[Keys.apiKey] = s.geminiApiKey
            prefs[Keys.model] = s.geminiModel
            prefs[Keys.ttsModel] = s.ttsModel
            prefs[Keys.voiceEngine] = s.voiceEngine
            prefs[Keys.androidFallback] = s.androidFallback
            prefs[Keys.rate] = s.speechRate
            prefs[Keys.listener] = s.listenerName
            prefs[Keys.station] = s.stationName
            prefs[Keys.greetings] = s.greetingsTo
            prefs[Keys.city] = s.city
            prefs[Keys.level] = s.level
            prefs[Keys.spice] = s.spice
            prefs[Keys.segAds] = s.segAds
            prefs[Keys.segPromos] = s.segPromos
            prefs[Keys.segHoroscope] = s.segHoroscope
            prefs[Keys.segDedications] = s.segDedications
            prefs[Keys.trivia] = s.allowTrivia
            s.announcers.take(ANNOUNCERS).forEachIndexed { i, a ->
                prefs[Keys.announcerName[i]] = a.name
                prefs[Keys.announcerVoice[i]] = a.voice
                prefs[Keys.announcerPersona[i]] = a.persona
                prefs[Keys.announcerLocal[i]] = a.localVoice
            }
        }
    }

    private fun Preferences.toSettings(): DjSettings {
        val d = DjSettings()
        val level = this[Keys.level] ?: when (this[Keys.legacyIntensity]) {
            1 -> 2
            3 -> 4
            else -> d.level
        }
        return DjSettings(
            djEnabled = this[Keys.djEnabled] ?: d.djEnabled,
            geminiApiKey = this[Keys.apiKey] ?: d.geminiApiKey,
            geminiModel = this[Keys.model] ?: d.geminiModel,
            ttsModel = this[Keys.ttsModel] ?: d.ttsModel,
            voiceEngine = (
                this[Keys.voiceEngine]
                    ?: if (this[Keys.legacyGeminiVoice] == false) DjSettings.ENGINE_LOCAL else d.voiceEngine
                ).coerceIn(DjSettings.ENGINE_AUTO, DjSettings.ENGINE_GEMINI),
            androidFallback = this[Keys.androidFallback] ?: d.androidFallback,
            speechRate = this[Keys.rate] ?: d.speechRate,
            listenerName = this[Keys.listener] ?: d.listenerName,
            stationName = this[Keys.station] ?: d.stationName,
            greetingsTo = this[Keys.greetings] ?: d.greetingsTo,
            city = this[Keys.city] ?: d.city,
            level = level.coerceIn(1, 5),
            spice = (this[Keys.spice] ?: d.spice).coerceIn(0, 2),
            segAds = this[Keys.segAds] ?: d.segAds,
            segPromos = this[Keys.segPromos] ?: d.segPromos,
            segHoroscope = this[Keys.segHoroscope] ?: d.segHoroscope,
            segDedications = this[Keys.segDedications] ?: d.segDedications,
            allowTrivia = this[Keys.trivia] ?: d.allowTrivia,
            announcers = d.announcers.mapIndexed { i, def ->
                Announcer(
                    name = this[Keys.announcerName[i]] ?: def.name,
                    voice = this[Keys.announcerVoice[i]] ?: def.voice,
                    persona = this[Keys.announcerPersona[i]] ?: def.persona,
                    localVoice = this[Keys.announcerLocal[i]] ?: def.localVoice,
                )
            },
        )
    }

    private companion object {
        const val ANNOUNCERS = 3
    }
}
