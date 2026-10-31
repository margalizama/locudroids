package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoodTest {

    @Test
    fun normalizesAccentsAndSynonyms() {
        assertEquals("romantica", Moods.normalize("Romántica"))
        assertEquals("fiesta", Moods.normalize("bailable"))
        assertEquals("tranquila", Moods.normalize(" Relajada "))
        assertNull(Moods.normalize("desconocido"))
        assertNull(Moods.normalize(null))
    }

    @Test
    fun parsesGeminiAnswerAndIgnoresJunk() {
        val raw = """texto {"songs":[{"i":0,"mood":"Fiesta","valence":5},{"i":1,"mood":"???"},{"i":2,"mood":"triste","valence":9},{"mood":"alegre"}]} fin"""
        val out = MoodParser.parse(raw)
        assertEquals(2, out.size)
        assertEquals("fiesta", out[0]!!.mood)
        assertEquals(5, out[0]!!.valence)
        assertEquals(5, out[2]!!.valence) // se limita a 1..5
        assertTrue(MoodParser.parse("no json").isEmpty())
    }

    @Test
    fun tempoDistanceAllowsHalfAndDoubleTime() {
        assertEquals(0f, MoodMixer.tempoDistance(60f, 120f), 0.01f)
        assertEquals(5f, MoodMixer.tempoDistance(120f, 125f), 0.01f)
    }

    @Test
    fun chainPrefersSimilarSongs() {
        val profiles = mapOf(
            "calma1" to SongProfile(bpm = 70f, energy = 0.2f, mood = "tranquila"),
            "calma2" to SongProfile(bpm = 72f, energy = 0.25f, mood = "tranquila"),
            "fiesta1" to SongProfile(bpm = 128f, energy = 0.9f, mood = "fiesta"),
            "fiesta2" to SongProfile(bpm = 126f, energy = 0.85f, mood = "fiesta"),
        )
        val chain = MoodMixer.chain("calma1", profiles.keys.toList(), profiles, 4, Random(1))
        assertEquals("calma1", chain.first())
        assertEquals(4, chain.size)
        // La otra tranquila debe quedar antes que cualquier fiesta.
        assertTrue(chain.indexOf("calma2") < chain.indexOf("fiesta1"))
    }

    @Test
    fun rampOrdersByEnergyAndKeepsUnknownLast() {
        val profiles = mapOf(
            "a" to SongProfile(energy = 0.8f),
            "b" to SongProfile(energy = 0.2f),
            "c" to SongProfile(),
        )
        assertEquals(listOf("b", "a", "c"), MoodMixer.ramp(listOf("a", "b", "c"), profiles, up = true))
        assertEquals(listOf("a", "b", "c"), MoodMixer.ramp(listOf("a", "b", "c"), profiles, up = false))
    }

    @Test
    fun moodCountsOnlyExistingSongs() {
        val profiles = mapOf(
            "a" to SongProfile(mood = "fiesta"),
            "b" to SongProfile(mood = "fiesta"),
            "gone" to SongProfile(mood = "triste"),
        )
        assertEquals(listOf("fiesta" to 2), MoodMixer.moodCounts(profiles, setOf("a", "b")))
    }
}
