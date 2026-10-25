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
)

/** Convierte cortes en voz (Gemini, con respaldo en la voz de Android) y los reproduce. */
class VoiceDirector(context: Context) {

    private val tts = GeminiTts()
    private val pcmPlayer = PcmPlayer()
    private val speaker = Speaker(context.applicationContext)
    private val gate = Semaphore(2)

    suspend fun makeClip(cue: ShowCue, s: DjSettings): Clip {
        val estimate = estimateMs(cue.lines, s.speechRate)
        if (!s.geminiVoice || s.geminiApiKey.isBlank()) return Clip(cue.lines, null, estimate, null)

        val lines = cue.lines.map { GeminiTts.TtsLine(it.who, voiceFor(it.who, s), it.style, it.text) }
        return try {
            val pcm = gate.withPermit {
                tts.synthesize(
                    apiKey = s.geminiApiKey.trim(),
                    model = s.ttsModel.ifBlank { DjSettings.DEFAULT_TTS_MODEL }.trim(),
                    lines = lines,
                )
            }
            Clip(cue.lines, pcm, pcm.durationMs, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "La voz de Gemini falló: ${e.message}")
            Clip(cue.lines, null, estimate, e.message ?: e.javaClass.simpleName)
        }
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
