package com.locudroids.app.data

import com.locudroids.app.ai.SongProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Perfil de cada canción (ritmo, energía y ánimo), guardado por ruta de archivo. */
class AnalysisRepository(private val file: File, private val io: CoroutineScope? = null) {

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<Map<String, SongProfile>> = _profiles.asStateFlow()
    private var saveJob: Job? = null

    fun putAudio(path: String, bpm: Float, energy: Float, brightness: Float) {
        _profiles.update { m ->
            val old = m[path] ?: SongProfile()
            val mood = old.mood ?: com.locudroids.app.ai.Moods.guessFromAudio(bpm, energy)
            val source = if (old.mood == null && mood != null) "audio" else old.moodSource
            m + (path to old.copy(bpm = bpm, energy = energy, brightness = brightness, mood = mood, moodSource = source))
        }
        persistLater()
    }

    /** Ánimo dicho por Gemini: sustituye al que se adivinó solo con el audio. */
    fun putMoods(moods: Map<String, Pair<String, Int?>>) {
        if (moods.isEmpty()) return
        _profiles.update { m ->
            val copy = HashMap(m)
            moods.forEach { (path, v) ->
                val old = copy[path] ?: SongProfile()
                copy[path] = old.copy(mood = v.first, valence = v.second, moodSource = "gemini")
            }
            copy
        }
        persistLater()
    }

    fun clear() {
        _profiles.value = emptyMap()
        persistNow()
    }

    fun flush() = persistNow()

    private fun persistLater() {
        if (io == null) {
            persistNow()
            return
        }
        saveJob?.cancel()
        saveJob = io.launch {
            delay(3_000L)
            persistNow()
        }
    }

    private fun persistNow() {
        val snapshot = _profiles.value
        synchronized(this) {
            val o = JSONObject()
            snapshot.forEach { (p, s) ->
                o.put(
                    p,
                    JSONObject().apply {
                        s.bpm?.let { put("bpm", it.toDouble()) }
                        s.energy?.let { put("e", it.toDouble()) }
                        s.brightness?.let { put("b", it.toDouble()) }
                        s.mood?.let { put("m", it) }
                        s.valence?.let { put("v", it) }
                        s.moodSource?.let { put("s", it) }
                    },
                )
            }
            file.writeAtomic(JSONObject().put("profiles", o).toString())
        }
    }

    private fun load(): Map<String, SongProfile> {
        val o = file.readJson()?.optJSONObject("profiles") ?: return emptyMap()
        val out = HashMap<String, SongProfile>()
        for (key in o.keys()) {
            val s = o.optJSONObject(key) ?: continue
            out[key] = SongProfile(
                bpm = if (s.has("bpm")) s.optDouble("bpm").toFloat() else null,
                energy = if (s.has("e")) s.optDouble("e").toFloat() else null,
                brightness = if (s.has("b")) s.optDouble("b").toFloat() else null,
                mood = s.optString("m", "").ifBlank { null },
                valence = if (s.has("v")) s.optInt("v") else null,
                moodSource = s.optString("s", "").ifBlank { null },
            )
        }
        return out
    }
}
