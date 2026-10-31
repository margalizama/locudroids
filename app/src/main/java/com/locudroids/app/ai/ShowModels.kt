package com.locudroids.app.ai

/** Momento de la canción en el que habla el locutor. */
enum class Slot(val key: String) {
    INTRO("intro"),
    MID("mid"),
    OUTRO("outro");

    companion object {
        fun fromKey(key: String): Slot? = entries.firstOrNull { it.key == key.trim().lowercase() }
    }
}

/** Qué cortes lleva una canción. [introIsTag] = el corte anterior ya la presentó, el intro es solo un remate corto. */
data class TrackPlan(val slots: Set<Slot>, val introIsTag: Boolean) {
    companion object {
        val NONE = TrackPlan(emptySet(), false)
    }
}

data class SongInfo(
    val title: String,
    val artist: String?,
    val album: String? = null,
    val year: Int? = null,
    /** Lo medido en el audio (ritmo y energía) y el ánimo detectado, ya redactado; null si no se ha analizado. */
    val measured: String? = null,
) {
    /** Forma natural de decirlo en voz alta. */
    fun spoken(): String = if (artist != null) "$title, de $artist" else title

    /** Datos disponibles de las etiquetas del archivo. */
    fun details(): String = buildList {
        add("título: $title")
        artist?.let { add("artista: $it") }
        album?.let { add("álbum: $it") }
        year?.let { add("año: $it") }
        measured?.let { add("medido en el audio: $it") }
    }.joinToString("; ")
}

/** Una frase de un locutor. [style] = cómo la dice (para la voz). */
data class ScriptLine(val who: String, val style: String?, val text: String)

/** Un corte completo: uno o varios locutores hablando en [slot]. */
data class ShowCue(val slot: Slot, val kind: String, val lines: List<ScriptLine>)

/** Guion de una canción. [usedAi] = false cuando se usó el guion de respaldo. */
data class Show(
    val mood: String?,
    val energy: Int,
    val cues: List<ShowCue>,
    val usedAi: Boolean,
    val error: String?,
)

private val TAG_REGEX = Regex("<[^>]*>")
private val SPACES_REGEX = Regex("\\s+")

/** Quita las marcas como <laugh> o <short pause> (para mostrar el texto o leerlo con la voz de Android). */
fun String.stripTags(): String = replace(TAG_REGEX, " ").replace(SPACES_REGEX, " ").trim()
