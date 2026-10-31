package com.locudroids.app.ai

import org.json.JSONException
import org.json.JSONObject
import java.text.Normalizer

/** Estados de ánimo con los que se clasifica la música. */
object Moods {
    class Mood(val key: String, val label: String)

    val all = listOf(
        Mood("fiesta", "Fiesta"),
        Mood("energica", "Energía"),
        Mood("alegre", "Alegre"),
        Mood("romantica", "Romántica"),
        Mood("nostalgica", "Nostálgica"),
        Mood("triste", "Triste"),
        Mood("tranquila", "Tranquila"),
        Mood("epica", "Épica"),
        Mood("rebelde", "Rebelde"),
    )

    private val synonyms = mapOf(
        "bailable" to "fiesta", "baile" to "fiesta", "party" to "fiesta", "festiva" to "fiesta",
        "energetica" to "energica", "energia" to "energica", "intensa" to "energica", "motivadora" to "energica",
        "feliz" to "alegre", "optimista" to "alegre", "divertida" to "alegre",
        "amor" to "romantica", "sensual" to "romantica",
        "nostalgia" to "nostalgica", "melancolica" to "nostalgica",
        "melancolia" to "nostalgica", "tristeza" to "triste", "oscura" to "triste",
        "relajada" to "tranquila", "calma" to "tranquila", "suave" to "tranquila", "chill" to "tranquila",
        "epico" to "epica", "dramatica" to "epica", "orgullosa" to "epica",
        "rebeldia" to "rebelde", "agresiva" to "rebelde", "protesta" to "rebelde",
    )

    private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Convierte lo que escribió el modelo ("Romántica", "bailable"...) en una de las claves; null si no se reconoce. */
    fun normalize(raw: String?): String? {
        val clean = Normalizer.normalize(raw.orEmpty().trim().lowercase(), Normalizer.Form.NFD).replace(accents, "")
        if (clean.isEmpty()) return null
        return all.firstOrNull { it.key == clean }?.key ?: synonyms[clean]
    }

    fun label(key: String?): String = all.firstOrNull { it.key == key }?.label ?: key.orEmpty()

    /** Ánimo aproximado solo con lo medido en el audio (cuando Gemini no está disponible). */
    fun guessFromAudio(bpm: Float?, energy: Float?): String? {
        if (bpm == null || energy == null) return null
        return when {
            energy >= 0.7f && bpm >= 115f -> "fiesta"
            energy >= 0.7f -> "energica"
            energy >= 0.45f && bpm >= 100f -> "alegre"
            energy < 0.3f -> "tranquila"
            bpm < 90f -> "romantica"
            else -> "alegre"
        }
    }
}

/** Instrucciones y lectura de la respuesta de Gemini al clasificar canciones por ánimo. */
object MoodPrompts {
    val system: String = "Eres un experto en música. Clasificas canciones por su estado de ánimo usando título, artista, álbum y año. " +
        "Si no conoces la canción, deduce por el título y el estilo probable del artista; no te niegues a clasificar.\n" +
        "Estados permitidos (usa exactamente estas claves): ${Moods.all.joinToString(", ") { it.key }}.\n" +
        "\"valence\" es de 1 (muy triste u oscura) a 5 (muy alegre).\n" +
        "Devuelve ÚNICAMENTE JSON válido con esta forma: {\"songs\":[{\"i\":0,\"mood\":\"fiesta\",\"valence\":4}]} " +
        "con un objeto por canción, usando el mismo número \"i\" que se te dio."

    fun user(songs: List<Triple<String, String?, String?>>, years: List<Int?>): String = buildString {
        appendLine("Clasifica estas canciones (número | título | artista | álbum | año):")
        songs.forEachIndexed { i, (title, artist, album) ->
            appendLine("$i | $title | ${artist ?: "?"} | ${album ?: "?"} | ${years.getOrNull(i) ?: "?"}")
        }
    }
}

object MoodParser {
    class Result(val mood: String, val valence: Int?)

    /** índice → ánimo. Ignora entradas inválidas; devuelve vacío si la respuesta no sirve. */
    fun parse(raw: String): Map<Int, Result> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        val root = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            return emptyMap()
        }
        val arr = root.optJSONArray("songs") ?: return emptyMap()
        val out = HashMap<Int, Result>()
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            if (!o.has("i")) continue
            val mood = Moods.normalize(o.optString("mood", "")) ?: continue
            val valence = if (o.has("valence")) o.optInt("valence", 3).coerceIn(1, 5) else null
            out[o.optInt("i", -1)] = Result(mood, valence)
        }
        return out
    }
}
