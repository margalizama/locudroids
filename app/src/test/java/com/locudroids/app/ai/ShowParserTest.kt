package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShowParserTest {

    private val names = listOf("Rafa", "Vale")
    private val all = setOf(Slot.INTRO, Slot.MID, Slot.OUTRO)

    private fun json(energy: Int = 4) = """
        {"mood":"fiesta","energy":$energy,"cues":[
          {"slot":"intro","kind":"presentacion","lines":[
            {"who":"rafa","style":"enérgico","text":"¡Arrancamos con **esta** canción!"}]},
          {"slot":"mid","kind":"anuncio","lines":[
            {"who":"Rafa","style":"burlón","text":"Y ahora un comercial."},
            {"who":"Nadie","style":"","text":"¿Quién habla?"}]},
          {"slot":"outro","kind":"presentacion","lines":[
            {"who":"Vale","text":"Sigue lo mejor."}]}
        ]}
    """.trimIndent()

    @Test
    fun lee_un_guion_valido_y_limpia_el_texto() {
        val show = ShowParser.parse(json(), all, names)
        assertNotNull(show)
        show!!
        assertEquals("fiesta", show.mood)
        assertEquals(4, show.energy)
        assertEquals(listOf(Slot.INTRO, Slot.MID, Slot.OUTRO), show.cues.map { it.slot })
        // Mayúsculas del nombre se normalizan y el markdown se quita.
        assertEquals("Rafa", show.cues[0].lines[0].who)
        assertEquals("¡Arrancamos con esta canción!", show.cues[0].lines[0].text)
        // Un nombre desconocido se asigna al primer locutor.
        assertEquals("Rafa", show.cues[1].lines[1].who)
        assertNull(show.cues[1].lines[1].style)
    }

    @Test
    fun acepta_json_envuelto_en_texto_o_cercas_de_codigo() {
        val raw = "```json\n" + json() + "\n```"
        assertNotNull(ShowParser.parse(raw, all, names))
    }

    @Test
    fun ignora_cortes_que_no_se_pidieron() {
        val show = ShowParser.parse(json(), setOf(Slot.INTRO), names)!!
        assertEquals(listOf(Slot.INTRO), show.cues.map { it.slot })
    }

    @Test
    fun en_canciones_tranquilas_no_hay_corte_a_media_cancion() {
        val show = ShowParser.parse(json(energy = 2), all, names)!!
        assertEquals(listOf(Slot.INTRO, Slot.OUTRO), show.cues.map { it.slot })
    }

    @Test
    fun devuelve_null_si_no_sirve() {
        assertNull(ShowParser.parse("no es json", all, names))
        assertNull(ShowParser.parse("{\"mood\":\"x\"}", all, names))
        assertNull(ShowParser.parse("{\"cues\":[]}", all, names))
    }
}
