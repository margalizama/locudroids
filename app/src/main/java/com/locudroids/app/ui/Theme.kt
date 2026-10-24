package com.locudroids.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LocudroidsColors = darkColorScheme(
    primary = Color(0xFFB39BFF),
    onPrimary = Color(0xFF1B0B52),
    primaryContainer = Color(0xFF3A22A8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF7FDBCA),
    background = Color(0xFF0E0B16),
    onBackground = Color(0xFFE8E4F2),
    surface = Color(0xFF0E0B16),
    onSurface = Color(0xFFE8E4F2),
    surfaceVariant = Color(0xFF211B33),
    onSurfaceVariant = Color(0xFFC8C1DB),
)

@Composable
fun LocudroidsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LocudroidsColors, content = content)
}
