package com.locudroids.app.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.locudroids.app.ai.BreakKind
import com.locudroids.app.ai.DjWriter
import com.locudroids.app.ai.SongInfo
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.MusicLibrary
import com.locudroids.app.data.SettingsRepository
import com.locudroids.app.data.Song
import com.locudroids.app.player.DjBus
import com.locudroids.app.player.PlaybackService
import com.locudroids.app.player.Speaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUi(
    val connected: Boolean = false,
    val playWhenReady: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<DjSettings?> = settingsRepo.flow
        .map<DjSettings, DjSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ui = MutableStateFlow(PlaybackUi())
    val ui: StateFlow<PlaybackUi> = _ui.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    val djSpeaking: StateFlow<Boolean> = DjBus.speaking
    val djLine: StateFlow<String?> = DjBus.lastLine

    private var controller: MediaController? = null
    private var items: List<MediaItem> = emptyList()

    private val testWriter by lazy { DjWriter() }
    private val testSpeaker by lazy { Speaker(app) }

    init {
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().await()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    refresh(player)
                }
            })
            refresh(c)
            while (isActive) {
                delay(500)
                if (c.isPlaying) {
                    _ui.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0L)) }
                }
            }
        }
    }

    private fun refresh(player: Player) {
        val duration = player.duration
        _ui.value = PlaybackUi(
            connected = true,
            playWhenReady = player.playWhenReady,
            mediaId = player.currentMediaItem?.mediaId,
            title = player.mediaMetadata.title?.toString(),
            artist = player.mediaMetadata.artist?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration,
            shuffle = player.shuffleModeEnabled,
        )
    }

    // ---------------------------------------------------------------- biblioteca

    fun loadLibrary() {
        viewModelScope.launch {
            val list = MusicLibrary.scan(getApplication())
            _songs.value = list
            items = list.map { it.toMediaItem() }
        }
    }

    // ---------------------------------------------------------------- reproducción

    fun playSong(index: Int) {
        val c = controller ?: return
        if (items.isEmpty() || index !in items.indices) return
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        when {
            c.mediaItemCount == 0 -> playSong(0)
            c.playWhenReady -> c.pause()
            else -> c.play() // También salta la intervención del locutor si está hablando.
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    // ---------------------------------------------------------------- ajustes

    fun toggleDj() {
        viewModelScope.launch { settingsRepo.update { it.copy(djEnabled = !it.djEnabled) } }
    }

    fun saveSettings(newSettings: DjSettings) {
        viewModelScope.launch { settingsRepo.update { newSettings } }
    }

    /** Genera y dice una intervención de prueba para comprobar clave de Gemini, clima y voz. */
    fun testDj() {
        viewModelScope.launch {
            _testStatus.value = "Generando…"
            val s = settingsRepo.current()
            val ctx = testWriter.buildContext(
                kind = BreakKind.BETWEEN,
                finished = SongInfo("Canción de prueba", "Artista de prueba"),
                next = SongInfo("Otra canción de prueba", "Otro artista"),
                settings = s,
            )
            val result = testWriter.write(ctx, s)
            val weatherNote = if (s.city.isNotBlank() && ctx.weather == null) {
                "\nNo se pudo obtener el clima de \"${s.city}\"."
            } else {
                ""
            }
            val errorNote = result.error?.let { "\nGemini: $it (se usó una frase de respaldo)." }.orEmpty()
            _testStatus.value = result.text + weatherNote + errorNote
            testSpeaker.speak(result.text, s.speechRate)
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        testSpeaker.shutdown()
        super.onCleared()
    }
}
