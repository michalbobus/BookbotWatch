package com.bookbotwatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1E6F4C)
private val GreenLight = Color(0xFF7FD1A8)
private val Sand = Color(0xFFF6F1E7)

val DropGreen = Color(0xFF137333)
val RiseRed = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = Color(0xFF08301F),
    secondary = Color(0xFF4C6358),
    background = Sand,
    onBackground = Color(0xFF1A1C1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFEDEAE1),
    onSurfaceVariant = Color(0xFF44483F)
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF105234),
    onPrimaryContainer = GreenLight,
    secondary = Color(0xFFB3CCBE),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF191C19),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BC)
)

@Composable
fun BookbotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
