package com.locudroids.app.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavTest {

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun wav(data: ByteArray, rate: Int, dataSize: Int = data.size): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray()); out.write(le32(36 + data.size)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le32(16))
        out.write(byteArrayOf(1, 0, 1, 0)) // PCM, mono
        out.write(le32(rate)); out.write(le32(rate * 2)); out.write(byteArrayOf(2, 0, 16, 0))
        out.write("data".toByteArray()); out.write(le32(dataSize)); out.write(data)
        return out.toByteArray()
    }

    @Test
    fun lee_wav_con_encabezado() {
        val audio = ByteArray(48_000) { (it % 100).toByte() }
        val pcm = Wav.toPcm(wav(audio, 24_000), "audio/wav")
        assertEquals(24_000, pcm.sampleRate)
        assertArrayEquals(audio, pcm.bytes)
        assertEquals(1_000L, pcm.durationMs)
    }

    @Test
    fun tolera_tamano_de_datos_invalido_en_wav_en_streaming() {
        val audio = ByteArray(1_000) { 7 }
        val pcm = Wav.toPcm(wav(audio, 16_000, dataSize = -1), null)
        assertEquals(16_000, pcm.sampleRate)
        assertArrayEquals(audio, pcm.bytes)
    }

    @Test
    fun pcm_crudo_toma_la_frecuencia_del_mime() {
        val raw = ByteArray(96_000)
        val pcm = Wav.toPcm(raw, "audio/L16;codec=pcm;rate=24000")
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(2_000L, pcm.durationMs)
    }
}
