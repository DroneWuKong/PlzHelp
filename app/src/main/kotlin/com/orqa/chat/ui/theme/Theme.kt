package com.orqa.chat.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── ORQA Dark Palette ─────────────────────────────────────────────

val Orange       = Color(0xFFE8610A)
val OrangeDim    = Color(0xFF7A3005)
val BgPrimary    = Color(0xFF0F0F10)
val BgSurface    = Color(0xFF1A1A1C)
val BgSurface2   = Color(0xFF222225)
val BorderDim    = Color(0xFF2E2E32)
val BorderMid    = Color(0xFF3E3E44)
val TextPrimary  = Color(0xFFE8E8EA)
val TextMuted    = Color(0xFF888890)
val TextDim      = Color(0xFF555560)
val BlueBg       = Color(0xFF0D1829)
val BlueBorder   = Color(0xFF1E3A5F)
val BlueText     = Color(0xFFA8C8F8)
val GreenOk      = Color(0xFF22C55E)
val RedError     = Color(0xFFEF4444)

private val OrqaColorScheme = darkColorScheme(
    primary          = Orange,
    onPrimary        = Color.White,
    secondary        = TextMuted,
    onSecondary      = BgPrimary,
    background       = BgPrimary,
    onBackground     = TextPrimary,
    surface          = BgSurface,
    onSurface        = TextPrimary,
    surfaceVariant   = BgSurface2,
    onSurfaceVariant = TextMuted,
    outline          = BorderDim,
    error            = RedError,
    onError          = Color.White,
)

@Composable
fun OrqaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OrqaColorScheme,
        content     = content
    )
}
