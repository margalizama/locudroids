package com.locudroids.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locudroids.app.data.Song
import kotlin.math.abs
import kotlin.math.sin

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Texto pequeño en mayúsculas y monoespaciado: da el toque "pantalla de equipo". */
@Composable
fun TechLabel(text: String, modifier: Modifier = Modifier, color: Color = Tech.TextDim) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )
}

/** Barras animadas tipo ecualizador. Se mueven mientras [active] sea true. */
@Composable
fun Visualizer(active: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "viz")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val bars = 36
        val gap = 5.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.verticalGradient(listOf(accent, Tech.Violet))
        for (i in 0 until bars) {
            val wave = abs(sin(t * (1 + i % 4) + i * 0.6f))
            val shimmer = 0.55f + 0.45f * abs(sin(t * 2f + i * 0.9f))
            val level = if (active) 0.15f + 0.85f * wave * shimmer else 0.06f
            val barHeight = size.height * level
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** Insignia "EN VIVO" / "AL AIRE" (pulsa cuando habla el locutor). */
@Composable
fun OnAirBadge(onAir: Boolean) {
    val transition = rememberInfiniteTransition(label = "air")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = if (onAir) Tech.Magenta else Tech.Cyan
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (onAir) pulse else 0.8f)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        TechLabel(if (onAir) "AL AIRE" else "EN VIVO", color = color)
    }
}

/** Una acción del menú ⋮ de una canción. */
class SongAction(val label: String, val onClick: () -> Unit)

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: List<SongAction> = emptyList(),
    detail: String? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) Tech.Cyan else MaterialTheme.colorScheme.onBackground,
            )
        },
        supportingContent = {
            Text(
                song.artist ?: "Artista desconocido",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Tech.TextDim,
            )
        },
        leadingContent = {
            Icon(
                if (isCurrent) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) Tech.Cyan else Tech.TextDim,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail ?: formatTime(song.durationMs),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Tech.TextDim,
                )
                if (actions.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = Tech.TextDim)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            actions.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a.label) },
                                    onClick = {
                                        open = false
                                        a.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
