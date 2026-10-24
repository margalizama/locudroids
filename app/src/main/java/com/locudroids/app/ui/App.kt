package com.locudroids.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Ahora", Icons.Filled.GraphicEq),
    NavItem("Biblioteca", Icons.Filled.LibraryMusic),
    NavItem("Ajustes", Icons.Filled.Settings),
)

@Composable
fun LocudroidsApp(vm: MainViewModel) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()

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

    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }

    TechBackground {
        if (!granted) {
            PermissionGate(onRequest = { launcher.launch(permissionsToAsk) })
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Column {
                        if (tab != 0 && ui.title != null) {
                            MiniPlayer(
                                title = ui.title.orEmpty(),
                                artist = ui.artist,
                                playing = ui.playWhenReady,
                                onOpen = { tab = 0 },
                                onToggle = { vm.togglePlay() },
                            )
                        }
                        NavigationBar(containerColor = Color(0xE60A1022), tonalElevation = 0.dp) {
                            navItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Tech.Cyan,
                                        selectedTextColor = Tech.Cyan,
                                        indicatorColor = Tech.Cyan.copy(alpha = 0.16f),
                                        unselectedIconColor = Tech.TextDim,
                                        unselectedTextColor = Tech.TextDim,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    when (tab) {
                        0 -> PlayerScreen(vm)
                        1 -> LibraryScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TechLabel("LOCUDROIDS FM", color = Tech.Cyan)
                Text(
                    "Necesito permiso para leer tus archivos de música.",
                    color = Tech.TextMain,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tech.Cyan,
                        contentColor = Color(0xFF04101A),
                    ),
                ) {
                    Text("Dar permiso")
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String?,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Tech.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artist != null) {
                    Text(artist, color = Tech.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    tint = Tech.Cyan,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
