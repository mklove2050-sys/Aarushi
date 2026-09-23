package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArushiDarkColorScheme = darkColorScheme(
    primary = ArushiCyan,
    onPrimary = Color.Black,
    secondary = ArushiViolet,
    onSecondary = Color.White,
    tertiary = ArushiMagenta,
    onTertiary = Color.White,
    background = ArushiDarkBg,
    onBackground = ArushiTextPrimary,
    surface = ArushiDarkSurface,
    onSurface = ArushiTextPrimary,
    surfaceVariant = ArushiDarkSurfaceVariant,
    onSurfaceVariant = ArushiTextSecondary,
    outline = ArushiGlowBorder,
    error = ArushiError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArushiDarkColorScheme,
        typography = Typography,
        content = content
    )
}

