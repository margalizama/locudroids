package com.locudroids.app.ai

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Lo que se mide de un tramo de audio. [beatOffsetMs] es el primer golpe de compás dentro del tramo. */
data class AudioFeatures(
    val bpm: Float,
    val beatOffsetMs: Long,
    /** 0 (muy suave) a 1 (muy fuerte y denso). */
    val energy: Float,
    /** 0 (graves, oscuro) a 1 (brillante). */
    val brightness: Float,
    /** 0 a 1: qué tan marcado es el ritmo. Con valores bajos el BPM no es fiable. */
    val confidence: Float,
)

/** Análisis de ritmo y energía sobre audio mono (sin Android: se prueba con tests normales). */
object AudioAnalyzer {
    private const val FRAME = 1024
    private const val HOP = 256
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 190f

    fun analyze(samples: FloatArray, sampleRate: Int): AudioFeatures? {
        if (samples.size < sampleRate * 8) return null

        val energy = energyOf(samples)
        val (flux, brightness) = onsetEnvelope(samples, sampleRate)
        if (flux.size < 64) return null
        val fps = sampleRate.toFloat() / HOP

        val centered = flux.copyOf()
        val mean = centered.average().toFloat()
        for (i in centered.indices) centered[i] -= mean

        val lagMin = (fps * 60f / MAX_BPM).toInt().coerceAtLeast(2)
        val lagMax = min((fps * 60f / MIN_BPM).toInt(), centered.size / 2)
        if (lagMax <= lagMin + 2) return null

        val acf0 = acf(centered, 0)
        if (acf0 <= 1e-9f) return null
        val scores = FloatArray(lagMax + 2)
        var bestLag = -1
        var bestScore = 0f
        for (lag in lagMin..lagMax) {
            val bpm = 60f * fps / lag
            val octaves = ln(bpm / 120f) / ln(2f)
            val prior = exp(-0.5f * (octaves / 0.8f).pow(2))
            val s = acf(centered, lag) / acf0 * prior
            scores[lag] = s
            if (s > bestScore) {
                bestScore = s
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // Interpolación parabólica para no quedar limitado a la resolución del cuadro.
        var lagF = bestLag.toFloat()
        if (bestLag in (lagMin + 1) until lagMax) {
            val a = scores[bestLag - 1]
            val b = scores[bestLag]
            val c = scores[bestLag + 1]
            val denom = a - 2 * b + c
            if (abs(denom) > 1e-9f) lagF += 0.5f * (a - c) / denom
        }
        val bpm = 60f * fps / lagF

        // Fase: el desplazamiento que más coincide con golpes de la envolvente.
        var bestOffset = 0
        var bestSum = -1f
        val period = lagF
        val steps = period.toInt().coerceAtLeast(1)
        for (o in 0 until steps) {
            var sum = 0f
            var t = o.toFloat()
            while (t < flux.size) {
                sum += flux[t.toInt()]
                t += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestOffset = o
            }
        }

        return AudioFeatures(
            bpm = bpm,
            beatOffsetMs = (bestOffset * 1000f / fps).toLong(),
            energy = energy,
            brightness = brightness,
            confidence = (bestScore / 0.6f).coerceIn(0f, 1f),
        )
    }

    private fun acf(x: FloatArray, lag: Int): Float {
        var sum = 0f
        val n = x.size - lag
        for (i in 0 until n) sum += x[i] * x[i + lag]
        return sum / n
    }

    /** Volumen medio de la pista llevado a 0..1 (−32 dBFS = 0, −8 dBFS = 1). */
    private fun energyOf(samples: FloatArray): Float {
        var sum = 0.0
        for (v in samples) sum += v * v
        val rms = sqrt(sum / samples.size).toFloat()
        val db = 20f * log10(rms + 1e-9f)
        return ((db + 32f) / 24f).coerceIn(0f, 1f)
    }

    /** Flujo espectral (cuánto "aparece" de golpe) por cuadro, y el brillo medio. */
    private fun onsetEnvelope(samples: FloatArray, sampleRate: Int): Pair<FloatArray, Float> {
        val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1))).toFloat() }
        val frames = (samples.size - FRAME) / HOP + 1
        val maxBin = min(FRAME / 2 - 1, (6000f * FRAME / sampleRate).toInt())
        val prev = FloatArray(maxBin + 1)
        val flux = FloatArray(frames)
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        var centroidSum = 0.0
        var centroidN = 0
        for (f in 0 until frames) {
            val base = f * HOP
            for (i in 0 until FRAME) {
                re[i] = samples[base + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var f0 = 0f
            var num = 0.0
            var den = 0.0
            for (b in 1..maxBin) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                val lg = ln(1f + 100f * mag)
                val d = lg - prev[b]
                if (d > 0) f0 += d
                prev[b] = lg
                num += b * mag.toDouble()
                den += mag
            }
            flux[f] = f0
            if (den > 1e-6) {
                centroidSum += num / den
                centroidN++
            }
        }
        val centroid = if (centroidN > 0) (centroidSum / centroidN).toFloat() / maxBin else 0f
        return flux to (centroid * 2.5f).coerceIn(0f, 1f)
    }

    /** FFT radix-2 en el lugar. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
