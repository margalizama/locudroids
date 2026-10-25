package com.locudroids.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val speaking by vm.djSpeaking.collectAsStateWithLifecycle()
    val line by vm.djLine.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val onAir by vm.djOnAir.collectAsStateWithLifecycle()
    val mood by vm.djMood.collectAsStateWithLifecycle()

    val djEnabled = settings?.djEnabled ?: true
    val accent = if (speaking) Tech.Magenta else Tech.Cyan

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Encabezado: marca + estación + insignia
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "LOCUDROIDS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 5.sp,
                    color = Tech.TextMain,
                )
                TechLabel(settings?.stationName ?: "Locudroids FM")
            }
            OnAirBadge(onAir = speaking)
        }

        Artwork(art, accent)
        Visualizer(active = ui.isPlaying, accent = accent)

        // Título y artista
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ui.title ?: "Elige una canción",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tech.TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                ui.artist ?: " ",
                fontSize = 14.sp,
                color = Tech.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // Barra de progreso
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val progress = dragValue
            ?: if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { vm.seekTo((it * ui.durationMs).toLong()) }
                    dragValue = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TechLabel(formatTime((progress * ui.durationMs).toLong()))
                TechLabel(formatTime(ui.durationMs))
            }
        }

        // Controles
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconToggleButton(checked = ui.shuffle, onCheckedChange = { vm.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (ui.shuffle) Tech.Cyan else Tech.TextDim,
                )
            }
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = Tech.TextMain)
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Tech.Cyan, Tech.Violet)))
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ui.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (ui.playWhenReady) "Pausar" else "Reproducir",
                    tint = Color(0xFF04101A),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Tech.TextMain)
            }
            IconToggleButton(checked = djEnabled, onCheckedChange = { vm.toggleDj() }) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Locutor IA",
                    tint = if (djEnabled) Tech.Magenta else Tech.TextDim,
                )
            }
        }

        // Cabina del locutor
        GlassCard(Modifier.fillMaxWidth(), glow = if (speaking) Tech.Magenta else null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (speaking) Tech.Magenta else Tech.Cyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    val crew = settings?.announcers?.joinToString(", ") { it.name } ?: "Locutores"
                    TechLabel(
                        when {
                            !djEnabled -> "LOCUTORES APAGADOS"
                            speaking -> "${(onAir ?: crew).uppercase()} · AL AIRE"
                            !mood.isNullOrBlank() -> "ÁNIMO · ${mood.orEmpty().uppercase()}"
                            else -> "EN CABINA · ${crew.uppercase()}"
                        },
                        color = if (speaking) Tech.Magenta else Tech.TextDim,
                    )
                    Spacer(Modifier.weight(1f))
                    if (speaking) {
                        TextButton(onClick = { vm.skipDj() }) {
                            Text("CALLAR", color = Tech.Magenta, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    line ?: "Tus locutores entran en cuanto empiece la música.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (speaking) Tech.TextMain else Tech.TextDim,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Artwork(art: ImageBitmap?, accent: Color) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth(0.78f)
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF16203A), Color(0xFF2A1F5C))))
            .border(1.5.dp, Brush.linearGradient(listOf(accent, Tech.Violet)), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = "Portada",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = accent.copy(alpha = 0.7f),
                modifier = Modifier.size(96.dp),
            )
        }
    }
}
