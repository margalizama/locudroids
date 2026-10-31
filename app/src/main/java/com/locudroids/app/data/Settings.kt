package com.locudroids.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** Velocidad de la voz local (1.0 = normal). */
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
    val segNews: Boolean = true,
    val allowTrivia: Boolean = true,

    /** Mezcla entre canciones: [MIX_OFF] sin mezcla (gapless), [MIX_CROSSFADE] fundido, [MIX_BEAT] con ritmo (experimental). */
    val mixMode: Int = MIX_CROSSFADE,
    /** Duración del fundido en segundos. */
    val mixSeconds: Int = 6,

    /** Empezar a tocar cuando se conecta uno de los dispositivos Bluetooth de [btDevices]. */
    val btAutoPlay: Boolean = false,
    /** Dispositivos elegidos, como "dirección|nombre". */
    val btDevices: Set<String> = emptySet(),

    /** Analizar la biblioteca (ánimo y ritmo) en segundo plano al abrir la app. */
    val autoAnalyze: Boolean = true,

    val announcers: List<Announcer> = Announcer.defaults(),
) {
    companion object {
        const val MIX_OFF = 0
        const val MIX_CROSSFADE = 1
        const val MIX_BEAT = 2
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_TTS_MODEL = "gemini-3.8-flash-tts"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val djEnabled = booleanPreferencesKey("dj_enabled")
        val apiKey = stringPreferencesKey("gemini_api_key")
        val model = stringPreferencesKey("gemini_model")
        val ttsModel = stringPreferencesKey("tts_model")
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
        val segNews = booleanPreferencesKey("seg_news")
        val trivia = booleanPreferencesKey("allow_trivia")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val mixMode = intPreferencesKey("mix_mode")
        val btAutoPlay = booleanPreferencesKey("bt_auto_play")
        val btDevices = stringSetPreferencesKey("bt_devices")
        val mixSeconds = intPreferencesKey("mix_seconds")

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
            prefs[Keys.segNews] = s.segNews
            prefs[Keys.trivia] = s.allowTrivia
            prefs[Keys.autoAnalyze] = s.autoAnalyze
            prefs[Keys.mixMode] = s.mixMode
            prefs[Keys.btAutoPlay] = s.btAutoPlay
            prefs[Keys.btDevices] = s.btDevices
            prefs[Keys.mixSeconds] = s.mixSeconds
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
            segNews = this[Keys.segNews] ?: d.segNews,
            allowTrivia = this[Keys.trivia] ?: d.allowTrivia,
            autoAnalyze = this[Keys.autoAnalyze] ?: d.autoAnalyze,
            btAutoPlay = this[Keys.btAutoPlay] ?: d.btAutoPlay,
            btDevices = this[Keys.btDevices] ?: d.btDevices,
            mixMode = (this[Keys.mixMode] ?: d.mixMode).coerceIn(DjSettings.MIX_OFF, DjSettings.MIX_BEAT),
            mixSeconds = (this[Keys.mixSeconds] ?: d.mixSeconds).coerceIn(2, 12),
            announcers = d.announcers.mapIndexed { i, def ->
                Announcer(
                    name = this[Keys.announcerName[i]] ?: def.name,
                    voice = this[Keys.announcerVoice[i]] ?: def.voice,
                    persona = migratePersona(i, this[Keys.announcerPersona[i]], def.persona),
                    localVoice = this[Keys.announcerLocal[i]] ?: def.localVoice,
                )
            },
        )
    }

    /** Las personalidades guardadas por versiones con 3 locutores mencionaban a Chuy, que ya no está. */
    private fun migratePersona(index: Int, stored: String?, default: String): String {
        if (stored == null) return default
        if (index == 0 && stored.startsWith("El conductor del programa") && !stored.contains("parodia")) return default
        return stored.replace("Chuy", "Rafa")
    }

    private companion object {
        const val ANNOUNCERS = 2
    }
}
