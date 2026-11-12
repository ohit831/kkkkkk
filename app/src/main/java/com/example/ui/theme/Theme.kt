package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RathodColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = NeonCyanLight,
    secondary = ElectricViolet,
    onSecondary = Color(0xFF240046),
    secondaryContainer = DeepViolet,
    onSecondaryContainer = ElectricVioletLight,
    tertiary = NeonCyanLight,
    onTertiary = Color(0xFF00363F),
    background = FuturisticDarkBg,
    onBackground = TextPrimary,
    surface = FuturisticSurface,
    onSurface = TextPrimary,
    surfaceVariant = FuturisticSurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = FuturisticBorder,
    outlineVariant = FuturisticBorderGlow,
    error = StatusError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RathodColorScheme,
        typography = Typography,
        content = content
    )
}
