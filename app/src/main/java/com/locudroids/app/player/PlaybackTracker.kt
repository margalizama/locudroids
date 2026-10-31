package com.locudroids.app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.locudroids.app.data.AppData
import com.locudroids.app.data.MusicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Cuenta las reproducciones (una canción cuenta al escucharla 30 s o la mitad, lo que ocurra primero)
 * y guarda la cola y la posición para retomar donde se quedó. Corre en el hilo principal, como el reproductor.
 */
class PlaybackTracker(
    private val player: Player,
    private val data: AppData,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var listenedMs = 0L
    private var counted = false
    private var currentPath: String? = null
    private var queueJob: Job? = null
    private var loop: Job? = null
    private var ticks = 0

    fun start() {
        player.addListener(this)
        loop = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    fun stop() {
        loop?.cancel()
        queueJob?.cancel()
        player.removeListener(this)
        savePosition()
    }

    private fun tick() {
        if (!player.isPlaying) return
        listenedMs += TICK_MS
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val needed = minOf(COUNT_AFTER_MS, duration / 2)
        val path = currentPath
        if (!counted && path != null && listenedMs >= needed) {
            counted = true
            data.stats.record(path)
        }
        if (++ticks % SAVE_EVERY_TICKS == 0) savePosition()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        currentPath = mediaItem?.mediaMetadata?.extras?.getString(MusicLibrary.EXTRA_PATH)
        listenedMs = 0L
        counted = false
        savePosition()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        // Varios cambios seguidos (cargar una lista) se guardan una sola vez.
        queueJob?.cancel()
        queueJob = scope.launch {
            delay(1_500L)
            saveQueue()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) savePosition()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = savePosition()

    private fun saveQueue() {
        val paths = (0 until player.mediaItemCount).mapNotNull {
            player.getMediaItemAt(it).mediaMetadata.extras?.getString(MusicLibrary.EXTRA_PATH)
        }
        if (paths.isNotEmpty()) data.session.saveQueue(paths)
    }

    private fun savePosition() {
        if (player.mediaItemCount == 0) return
        data.session.savePosition(
            currentPath = player.currentMediaItem?.mediaMetadata?.extras?.getString(MusicLibrary.EXTRA_PATH),
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
        )
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val COUNT_AFTER_MS = 30_000L
        const val SAVE_EVERY_TICKS = 5
    }
}
