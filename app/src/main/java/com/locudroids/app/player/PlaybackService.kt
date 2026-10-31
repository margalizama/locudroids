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
import com.locudroids.app.data.AppData
import com.locudroids.app.data.MusicLibrary
import com.locudroids.app.data.SessionStore
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var dj: DjController? = null
    private var tracker: PlaybackTracker? = null
    private var mix: MixEngine? = null

    /** Se restauró la cola guardada y el locutor aún no preparó la canción actual (lo hará al dar play). */
    private var needsFirstPrep = false
    /** true solo mientras se carga la cola guardada: así el locutor no escribe guion al abrir la app. */
    private var restoring = false

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
        exoPlayer = player

        val settingsRepo = SettingsRepository(applicationContext)
        val bus = VolumeBus(player)
        val mixer = MixEngine(this, player, bus, scope, settingsRepo)
        mixer.start()
        mix = mixer

        val controller = DjController(this, player, bus, scope, settingsRepo)
        dj = controller

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val own = mixer.ownTransition
                if (own) mixer.ownTransition = false
                // Si el cambio lo hizo la mezcla, los locutores no se cortan.
                if (mediaItem != null && !restoring) {
                    controller.onTrackStarted(
                        mediaItem,
                        interrupt = reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !own,
                    )
                }
                if (!own && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) mixer.onUserChange()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && needsFirstPrep) {
                    needsFirstPrep = false
                    player.currentMediaItem?.let { controller.onTrackStarted(it, interrupt = false) }
                }
            }

            // Pausa de verdad (no un simple "cargando"): los locutores callan y la mezcla se cancela.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    controller.onPaused()
                    mixer.onUserChange()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && !mixer.isMixing && !mixer.ownTransition) mixer.onUserChange()
            }
        })

        val data = AppData.get(applicationContext)
        val t = PlaybackTracker(player, data, scope)
        t.start()
        tracker = t
        scope.launch { restoreSession(player, data) }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /** Al abrir la app deja la cola, la canción y el segundo donde se quedó la última vez (en pausa). */
    private suspend fun restoreSession(player: ExoPlayer, data: AppData) {
        try {
            val saved = data.session.load()
            if (saved != null && player.mediaItemCount == 0) {
                val byPath = MusicLibrary.scan(applicationContext).associateBy { it.path }
                val resolved = SessionStore.resolve(saved, byPath)
                // Si mientras tanto el usuario eligió otra cosa, no se pisa.
                if (resolved != null && player.mediaItemCount == 0) {
                    restoring = true
                    player.shuffleModeEnabled = saved.shuffle
                    player.setMediaItems(resolved.songs.map { it.toMediaItem() }, resolved.index, resolved.positionMs)
                    player.prepare()
                    restoring = false
                    needsFirstPrep = true
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackService", "No se pudo restaurar la sesión: ${e.message}")
        } finally {
            restoring = false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        tracker?.stop()
        tracker = null
        mix?.release()
        mix = null
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
