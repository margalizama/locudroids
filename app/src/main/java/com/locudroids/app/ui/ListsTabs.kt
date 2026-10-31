package com.locudroids.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locudroids.app.data.Playlist
import com.locudroids.app.data.Song

/** Acciones del menú ⋮ de una canción. */
fun songActions(vm: MainViewModel, song: Song, onAddToList: (Song) -> Unit): List<SongAction> = listOf(
    SongAction("Reproducir a continuación") { vm.playNext(song) },
    SongAction("Agregar a la cola") { vm.addToQueue(song) },
    SongAction("Agregar a una lista…") { onAddToList(song) },
    SongAction("Radio DJ desde esta canción") { vm.djFrom(song) },
)

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nombre") })
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elige a qué lista agregar la canción (o crea una nueva). */
@Composable
fun AddToListDialog(vm: MainViewModel, song: Song, onDismiss: () -> Unit) {
    val lists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextPromptDialog("Nueva lista", "", "Crear y agregar", onDismiss = { creating = false }) { name ->
            vm.createPlaylist(name, song)
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a una lista") },
        text = {
            Column {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Nueva lista")
                }
                lists.forEach { list ->
                    TextButton(onClick = {
                        vm.addToPlaylist(list.id, listOf(song))
                        onDismiss()
                    }) { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun PlaylistsTab(
    vm: MainViewModel,
    currentId: String?,
    onAddToList: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lists by vm.playlists.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val open = lists.firstOrNull { it.id == openId }

    BackHandler(enabled = open != null) { openId = null }

    if (creating) {
        TextPromptDialog("Nueva lista", "", "Crear", onDismiss = { creating = false }) { name ->
            openId = vm.createPlaylist(name).id
            creating = false
        }
    }

    if (open == null) {
        Column(modifier) {
            Button(
                onClick = { creating = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tech.Cyan, contentColor = Color(0xFF04101A)),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Nueva lista")
            }
            if (lists.isEmpty()) {
                Text(
                    "Aún no tienes listas. Crea una o usa ⋮ en cualquier canción → Agregar a una lista.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = Tech.TextDim,
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(lists, key = { it.id }) { list ->
                    val count = vm.songsOf(list).size
                    ListItem(
                        headlineContent = { Text(list.name, color = Tech.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$count canciones", color = Tech.TextDim) },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Tech.Violet)
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.playPlaylist(list, shuffle = false) }, enabled = count > 0) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir lista", tint = Tech.Cyan)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openId = list.id },
                    )
                    HorizontalDivider(color = Tech.GlassBorder)
                }
            }
        }
        return
    }

    val songs = vm.songsOf(open)
    if (renaming) {
        TextPromptDialog("Renombrar lista", open.name, "Guardar", onDismiss = { renaming = false }) { name ->
            vm.renamePlaylist(open.id, name)
            renaming = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar \"${open.name}\"?") },
            text = { Text("Solo se borra la lista; tus canciones no se tocan.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(open.id)
                    confirmDelete = false
                    openId = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { openId = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Tech.Cyan)
            }
            Column(Modifier.weight(1f)) {
                Text(open.name, style = MaterialTheme.typography.titleMedium, color = Tech.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TechLabel("${songs.size} CANCIONES")
            }
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Renombrar", tint = Tech.TextDim)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar lista", tint = Tech.TextDim)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { vm.playPlaylist(open, shuffle = false) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Tech.Cyan, contentColor = Color(0xFF04101A)),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Reproducir")
            }
            OutlinedButton(
                onClick = { vm.playPlaylist(open, shuffle = true) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Aleatorio")
            }
        }
        if (songs.isEmpty()) {
            Text(
                "Lista vacía. Agrega canciones con ⋮ → Agregar a una lista.",
                modifier = Modifier.padding(24.dp),
                color = Tech.TextDim,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = currentId == song.id.toString(),
                    onClick = { vm.playQueue(songs, index) },
                    actions = songActions(vm, song, onAddToList) + SongAction("Quitar de esta lista") {
                        vm.removeFromPlaylist(open.id, song)
                    },
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
        }
    }
}

@Composable
fun TopTab(
    vm: MainViewModel,
    currentId: String?,
    onAddToList: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val top by vm.topSongs.collectAsStateWithLifecycle()
    val recent by vm.recentSongs.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var showRecent by rememberSaveable { mutableStateOf(false) }
    val songs = if (showRecent) recent else top

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !showRecent, onClick = { showRecent = false }, label = { Text("Más escuchadas") })
            FilterChip(selected = showRecent, onClick = { showRecent = true }, label = { Text("Recientes") })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { vm.playQueue(songs, 0, shuffle = false) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir todo", tint = Tech.Cyan)
                }
            }
        }
        if (songs.isEmpty()) {
            Text(
                "Todavía no hay historial. Una canción cuenta cuando la escuchas 30 segundos o la mitad.",
                modifier = Modifier.padding(24.dp),
                color = Tech.TextDim,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = currentId == song.id.toString(),
                    onClick = { vm.playQueue(songs, index) },
                    actions = songActions(vm, song, onAddToList),
                    detail = if (showRecent) null else "${stats[song.path]?.plays ?: 0}×",
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
        }
    }
}
