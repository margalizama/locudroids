package com.locudroids.app.player

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.locudroids.app.ai.BreakContext
import com.locudroids.app.ai.BreakKind
import com.locudroids.app.ai.DjScript
import com.locudroids.app.ai.DjWriter
import com.locudroids.app.ai.SongInfo
import com.locudroids.app.ai.Tone
import com.locudroids.app.ai.Written
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * El "cerebro" del locutor. Vive dentro del servicio de reproducción.
 *
 * - Al dar play por primera vez en la sesión, hace una apertura (saludo + clima + primera canción).
 * - Cada N canciones terminadas, pausa en el cambio de tema, habla y reanuda.
 * - Mientras suena una canción que va a terminar en pausa del locutor, ya va preparando el texto.
 *
 * Todo corre en el hilo principal (el mismo del reproductor).
 */
class DjController(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val writer = DjWriter()
    private val speaker = Speaker(appContext)
    private val wakeLock: PowerManager.WakeLock =
        (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Locudroids:dj")
            .apply { setReferenceCounted(false) }

    @Volatile
    private var settings = DjSettings()

    private var introPending = true
    private var finishedSinceBreak = 0
    private var lastStarted: MediaItem? = null
    private var prefetch: Prefetch? = null
    private var breakJob: Job? = null
    private var breakGeneration = 0

    /** true mientras el locutor habla. */
    var breakActive = false
        private set

    /** true desde que termina una canción hasta que se reanuda la música: mantiene el servicio en primer plano. */
    var keepAlive = false
        private set

    private class Prefetch(val mediaId: String, val line: Deferred<Written>)

    init {
        scope.launch { settingsRepo.flow.collect { settings = it } }
    }

    // ---------------------------------------------------------------- eventos del reproductor

    /** Empezó una canción (o se cargó la lista). */
    fun onTrackStarted(item: MediaItem) {
        lastStarted = item
        prefetch?.line?.cancel()
        prefetch = null

        val s = settings
        if (!s.djEnabled || finishedSinceBreak + 1 < s.songsBetweenBreaks) return

        // Esta canción es la que cierra el bloque: se prepara ya lo que dirá el locutor al terminar.
        val next = nextItem()
        val job = scope.async {
            val ctx = writer.buildContext(BreakKind.BETWEEN, item.toSongInfo(), next?.toSongInfo(), s)
            writer.write(ctx, s)
        }
        prefetch = Prefetch(item.mediaId, job)
    }

    /** Terminó una canción y el reproductor quedó en pausa esperando. [resume] reanuda la música. */
    fun onSongEnded(resume: () -> Unit) {
        keepAlive = true
        finishedSinceBreak++
        val s = settings
        if (!s.djEnabled || finishedSinceBreak < s.songsBetweenBreaks) {
            resumeLater(resume)
            return
        }
        finishedSinceBreak = 0

        val current = player.currentMediaItem
        val finished = lastStarted
        // Si el reproductor ya avanzó, la "siguiente" es la actual.
        val next = if (current != null && current.mediaId == finished?.mediaId) nextItem() else current
        val pre = prefetch?.takeIf { it.mediaId == finished?.mediaId }?.line
        prefetch = null

        startBreak(BreakKind.BETWEEN, finished, next, pre, resume)
    }

    // ---------------------------------------------------------------- acciones del usuario

    /** El usuario pidió reproducir. [resume] reproduce de verdad. */
    fun onPlayRequested(resume: () -> Unit) {
        if (breakActive) {
            // Tocar play mientras habla el locutor = saltarse la intervención.
            cancelBreak()
            resume()
            return
        }
        val current = player.currentMediaItem
        if (!introPending || current == null) {
            resume()
            return
        }
        introPending = false
        if (!settings.djEnabled) {
            resume()
            return
        }
        keepAlive = true
        startBreak(BreakKind.INTRO, null, current, null, resume)
    }

    /** Cancela la intervención si está en curso. Devuelve true si había una. */
    fun cancelBreakIfActive(): Boolean {
        if (!breakActive) return false
        cancelBreak()
        return true
    }

    fun release() {
        cancelBreak()
        prefetch?.line?.cancel()
        speaker.shutdown()
    }

    // ---------------------------------------------------------------- internos

    private fun startBreak(
        kind: BreakKind,
        finished: MediaItem?,
        next: MediaItem?,
        pre: Deferred<Written>?,
        resume: () -> Unit,
    ) {
        cancelBreak()
        val generation = breakGeneration
        breakActive = true
        keepAlive = true
        DjBus.setSpeaking(true)
        wakeLock.acquire(WAKE_LOCK_MS)

        breakJob = scope.launch {
            val s = settings
            try {
                val finishedInfo = finished?.toSongInfo()
                val nextInfo = next?.toSongInfo()
                val generated: String? = if (pre != null) {
                    withTimeoutOrNull(GENERATION_TIMEOUT_MS) { pre.await().text }
                } else {
                    withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                        val ctx = writer.buildContext(kind, finishedInfo, nextInfo, s)
                        writer.write(ctx, s).text
                    }
                }
                // Si tardó demasiado: frase simple, sin clima ni IA, para no dejar silencio.
                val line: String = generated ?: DjScript.fallback(
                    BreakContext(
                        kind = kind,
                        finished = finishedInfo,
                        next = nextInfo,
                        weather = null,
                        now = LocalDateTime.now(),
                        tone = Tone.RELAJADO,
                        listener = s.listenerName.ifBlank { "amigo" },
                        djName = s.djName.ifBlank { "Locudroid" },
                    )
                )
                DjBus.setLine(line)
                speaker.speak(line, s.speechRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error en la intervención del locutor: ${e.message}")
            } finally {
                if (generation == breakGeneration) {
                    breakActive = false
                    DjBus.setSpeaking(false)
                    releaseWakeLock()
                }
            }
            // Solo llega aquí si no fue cancelada.
            if (generation == breakGeneration) {
                keepAlive = false
                resume()
            }
        }
    }

    private fun cancelBreak() {
        breakGeneration++
        breakJob?.cancel()
        breakJob = null
        speaker.stop()
        breakActive = false
        keepAlive = false
        DjBus.setSpeaking(false)
        releaseWakeLock()
    }

    private fun resumeLater(resume: () -> Unit) {
        scope.launch {
            keepAlive = false
            resume()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun nextItem(): MediaItem? {
        val index = player.nextMediaItemIndex
        return if (index != C.INDEX_UNSET) player.getMediaItemAt(index) else null
    }

    private fun MediaItem.toSongInfo(): SongInfo {
        val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "una canción sin título"
        val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "<unknown>" }
        return SongInfo(title, artist)
    }

    companion object {
        private const val TAG = "DjController"
        private const val GENERATION_TIMEOUT_MS = 8_000L
        private const val WAKE_LOCK_MS = 90_000L
    }
}
