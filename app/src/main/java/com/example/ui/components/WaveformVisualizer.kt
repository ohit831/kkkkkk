package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    isActive: Boolean,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = totalWidth / barCount
        val barWidth = (barSpacing * 0.42f).coerceAtLeast(3f)

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount
            // Bell curve window so bars are higher in the center like a natural vocal spectrum
            val bellCurve = sin(normalizedX * Math.PI).toFloat()

            val animatedHeight = if (isActive) {
                val wave = (sin(phase + i * 0.45f) + 1f) / 2f
                val dynamicFactor = if (rmsLevel > 0.05f) {
                    0.3f + (rmsLevel * 0.7f * wave)
                } else {
                    0.25f + (wave * 0.5f)
                }
                (canvasHeight * bellCurve * dynamicFactor).coerceIn(6f, canvasHeight)
            } else {
                (canvasHeight * 0.12f * bellCurve).coerceAtLeast(4f)
            }

            val x = i * barSpacing + (barSpacing - barWidth) / 2f
            val y = (canvasHeight - animatedHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(NeonCyan, ElectricViolet),
                    startY = y,
                    endY = y + animatedHeight
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, animatedHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
