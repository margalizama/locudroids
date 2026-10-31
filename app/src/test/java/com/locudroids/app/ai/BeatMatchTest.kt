package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeatMatchTest {

    @Test
    fun planStretchesWithinLimits() {
        val p = BeatMatch.plan(bpmOut = 120f, bpmIn = 118f)!!
        assertEquals(120f / 118f, p.rate, 0.001f)
        assertEquals(1f, p.m, 0f)
    }

    @Test
    fun planUsesDoubleTime() {
        val p = BeatMatch.plan(bpmOut = 128f, bpmIn = 64f)!!
        assertEquals(1f, p.rate, 0.001f)
        assertEquals(0.5f, p.m, 0f)
        val q = BeatMatch.plan(bpmOut = 70f, bpmIn = 140f)!!
        assertEquals(2f, q.m, 0f)
    }

    @Test
    fun planRefusesBigDifferences() {
        assertNull(BeatMatch.plan(bpmOut = 100f, bpmIn = 133f))
        assertNull(BeatMatch.plan(bpmOut = 0f, bpmIn = 120f))
    }

    @Test
    fun lockConvergesFromAnOffset() {
        val plan = BeatMatch.plan(120f, 118f)!!
        val pOut = 500f
        val pIn = 60000f / 118f
        val lock = BeatMatch.Lock(plan, oIn = 40f, pIn = pIn, oOut = 100f, pOut = pOut)

        var outPos = 100_000L
        var inPos = 0L
        // Empezamos con los golpes desalineados unos ~180 ms.
        inPos = (40f + 20 * pIn + 180f * plan.rate).toLong()
        var speed = plan.rate
        var error = 0f
        for (step in 0 until 150) { // 15 s en pasos de 100 ms
            outPos += 100
            inPos += (100 * speed).toLong()
            error = lock.errorMs(inPos, outPos)
            speed = plan.rate * lock.correction(inPos, outPos)
        }
        assertTrue("error final $error", abs(error) < 25f)
    }

    @Test
    fun startPositionPutsFirstBeatOnABeat() {
        val plan = BeatMatch.plan(120f, 120f)!!
        val start = BeatMatch.startPosition(
            plan, oIn = 80f, pIn = 500f, oOut = 130f, pOut = 500f,
            outPos = 200_000f, minStart = 0f, latencyMs = 0f,
        )
        // Debe caer en un golpe de la que entra (80 + k*500) y no antes de 0.
        assertTrue(start >= 0)
        assertEquals(0f, ((start - 80f) % 500f + 500f) % 500f, 1f)
    }
}
