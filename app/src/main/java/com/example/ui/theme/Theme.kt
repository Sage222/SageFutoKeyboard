package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FutoDarkColorScheme = darkColorScheme(
    primary = TelemetryCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF00414B),
    onPrimaryContainer = Color(0xFFB8F6FF),
    secondary = AdaptiveAmber,
    onSecondary = Color(0xFF2D1E00),
    secondaryContainer = Color(0xFF473100),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = NeuralEmerald,
    onTertiary = Color(0xFF003819),
    background = FutoSlateBg,
    onBackground = TextPrimary,
    surface = FutoSurface,
    onSurface = TextPrimary,
    surfaceVariant = FutoSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = FutoKeyCapBorder,
    error = CoralAlert
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FutoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
