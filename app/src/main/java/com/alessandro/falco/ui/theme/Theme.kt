package com.alessandro.falco.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FalcoBlack = Color(0xFF0B0D13)
val FalcoSurface = Color(0xFF11141D)
val FalcoSurface2 = Color(0xFF1A1E29)
val FalcoLime = Color(0xFFF9A90A)
val FalcoMuted = Color(0xFF72798A)
val FalcoTeal = Color(0xFF299CA2)

private val scheme = darkColorScheme(
    primary = FalcoLime, onPrimary = FalcoBlack, secondary = FalcoTeal, background = FalcoBlack,
    onBackground = Color(0xFFF3F6F8), surface = FalcoSurface, surfaceVariant = FalcoSurface2,
    onSurface = Color(0xFFF3F6F8), onSurfaceVariant = FalcoMuted, error = Color(0xFFFF6B6B)
)

@Composable fun FalcoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
