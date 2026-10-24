package com.locudroids.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var dj: DjController? = null

    // Main (no inmediato): los callbacks del reproductor nunca se ejecutan re-entrantes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        // Pausa al final de cada canción: ahí es donde puede entrar el locutor.
        player.setPauseAtEndOfMediaItems(true)
        exoPlayer = player

        val controller = DjController(this, player, scope, SettingsRepository(applicationContext))
        dj = controller

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) controller.onTrackStarted(mediaItem)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    controller.onSongEnded { player.play() }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, DjForwardingPlayer(player, controller)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Durante la intervención del locutor la música está en pausa; aun así el servicio debe seguir
     * en primer plano para que Android no lo duerma ni impida reanudar en segundo plano.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || dj?.keepAlive == true)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        val busy = dj?.keepAlive == true
        if (player == null || (!player.playWhenReady && !busy) || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        dj?.release()
        dj = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
        super.onDestroy()
    }
}
