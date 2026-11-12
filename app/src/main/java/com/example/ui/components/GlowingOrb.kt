package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun GlowingOrb(
    orbState: OrbState,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    // Slow organic breathing
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Orbital ring rotations
    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_fast"
    )

    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_slow"
    )

    // Dynamic reactivity multiplier based on state and audio RMS
    val stateBoost = when (orbState) {
        OrbState.LISTENING -> 1.15f + (rmsLevel * 0.45f)
        OrbState.THINKING -> 1.08f
        OrbState.SPEAKING -> 1.18f + (breatheScale * 0.12f)
        OrbState.ERROR -> 1.0f
        OrbState.IDLE -> breatheScale
    }

    val primaryGlowColor = when (orbState) {
        OrbState.LISTENING -> NeonCyan
        OrbState.THINKING -> ElectricVioletLight
        OrbState.SPEAKING -> ElectricViolet
        OrbState.ERROR -> Color(0xFFFF3366)
        OrbState.IDLE -> NeonCyan
    }

    val secondaryGlowColor = when (orbState) {
        OrbState.LISTENING -> ElectricViolet
        OrbState.THINKING -> NeonCyan
        OrbState.SPEAKING -> NeonCyanLight
        OrbState.ERROR -> Color(0xFF990022)
        OrbState.IDLE -> ElectricViolet
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.62f * stateBoost

            // 1. Ambient outer diffuse halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.35f),
                        secondaryGlowColor.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f
                ),
                radius = baseRadius * 1.55f,
                center = center
            )

            // 2. Rotating outer orbital energy ring 1
            rotate(rotationFast, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryGlowColor,
                            secondaryGlowColor,
                            Color.Transparent,
                            primaryGlowColor
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - baseRadius * 1.25f, center.y - baseRadius * 0.85f),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2.5f, baseRadius * 1.7f),
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }

            // 3. Counter-rotating orbital ring 2
            rotate(rotationSlow, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryGlowColor,
                            Color.Transparent,
                            primaryGlowColor,
                            secondaryGlowColor
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - baseRadius * 0.95f, center.y - baseRadius * 1.3f),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 1.9f, baseRadius * 2.6f),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 4. Core 3D glass sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F1A3A),
                        Color(0xFF0A071E),
                        Color(0xFF04030A)
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // 5. Luminescent inner energy glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.85f),
                        secondaryGlowColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.15f, center.y - baseRadius * 0.2f),
                    radius = baseRadius * 0.8f
                ),
                radius = baseRadius * 0.8f,
                center = center
            )

            // 6. Specular optical glass highlight (gives 3D glass orb volume)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.32f, center.y - baseRadius * 0.35f),
                    radius = baseRadius * 0.4f
                ),
                radius = baseRadius * 0.35f,
                center = Offset(center.x - baseRadius * 0.32f, center.y - baseRadius * 0.35f)
            )

            // 7. Dynamic particle nodes around core
            val particleCount = 12
            for (i in 0 until particleCount) {
                val angle = Math.toRadians((i * (360.0 / particleCount) + rotationFast * 0.5).toDouble())
                val particleRadius = baseRadius * (1.05f + 0.1f * sin(angle.toFloat() * 2f))
                val px = center.x + (particleRadius * cos(angle)).toFloat()
                val py = center.y + (particleRadius * sin(angle)).toFloat()
                drawCircle(
                    color = primaryGlowColor.copy(alpha = 0.8f),
                    radius = 2.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}
