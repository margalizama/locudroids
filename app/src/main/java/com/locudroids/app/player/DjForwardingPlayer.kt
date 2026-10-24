package com.locudroids.app.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Envuelve al reproductor para que el locutor pueda intervenir:
 * - play(): puede abrir la sesión con el locutor antes de que suene la música.
 * - pausar / saltar mientras habla el locutor: lo cancela.
 */
class DjForwardingPlayer(
    private val wrapped: Player,
    private val dj: DjController,
) : ForwardingPlayer(wrapped) {

    override fun play() {
        dj.onPlayRequested { wrapped.play() }
    }

    override fun pause() {
        dj.cancelBreakIfActive()
        super.pause()
    }

    override fun stop() {
        dj.cancelBreakIfActive()
        super.stop()
    }

    override fun seekToNext() {
        val interrupted = dj.cancelBreakIfActive()
        super.seekToNext()
        if (interrupted) wrapped.play()
    }

    override fun seekToNextMediaItem() {
        val interrupted = dj.cancelBreakIfActive()
        super.seekToNextMediaItem()
        if (interrupted) wrapped.play()
    }

    override fun seekToPrevious() {
        val interrupted = dj.cancelBreakIfActive()
        super.seekToPrevious()
        if (interrupted) wrapped.play()
    }

    override fun seekToPreviousMediaItem() {
        val interrupted = dj.cancelBreakIfActive()
        super.seekToPreviousMediaItem()
        if (interrupted) wrapped.play()
    }
}
