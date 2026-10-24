package com.locudroids.app.ai

import java.time.LocalTime

/** Hora dicha como la diría un locutor: "las cuatro y diez de la tarde". */
object SpokenTime {

    private val small = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
        "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
        "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    )

    private fun number(n: Int): String = when {
        n < 30 -> small[n]
        n % 10 == 0 -> when (n) {
            30 -> "treinta"
            40 -> "cuarenta"
            else -> "cincuenta"
        }
        else -> {
            val tens = when (n / 10) {
                3 -> "treinta"
                4 -> "cuarenta"
                else -> "cincuenta"
            }
            "$tens y ${small[n % 10]}"
        }
    }

    fun format(time: LocalTime): String {
        val h24 = time.hour
        val minute = time.minute
        var hour = h24 % 12
        if (hour == 0) hour = 12

        val hourText = if (hour == 1) "la una" else "las ${small[hour]}"
        val minuteText = when (minute) {
            0 -> " en punto"
            15 -> " y cuarto"
            30 -> " y media"
            else -> " y ${number(minute)}"
        }
        val period = when (h24) {
            in 0..5 -> "de la madrugada"
            in 6..11 -> "de la mañana"
            12 -> "del día"
            in 13..18 -> "de la tarde"
            else -> "de la noche"
        }
        return "$hourText$minuteText $period"
    }
}
