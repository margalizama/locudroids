package com.locudroids.app.player

import android.content.Context
import android.util.Log
import com.locudroids.app.ai.GeminiTts
import com.locudroids.app.ai.Pcm
import com.locudroids.app.ai.ScriptLine
import com.locudroids.app.ai.ShowCue
import com.locudroids.app.ai.stripTags
import com.locudroids.app.data.DjSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Un corte ya convertido en audio. [pcm] = voz de Gemini; si es null, se dice con la voz de Android.
 * [ttsError] guarda por qué falló la voz de Gemini (si falló).
 */
class Clip(
    val lines: List<ScriptLine>,
    val pcm: Pcm?,
    val estMs: Long,
    val ttsError: String?,
    /** true = la voz de Gemini falló y el ajuste pide no usar la voz de Android: el corte no se dice. */
    val skip: Boolean = false,
)

/** Convierte cortes en voz (Gemini, con respaldo en la voz de Android) y los reproduce. */
class VoiceDirector(context: Context) {

    private val tts = GeminiTts()
    private val pcmPlayer = PcmPlayer()
    private val speaker = Speaker(context.applicationContext)
    // Una petición de voz a la vez: varias en paralelo disparan los límites de la API y la voz cae a Android.
    private val gate = Semaphore(1)

    suspend fun makeClip(cue: ShowCue, s: DjSettings): Clip {
        val estimate = estimateMs(cue.lines, s.speechRate)
        if (!s.geminiVoice || s.geminiApiKey.isBlank()) return Clip(cue.lines, null, estimate, null)

        val lines = cue.lines.map { GeminiTts.TtsLine(it.who, voiceFor(it.who, s), it.style, it.text) }
        return try {
            val pcm = gate.withPermit { synthesizeWithRetry(s, lines) }
            Clip(cue.lines, pcm, pcm.durationMs, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = (e.message ?: e.javaClass.simpleName).take(120)
            Log.w(TAG, "La voz de Gemini falló: $reason")
            Clip(cue.lines, null, estimate, reason, skip = !s.androidFallback)
        }
    }

    /** Reintenta con pausas; si son 2 locutores y sigue fallando, prueba línea por línea. */
    private suspend fun synthesizeWithRetry(s: DjSettings, lines: List<GeminiTts.TtsLine>): Pcm {
        val key = s.geminiApiKey.trim()
        val model = s.ttsModel.ifBlank { DjSettings.DEFAULT_TTS_MODEL }.trim()
        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                return tts.synthesize(key, model, lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (isPermanent(e)) break
                if (attempt < 2) delay(if (attempt == 0) 1_500L else 4_000L)
            }
        }
        val failure = last ?: IllegalStateException("La voz de Gemini no respondió")
        if (lines.map { it.who }.distinct().size > 1 && !isPermanent(failure)) {
            return tts.synthesizeSequential(key, model, lines)
        }
        throw failure
    }

    /** Errores que no se arreglan reintentando (clave inválida, petición mal formada, modelo inexistente). */
    private fun isPermanent(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.startsWith("HTTP 400") || msg.startsWith("HTTP 401") ||
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
    }

    private fun voiceFor(who: String, s: DjSettings): String =
        s.announcers.firstOrNull { it.name.equals(who, ignoreCase = true) }?.voice
            ?: s.announcers.firstOrNull()?.voice
            ?: "Kore"

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
