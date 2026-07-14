package com.alessandro.falco.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FalcoBlack = Color(0xFF090B0D)
val FalcoSurface = Color(0xFF14181C)
val FalcoSurface2 = Color(0xFF1B2025)
val FalcoLime = Color(0xFFC6FF3D)
val FalcoMuted = Color(0xFF99A3AD)

private val scheme = darkColorScheme(
    primary = FalcoLime, onPrimary = FalcoBlack, background = FalcoBlack,
    onBackground = Color(0xFFF3F6F8), surface = FalcoSurface, surfaceVariant = FalcoSurface2,
    onSurface = Color(0xFFF3F6F8), onSurfaceVariant = FalcoMuted, error = Color(0xFFFF6B6B)
)

@Composable fun FalcoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
