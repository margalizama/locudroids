package com.locudroids.app.ai

import com.locudroids.app.data.WeatherInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class BreakKind { INTRO, BETWEEN }

data class SongInfo(val title: String, val artist: String?) {
    /** Forma natural de decirlo en voz alta. */
    fun spoken(): String = if (artist != null) "$title, de $artist" else title
}

enum class Tone(val description: String) {
    ENERGICO("enérgico y animado, como locutor de radio matutina"),
    RELAJADO("tranquilo y cálido, con voz suave"),
    BROMISTA("bromista y ligero, con un comentario simpático (sin burlarte del oyente)"),
    NOSTALGICO("nostálgico y cercano"),
    MISTERIOSO("misterioso y pausado, como programa nocturno"),
    ELEGANTE("sofisticado y sereno, como presentador de jazz"),
}

data class BreakContext(
    val kind: BreakKind,
    val finished: SongInfo?,
    val next: SongInfo?,
    val weather: WeatherInfo?,
    val now: LocalDateTime,
    val tone: Tone,
    val listener: String,
    val djName: String,
)

/** Prompts, elección de tono y frases de respaldo (sin IA) del locutor. */
object DjScript {

    private val spanish = Locale("es", "MX")

    /** La personalidad cambia según la hora, el clima y lo que tocó la vez anterior. */
    fun pickTone(hour: Int, weather: WeatherInfo?, previous: Tone?): Tone {
        val pool = when {
            hour < 6 -> listOf(Tone.MISTERIOSO, Tone.RELAJADO, Tone.NOSTALGICO)
            hour < 12 -> listOf(Tone.ENERGICO, Tone.BROMISTA, Tone.RELAJADO)
            hour < 19 -> listOf(Tone.BROMISTA, Tone.ENERGICO, Tone.NOSTALGICO, Tone.ELEGANTE)
            else -> listOf(Tone.RELAJADO, Tone.NOSTALGICO, Tone.ELEGANTE, Tone.MISTERIOSO, Tone.BROMISTA)
        }.toMutableList()
        if (weather?.isRaining == true) pool += listOf(Tone.NOSTALGICO, Tone.RELAJADO)
        val candidates = pool.filter { it != previous }.ifEmpty { pool }
        return candidates.random()
    }

    fun systemPrompt(ctx: BreakContext): String = """
        Eres "${ctx.djName}", locutor de radio y DJ privado de ${ctx.listener}. Hablas en español de México, con voz natural de radio, y tu único oyente es ${ctx.listener}.
        Tu personalidad cambia de una intervención a otra según el momento del día, el clima y el ánimo de la música; en cada intervención se te indica el tono a usar.
        Reglas estrictas:
        - Responde únicamente con las palabras que dirás al aire: sin comillas, emojis, markdown, acotaciones ni explicaciones.
        - Entre 1 y 3 frases cortas, máximo 45 palabras.
        - Menciona de forma natural el nombre de la canción que sigue y su artista (si se conoce). Si acaba de terminar una canción, puedes referirte a ella brevemente.
        - No inventes datos (años, premios, anécdotas, significados) sobre canciones o artistas. Comenta solo lo que se te da: la hora, el clima y el ánimo.
        - Menciona el clima solo cuando aporte algo, no en todas las intervenciones.
        - Escribe números y unidades como se pronuncian (por ejemplo, "veintidós grados").
        - Varía la forma de abrir y cerrar; evita repetir muletillas.
    """.trimIndent()

    fun userPrompt(ctx: BreakContext): String {
        val kind = when (ctx.kind) {
            BreakKind.INTRO -> "Apertura de la sesión: saluda y presenta la primera canción."
            BreakKind.BETWEEN -> "Pausa entre canciones: cierra la anterior y presenta la siguiente."
        }
        val dayName = ctx.now.dayOfWeek.getDisplayName(TextStyle.FULL, spanish)
        val time = ctx.now.format(DateTimeFormatter.ofPattern("HH:mm"))
        return buildString {
            appendLine("Tipo de intervención: $kind")
            appendLine("Tono de esta intervención: ${ctx.tone.description}")
            appendLine("Oyente: ${ctx.listener}")
            appendLine("Momento: ${dayPart(ctx.now.hour)}, $dayName, $time")
            appendLine("Clima: ${weatherText(ctx.weather)}")
            ctx.finished?.let { appendLine("Canción que acaba de terminar: ${it.spoken()}") }
            ctx.next?.let { appendLine("Canción que sigue: ${it.spoken()}") }
            append("Escribe lo que dirás al aire.")
        }
    }

    private fun dayPart(hour: Int): String = when {
        hour < 6 -> "madrugada"
        hour < 12 -> "mañana"
        hour < 19 -> "tarde"
        else -> "noche"
    }

    private fun weatherText(w: WeatherInfo?): String =
        if (w == null) {
            "no disponible (no menciones el clima)"
        } else {
            "en ${w.city}: ${w.description}, ${w.temperatureC} grados " +
                "(sensación térmica de ${w.feelsLikeC}), viento de ${w.windKmh} kilómetros por hora"
        }

    /** Limpia lo que devuelva el modelo para que el TTS lo lea bien. */
    fun clean(raw: String): String {
        var t = raw
            .replace(Regex("[*_`#]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', '«', '»')
            .trim()
        if (t.length > 450) {
            val cut = t.take(450)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            t = if (end > 100) cut.take(end + 1) else cut
        }
        return t
    }

    /** Frase de respaldo cuando no hay clave de Gemini o la llamada falla. */
    fun fallback(ctx: BreakContext): String {
        val next = ctx.next?.spoken()
        val name = ctx.listener
        return when (ctx.kind) {
            BreakKind.INTRO -> {
                val w = ctx.weather?.let {
                    " Afuera hay ${it.description} y ${it.temperatureC} grados."
                }.orEmpty()
                val start = if (next != null) " Arrancamos con $next." else ""
                "Hola $name, aquí ${ctx.djName}.$w$start"
            }
            BreakKind.BETWEEN -> {
                val options = buildList {
                    if (next != null) {
                        add("Seguimos, $name. Ahora va $next.")
                        add("No te vayas, que ahora viene $next.")
                        ctx.finished?.let { add("Eso fue ${it.title}. Sigue $next.") }
                    } else {
                        add("Seguimos con la música, $name.")
                    }
                }
                options.random()
            }
        }
    }
}
