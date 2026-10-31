package com.locudroids.app.player

import android.content.Context
import android.util.Log
import com.locudroids.app.ai.GeminiTts
import com.locudroids.app.ai.LocalSpeech
import com.locudroids.app.ai.Pcm
import com.locudroids.app.ai.ScriptLine
import com.locudroids.app.ai.ShowCue
import com.locudroids.app.ai.stripTags
import com.locudroids.app.data.DjSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Con qué voz se dijo (o se dirá) un corte. */
enum class VoiceSource { GEMINI, LOCAL, NONE }

/**
 * Un corte ya convertido en audio con la voz de Gemini o la local ([source]). Si ninguna estuvo disponible, [source] es NONE,
 * [pcm] es null y el corte se omite. [note] explica por qué no se usó la primera opción (Gemini en pausa, falló...).
 */
class Clip(
    val lines: List<ScriptLine>,
    val pcm: Pcm?,
    val estMs: Long,
    val source: VoiceSource,
    val note: String? = null,
) {
    /** No hubo ninguna voz disponible: el corte no se dice. */
    val skip: Boolean get() = source == VoiceSource.NONE
}

/** Convierte cortes en voz (Gemini primero; si falla o está en pausa, la voz neuronal local) y los reproduce. */
class VoiceDirector(context: Context) {

    private val gemini = GeminiTts()
    private val local = LocalTts(context)
    private val pcmPlayer = PcmPlayer()
    // Una petición de voz a Gemini a la vez: varias en paralelo disparan los límites de la API.
    private val gate = Semaphore(1)

    /** Carga la voz local con anticipación (tarda unos segundos la primera vez). */
    suspend fun warmUp() {
        local.warmUp()
    }

    /** Gemini primero; si falla, está en pausa o no hay clave, la voz local. Sin ninguna, el corte se omite. */
    suspend fun makeClip(cue: ShowCue, s: DjSettings): Clip {
        val estimate = estimateMs(cue.lines, s.speechRate)
        var geminiNote: String? = null

        when {
            s.geminiApiKey.isBlank() -> Unit
            GeminiHealth.isBlocked() ->
                geminiNote = "Gemini en pausa (${GeminiHealth.reason}); reintenta en ${GeminiHealth.remainingMinutes()} min"
            else -> try {
                val lines = cue.lines.map { GeminiTts.TtsLine(it.who, geminiVoiceFor(it.who, s), it.style, it.text) }
                val pcm = gate.withPermit { synthesizeGemini(s, lines) }
                GeminiHealth.clear()
                return Clip(cue.lines, pcm, pcm.durationMs, VoiceSource.GEMINI)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = (e.message ?: e.javaClass.simpleName).take(120)
                Log.w(TAG, "La voz de Gemini falló: $reason")
                GeminiHealth.report(reason)
                geminiNote = "Gemini falló ($reason)"
            }
        }

        return try {
            val lines = cue.lines.map {
                LocalLine(LocalSpeech.prepare(it.text), localVoiceFor(it.who, s), LocalSpeech.speedFor(it.style, s.speechRate))
            }
            val pcm = local.synthesize(lines)
            Clip(cue.lines, pcm, pcm.durationMs, VoiceSource.LOCAL, note = geminiNote)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val reason = (e.message ?: e.javaClass.simpleName).take(120)
            Log.w(TAG, "La voz local falló: $reason", e)
            Clip(cue.lines, null, estimate, VoiceSource.NONE, note = listOfNotNull(geminiNote, "voz local falló ($reason)").joinToString("; "))
        }
    }

    /** Un reintento rápido ante fallos pasajeros; con cuota agotada (429) no se insiste. */
    private suspend fun synthesizeGemini(s: DjSettings, lines: List<GeminiTts.TtsLine>): Pcm {
        val key = s.geminiApiKey.trim()
        val model = s.ttsModel.ifBlank { DjSettings.DEFAULT_TTS_MODEL }.trim()
        var last: Exception? = null
        for (attempt in 0 until 2) {
            try {
                return gemini.synthesize(key, model, lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (isFinal(e)) break
                if (attempt == 0) delay(1_500L)
            }
        }
        val failure = last ?: IllegalStateException("La voz de Gemini no respondió")
        if (lines.map { it.who }.distinct().size > 1 && !isFinal(failure)) {
            return gemini.synthesizeSequential(key, model, lines)
        }
        throw failure
    }

    /** Errores que no se arreglan reintentando: cuota agotada, clave inválida, petición mal formada, modelo inexistente. */
    private fun isFinal(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.startsWith("HTTP 429") || msg.startsWith("HTTP 400") || msg.startsWith("HTTP 401") ||
            msg.startsWith("HTTP 403") || msg.startsWith("HTTP 404")
    }

    /** Reproduce el corte y espera a que termine. Cancelar la corrutina lo detiene. */
    suspend fun play(clip: Clip) {
        clip.pcm?.let { pcmPlayer.play(it) }
    }

    fun shutdown() {
        local.release()
    }

    private fun geminiVoiceFor(who: String, s: DjSettings): String =
        s.announcers.firstOrNull { it.name.equals(who, ignoreCase = true) }?.voice
            ?: s.announcers.firstOrNull()?.voice
            ?: "Kore"

    private fun localVoiceFor(who: String, s: DjSettings): Int =
        (s.announcers.firstOrNull { it.name.equals(who, ignoreCase = true) } ?: s.announcers.firstOrNull())?.localVoice
            ?: com.locudroids.app.ai.LocalVoices.ALEX

    private fun estimateMs(lines: List<ScriptLine>, rate: Float): Long {
        val chars = lines.sumOf { it.text.stripTags().length }
        val speech = chars * 1000.0 / (CHARS_PER_SECOND * rate.coerceAtLeast(0.5f))
        return speech.toLong() + 300L * lines.size
    }

    private companion object {
        const val TAG = "VoiceDirector"
        const val CHARS_PER_SECOND = 14.0
    }
}
