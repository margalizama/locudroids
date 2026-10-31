package com.locudroids.app.ai

import kotlin.math.abs
import kotlin.math.min

/**
 * Matemática de la mezcla con ritmo (sin Android, se prueba con tests normales).
 *
 * Al mezclar, la canción que sale ("out") sigue a su velocidad y la que entra ("in") se acelera o frena un poco
 * para llevar el mismo tempo; después un lazo de fase la empuja suavemente para que sus golpes caigan juntos.
 * Los golpes de cada canción son una rejilla: primer golpe [oOut]/[oIn] (ms dentro de la canción) y separación [pOut]/[pIn] (ms).
 */
object BeatMatch {

    /** [rate] = velocidad de la canción que entra; [m] = relación de compases usada (1, 2 o 0.5: medio y doble tiempo). */
    class Plan(val rate: Float, val m: Float)

    /** Null si habría que estirar más de [maxStretch] (queda un fundido normal). */
    fun plan(bpmOut: Float, bpmIn: Float, maxStretch: Float = 0.08f): Plan? {
        if (bpmOut <= 0f || bpmIn <= 0f) return null
        var best: Plan? = null
        for (m in floatArrayOf(1f, 2f, 0.5f)) {
            val r = bpmOut * m / bpmIn
            if (abs(r - 1f) <= maxStretch && (best == null || abs(r - 1f) < abs(best.rate - 1f))) best = Plan(r, m)
        }
        return best
    }

    private fun mod(x: Float, m: Float): Float {
        val r = x % m
        return if (r < 0) r + m else r
    }

    /** Lleva el error de fase al rango (−u/2, u/2]. */
    private fun wrap(x: Float, u: Float): Float {
        val r = mod(x, u)
        return if (r > u / 2) r - u else r
    }

    /**
     * Posición (ms) desde donde conviene empezar la canción que entra para que su primer golpe caiga con un golpe
     * de la que sale. [outPos] = dónde va la que sale al momento del cambio; [latencyMs] = lo que tarda en sonar
     * la que entra después de pedirla.
     */
    fun startPosition(
        plan: Plan,
        oIn: Float,
        pIn: Float,
        oOut: Float,
        pOut: Float,
        outPos: Float,
        minStart: Float,
        latencyMs: Float,
    ): Long {
        val wait = mod(oOut - outPos, pOut) // ms hasta el próximo golpe de la que sale
        val target = wait - latencyMs
        var start = oIn - plan.rate * target
        // El primer golpe aceptable: el más cercano a [minStart] sin quedar antes.
        while (start < minStart) start += pIn
        while (start - pIn >= minStart) start -= pIn
        return start.toLong()
    }

    /** Lazo de fase: devuelve el factor (cerca de 1) que se multiplica a la velocidad base para alinear los golpes. */
    class Lock(
        private val plan: Plan,
        private val oIn: Float,
        private val pIn: Float,
        private val oOut: Float,
        private val pOut: Float,
    ) {
        private val unit = min(pOut, pOut / plan.m)

        /** Error en ms: positivo = los golpes de la que entra llegan antes que los de la que sale. */
        fun errorMs(inPos: Long, outPos: Long): Float {
            val phaseOut = mod(outPos - oOut, unit)
            val phaseIn = mod(inPos - oIn, pIn) / plan.rate
            return wrap(phaseIn - phaseOut, unit)
        }

        fun correction(inPos: Long, outPos: Long): Float {
            val e = errorMs(inPos, outPos)
            val nudge = -(e / 150f).coerceIn(-1f, 1f) * 0.05f
            return 1f + nudge
        }
    }
}
