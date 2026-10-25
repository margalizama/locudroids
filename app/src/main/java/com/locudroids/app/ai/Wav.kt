package com.locudroids.app.ai

/** Audio PCM de 16 bits, mono, en little-endian. */
class Pcm(val bytes: ByteArray, val sampleRate: Int) {
    val durationMs: Long get() = bytes.size * 1000L / (sampleRate * 2L)
}

/** Lee el audio que devuelve Gemini TTS: WAV con encabezado o PCM crudo. */
object Wav {
    private const val DEFAULT_RATE = 24_000

    fun toPcm(bytes: ByteArray, mime: String?): Pcm {
        val rateFromMime = Regex("rate=(\\d+)").find(mime.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return if (isRiff(bytes)) parseWav(bytes, rateFromMime ?: DEFAULT_RATE) else Pcm(bytes, rateFromMime ?: DEFAULT_RATE)
    }

    private fun isRiff(b: ByteArray): Boolean =
        b.size > 44 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()

    private fun parseWav(bytes: ByteArray, defaultRate: Int): Pcm {
        var rate = defaultRate
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = leInt(bytes, offset + 4)
            val body = offset + 8
            if (id == "fmt " && body + 8 <= bytes.size) rate = leInt(bytes, body + 4)
            if (id == "data") {
                val available = bytes.size - body
                val len = if (size <= 0 || size > available) available else size
                return Pcm(bytes.copyOfRange(body, body + len), rate)
            }
            if (size < 0) break
            offset = body + size + (size and 1)
        }
        return Pcm(bytes.copyOfRange(minOf(44, bytes.size), bytes.size), rate)
    }

    private fun leInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
