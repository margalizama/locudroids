package com.locudroids.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.locudroids.app.ai.BreakKind
import com.locudroids.app.ai.DjWriter
import com.locudroids.app.ai.SongInfo
import com.locudroids.app.ai.Written
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * El locutor estilo radio. Vive dentro del servicio de reproducción.
 *
 * La música NO se detiene: el locutor habla ENCIMA de la canción (la música baja de volumen mientras tanto).
 * - Al inicio de la canción: nombre, hora, clima, saludos y datos (sobre la introducción).
 * - Cerca del final (intensidad 3): cierra la canción y presenta la siguiente.
 *
 * Todo corre en el hilo principal (el mismo del reproductor).
 */
class DjController(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
) {
    private val writer = DjWriter()
    private val speaker = Speaker(context.applicationContext)

    @Volatile
    private var settings = DjSettings()

    private var songsStarted = 0
    private var plan: TrackPlan? = null
    private var talkJob: Job? = null
    private var talkGeneration = 0

    /** true mientras el locutor habla. */
    var speaking = false
        private set

    private class TrackPlan(
        val mediaId: String,
        val current: SongInfo,
        val first: Boolean,
        val doStart: Boolean,
        val doEnd: Boolean,
    ) {
        var startLine: Deferred<Written>? = null
        var startDone = false
        var endLine: Deferred<Written>? = null
        var endDone = false
    }

    init {
        scope.launch { settingsRepo.flow.collect { settings = it } }
        scope.launch { DjBus.skip.collect { cancelTalk() } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                try {
                    tick()
                } catch (e: Exception) {
                    Log.w(TAG, "tick falló: ${e.message}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- eventos del reproductor

    /**
     * Empezó una canción (o se cargó la lista).
     * [interrupt] = false cuando la canción avanzó sola: así no se corta al locutor si está terminando de hablar.
     */
    fun onTrackStarted(item: MediaItem, interrupt: Boolean) {
        if (interrupt) cancelTalk()
        plan?.let {
            it.startLine?.cancel()
            it.endLine?.cancel()
        }

        songsStarted++
        val s = settings
        val first = songsStarted == 1
        val doStart = s.djEnabled && (s.intensity >= 2 || songsStarted % 2 == 1)
        val doEnd = s.djEnabled && s.intensity >= 3
        val p = TrackPlan(item.mediaId, item.toSongInfo(), first, doStart, doEnd)
        plan = p
        if (doStart) {
            // Pequeña espera por si el usuario salta de canción enseguida.
            p.startLine = generate(BreakKind.START, p, null, s, START_GEN_DELAY_MS)
        }
    }

    /** La música se pausó (o se detuvo): el locutor también calla. */
    fun onPaused() {
        if (speaking) cancelTalk()
    }

    fun release() {
        cancelTalk()
        plan?.let {
            it.startLine?.cancel()
            it.endLine?.cancel()
        }
        speaker.shutdown()
    }

    // ---------------------------------------------------------------- planificador

    private fun tick() {
        val p = plan ?: return
        val s = settings
        if (!s.djEnabled || speaking || !player.isPlaying) return

        val pos = player.currentPosition
        val dur = player.duration
        val hasDuration = dur != C.TIME_UNSET && dur > 0

        if (p.doStart && !p.startDone && pos >= START_AT_MS) {
            val line = p.startLine
            when {
                line == null -> p.startDone = true
                line.isCompleted -> {
                    p.startDone = true
                    intrude(line)
                    return
                }
                // Se tardó demasiado: mejor callar que hablar encima de la voz del cantante.
                pos > START_LATEST_MS -> p.startDone = true
            }
        }

        if (p.doEnd && !p.endDone && hasDuration && dur > MIN_DURATION_FOR_END_MS) {
            val remaining = dur - pos
            if (p.endLine == null && remaining <= END_PREPARE_MS) {
                p.endLine = generate(BreakKind.END, p, nextItem()?.toSongInfo(), s, 0L)
            }
            if (remaining <= END_AT_MS) {
                val line = p.endLine
                when {
                    line != null && line.isCompleted -> {
                        p.endDone = true
                        intrude(line)
                    }
                    remaining < END_GIVE_UP_MS -> p.endDone = true
                }
            }
        }
    }

    private fun generate(
        kind: BreakKind,
        p: TrackPlan,
        next: SongInfo?,
        s: DjSettings,
        delayMs: Long,
    ): Deferred<Written> = scope.async {
        if (delayMs > 0) delay(delayMs)
        val ctx = writer.buildContext(kind, p.current, next, s, p.first && kind == BreakKind.START)
        writer.write(ctx, s)
    }

    // ---------------------------------------------------------------- hablar encima de la música

    private fun intrude(line: Deferred<Written>) {
        cancelTalk()
        val generation = talkGeneration
        speaking = true
        DjBus.setSpeaking(true)
        val rate = settings.speechRate

        talkJob = scope.launch {
            try {
                val text = line.await().text
                DjBus.setLine(text)
                fadeTo(DUCK_VOLUME, 350)
                speaker.speak(text, rate)
                fadeTo(1f, 700)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error en la intervención del locutor: ${e.message}")
            } finally {
                if (generation == talkGeneration) {
                    speaking = false
                    DjBus.setSpeaking(false)
                    player.volume = 1f
                }
            }
        }
    }

    private fun cancelTalk() {
        talkGeneration++
        talkJob?.cancel()
        talkJob = null
        speaker.stop()
        speaking = false
        DjBus.setSpeaking(false)
        player.volume = 1f
    }

    /** Sube o baja el volumen de la música poco a poco (sin saltos bruscos). */
    private suspend fun fadeTo(target: Float, durationMs: Long) {
        val start = player.volume
        val steps = 10
        for (i in 1..steps) {
            player.volume = start + (target - start) * i / steps
            delay(durationMs / steps)
        }
    }

    private fun nextItem(): MediaItem? {
        val index = player.nextMediaItemIndex
        return if (index != C.INDEX_UNSET) player.getMediaItemAt(index) else null
    }

    private fun MediaItem.toSongInfo(): SongInfo {
        val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "una canción sin título"
        val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "<unknown>" }
        val album = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() && it != "<unknown>" }
        return SongInfo(title, artist, album, mediaMetadata.recordingYear)
    }

    companion object {
        private const val TAG = "DjController"
        private const val TICK_MS = 500L
        private const val DUCK_VOLUME = 0.22f

        private const val START_GEN_DELAY_MS = 1_200L
        private const val START_AT_MS = 3_500L
        private const val START_LATEST_MS = 22_000L

        private const val MIN_DURATION_FOR_END_MS = 100_000L
        private const val END_PREPARE_MS = 75_000L
        private const val END_AT_MS = 30_000L
        private const val END_GIVE_UP_MS = 12_000L
    }
}
