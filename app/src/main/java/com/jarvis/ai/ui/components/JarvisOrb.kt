package com.jarvis.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.ai.model.JarvisStatus
import com.jarvis.ai.ui.theme.JarvisColors

@Composable
fun JarvisOrbButton(
    status: JarvisStatus,
    amplitude: Float,
    size: Dp = 84.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    JarvisStatus.THINKING, JarvisStatus.LOADING -> 1800
                    JarvisStatus.LISTENING, JarvisStatus.SPEAKING -> 2600
                    else -> 8000
                },
                easing = LinearEasing
            )
        ),
        label = "rot"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val smoothAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "amp"
    )

    val coreColor = when (status) {
        JarvisStatus.NO_MODEL -> JarvisColors.TextSecondary
        JarvisStatus.LISTENING -> JarvisColors.RoseAlert
        JarvisStatus.THINKING, JarvisStatus.LOADING, JarvisStatus.IMPORTING -> JarvisColors.IndigoAccent
        JarvisStatus.SPEAKING -> JarvisColors.EmeraldOk
        JarvisStatus.READY_VISION, JarvisStatus.READY_TEXT -> JarvisColors.CyanPrimary
    }

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val pulseScale = if (status == JarvisStatus.LISTENING) {
                0.86f + smoothAmp * 0.24f
            } else {
                0.88f * breath
            }

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.34f),
                        coreColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.12f
                ),
                radius = radius * 1.12f,
                center = center
            )

            // Rotating segmented tech arc
            val arcInset = radius * 0.18f
            val arcSize = Size((radius - arcInset) * 2f, (radius - arcInset) * 2f)
            val arcTopLeft = Offset(center.x - (radius - arcInset), center.y - (radius - arcInset))

            drawArc(
                color = coreColor.copy(alpha = 0.85f),
                startAngle = rotation,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
            drawArc(
                color = JarvisColors.IndigoAccent.copy(alpha = 0.65f),
                startAngle = rotation + 180f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
            )

            // Solid glossy inner core
            val coreRadius = radius * 0.56f * pulseScale.coerceAtMost(1.05f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.95f),
                        coreColor.copy(alpha = 0.45f),
                        JarvisColors.BgSurface
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
            drawCircle(
                color = coreColor.copy(alpha = 0.9f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 1.8f)
            )
        }

        val icon = when (status) {
            JarvisStatus.NO_MODEL -> Icons.Rounded.PowerSettingsNew
            JarvisStatus.LISTENING -> Icons.Rounded.GraphicEq
            JarvisStatus.THINKING, JarvisStatus.SPEAKING -> Icons.Rounded.Stop
            else -> Icons.Rounded.Mic
        }

        Icon(
            imageVector = icon,
            contentDescription = "Jarvis Voice",
            tint = Color.White,
            modifier = Modifier.size(size * 0.32f)
        )
    }
}
