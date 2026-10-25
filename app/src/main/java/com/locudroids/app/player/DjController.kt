package com.locudroids.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.locudroids.app.ai.CuePlanner
import com.locudroids.app.ai.Show
import com.locudroids.app.ai.ShowWriter
import com.locudroids.app.ai.Slot
import com.locudroids.app.ai.SongInfo
import com.locudroids.app.ai.TrackPlan
import com.locudroids.app.ai.stripTags
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.MusicLibrary
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * El programa de radio. Vive dentro del servicio de reproducción.
 *
 * La música NO se detiene: baja de volumen mientras los locutores hablan ENCIMA de la canción.
 * - Por cada canción se pide un guion a Gemini (ánimo + cortes) y se convierte en voz antes de que haga falta.
 * - El guion de la siguiente canción se prepara mientras suena la actual, para que no haya esperas.
 * - Los cortes caen al inicio (sobre la introducción), a media canción y sobre el final, según el nivel.
 *
 * Todo corre en el hilo principal (el mismo del reproductor).
 */
class DjController(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
) {
    private val writer = ShowWriter()
    private val director = VoiceDirector(context.applicationContext)

    @Volatile
    private var settings = DjSettings()

    private var songsStarted = 0
    private var cur: Prepared? = null
    private val prefetched = HashMap<String, Prepared>()
    private var lastSpoke: String? = null
    private var talkJob: Job? = null
    private var talkGeneration = 0

    /** true mientras hay locutores hablando. */
    var speaking = false
        private set

    private class ClipHolder {
        @Volatile
        var clip: Clip? = null
        var job: Job? = null
    }

    /** Guion y audios de una canción, en preparación o listos. */
    private inner class Prepared(val item: MediaItem, val nextId: String?, val plan: TrackPlan) {
        val mediaId: String get() = item.mediaId

        /** null mientras se está escribiendo el guion. */
        var show: Show? = null
        val clips = HashMap<Slot, ClipHolder>()
        val done = HashSet<Slot>()
        private var job: Job? = null

        /** El guion ya está y el modelo no escribió ningún corte para [slot]. */
        fun noCue(slot: Slot): Boolean = show != null && slot !in clips

        fun start(next: SongInfo?, first: Boolean, delayMs: Long) {
            if (plan.slots.isEmpty()) {
                show = Show(null, 3, emptyList(), usedAi = false, error = null)
                return
            }
            job = scope.launch {
                try {
                    if (delayMs > 0) delay(delayMs)
                    val s = settings
                    val ctx = writer.context(songInfo(item), next, plan, s, first, lastSpoke)
                    val result = writer.write(ctx, s)
                    show = result
                    for (cue in result.cues) {
                        val holder = ClipHolder()
                        clips[cue.slot] = holder
                        holder.job = scope.launch { holder.clip = director.makeClip(cue, settings) }
                    }
                    if (cur === this@Prepared) DjBus.setMood(result.mood)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo preparar el guion: ${e.message}")
                    show = Show(null, 3, emptyList(), usedAi = false, error = e.message)
                }
            }
        }

        fun cancel() {
            job?.cancel()
            clips.values.forEach { it.job?.cancel() }
            if (show == null) show = Show(null, 3, emptyList(), usedAi = false, error = "cancelado")
        }
    }

    init {
        scope.launch { settingsRepo.flow.collect { settings = it } }
        scope.launch { DjBus.skip.collect { cancelTalk() } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                try {
                    tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "tick falló: ${e.message}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- eventos del reproductor

    /**
     * Empezó una canción (o se cargó la lista).
     * [interrupt] = false cuando la canción avanzó sola: así no se corta a los locutores si siguen hablando.
     */
    fun onTrackStarted(item: MediaItem, interrupt: Boolean) {
        if (interrupt) cancelTalk()
        songsStarted++
        val n = songsStarted
        val s = settings

        val old = cur
        val ready = prefetched.remove(item.mediaId)
        prefetched.values.forEach { it.cancel() }
        prefetched.clear()
        old?.cancel()
        DjBus.setMood(null)

        if (!s.djEnabled) {
            cur = null
            return
        }

        val nextIdx = nextIndexAfter(player.currentMediaItemIndex)
        val next = itemAt(nextIdx)
        val prevHadOutro = old?.plan?.slots?.contains(Slot.OUTRO) == true
        val p = ready ?: Prepared(
            item = item,
            nextId = next?.mediaId,
            plan = CuePlanner.plan(s.level, n, durationOf(item), prevHadOutro),
        )
        cur = p
        if (ready == null) {
            p.start(next?.let(::songInfo), first = n == 1, delayMs = 0L)
        } else {
            p.show?.let { DjBus.setMood(it.mood) }
        }
        prefetchNext(nextIdx, n + 1, p, s)
    }

    /** La música se pausó (o se detuvo): los locutores también callan. */
    fun onPaused() {
        if (speaking) cancelTalk()
    }

    fun release() {
        cancelTalk()
        cur?.cancel()
        prefetched.values.forEach { it.cancel() }
        prefetched.clear()
        director.shutdown()
    }

    // ---------------------------------------------------------------- preparación anticipada

    /** Mientras suena la canción actual, deja listo el guion de la que sigue. */
    private fun prefetchNext(nextIdx: Int, nextN: Int, current: Prepared, s: DjSettings) {
        val nextItem = itemAt(nextIdx) ?: return
        if (nextItem.mediaId == current.mediaId) return
        val plan = CuePlanner.plan(
            level = s.level,
            n = nextN,
            durationMs = durationOf(nextItem),
            prevHadOutro = Slot.OUTRO in current.plan.slots,
        )
        if (plan.slots.isEmpty()) return
        val after = itemAt(nextIndexAfter(nextIdx))
        val p = Prepared(nextItem, after?.mediaId, plan)
        prefetched[nextItem.mediaId] = p
        p.start(after?.let(::songInfo), first = false, delayMs = PREFETCH_DELAY_MS)
    }

    // ---------------------------------------------------------------- planificador

    private fun tick() {
        val p = cur ?: return
        if (!settings.djEnabled || speaking || !player.isPlaying) return

        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: durationOf(p.item) ?: 0L

        // Inicio: sobre la introducción de la canción.
        if (Slot.INTRO in p.plan.slots && Slot.INTRO !in p.done) {
            val at = if (p.plan.introIsTag) TAG_AT_MS else INTRO_AT_MS
            if (pos >= at) {
                val clip = p.clips[Slot.INTRO]?.clip
                when {
                    clip != null -> {
                        p.done += Slot.INTRO
                        intrude(clip)
                        return
                    }
                    p.noCue(Slot.INTRO) || pos > INTRO_LATEST_MS -> p.done += Slot.INTRO
                }
            }
        }

        // Mitad: anuncios, promos, horóscopo, dedicatorias.
        if (Slot.MID in p.plan.slots && Slot.MID !in p.done && dur > 0) {
            val from = (dur * MID_FROM).toLong()
            val until = (dur * MID_UNTIL).toLong()
            if (pos >= from) {
                val clip = p.clips[Slot.MID]?.clip
                when {
                    clip != null && pos <= until -> {
                        p.done += Slot.MID
                        intrude(clip)
                        return
                    }
                    p.noCue(Slot.MID) || pos > until -> p.done += Slot.MID
                }
            }
        }

        // Final: cierra la canción y presenta la siguiente; empieza para terminar poco antes del cambio.
        if (Slot.OUTRO in p.plan.slots && Slot.OUTRO !in p.done && dur > 0) {
            val remaining = dur - pos
            val clip = p.clips[Slot.OUTRO]?.clip
            when {
                remaining < OUTRO_MIN_MS || p.noCue(Slot.OUTRO) -> p.done += Slot.OUTRO
                clip != null && remaining <= clip.estMs + OUTRO_TAIL_MS -> {
                    p.done += Slot.OUTRO
                    // Si el usuario cambió el orden, la canción que sigue ya no es la del guion.
                    val stillNext = itemAt(nextIndexAfter(player.currentMediaItemIndex))?.mediaId == p.nextId
                    if (stillNext) {
                        intrude(clip)
                        return
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- hablar encima de la música

    private fun intrude(clip: Clip) {
        cancelTalk()
        val generation = talkGeneration
        val s = settings
        speaking = true
        DjBus.setSpeaking(true)
        DjBus.setOnAir(clip.lines.map { it.who }.distinct().joinToString(" y "))
        DjBus.setLine(clip.lines.joinToString("\n") { "${it.who}: ${it.text.stripTags()}" })
        clip.lines.lastOrNull()?.who?.let { lastSpoke = it }

        talkJob = scope.launch {
            try {
                fadeTo(DUCK_VOLUME, 350)
                director.play(clip, s)
                fadeTo(1f, 700)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error en el corte: ${e.message}")
            } finally {
                if (generation == talkGeneration) {
                    speaking = false
                    DjBus.setSpeaking(false)
                    DjBus.setOnAir(null)
                    player.volume = 1f
                }
            }
        }
    }

    private fun cancelTalk() {
        talkGeneration++
        talkJob?.cancel()
        talkJob = null
        director.stop()
        speaking = false
        DjBus.setSpeaking(false)
        DjBus.setOnAir(null)
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

    // ---------------------------------------------------------------- utilidades de la cola

    /** Índice de la canción que sigue a [index] respetando aleatorio y repetición; -1 si no hay. */
    private fun nextIndexAfter(index: Int): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || index < 0) return C.INDEX_UNSET
        return timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
    }

    private fun itemAt(index: Int): MediaItem? =
        if (index in 0 until player.mediaItemCount) player.getMediaItemAt(index) else null

    private fun durationOf(item: MediaItem): Long? =
        item.mediaMetadata.extras?.getLong(MusicLibrary.EXTRA_DURATION, 0L)?.takeIf { it > 0 }

    private fun songInfo(item: MediaItem): SongInfo {
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "una canción sin título"
        val artist = item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "<unknown>" }
        val album = item.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() && it != "<unknown>" }
        return SongInfo(title, artist, album, item.mediaMetadata.recordingYear)
    }

    private companion object {
        const val TAG = "DjController"
        const val TICK_MS = 400L
        const val DUCK_VOLUME = 0.22f

        const val INTRO_AT_MS = 3_500L
        const val TAG_AT_MS = 1_500L
        const val INTRO_LATEST_MS = 45_000L

        const val MID_FROM = 0.42
        const val MID_UNTIL = 0.68

        const val OUTRO_MIN_MS = 5_000L
        const val OUTRO_TAIL_MS = 7_000L

        const val PREFETCH_DELAY_MS = 6_000L
    }
}
