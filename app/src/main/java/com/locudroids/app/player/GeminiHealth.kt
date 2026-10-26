package com.locudroids.app.player

/**
 * Recuerda si la voz de Gemini está fallando para no insistir en vano: una cuota diaria agotada (HTTP 429)
 * no se arregla reintentando. Mientras dura la pausa, los cortes salen con la voz local.
 */
object GeminiHealth {
    const val QUOTA_PAUSE_MS = 10 * 60_000L
    const val CONFIG_PAUSE_MS = 60 * 60_000L
    const val OTHER_PAUSE_MS = 2 * 60_000L

    @Volatile private var blockedUntil = 0L

    @Volatile var reason: String? = null
        private set

    fun isBlocked(now: Long = System.currentTimeMillis()): Boolean = now < blockedUntil

    fun remainingMinutes(now: Long = System.currentTimeMillis()): Int =
        (((blockedUntil - now).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()

    /** Pausa de Gemini según el error: cuota, clave o modelo mal puestos, u otro fallo pasajero. */
    fun report(message: String, now: Long = System.currentTimeMillis()) {
        reason = message
        blockedUntil = now + pauseFor(message)
    }

    fun clear() {
        blockedUntil = 0L
        reason = null
    }

    fun pauseFor(message: String): Long = when {
        message.startsWith("HTTP 429") || message.startsWith("HTTP 503") -> QUOTA_PAUSE_MS
        message.startsWith("HTTP 400") || message.startsWith("HTTP 401") ||
            message.startsWith("HTTP 403") || message.startsWith("HTTP 404") -> CONFIG_PAUSE_MS
        else -> OTHER_PAUSE_MS
    }
}
