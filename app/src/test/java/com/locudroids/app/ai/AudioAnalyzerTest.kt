package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class AudioAnalyzerTest {

    private val rate = 22_050

    /** Bombo sintético (seno grave con caída rápida) en cada tiempo, más un poco de tono constante. */
    private fun kickTrack(bpm: Float, seconds: Int, firstBeatMs: Int = 0, amp: Float = 0.8f): FloatArray {
        val out = FloatArray(rate * seconds)
        val periodSamples = rate * 60.0 / bpm
        var t = firstBeatMs / 1000.0 * rate
        while (t < out.size) {
            val start = t.toInt()
            for (i in 0 until 2_200) {
                val idx = start + i
                if (idx >= out.size) break
                val time = i.toDouble() / rate
                out[idx] += (amp * exp(-time * 35) * sin(2 * PI * 70 * time)).toFloat()
            }
            t += periodSamples
        }
        return out
    }

    @Test
    fun detectsTempoOf120() {
        val f = AudioAnalyzer.analyze(kickTrack(120f, 30), rate)
        assertNotNull(f)
        assertEquals(120f, f!!.bpm, 3f)
        assertTrue(f.confidence > 0.3f)
    }

    @Test
    fun detectsTempoOf100() {
        val f = AudioAnalyzer.analyze(kickTrack(100f, 30), rate)!!
        assertEquals(100f, f.bpm, 3f)
    }

    @Test
    fun findsBeatPhase() {
        val f = AudioAnalyzer.analyze(kickTrack(120f, 30, firstBeatMs = 200), rate)!!
        // El periodo es 500 ms: la fase debe caer cerca de 200 ms (módulo el periodo).
        val phase = f.beatOffsetMs % 500
        assertTrue("fase $phase", kotlin.math.abs(phase - 200) < 60)
    }

    @Test
    fun loudTrackHasMoreEnergyThanQuiet() {
        val loud = AudioAnalyzer.analyze(kickTrack(120f, 20, amp = 0.9f), rate)!!
        val quiet = AudioAnalyzer.analyze(kickTrack(120f, 20, amp = 0.03f), rate)!!
        assertTrue(loud.energy > quiet.energy)
    }

    @Test
    fun tooShortAudioIsRejected() {
        assertNull(AudioAnalyzer.analyze(FloatArray(rate * 3), rate))
    }
}
