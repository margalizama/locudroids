package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuePlannerTest {

    private val song = 200_000L

    @Test
    fun susurro_habla_cada_cuatro_canciones() {
        val hablan = (1..8).filter { Slot.INTRO in CuePlanner.plan(1, it, song, false).slots }
        assertEquals(listOf(1, 5), hablan)
    }

    @Test
    fun suave_habla_una_de_cada_dos() {
        val hablan = (1..6).filter { Slot.INTRO in CuePlanner.plan(2, it, song, false).slots }
        assertEquals(listOf(1, 3, 5), hablan)
    }

    @Test
    fun radio_habla_al_inicio_de_todas() {
        for (n in 1..5) {
            assertEquals(setOf(Slot.INTRO), CuePlanner.plan(3, n, song, false).slots)
        }
    }

    @Test
    fun intenso_presenta_la_siguiente_en_canciones_impares() {
        assertEquals(setOf(Slot.INTRO, Slot.OUTRO), CuePlanner.plan(4, 1, song, false).slots)
        assertEquals(setOf(Slot.INTRO), CuePlanner.plan(4, 2, song, true).slots)
    }

    @Test
    fun el_intro_es_remate_si_el_corte_anterior_ya_presento_la_cancion() {
        assertTrue(CuePlanner.plan(4, 2, song, prevHadOutro = true).introIsTag)
        assertFalse(CuePlanner.plan(4, 2, song, prevHadOutro = false).introIsTag)
        // La primera canción nunca es remate.
        assertFalse(CuePlanner.plan(5, 1, song, prevHadOutro = true).introIsTag)
    }

    @Test
    fun locura_agrega_mitad_solo_en_canciones_largas() {
        assertEquals(setOf(Slot.INTRO, Slot.MID, Slot.OUTRO), CuePlanner.plan(5, 3, 200_000L, false).slots)
        assertEquals(setOf(Slot.INTRO, Slot.OUTRO), CuePlanner.plan(5, 3, 120_000L, false).slots)
        assertEquals(setOf(Slot.INTRO, Slot.OUTRO), CuePlanner.plan(5, 3, null, false).slots)
    }

    @Test
    fun canciones_muy_cortas_no_llevan_final() {
        assertFalse(Slot.OUTRO in CuePlanner.plan(5, 3, 60_000L, false).slots)
    }
}
