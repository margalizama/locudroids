package com.locudroids.app.ai

import org.json.JSONException
import org.json.JSONObject

/** Convierte el JSON que devuelve el modelo en un [Show]. Devuelve null si no sirve. */
object ShowParser {
    private const val MAX_LINES = 6
    private const val MAX_STYLE = 80
    private const val MAX_TEXT = 700
    private val spaces = Regex("\\s+")
    private val markdown = Regex("[*_`#]")

    fun parse(raw: String, allowed: Set<Slot>, announcerNames: List<String>): Show? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            return null
        }

        val mood = root.optString("mood", "").trim().take(30).ifEmpty { null }
        val energy = root.optInt("energy", 3).coerceIn(1, 5)
        val array = root.optJSONArray("cues") ?: return null

        val seen = mutableSetOf<Slot>()
        val cues = mutableListOf<ShowCue>()
        for (i in 0 until array.length()) {
            val cue = array.optJSONObject(i) ?: continue
            val slot = Slot.fromKey(cue.optString("slot", "")) ?: continue
            if (slot !in allowed || !seen.add(slot)) continue
            // En canciones tranquilas o tristes no se habla a media canción.
            if (slot == Slot.MID && energy <= 2) continue

            val linesJson = cue.optJSONArray("lines") ?: continue
            val lines = mutableListOf<ScriptLine>()
            for (j in 0 until minOf(linesJson.length(), MAX_LINES)) {
                val line = linesJson.optJSONObject(j) ?: continue
                val text = cleanText(line.optString("text", ""))
                if (text.isBlank()) continue
                val who = announcerNames.firstOrNull { it.equals(line.optString("who", "").trim(), ignoreCase = true) }
                    ?: announcerNames.firstOrNull()
                    ?: "Locutor"
                val style = line.optString("style", "").trim().take(MAX_STYLE).ifEmpty { null }
                lines += ScriptLine(who, style, text)
            }
            if (lines.isEmpty()) continue
            cues += ShowCue(slot, cue.optString("kind", "presentacion").trim().lowercase(), lines)
        }
        if (cues.isEmpty()) return null
        return Show(mood, energy, cues.sortedBy { it.slot.ordinal }, usedAi = true, error = null)
    }

    fun cleanText(raw: String): String {
        var t = raw
            .replace(markdown, "")
            .replace(spaces, " ")
            .trim()
            .trim('"', '“', '”', '«', '»')
            .trim()
        if (t.length > MAX_TEXT) {
            val cut = t.take(MAX_TEXT)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            t = if (end > 100) cut.take(end + 1) else cut
        }
        return t
    }
}
