package com.locudroids.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta tecnológica: azul noche, cian eléctrico, violeta y magenta para "al aire". */
object Tech {
    val Bg0 = Color(0xFF04060D)
    val Bg1 = Color(0xFF0B1228)
    val Cyan = Color(0xFF38E1FF)
    val Violet = Color(0xFF8B7BFF)
    val Magenta = Color(0xFFFF4D9A)
    val Glass = Color(0x14FFFFFF)
    val GlassBorder = Color(0x26FFFFFF)
    val TextDim = Color(0xFF93A0BE)
    val TextMain = Color(0xFFE6ECFA)
}

private val TechColors = darkColorScheme(
    primary = Tech.Cyan,
    onPrimary = Color(0xFF00212A),
    primaryContainer = Color(0xFF12394A),
    onPrimaryContainer = Color(0xFFCDF3FF),
    secondary = Tech.Violet,
    tertiary = Tech.Magenta,
    background = Tech.Bg0,
    onBackground = Tech.TextMain,
    surface = Tech.Bg1,
    onSurface = Tech.TextMain,
    surfaceVariant = Color(0xFF16203A),
    onSurfaceVariant = Tech.TextDim,
    outline = Color(0x33FFFFFF),
)

@Composable
fun LocudroidsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TechColors, content = content)
}

/** Fondo degradado con resplandores violeta y cian. */
@Composable
fun TechBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tech.Bg1, Tech.Bg0)))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Tech.Violet.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(900f, 0f),
                        radius = 1100f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Tech.Cyan.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(0f, 1900f),
                        radius = 1300f,
                    )
                )
        )
        content()
    }
}

/** Tarjeta translúcida con borde fino (efecto vidrio). Con [glow] el borde se ilumina. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(Tech.Glass)
            .border(1.dp, glow?.copy(alpha = 0.6f) ?: Tech.GlassBorder, shape),
        content = content,
    )
}
