package com.locudroids.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class SpokenTimeTest {

    @Test
    fun tarde() {
        assertEquals("las cuatro y diez de la tarde", SpokenTime.format(LocalTime.of(16, 10)))
    }

    @Test
    fun en_punto_y_la_una() {
        assertEquals("la una en punto de la tarde", SpokenTime.format(LocalTime.of(13, 0)))
        assertEquals("las doce en punto del día", SpokenTime.format(LocalTime.of(12, 0)))
    }

    @Test
    fun cuarto_y_media() {
        assertEquals("las nueve y cuarto de la mañana", SpokenTime.format(LocalTime.of(9, 15)))
        assertEquals("las doce y media de la madrugada", SpokenTime.format(LocalTime.of(0, 30)))
    }

    @Test
    fun minutos_compuestos() {
        assertEquals("las nueve y cuarenta y cinco de la mañana", SpokenTime.format(LocalTime.of(9, 45)))
        assertEquals("las diez y veintidós de la noche", SpokenTime.format(LocalTime.of(22, 22)))
    }
}
