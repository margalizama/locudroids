package com.locudroids.app.ai

/** Las voces en español del modelo Kokoro que va dentro de la app (id = número de hablante del modelo). */
object LocalVoices {
    const val DORA = 28
    const val ALEX = 29
    const val SANTA = 53

    val all: List<Pair<Int, String>> = listOf(
        DORA to "Dora · mujer, clara",
        ALEX to "Alex · hombre, firme",
        SANTA to "Santa · hombre, grave",
    )

    fun label(id: Int): String = all.firstOrNull { it.first == id }?.second ?: "Voz $id"
}

/** Preparación del texto y del audio para la voz local (no hay Android aquí: se prueba con tests normales). */
object LocalSpeech {
    /** Silencio entre una línea y otra de un diálogo. */
    const val GAP_MS = 240

    private val PAUSE = Regex("<\\s*short\\s*pause\\s*>", RegexOption.IGNORE_CASE)
    private val LAUGH = Regex("<\\s*laugh\\s*>", RegexOption.IGNORE_CASE)
    private val SIGH = Regex("<\\s*sigh\\s*>", RegexOption.IGNORE_CASE)

    private val FAST = listOf("enérgic", "energic", "rápid", "rapid", "grit", "aceler", "emocion", "euf", "intens", "animad", "entusias")
    private val SLOW = listOf("suave", "susurr", "bajo", "lent", "cálid", "calid", "triste", "romántic", "romantic", "nostálg", "nostalg", "tranquil", "íntim", "intim")

    /** Convierte las marcas del guion en algo que una voz sin marcas pueda decir. */
    fun prepare(text: String): String {
        val withSounds = text
            .replace(LAUGH, " ¡Ja, ja, ja! ")
            .replace(SIGH, " Ay... ")
            .replace(PAUSE, ", ")
        return withSounds.stripTags()
    }

    /** Velocidad según la indicación de estilo: con energía habla más rápido, con calidez más lento. */
    fun speedFor(style: String?, base: Float): Float {
        val s = style.orEmpty().lowercase()
        val delta = when {
            FAST.any { s.contains(it) } -> 0.12f
            SLOW.any { s.contains(it) } -> -0.10f
            else -> 0f
        }
        return ((base.coerceIn(0.7f, 1.5f) + delta)).coerceIn(0.75f, 1.45f)
    }

    /** Junta los tramos de audio (con silencio entre ellos) y normaliza el volumen a PCM de 16 bits. */
    fun toPcm(chunks: List<FloatArray>, sampleRate: Int, gapMs: Int = GAP_MS): Pcm {
        val gap = (sampleRate * gapMs / 1000).coerceAtLeast(0)
        val total = chunks.sumOf { it.size } + gap * (chunks.size - 1).coerceAtLeast(0)
        var peak = 0f
        for (c in chunks) for (v in c) {
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
        }
        val gain = if (peak > 0.0001f) (0.9f / peak).coerceAtMost(4f) else 1f
        val bytes = ByteArray(total * 2)
        var pos = 0
        chunks.forEachIndexed { i, chunk ->
            if (i > 0) pos += gap * 2
            for (v in chunk) {
                val s = (v * gain * 32767f).toInt().coerceIn(-32768, 32767)
                bytes[pos] = (s and 0xFF).toByte()
                bytes[pos + 1] = ((s shr 8) and 0xFF).toByte()
                pos += 2
            }
        }
        return Pcm(bytes, sampleRate)
    }
}
