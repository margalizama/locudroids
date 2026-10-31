package com.locudroids.app.data

import android.content.Context
import android.util.Log
import com.locudroids.app.ai.AudioAnalyzer
import com.locudroids.app.ai.GeminiClient
import com.locudroids.app.ai.MoodParser
import com.locudroids.app.ai.MoodPrompts
import com.locudroids.app.player.GeminiHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalysisState(
    val running: Boolean = false,
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val note: String? = null,
)

/**
 * Analiza la biblioteca en segundo plano: primero Gemini clasifica el ánimo (por lotes) y luego se mide
 * el ritmo y la energía de cada canción en el teléfono. Lo ya analizado no se repite.
 */
class AnalysisRunner(
    private val context: Context,
    private val repo: AnalysisRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private val gemini = GeminiClient()
    private var job: Job? = null
    private val failed = HashSet<String>()

    fun start(songs: List<Song>, settings: DjSettings) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                run(songs, settings)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(running = false, phase = "Detenido")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "El análisis falló: ${e.message}")
                _state.value = AnalysisState(note = "Error: ${e.message}")
            } finally {
                repo.flush()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** Cuántas canciones faltan por analizar (ánimo con Gemini o medida del audio). */
    fun pending(songs: List<Song>): Int {
        val p = repo.profiles.value
        return songs.count { p[it.path]?.bpm == null || p[it.path]?.moodSource != "gemini" }
    }

    private suspend fun run(songs: List<Song>, s: DjSettings) {
        var note: String? = null
        val profiles = repo.profiles.value

        // 1) Ánimo con Gemini.
        val needMood = songs.filter { profiles[it.path]?.moodSource != "gemini" }
        val needAudio = songs.filter { profiles[it.path]?.bpm == null && it.path !in failed }
        val total = needMood.size + needAudio.size
        var done = 0
        _state.value = AnalysisState(running = true, phase = "Preguntando a Gemini por el ánimo", total = total)

        if (needMood.isNotEmpty() && s.geminiApiKey.isNotBlank() && !GeminiHealth.isBlocked()) {
            val model = s.geminiModel.ifBlank { DjSettings.DEFAULT_MODEL }
            try {
                for (batch in needMood.chunked(BATCH)) {
                    val result = classify(batch, s.geminiApiKey.trim(), model)
                    repo.putMoods(
                        result.mapNotNull { (i, r) -> batch.getOrNull(i)?.let { it.path to (r.mood to r.valence) } }.toMap()
                    )
                    done += batch.size
                    _state.value = _state.value.copy(done = done)
                    delay(400)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = (e.message ?: e.javaClass.simpleName).take(120)
                if (msg.startsWith("HTTP")) GeminiHealth.report(msg)
                note = "Gemini no pudo clasificar ($msg). Se usa el ánimo que se deduce del audio."
            }
        } else if (needMood.isNotEmpty()) {
            note = if (s.geminiApiKey.isBlank()) "Sin clave de Gemini: el ánimo se deduce solo del audio." else
                "Gemini está en pausa (${GeminiHealth.reason}): el ánimo se deduce del audio por ahora."
        }
        done = needMood.size

        // 2) Ritmo y energía en el teléfono.
        _state.value = AnalysisState(running = true, phase = "Midiendo ritmo y energía", done = done, total = total, note = note)
        for (song in needAudio) {
            if (!scope.isActive) break
            val ok = withContext(Dispatchers.Default) { measure(song) }
            if (!ok) failed += song.path
            done++
            if (done % 5 == 0) _state.value = _state.value.copy(done = done)
        }
        _state.value = AnalysisState(running = false, phase = "Listo", done = total, total = total, note = note)
    }

    private suspend fun classify(batch: List<Song>, key: String, model: String): Map<Int, MoodParser.Result> {
        val prompt = MoodPrompts.user(batch.map { Triple(it.title, it.artist, it.album) }, batch.map { it.year })
        val raw = gemini.generate(key, model, MoodPrompts.system, prompt, json = true, maxTokens = 4096)
        return MoodParser.parse(raw)
    }

    private fun measure(song: Song): Boolean {
        val windowMs = 45_000L
        val start = (song.durationMs * 0.3).toLong().coerceAtMost((song.durationMs - windowMs).coerceAtLeast(0L))
        val audio = AudioDecoder.decode(context, song.uri, start, windowMs) ?: return false
        val f = AudioAnalyzer.analyze(audio.samples, audio.sampleRate) ?: return false
        repo.putAudio(song.path, f.bpm, f.energy, f.brightness)
        return true
    }

    private companion object {
        const val TAG = "AnalysisRunner"
        const val BATCH = 40
    }
}
