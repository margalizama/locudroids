package com.locudroids.app.ai

import android.util.Log
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.WeatherClient
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

/** Resultado de escribir una intervención. [error] != null significa que se usó la frase de respaldo. */
data class Written(val text: String, val usedAi: Boolean, val error: String?)

/** Arma el contexto (clima, hora, tono, saludos) y le pide el texto al modelo. */
class DjWriter(
    private val gemini: GeminiClient = GeminiClient(),
    private val weather: WeatherClient = WeatherClient(),
) {
    private var lastTone: Tone? = null

    suspend fun buildContext(
        kind: BreakKind,
        current: SongInfo,
        next: SongInfo?,
        settings: DjSettings,
        first: Boolean,
    ): BreakContext {
        val w = weather.current(settings.city)
        val now = LocalDateTime.now()
        val tone = DjScript.pickTone(now.hour, w, lastTone)
        lastTone = tone
        // Se rotan los saludos: en cada corte se escogen hasta 2 al azar.
        val greetings = settings.greetingsTo
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .shuffled()
            .take(2)
        return BreakContext(
            kind = kind,
            current = current,
            next = next,
            weather = w,
            now = now,
            tone = tone,
            listener = settings.listenerName.ifBlank { "amigo" },
            djName = settings.djName.ifBlank { "Locudroid" },
            station = settings.stationName.ifBlank { "Locudroids FM" },
            greetings = greetings,
            allowTrivia = settings.allowTrivia,
            first = first,
            intensity = settings.intensity,
        )
    }

    suspend fun write(ctx: BreakContext, settings: DjSettings): Written {
        if (settings.geminiApiKey.isBlank()) {
            return Written(DjScript.fallback(ctx), usedAi = false, error = "Falta la clave de Gemini")
        }
        return try {
            val raw = gemini.generate(
                apiKey = settings.geminiApiKey.trim(),
                model = settings.geminiModel.ifBlank { DjSettings.DEFAULT_MODEL }.trim(),
                system = DjScript.systemPrompt(ctx),
                prompt = DjScript.userPrompt(ctx),
            )
            val text = DjScript.clean(raw)
            if (text.isBlank()) {
                Written(DjScript.fallback(ctx), usedAi = false, error = "Respuesta vacía")
            } else {
                Written(text, usedAi = true, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DjWriter", "Gemini falló: ${e.message}")
            Written(DjScript.fallback(ctx), usedAi = false, error = e.message ?: e.javaClass.simpleName)
        }
    }
}
