package com.locudroids.app.player

import androidx.media3.common.Player

/**
 * Reparte el volumen entre quien lo baja para que hablen los locutores ([duck]) y la mezcla entre canciones
 * ([mainMix] para la canción que entra, [tailMix] para la cola de la que sale). Todo en el hilo principal.
 */
class VolumeBus(private val main: Player) {
    var duck = 1f
        set(value) {
            field = value
            apply()
        }
    var mainMix = 1f
    var tailMix = 0f
    var tail: Player? = null

    fun apply() {
        main.volume = (duck * mainMix).coerceIn(0f, 1f)
        tail?.volume = (duck * tailMix).coerceIn(0f, 1f)
    }

    fun setMix(main: Float, tail: Float) {
        mainMix = main
        tailMix = tail
        apply()
    }
}
