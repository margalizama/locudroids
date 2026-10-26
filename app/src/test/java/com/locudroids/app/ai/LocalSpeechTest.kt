package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSpeechTest {

    @Test
    fun prepareTurnsTagsIntoSpeech() {
        val out = LocalSpeech.prepare("Ey <short pause> qué onda <laugh> adiós <sigh>")
        assertTrue(out.contains("¡Ja, ja, ja!"))
        assertTrue(out.contains("Ay..."))
        assertTrue(!out.contains("<"))
    }

    @Test
    fun speedFollowsStyle() {
        assertTrue(LocalSpeech.speedFor("enérgico y rápido", 1f) > 1f)
        assertTrue(LocalSpeech.speedFor("suave y cálido", 1f) < 1f)
        assertEquals(1f, LocalSpeech.speedFor(null, 1f), 0.001f)
    }

    @Test
    fun toPcmJoinsChunksWithGapAndNormalizes() {
        val pcm = LocalSpeech.toPcm(listOf(floatArrayOf(0.5f, -0.5f), floatArrayOf(0.25f)), 1000, gapMs = 10)
        // 2 + 10 de silencio + 1 muestras, 2 bytes cada una
        assertEquals((2 + 10 + 1) * 2, pcm.bytes.size)
        val first = (pcm.bytes[0].toInt() and 0xFF) or (pcm.bytes[1].toInt() shl 8)
        assertTrue(first in 29000..30000) // 0.5 normalizado a pico 0.9
    }
}
