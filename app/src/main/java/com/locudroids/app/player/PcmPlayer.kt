package com.locudroids.app.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.locudroids.app.ai.Pcm
import kotlinx.coroutines.delay

/** Reproduce audio PCM (la voz de Gemini). Al cancelar la corrutina, se calla al instante. */
class PcmPlayer {

    suspend fun play(pcm: Pcm) {
        if (pcm.bytes.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.bytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm.bytes, 0, pcm.bytes.size)
            track.play()
            val frames = pcm.bytes.size / 2
            val deadline = pcm.durationMs + 3_000
            var waited = 0L
            while (track.playbackHeadPosition < frames && waited < deadline) {
                delay(40)
                waited += 40
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
