package com.locudroids.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteOrder

/** Audio mono ya reducido de frecuencia, listo para analizar. */
class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

/** Decodifica un tramo de un archivo de audio a mono flotante (~22 kHz) con MediaCodec. */
object AudioDecoder {

    private const val TIMEOUT_US = 10_000L
    private const val MAX_WALL_MS = 25_000L

    /** Devuelve null si el archivo no se puede leer. [alive] permite cancelar a mitad de la decodificación. */
    fun decode(
        context: Context,
        uri: Uri,
        startMs: Long,
        lengthMs: Long,
        targetRate: Int = 22_050,
        alive: () -> Boolean = { true },
    ): DecodedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            if (startMs > 0) extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var floatPcm = false
            var factor = (sampleRate.toFloat() / targetRate).toInt().coerceAtLeast(1)

            val wanted = (lengthMs / 1000.0 * sampleRate / factor).toInt()
            val out = FloatArray(wanted)
            var count = 0
            var acc = 0f
            var accN = 0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val began = SystemClock.elapsedRealtime()

            while (count < wanted && alive() && SystemClock.elapsedRealtime() - began < MAX_WALL_MS) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            buf.order(ByteOrder.nativeOrder())
                            if (floatPcm) {
                                val fb = buf.asFloatBuffer()
                                while (fb.remaining() >= channels && count < wanted) {
                                    var s = 0f
                                    for (c in 0 until channels) s += fb.get()
                                    acc += s / channels
                                    if (++accN == factor) {
                                        out[count++] = acc / factor
                                        acc = 0f
                                        accN = 0
                                    }
                                }
                            } else {
                                val sb = buf.asShortBuffer()
                                while (sb.remaining() >= channels && count < wanted) {
                                    var s = 0f
                                    for (c in 0 until channels) s += sb.get() / 32768f
                                    acc += s / channels
                                    if (++accN == factor) {
                                        out[count++] = acc / factor
                                        acc = 0f
                                        accN = 0
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                        factor = (sampleRate.toFloat() / targetRate).toInt().coerceAtLeast(1)
                    }
                }
            }
            if (count < sampleRate / factor * 4) return null
            return DecodedAudio(out.copyOf(count), sampleRate / factor)
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }
}
