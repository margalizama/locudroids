package com.locudroids.app.ui

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.locudroids.app.ai.ShowWriter
import com.locudroids.app.ai.Slot
import com.locudroids.app.ai.SongInfo
import com.locudroids.app.ai.TrackPlan
import com.locudroids.app.ai.stripTags
import com.locudroids.app.data.ArtLoader
import com.locudroids.app.data.BrowserState
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.Folders
import com.locudroids.app.data.MusicLibrary
import com.locudroids.app.data.SettingsRepository
import com.locudroids.app.data.Song
import com.locudroids.app.player.DjBus
import com.locudroids.app.player.PlaybackService
import com.locudroids.app.player.VoiceDirector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUi(
    val connected: Boolean = false,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
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

    private val _browsePath = MutableStateFlow<String?>(null)

    /** Carpeta que se está viendo en el explorador. */
    val browser: StateFlow<BrowserState> = combine(_songs, _browsePath) { songs, path ->
        Folders.build(songs, path)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowserState())

    private val _ui = MutableStateFlow(PlaybackUi())
    val ui: StateFlow<PlaybackUi> = _ui.asStateFlow()

    private val _artwork = MutableStateFlow<ImageBitmap?>(null)
    val artwork: StateFlow<ImageBitmap?> = _artwork.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    val djSpeaking: StateFlow<Boolean> = DjBus.speaking
    val djLine: StateFlow<String?> = DjBus.lastLine
    val djOnAir: StateFlow<String?> = DjBus.onAir
    val djMood: StateFlow<String?> = DjBus.mood
    val djVoiceInfo: StateFlow<String?> = DjBus.voiceInfo

    private var controller: MediaController? = null
    private var artJob: Job? = null
    private var artFor: String? = null

    private val testWriter by lazy { ShowWriter() }
    private val testDirectorLazy = lazy { VoiceDirector(app) }
    private val testDirector: VoiceDirector by testDirectorLazy

    init {
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            val application = getApplication<Application>()
            val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
            val c = MediaController.Builder(application, token).buildAsync().await()
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
        val mediaId = player.currentMediaItem?.mediaId
        _ui.value = PlaybackUi(
            connected = true,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            mediaId = mediaId,
            title = player.mediaMetadata.title?.toString(),
            artist = player.mediaMetadata.artist?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration,
            shuffle = player.shuffleModeEnabled,
        )
        if (mediaId != artFor) loadArtwork(mediaId)
    }

    private fun loadArtwork(mediaId: String?) {
        artFor = mediaId
        artJob?.cancel()
        val id = mediaId?.toLongOrNull()
        if (id == null) {
            _artwork.value = null
            return
        }
        artJob = viewModelScope.launch {
            val bitmap = ArtLoader.load(getApplication(), id, 720)
            if (artFor == mediaId) _artwork.value = bitmap?.asImageBitmap()
        }
    }

    // ---------------------------------------------------------------- biblioteca

    fun loadLibrary() {
        viewModelScope.launch {
            _songs.value = MusicLibrary.scan(getApplication())
        }
    }

    fun openFolder(path: String) {
        _browsePath.value = path
    }

    fun upFolder() {
        val state = browser.value
        if (!state.atRoot) _browsePath.value = Folders.parentOf(state.path)
    }

    /** Canciones de una carpeta y todas sus subcarpetas. */
    private fun songsUnder(path: String): List<Song> {
        val prefix = "$path/"
        return _songs.value
            .filter { it.folder == path || it.folder.startsWith(prefix) }
            .sortedBy { it.path.lowercase() }
    }

    fun playFolder(path: String, shuffle: Boolean) {
        val queue = songsUnder(path)
        if (queue.isEmpty()) return
        playQueue(queue, if (shuffle) queue.indices.random() else 0, shuffle)
    }

    // ---------------------------------------------------------------- reproducción

    /** Reproduce [queue] empezando en [startIndex]. Si [shuffle] es null se respeta el modo aleatorio actual. */
    fun playQueue(queue: List<Song>, startIndex: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (queue.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        c.setMediaItems(queue.map { it.toMediaItem() }, startIndex.coerceIn(queue.indices), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        when {
            c.mediaItemCount == 0 -> playQueue(_songs.value, 0)
            c.playWhenReady -> c.pause()
            else -> c.play()
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

    // ---------------------------------------------------------------- locutores y ajustes

    fun toggleDj() {
        viewModelScope.launch { settingsRepo.update { it.copy(djEnabled = !it.djEnabled) } }
    }

    /** Calla a los locutores en este momento (la música sigue). */
    fun skipDj() {
        DjBus.requestSkip()
    }

    fun saveSettings(newSettings: DjSettings) {
        viewModelScope.launch { settingsRepo.update { newSettings } }
    }

    /** Genera un corte de prueba y lo dice, para comprobar clave, clima, guion y voz sin esperar a una canción. */
    fun testDj() {
        viewModelScope.launch {
            _testStatus.value = "Escribiendo el guion…"
            val s = settingsRepo.current()
            val plan = TrackPlan(setOf(Slot.INTRO), introIsTag = false)
            val ctx = testWriter.context(
                current = SongInfo("Canción de prueba", "Artista de prueba", "Álbum de prueba", 2015),
                next = SongInfo("Otra canción de prueba", "Otro artista"),
                plan = plan,
                settings = s,
                first = true,
                lastSpoke = null,
            )
            val show = testWriter.write(ctx, s)
            val cue = show.cues.firstOrNull()
            if (cue == null) {
                _testStatus.value = "No salió ningún corte." + (show.error?.let { "\nGemini: $it" } ?: "")
                return@launch
            }

            val notes = buildString {
                if (s.city.isNotBlank() && ctx.weather == null) append("\nNo se pudo obtener el clima de \"${s.city}\".")
                show.error?.let { append("\nGemini (guion): $it. Se usó un guion de respaldo.") }
            }
            val script = "Ánimo: ${show.mood ?: "—"}\n" +
                cue.lines.joinToString("\n") { "${it.who}: ${it.text.stripTags()}" } + notes
            _testStatus.value = "$script\nGenerando la voz…"

            val clip = testDirector.makeClip(cue, s)
            val voice = when {
                clip.pcm != null -> "\nVoz: Gemini."
                clip.skip -> "\nLa voz de Gemini falló: ${clip.ttsError}. En el programa este corte se omitiría " +
                    "(activa \"Si Gemini falla, usar la voz de Android\" si prefieres oírlo robótico)."
                clip.ttsError != null -> "\nLa voz de Gemini falló: ${clip.ttsError}. Se usa la voz de Android."
                else -> "\nVoz: Android."
            }
            _testStatus.value = script + voice
            if (!clip.skip) testDirector.play(clip, s)
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        if (testDirectorLazy.isInitialized()) testDirector.shutdown()
        super.onCleared()
    }
}
