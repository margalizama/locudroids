package com.locudroids.app.ai

import kotlin.math.abs
import kotlin.random.Random

/** Lo que se sabe de una canción para armar mezclas. Todo opcional: falta información mientras no se analiza. */
data class SongProfile(
    val bpm: Float? = null,
    val energy: Float? = null,
    val brightness: Float? = null,
    val mood: String? = null,
    val valence: Int? = null,
    /** "gemini" o "audio". */
    val moodSource: String? = null,
)

/** Arma secuencias de canciones parecidas, como lo haría un DJ. Trabaja con rutas de archivo. */
object MoodMixer {

    /** Diferencia de tempo permitiendo medio tiempo y doble tiempo (60 y 120 se mezclan bien). */
    fun tempoDistance(a: Float, b: Float): Float =
        minOf(abs(a - b), abs(a - 2 * b), abs(2 * a - b))

    fun distance(a: SongProfile, b: SongProfile): Float {
        val tempo = if (a.bpm != null && b.bpm != null) tempoDistance(a.bpm, b.bpm) / 30f else 0.3f
        val energy = if (a.energy != null && b.energy != null) abs(a.energy - b.energy) * 2f else 0.3f
        val mood = if (a.mood != null && b.mood != null && a.mood != b.mood) 0.6f else 0f
        val valence = if (a.valence != null && b.valence != null) abs(a.valence - b.valence) / 4f * 0.5f else 0f
        return tempo + energy + mood + valence
    }

    /**
     * Cadena de canciones donde cada una se parece a la anterior (ritmo, energía y ánimo).
     * Escoge al azar entre las 3 más cercanas para que no sea siempre igual.
     */
    fun chain(
        start: String?,
        pool: List<String>,
        profiles: Map<String, SongProfile>,
        limit: Int,
        random: Random = Random.Default,
    ): List<String> {
        val remaining = pool.distinct().toMutableList()
        if (remaining.isEmpty()) return emptyList()
        val first = start?.takeIf { it in remaining } ?: remaining.random(random)
        val out = mutableListOf(first)
        remaining.remove(first)
        var current = first
        while (out.size < limit && remaining.isNotEmpty()) {
            val from = profiles[current] ?: SongProfile()
            val nearest = remaining
                .sortedBy { distance(from, profiles[it] ?: SongProfile()) }
                .take(3)
            current = nearest.random(random)
            remaining.remove(current)
            out += current
        }
        return out
    }

    /** Ordena de menor a mayor energía (subiendo) o al revés; las que no tienen medida van al final. */
    fun ramp(paths: List<String>, profiles: Map<String, SongProfile>, up: Boolean): List<String> {
        val (known, unknown) = paths.partition { profiles[it]?.energy != null }
        val sorted = if (up) known.sortedBy { profiles[it]!!.energy } else known.sortedByDescending { profiles[it]!!.energy }
        return sorted + unknown
    }

    /** Cuántas canciones hay de cada ánimo, de más a menos. */
    fun moodCounts(profiles: Map<String, SongProfile>, existing: Set<String>): List<Pair<String, Int>> =
        profiles.entries
            .filter { it.key in existing && it.value.mood != null }
            .groupingBy { it.value.mood!! }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
}
