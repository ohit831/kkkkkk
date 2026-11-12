package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium Futuristic Black, Cyan, and Violet Theme
val FuturisticDarkBg = Color(0xFF07060F)
val FuturisticSurface = Color(0xFF110D23)
val FuturisticSurfaceCard = Color(0xFF181332)
val FuturisticSurfaceElevated = Color(0xFF221A45)
val FuturisticBorder = Color(0xFF2D2256)
val FuturisticBorderGlow = Color(0xFF4C3688)

val NeonCyan = Color(0xFF00E5FF)
val NeonCyanLight = Color(0xFF80F2FF)
val NeonCyanDark = Color(0xFF009CB3)

val ElectricViolet = Color(0xFF9D4EDD)
val ElectricVioletLight = Color(0xFFC77DFF)
val DeepViolet = Color(0xFF5A189A)
val GlowViolet = Color(0xFF7B2CBF)

val AccentPink = Color(0xFFFF007F)

val TextPrimary = Color(0xFFF6F5FD)
val TextSecondary = Color(0xFFA59EC4)
val TextTertiary = Color(0xFF6D668E)

val StatusActive = Color(0xFF00F5A0)
val StatusWarning = Color(0xFFFFB703)
val StatusError = Color(0xFFFF3366)

val CyberGlowBrush = Brush.linearGradient(
    colors = listOf(NeonCyan, ElectricViolet, AccentPink)
)

val CyanVioletGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, ElectricViolet)
)

val DarkCardGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF1A1437), Color(0xFF0F0B21))
)
