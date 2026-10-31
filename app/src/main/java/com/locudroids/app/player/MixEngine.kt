package com.locudroids.app.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.locudroids.app.ai.AudioAnalyzer
import com.locudroids.app.ai.BeatMatch
import com.locudroids.app.data.AudioDecoder
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mezcla automática entre canciones. Corre en el hilo principal, junto al reproductor.
 *
 * Truco: el reproductor principal (el de la sesión) salta a la siguiente canción al empezar el fundido y la
 * toca desde su inicio subiendo de volumen; un segundo reproductor toca la cola de la canción que sale
 * bajando de volumen. Así la lista, las notificaciones y el locutor siempre ven la canción nueva desde el principio.
 *
 * Con "mezcla con ritmo" la que entra se acelera o frena un poco al tempo de la que sale y un lazo de fase
 * (pequeños ajustes de velocidad) alinea los golpes. Es experimental: si no se pudo medir el ritmo, hace un fundido normal.
 */
class MixEngine(
    private val context: Context,
    private val main: ExoPlayer,
    private val bus: VolumeBus,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
) {
    @Volatile
    private var settings = DjSettings()

    private var tail: ExoPlayer? = null
    private var plan: Plan? = null
    private var mixing: Mixing? = null
    private var glideJob: Job? = null
    private var loop: Job? = null

    /** true justo antes de que la mezcla mueva el reproductor principal: el servicio lo consulta para no cortar a los locutores. */
    @Volatile
    var ownTransition = false

    /** Hay un fundido en curso. */
    val isMixing: Boolean get() = mixing != null

    private class BeatInfo(val plan: BeatMatch.Plan, val oIn: Float, val pIn: Float, val oOut: Float, val pOut: Float)

    private class Plan(val key: String, val nextIndex: Int, val fadeMs: Long) {
        @Volatile var beat: BeatInfo? = null
        var analysis: Job? = null
        var tailPrepared = false
        var failed = false
    }

    private class Mixing(
        val startedAt: Long,
        val fadeMs: Long,
        val nextId: String,
        val beat: BeatInfo?,
        val lock: BeatMatch.Lock?,
    ) {
        var lastNudge = 0L
    }

    fun start() {
        scope.launch { settingsRepo.flow.collect { settings = it } }
        loop = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                try {
                    tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "mezcla: ${e.message}")
                    abort()
                }
            }
        }
    }

    fun release() {
        loop?.cancel()
        glideJob?.cancel()
        plan?.analysis?.cancel()
        tail?.release()
        tail = null
        bus.tail = null
    }

    /** El usuario cambió de canción, buscó otra posición o pausó: cualquier mezcla o preparación queda sin efecto. */
    fun onUserChange() {
        if (mixing != null) finishMix()
        clearPlan()
    }

    // ---------------------------------------------------------------- bucle

    private fun tick() {
        mixing?.let {
            stepMix(it)
            return
        }
        if (settings.mixMode == DjSettings.MIX_OFF || !main.isPlaying) {
            if (plan != null && !main.playWhenReady) clearPlan()
            return
        }
        val item = main.currentMediaItem ?: return
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val nextIndex = main.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val next = main.getMediaItemAt(nextIndex)

        val nextDuration = next.mediaMetadata.extras?.getLong(com.locudroids.app.data.MusicLibrary.EXTRA_DURATION, 0L) ?: 0L
        val fade = fadeFor(duration, nextDuration)
        val remaining = duration - main.currentPosition
        val key = "${main.currentMediaItemIndex}:${item.mediaId}>${next.mediaId}"
        if (plan != null && plan?.key != key) clearPlan()
        if (remaining > fade + PREFETCH_MS) return

        val p = plan ?: Plan(key, nextIndex, fade).also {
            plan = it
            if (settings.mixMode == DjSettings.MIX_BEAT) it.analysis = scope.launch { analyze(it, item, next, duration, fade) }
        }
        if (p.failed) return

        if (remaining <= fade + TAIL_PREP_MS && !p.tailPrepared) prepareTail(p, item)
        if (remaining <= fade && p.tailPrepared) switchNow(p, item, next)
    }

    private fun fadeFor(durationMs: Long, nextMs: Long): Long {
        var fade = settings.mixSeconds * 1000L
        if (settings.mixMode == DjSettings.MIX_BEAT) fade = fade.coerceAtLeast(BEAT_MIN_FADE_MS)
        val shortest = if (nextMs > 0) minOf(durationMs, nextMs) else durationMs
        return fade.coerceAtMost(shortest / 3).coerceAtLeast(1_000L)
    }

    // ---------------------------------------------------------------- preparación

    private fun ensureTail(): ExoPlayer {
        tail?.let { return it }
        val t = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false,
            )
            .build()
        tail = t
        bus.tail = t
        return t
    }

    private fun prepareTail(p: Plan, item: MediaItem) {
        val t = ensureTail()
        t.volume = 0f
        t.playWhenReady = false
        t.setMediaItem(item, (main.currentPosition + TAIL_PREP_MS).coerceAtLeast(0L))
        t.prepare()
        p.tailPrepared = true
    }

    private suspend fun analyze(p: Plan, item: MediaItem, next: MediaItem, duration: Long, fade: Long) {
        try {
            val outUri = item.localConfiguration?.uri ?: return
            val inUri = next.localConfiguration?.uri ?: return
            val result = withContext(Dispatchers.Default) {
                val outStart = (duration - fade - 32_000L).coerceAtLeast(0L)
                val outAudio = AudioDecoder.decode(context, outUri, outStart, 30_000L) ?: return@withContext null
                val out = AudioAnalyzer.analyze(outAudio.samples, outAudio.sampleRate) ?: return@withContext null
                val inAudio = AudioDecoder.decode(context, inUri, 0L, 25_000L) ?: return@withContext null
                val inn = AudioAnalyzer.analyze(inAudio.samples, inAudio.sampleRate) ?: return@withContext null
                if (out.confidence < MIN_CONFIDENCE || inn.confidence < MIN_CONFIDENCE) return@withContext null
                val plan = BeatMatch.plan(out.bpm, inn.bpm) ?: return@withContext null
                BeatInfo(
                    plan = plan,
                    oIn = inn.beatOffsetMs.toFloat(),
                    pIn = 60_000f / inn.bpm,
                    oOut = (outStart + out.beatOffsetMs).toFloat(),
                    pOut = 60_000f / out.bpm,
                )
            }
            p.beat = result
            Log.i(TAG, if (result != null) "mezcla con ritmo lista (x${"%.3f".format(result.plan.rate)})" else "sin ritmo fiable: fundido normal")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "análisis de mezcla falló: ${e.message}")
        }
    }

    private fun clearPlan() {
        plan?.analysis?.cancel()
        plan = null
        tail?.let {
            if (mixing == null) {
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    // ---------------------------------------------------------------- el cambio

    private fun switchNow(p: Plan, item: MediaItem, next: MediaItem) {
        val t = tail ?: return
        if (t.playbackState != Player.STATE_READY) {
            if (t.playbackState == Player.STATE_IDLE || t.playbackState == Player.STATE_ENDED) p.failed = true
            return // todavía cargando: se reintenta en el siguiente tick
        }
        val outPos = main.currentPosition
        val beat = p.beat
        val rate = beat?.plan?.rate ?: 1f

        var startPos = 0L
        var lock: BeatMatch.Lock? = null
        if (beat != null) {
            startPos = BeatMatch.startPosition(
                beat.plan, beat.oIn, beat.pIn, beat.oOut, beat.pOut,
                outPos = (outPos + TAIL_LEAD_MS).toFloat(), minStart = 0f, latencyMs = START_LATENCY_MS,
            )
            lock = BeatMatch.Lock(beat.plan, beat.oIn, beat.pIn, beat.oOut, beat.pOut)
        }

        // La cola de la canción que sale sigue exactamente donde iba; la nueva entra en silencio.
        t.seekTo(outPos + TAIL_LEAD_MS)
        bus.setMix(main = 0f, tail = 1f)
        t.play()

        ownTransition = true
        if (beat != null) main.setPlaybackParameters(PlaybackParameters(rate, 1f))
        main.seekTo(p.nextIndex, startPos)
        scope.launch {
            delay(1_000L)
            ownTransition = false
        }

        mixing = Mixing(SystemClock.elapsedRealtime(), p.fadeMs, next.mediaId, beat, lock)
        plan = null
    }

    private fun stepMix(m: Mixing) {
        val now = SystemClock.elapsedRealtime()
        val t = ((now - m.startedAt).toFloat() / m.fadeMs).coerceIn(0f, 1f)

        if (!main.playWhenReady || main.currentMediaItem?.mediaId != m.nextId) {
            finishMix()
            return
        }
        // Potencia constante: la suma de volúmenes no se hunde a la mitad del fundido.
        bus.setMix(main = sin(t * PI.toFloat() / 2), tail = cos(t * PI.toFloat() / 2))

        val lock = m.lock
        val beat = m.beat
        val tl = tail
        if (lock != null && beat != null && tl != null && main.isPlaying && now - m.lastNudge >= NUDGE_EVERY_MS) {
            m.lastNudge = now
            val c = lock.correction(main.currentPosition, tl.currentPosition)
            main.setPlaybackParameters(PlaybackParameters(beat.plan.rate * c, 1f))
        }
        if (t >= 1f) finishMix()
    }

    /** Termina el fundido: la cola se detiene y la canción nueva queda sola; si se estiró, vuelve poco a poco a su velocidad. */
    private fun finishMix() {
        val m = mixing
        mixing = null
        tail?.let {
            it.stop()
            it.clearMediaItems()
        }
        bus.setMix(main = 1f, tail = 0f)
        val speed = main.playbackParameters.speed
        if (m?.beat != null && speed != 1f) glideSpeedToNormal(speed) else if (speed != 1f) main.setPlaybackParameters(PlaybackParameters.DEFAULT)
    }

    private fun abort() {
        mixing = null
        plan = null
        tail?.let {
            it.stop()
            it.clearMediaItems()
        }
        bus.setMix(main = 1f, tail = 0f)
        if (main.playbackParameters.speed != 1f) main.setPlaybackParameters(PlaybackParameters.DEFAULT)
    }

    private fun glideSpeedToNormal(from: Float) {
        glideJob?.cancel()
        glideJob = scope.launch {
            val steps = 16
            for (i in 1..steps) {
                delay(250L)
                val s = from + (1f - from) * i / steps
                main.setPlaybackParameters(PlaybackParameters(s, 1f))
            }
            main.setPlaybackParameters(PlaybackParameters.DEFAULT)
        }
    }

    private companion object {
        const val TAG = "MixEngine"
        const val TICK_MS = 100L
        const val PREFETCH_MS = 30_000L
        const val TAIL_PREP_MS = 4_000L
        const val TAIL_LEAD_MS = 60L
        const val START_LATENCY_MS = 120f
        const val BEAT_MIN_FADE_MS = 8_000L
        const val MIN_CONFIDENCE = 0.25f
        const val NUDGE_EVERY_MS = 200L
    }
}
