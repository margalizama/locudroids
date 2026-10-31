package com.locudroids.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsParserTest {

    private val xml = """
        <rss><channel><title>Google Noticias</title>
        <item><title>Inauguran nuevo parque lineal en la ciudad con juegos &amp; áreas verdes - El Diario</title><link>x</link></item>
        <item><title><![CDATA[El equipo local gana el clásico por la mínima diferencia - Deportes MX]]></title></item>
        <item><title>Corto</title></item>
        <item><title>Inauguran nuevo parque lineal en la ciudad con juegos &amp; áreas verdes - Otro Medio</title></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun cleansTitlesAndDropsSourceAndDuplicates() {
        val list = NewsParser.parse(xml, 5)
        assertEquals(2, list.size)
        assertEquals("Inauguran nuevo parque lineal en la ciudad con juegos & áreas verdes", list[0])
        assertTrue(list[1].startsWith("El equipo local gana"))
        assertTrue(!list[1].contains("Deportes MX"))
    }

    @Test
    fun respectsLimitAndHandlesGarbage() {
        assertEquals(1, NewsParser.parse(xml, 1).size)
        assertTrue(NewsParser.parse("no es xml", 5).isEmpty())
    }
}
