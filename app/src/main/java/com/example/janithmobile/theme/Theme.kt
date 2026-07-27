package com.example.janithmobile.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = CyberBg,
    onBackground = LightText,
    surface = CyberBgSecondary,
    onSurface = LightText,
    surfaceVariant = CyberBgTertiary,
    onSurfaceVariant = SlateText,
    error = RedDanger,
    onError = Color.White
)

@Composable
fun JanithMobileTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

