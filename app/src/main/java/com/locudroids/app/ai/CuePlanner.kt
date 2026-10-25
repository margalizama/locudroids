package com.locudroids.app.ai

data class LevelInfo(
    val level: Int,
    val name: String,
    val detail: String,
    val maxWords: Int,
    /** true = los locutores pueden platicar entre ellos dentro de un mismo corte. */
    val dialogue: Boolean,
)

object Levels {
    val all = listOf(
        LevelInfo(1, "Susurro", "Un comentario corto cada 4 canciones", 22, false),
        LevelInfo(2, "Suave", "Un corte al inicio de una de cada 2 canciones", 32, false),
        LevelInfo(3, "Radio", "Un corte al inicio de cada canción", 45, false),
        LevelInfo(4, "Intenso", "Inicio de cada canción, y presentación de la siguiente cada 2 canciones, con charla entre locutores", 70, true),
        LevelInfo(5, "Locura", "Programa completo: inicio, mitad y final de cada canción, con anuncios, promos y diálogos", 90, true),
    )

    fun of(level: Int): LevelInfo = all[level.coerceIn(1, 5) - 1]
}

/** Decide en qué momentos de cada canción habla el locutor, según el nivel de intensidad. */
object CuePlanner {
    const val MIN_MID_DURATION_MS = 150_000L
    const val MIN_OUTRO_DURATION_MS = 75_000L

    /**
     * @param n número de canción de la sesión (1 = la primera).
     * @param durationMs duración de la canción si se conoce.
     * @param prevHadOutro la canción anterior terminó con un corte que presentó a esta.
     */
    fun plan(level: Int, n: Int, durationMs: Long?, prevHadOutro: Boolean): TrackPlan {
        val slots = mutableSetOf<Slot>()
        when (level.coerceIn(1, 5)) {
            1 -> if ((n - 1) % 4 == 0) slots += Slot.INTRO
            2 -> if ((n - 1) % 2 == 0) slots += Slot.INTRO
            3 -> slots += Slot.INTRO
            4 -> {
                slots += Slot.INTRO
                if (n % 2 == 1) slots += Slot.OUTRO
            }
            else -> {
                slots += Slot.INTRO
                slots += Slot.OUTRO
                if ((durationMs ?: 0L) >= MIN_MID_DURATION_MS) slots += Slot.MID
            }
        }
        if (durationMs != null && durationMs < MIN_OUTRO_DURATION_MS) slots -= Slot.OUTRO
        val tag = Slot.INTRO in slots && prevHadOutro && n > 1
        return TrackPlan(slots, tag)
    }
}
