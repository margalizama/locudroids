package com.locudroids.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiHealthTest {

    @Test
    fun quotaPausesTenMinutesThenRecovers() {
        GeminiHealth.clear()
        GeminiHealth.report("HTTP 429 cuota", now = 0L)
        assertTrue(GeminiHealth.isBlocked(now = 1_000L))
        assertEquals(10, GeminiHealth.remainingMinutes(now = 1_000L))
        assertFalse(GeminiHealth.isBlocked(now = GeminiHealth.QUOTA_PAUSE_MS + 1))
        GeminiHealth.clear()
    }

    @Test
    fun pausesDependOnError() {
        assertEquals(GeminiHealth.CONFIG_PAUSE_MS, GeminiHealth.pauseFor("HTTP 403 clave"))
        assertEquals(GeminiHealth.OTHER_PAUSE_MS, GeminiHealth.pauseFor("timeout"))
    }
}
