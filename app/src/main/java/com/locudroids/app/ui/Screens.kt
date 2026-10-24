package com.locudroids.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locudroids.app.data.DjSettings
import kotlin.math.roundToInt

@Composable
fun LocudroidsApp(vm: MainViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    if (showSettings) {
        SettingsScreen(vm, onBack = { showSettings = false })
    } else {
        PlayerScreen(vm, onOpenSettings = { showSettings = true })
    }
}

// ============================================================================ Reproductor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val speaking by vm.djSpeaking.collectAsStateWithLifecycle()
    val line by vm.djLine.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionsToAsk = remember {
        buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[audioPermission] == true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(permissionsToAsk)
    }
    LaunchedEffect(granted) {
        if (granted) vm.loadLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locudroids") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NowPlayingCard(
                vm = vm,
                ui = ui,
                speaking = speaking,
                line = line,
                djEnabled = settings?.djEnabled ?: true,
            )

            if (!granted) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Necesito permiso para leer tus archivos de música.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { launcher.launch(permissionsToAsk) }) {
                        Text("Dar permiso")
                    }
                }
            } else if (songs.isEmpty()) {
                Text(
                    "No encontré MP3 en este teléfono.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = ui.mediaId == song.id.toString()
                        ListItem(
                            headlineContent = {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    song.artist ?: "Artista desconocido",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (isCurrent) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { vm.playSong(index) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    vm: MainViewModel,
    ui: PlaybackUi,
    speaking: Boolean,
    line: String?,
    djEnabled: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ui.title ?: "Elige una canción",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ui.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            var dragValue by remember { mutableStateOf<Float?>(null) }
            val progress = dragValue
                ?: if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { vm.seekTo((it * ui.durationMs).toLong()) }
                    dragValue = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((progress * ui.durationMs).toLong()), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(ui.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconToggleButton(checked = ui.shuffle, onCheckedChange = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (ui.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior")
                }
                FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (ui.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (ui.playWhenReady) "Pausar" else "Reproducir",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                }
                IconToggleButton(checked = djEnabled, onCheckedChange = { vm.toggleDj() }) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Locutor IA",
                        tint = if (djEnabled) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (speaking || !line.isNullOrBlank()) {
                HorizontalDivider()
                Text(
                    if (speaking) "Locutor al aire…" else "Lo último que dijo el locutor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (!line.isNullOrBlank()) {
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// ============================================================================ Ajustes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val testStatus by vm.testStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        val initial = settings
        if (initial == null) {
            Text("Cargando…", modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            SettingsForm(
                initial = initial,
                testStatus = testStatus,
                onChange = vm::saveSettings,
                onTest = vm::testDj,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SettingsForm(
    initial: DjSettings,
    testStatus: String?,
    onChange: (DjSettings) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(initial) }
    fun edit(transform: (DjSettings) -> DjSettings) {
        draft = transform(draft)
        onChange(draft)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Locutor IA activado", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = draft.djEnabled, onCheckedChange = { v -> edit { it.copy(djEnabled = v) } })
        }

        OutlinedTextField(
            value = draft.geminiApiKey,
            onValueChange = { v -> edit { it.copy(geminiApiKey = v) } },
            label = { Text("Clave de API de Gemini") },
            supportingText = { Text("Se guarda solo en este teléfono. Sin clave, el locutor usa frases fijas.") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.geminiModel,
            onValueChange = { v -> edit { it.copy(geminiModel = v) } },
            label = { Text("Modelo de Gemini") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.listenerName,
            onValueChange = { v -> edit { it.copy(listenerName = v) } },
            label = { Text("Tu nombre (el locutor te llama así)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.djName,
            onValueChange = { v -> edit { it.copy(djName = v) } },
            label = { Text("Nombre del locutor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.city,
            onValueChange = { v -> edit { it.copy(city = v) } },
            label = { Text("Ciudad para el clima") },
            supportingText = { Text("Ejemplo: Ciudad de México") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "El locutor habla cada ${draft.songsBetweenBreaks} canciones",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = draft.songsBetweenBreaks.toFloat(),
            onValueChange = { v -> edit { it.copy(songsBetweenBreaks = v.roundToInt().coerceIn(1, 6)) } },
            valueRange = 1f..6f,
            steps = 4,
        )

        Text(
            "Velocidad de la voz: ${"%.1f".format(draft.speechRate)}x",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = draft.speechRate,
            onValueChange = { v -> edit { it.copy(speechRate = (v * 10).roundToInt() / 10f) } },
            valueRange = 0.7f..1.5f,
        )

        Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
            Text("Probar al locutor")
        }
        if (testStatus != null) {
            Text(testStatus, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}
