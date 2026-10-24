package com.locudroids.app.ai

import com.locudroids.app.data.WeatherInfo
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** START: el locutor habla encima de la intro. END: habla encima del final y presenta la siguiente. */
enum class BreakKind { START, END }

data class SongInfo(
    val title: String,
    val artist: String?,
    val album: String? = null,
    val year: Int? = null,
) {
    /** Forma natural de decirlo en voz alta. */
    fun spoken(): String = if (artist != null) "$title, de $artist" else title

    /** Datos disponibles de las etiquetas del archivo. */
    fun details(): String = buildList {
        add("título: $title")
        artist?.let { add("artista: $it") }
        album?.let { add("álbum: $it") }
        year?.let { add("año: $it") }
    }.joinToString("; ")
}

enum class Tone(val description: String) {
    ENERGICO("enérgico y arrollador, como locutor de radio matutina de éxitos"),
    RELAJADO("relajado y cálido, como radio de la tarde-noche"),
    BROMISTA("bromista y ligero, con un comentario simpático (sin burlarte del oyente)"),
    NOSTALGICO("nostálgico y cercano"),
    MISTERIOSO("misterioso y pausado, como programa nocturno"),
    ELEGANTE("sofisticado y sereno, como presentador de radio de culto"),
}

data class BreakContext(
    val kind: BreakKind,
    val current: SongInfo,
    val next: SongInfo?,
    val weather: WeatherInfo?,
    val now: LocalDateTime,
    val tone: Tone,
    val listener: String,
    val djName: String,
    val station: String,
    val greetings: List<String>,
    val allowTrivia: Boolean,
    val first: Boolean,
    val intensity: Int,
)

/** Prompts, elección de tono y frases de respaldo (sin IA) del locutor. */
object DjScript {

    private val spanish = Locale("es", "MX")

    /** La personalidad cambia según la hora, el clima y lo que tocó la vez anterior. */
    fun pickTone(hour: Int, weather: WeatherInfo?, previous: Tone?): Tone {
        val pool = when {
            hour < 6 -> listOf(Tone.MISTERIOSO, Tone.RELAJADO, Tone.NOSTALGICO)
            hour < 12 -> listOf(Tone.ENERGICO, Tone.BROMISTA, Tone.RELAJADO)
            hour < 19 -> listOf(Tone.ENERGICO, Tone.BROMISTA, Tone.NOSTALGICO, Tone.ELEGANTE)
            else -> listOf(Tone.RELAJADO, Tone.NOSTALGICO, Tone.ELEGANTE, Tone.MISTERIOSO, Tone.BROMISTA)
        }.toMutableList()
        if (weather?.isRaining == true) pool += listOf(Tone.NOSTALGICO, Tone.RELAJADO)
        val candidates = pool.filter { it != previous }.ifEmpty { pool }
        return candidates.random()
    }

    private fun maxWords(ctx: BreakContext): Int =
        if (ctx.kind == BreakKind.START && ctx.intensity < 3) 55 else 40

    fun systemPrompt(ctx: BreakContext): String {
        val trivia = if (ctx.allowTrivia) {
            "- No inventes datos. Un dato de la canción, el artista o el álbum solo si estás completamente seguro de que es verdad; si no lo estás, comenta el ambiente o el momento del día. Nunca inventes años, premios ni anécdotas."
        } else {
            "- No compartas datos ni anécdotas de canciones o artistas; solo nombre y artista."
        }
        return """
            Eres "${ctx.djName}", el locutor estrella de "${ctx.station}", una estación de radio musical juvenil de México al estilo de Exa FM: energía alta, ritmo ágil, frases de radio, cercano y divertido. Tu único oyente es ${ctx.listener}, pero hablas como si transmitieras en vivo para toda la ciudad.
            Hablas ENCIMA de la música: la canción sigue sonando bajito de fondo mientras tú hablas.
            Tu estilo cambia de un corte a otro (se te indica el tono), pero siempre suenas a radio.
            Reglas estrictas:
            - Responde únicamente con las palabras que dirás al aire: sin comillas, emojis, markdown, acotaciones ni explicaciones.
            - Español de México, natural y hablado. Máximo ${maxWords(ctx)} palabras.
            - Di la hora EXACTAMENTE como se te da, sin convertirla a números.
            - Escribe las cifras como se pronuncian (por ejemplo, "veintidós grados").
            $trivia
            - Menciona el nombre de la estación solo de vez en cuando. No repitas siempre la misma estructura ni las mismas muletillas.
        """.trimIndent()
    }

    fun userPrompt(ctx: BreakContext): String {
        val situation = when (ctx.kind) {
            BreakKind.START -> "Hablas encima de la introducción de la canción que acaba de empezar."
            BreakKind.END -> "Hablas encima del final de la canción actual: la cierras y presentas la que sigue."
        }
        val includes = buildList {
            when (ctx.kind) {
                BreakKind.START -> {
                    add("nombre y artista de la canción que suena")
                    add("la hora")
                    if (ctx.intensity < 3) add("el clima (si está disponible)")
                    add("un saludo")
                    if (ctx.allowTrivia && ctx.intensity < 3) add("un dato de la canción o el artista, solo si estás seguro")
                }
                BreakKind.END -> {
                    add("el clima (si está disponible)")
                    if (ctx.allowTrivia) add("un dato de la canción o el artista, solo si estás seguro")
                    add("la canción que sigue (nombre y artista)")
                    add("un cierre estilo radio")
                }
            }
        }
        val dayName = ctx.now.dayOfWeek.getDisplayName(TextStyle.FULL, spanish)
        val greet = if (ctx.greetings.isEmpty()) {
            "Saluda a ${ctx.listener}."
        } else {
            "Saludos para este corte: ${ctx.greetings.joinToString(", ")}."
        }
        return buildString {
            appendLine("Situación: $situation")
            if (ctx.first && ctx.kind == BreakKind.START) {
                appendLine("Es el primer corte de la sesión: arranca presentando la estación y dando la bienvenida.")
            }
            appendLine("Tono de este corte: ${ctx.tone.description}")
            appendLine("Hora (dila tal cual): ${SpokenTime.format(ctx.now.toLocalTime())}")
            appendLine("Día: $dayName")
            appendLine("Clima: ${weatherText(ctx.weather)}")
            appendLine("Canción que suena: ${ctx.current.details()}")
            ctx.next?.let { appendLine("Canción que sigue: ${it.details()}") }
            appendLine(greet)
            appendLine("Incluye: ${includes.joinToString("; ")}.")
            appendLine("Puedes cambiar el orden y omitir algo si suena más natural.")
            append("Escribe lo que dirás al aire.")
        }
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
        if (t.length > 480) {
            val cut = t.take(480)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            t = if (end > 100) cut.take(end + 1) else cut
        }
        return t
    }

    /** Frase de respaldo cuando no hay clave de Gemini o la llamada falla. */
    fun fallback(ctx: BreakContext): String {
        val hora = SpokenTime.format(ctx.now.toLocalTime())
        val greet = ctx.greetings.randomOrNull() ?: ctx.listener
        val weather = ctx.weather?.let { "Afuera hay ${it.description} y ${it.temperatureC} grados. " }.orEmpty()
        return when (ctx.kind) {
            BreakKind.START -> {
                val welcome = if (ctx.first) "Hola ${ctx.listener}, aquí ${ctx.djName} en ${ctx.station}. " else ""
                welcome + "Son $hora. " + weather +
                    "Suena ${ctx.current.spoken()}. Un saludo para $greet."
            }
            BreakKind.END -> {
                val next = ctx.next?.let { "Sigue ${it.spoken()}." } ?: "Seguimos con la música."
                "Eso fue ${ctx.current.title}. $weather$next ¡Esto es ${ctx.station}!"
            }
        }
    }
}
