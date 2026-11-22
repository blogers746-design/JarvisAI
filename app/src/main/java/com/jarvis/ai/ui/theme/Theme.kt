package com.jarvis.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JarvisColors {
    val BgDeep = Color(0xFF060A12)
    val BgSurface = Color(0xFF0E1525)
    val BgCard = Color(0xFF131D31)
    val BorderSubtle = Color(0xFF1E2D4A)
    val CyanPrimary = Color(0xFF00E5FF)
    val CyanSoft = Color(0xFF38BDF8)
    val IndigoAccent = Color(0xFF6366F1)
    val EmeraldOk = Color(0xFF10B981)
    val AmberWarn = Color(0xFFF59E0B)
    val RoseAlert = Color(0xFFF43F5E)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
}

private val JarvisDarkScheme = darkColorScheme(
    primary = JarvisColors.CyanPrimary,
    onPrimary = Color(0xFF04131B),
    secondary = JarvisColors.IndigoAccent,
    onSecondary = Color.White,
    background = JarvisColors.BgDeep,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.BgSurface,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.BgCard,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.BorderSubtle
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkScheme,
        content = content
    )
}
