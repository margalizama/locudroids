package com.locudroids.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locudroids.app.data.BrowserState
import com.locudroids.app.data.FolderEntry
import com.locudroids.app.data.Song

@Composable
fun LibraryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var addFor by remember { mutableStateOf<Song?>(null) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val browser by vm.browser.collectAsStateWithLifecycle()

    // Atrás = subir una carpeta (si no se está en la raíz).
    BackHandler(enabled = tab == 0 && !browser.atRoot) { vm.upFolder() }

    Column(modifier.fillMaxSize()) {
        Text(
            "BIBLIOTECA",
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 5.sp,
            color = Tech.TextMain,
        )
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = Tech.Cyan,
            edgePadding = 8.dp,
            divider = {},
        ) {
            listOf("CARPETAS", "TODAS", "LISTAS", "TOP").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { TechLabel(label, color = if (tab == index) Tech.Cyan else Tech.TextDim) },
                )
            }
        }

        addFor?.let { AddToListDialog(vm, it, onDismiss = { addFor = null }) }
        val actionsFor: (Song) -> List<SongAction> = { song -> songActions(vm, song) { addFor = it } }

        if (songs.isEmpty()) {
            Text(
                "No encontré MP3 en este teléfono.",
                modifier = Modifier.padding(24.dp),
                color = Tech.TextDim,
            )
        } else when (tab) {
            0 -> FoldersTab(vm, browser, ui.mediaId, actionsFor, Modifier.fillMaxSize())
            1 -> AllSongsTab(vm, songs, ui.mediaId, actionsFor, Modifier.fillMaxSize())
            2 -> PlaylistsTab(vm, ui.mediaId, onAddToList = { addFor = it }, modifier = Modifier.fillMaxSize())
            else -> TopTab(vm, ui.mediaId, onAddToList = { addFor = it }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FoldersTab(
    vm: MainViewModel,
    browser: BrowserState,
    currentId: String?,
    actionsFor: (Song) -> List<SongAction>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GlassCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!browser.atRoot) {
                        IconButton(onClick = { vm.upFolder() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Subir una carpeta",
                                tint = Tech.Cyan,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            browser.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Tech.TextMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TechLabel("${browser.total} CANCIONES")
                    }
                }

                // Migas de pan: tocar una sube directamente a esa carpeta.
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    browser.breadcrumb.forEachIndexed { index, crumb ->
                        if (index > 0) {
                            Text("›", color = Tech.TextDim, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                        Text(
                            crumb.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (index == browser.breadcrumb.lastIndex) Tech.Cyan else Tech.TextDim,
                            modifier = Modifier.clickable { vm.openFolder(crumb.path) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.playFolder(browser.path, shuffle = false) },
                        enabled = browser.total > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Tech.Cyan,
                            contentColor = Color(0xFF04101A),
                        ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Reproducir")
                    }
                    OutlinedButton(
                        onClick = { vm.playFolder(browser.path, shuffle = true) },
                        enabled = browser.total > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Aleatorio")
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(browser.folders, key = { it.path }) { folder ->
                FolderRow(
                    folder = folder,
                    onOpen = { vm.openFolder(folder.path) },
                    onPlay = { vm.playFolder(folder.path, shuffle = false) },
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
            itemsIndexed(browser.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = currentId == song.id.toString(),
                    onClick = { vm.playQueue(browser.songs, index) },
                    actions = actionsFor(song),
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntry, onOpen: () -> Unit, onPlay: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Tech.TextMain)
        },
        supportingContent = {
            Text("${folder.count} canciones", color = Tech.TextDim)
        },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Tech.Violet)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir carpeta", tint = Tech.Cyan)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Tech.TextDim)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    )
}

@Composable
private fun AllSongsTab(
    vm: MainViewModel,
    songs: List<Song>,
    currentId: String?,
    actionsFor: (Song) -> List<SongAction>,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar canción o artista") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(filtered, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = currentId == song.id.toString(),
                    onClick = { vm.playQueue(filtered, index) },
                    actions = actionsFor(song),
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
        }
    }
}
