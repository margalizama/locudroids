package com.locudroids.app.ai

import android.util.Log
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.NewsClient
import com.locudroids.app.data.WeatherClient
import com.locudroids.app.data.WeatherInfo
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

data class ShowContext(
    val current: SongInfo,
    val next: SongInfo?,
    val plan: TrackPlan,
    val weather: WeatherInfo?,
    val now: LocalDateTime,
    val first: Boolean,
    val lastSpoke: String?,
    val greetings: List<String>,
    val news: List<String> = emptyList(),
)

/** Pide el guion de una canción a Gemini; si no hay clave o falla, arma uno de respaldo. */
class ShowWriter(
    private val gemini: GeminiClient = GeminiClient(),
    private val weather: WeatherClient = WeatherClient(),
    private val news: NewsClient = NewsClient(),
) {

    suspend fun context(
        current: SongInfo,
        next: SongInfo?,
        plan: TrackPlan,
        settings: DjSettings,
        first: Boolean,
        lastSpoke: String?,
    ): ShowContext {
        val greetings = settings.greetingsTo
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .shuffled()
            .take(3)
        return ShowContext(
            current = current,
            next = next,
            plan = plan,
            weather = weather.current(settings.city),
            now = LocalDateTime.now(),
            first = first,
            lastSpoke = lastSpoke,
            greetings = greetings,
            news = if (settings.segNews && plan.slots.isNotEmpty()) news.headlines(settings.city) else emptyList(),
        )
    }

    suspend fun write(ctx: ShowContext, s: DjSettings): Show {
        if (ctx.plan.slots.isEmpty()) return Show(null, 3, emptyList(), usedAi = false, error = null)
        if (s.geminiApiKey.isBlank()) return fallback(ctx, s, "Falta la clave de Gemini")
        return try {
            val raw = gemini.generate(
                apiKey = s.geminiApiKey.trim(),
                model = s.geminiModel.ifBlank { DjSettings.DEFAULT_MODEL }.trim(),
                system = ShowPrompts.system(s),
                prompt = ShowPrompts.user(ctx, s),
                json = true,
                maxTokens = 3072,
            )
            ShowParser.parse(raw, ctx.plan.slots, s.announcers.map { it.name })
                ?: fallback(ctx, s, "Gemini devolvió un guion que no se pudo leer")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini falló: ${e.message}")
            fallback(ctx, s, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Guion sin IA: frases fijas, pero con hora, clima y saludo. */
    fun fallback(ctx: ShowContext, s: DjSettings, error: String?): Show {
        val crew = s.announcers.map { it.name }
        val lead = crew.firstOrNull { !it.equals(ctx.lastSpoke, ignoreCase = true) } ?: crew.firstOrNull() ?: "Locutor"
        val hora = SpokenTime.format(ctx.now.toLocalTime())
        val verb = if (hora.startsWith("la una")) "Es" else "Son"
        val greet = ctx.greetings.randomOrNull() ?: ShowPrompts.listener(s)
        val clima = ctx.weather?.let { "Afuera hay ${it.description} y ${it.temperatureC} grados. " }.orEmpty()
        val station = ShowPrompts.station(s)

        val cues = ctx.plan.slots.sortedBy { it.ordinal }.map { slot ->
            val text = when (slot) {
                Slot.INTRO ->
                    if (ctx.plan.introIsTag) {
                        "¡Esto es $station!"
                    } else {
                        val welcome = if (ctx.first) "Hola ${ShowPrompts.listener(s)}, aquí $lead en $station. " else ""
                        welcome + "$verb $hora. " + clima + "Suena ${ctx.current.spoken()}. Un saludo para $greet."
                    }
                Slot.MID -> "Estás en $station, la estación que sí te pone la música. Con ${crew.joinToString(", ")}."
                Slot.OUTRO -> {
                    val next = ctx.next?.let { "Sigue ${it.spoken()}." } ?: "Seguimos con la música."
                    "Eso fue ${ctx.current.title}. $clima$next"
                }
            }
            ShowCue(slot, "presentacion", listOf(ScriptLine(lead, null, text)))
        }
        return Show(null, 3, cues, usedAi = false, error = error)
    }

    private companion object {
        const val TAG = "ShowWriter"
    }
}
