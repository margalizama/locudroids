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
enum class VoiceSource { GEMINI, LOCAL, ANDROID }

/**
 * Un corte ya convertido en audio. [pcm] trae el audio de Gemini o de la voz local; si es null, se dice con la voz de Android.
 * [note] explica por qué no se usó la primera opción (Gemini en pausa, falta la voz local...).
 */
class Clip(
    val lines: List<ScriptLine>,
    val pcm: Pcm?,
    val estMs: Long,
    val source: VoiceSource,
    val note: String? = null,
    /** true = no hubo ninguna voz buena disponible y el ajuste pide no usar la de Android: el corte no se dice. */
    val skip: Boolean = false,
)

/** Convierte cortes en voz (Gemini, voz neuronal local y como último recurso la de Android) y los reproduce. */
class VoiceDirector(context: Context) {

    private val gemini = GeminiTts()
    private val local = LocalTts(context)
    private val pcmPlayer = PcmPlayer()
    private val speaker = Speaker(context.applicationContext)
    // Una petición de voz a Gemini a la vez: varias en paralelo disparan los límites de la API.
    private val gate = Semaphore(1)

    /** Carga la voz local con anticipación (tarda unos segundos la primera vez). */
    suspend fun warmUp(s: DjSettings) {
        if (s.voiceEngine != DjSettings.ENGINE_GEMINI) local.warmUp()
    }

    suspend fun makeClip(cue: ShowCue, s: DjSettings): Clip {
        val estimate = estimateMs(cue.lines, s.speechRate)
        val engine = s.voiceEngine
        var geminiNote: String? = null

        if (engine != DjSettings.ENGINE_LOCAL) {
            when {
                s.geminiApiKey.isBlank() ->
                    if (engine == DjSettings.ENGINE_GEMINI) geminiNote = "falta la clave de Gemini"
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
        }

        var localNote: String? = null
        if (engine != DjSettings.ENGINE_GEMINI) {
            try {
                val lines = cue.lines.map {
                    LocalLine(LocalSpeech.prepare(it.text), localVoiceFor(it.who, s), LocalSpeech.speedFor(it.style, s.speechRate))
                }
                val pcm = local.synthesize(lines)
                return Clip(cue.lines, pcm, pcm.durationMs, VoiceSource.LOCAL, note = geminiNote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val reason = (e.message ?: e.javaClass.simpleName).take(120)
                Log.w(TAG, "La voz local falló: $reason", e)
                localNote = "voz local falló ($reason)"
            }
        }

        val note = listOfNotNull(geminiNote, localNote).joinToString("; ").ifBlank { null }
        return Clip(cue.lines, null, estimate, VoiceSource.ANDROID, note = note, skip = !s.androidFallback)
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
    suspend fun play(clip: Clip, s: DjSettings) {
        val pcm = clip.pcm
        if (pcm != null) {
            pcmPlayer.play(pcm)
        } else {
            for (line in clip.lines) {
                speaker.speak(line.text.stripTags(), s.speechRate, pitchFor(line.who, s))
            }
        }
    }

    fun stop() {
        speaker.stop()
    }

    fun shutdown() {
        speaker.shutdown()
        local.release()
    }

    private fun geminiVoiceFor(who: String, s: DjSettings): String =
        s.announcers.firstOrNull { it.name.equals(who, ignoreCase = true) }?.voice
            ?: s.announcers.firstOrNull()?.voice
            ?: "Kore"

    private fun localVoiceFor(who: String, s: DjSettings): Int =
        (s.announcers.firstOrNull { it.name.equals(who, ignoreCase = true) } ?: s.announcers.firstOrNull())?.localVoice
            ?: com.locudroids.app.ai.LocalVoices.ALEX

    /** Con la voz de Android, cada locutor tiene un tono distinto para distinguirlos. */
    private fun pitchFor(who: String, s: DjSettings): Float {
        val index = s.announcers.indexOfFirst { it.name.equals(who, ignoreCase = true) }
        return PITCHES.getOrElse(index) { 1.0f }
    }

    private fun estimateMs(lines: List<ScriptLine>, rate: Float): Long {
        val chars = lines.sumOf { it.text.stripTags().length }
        val speech = chars * 1000.0 / (CHARS_PER_SECOND * rate.coerceAtLeast(0.5f))
        return speech.toLong() + 300L * lines.size
    }

    private companion object {
        const val TAG = "VoiceDirector"
        const val CHARS_PER_SECOND = 14.0
        val PITCHES = listOf(0.95f, 1.25f, 0.8f)
    }
}
