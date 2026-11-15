package com.elsewhere.app.ui.world

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The poster palette: a sheet of stock, printed in ink and a red plate, with one mustard spot. */
object Poster {
    val Stock = Color(0xFFEFE6D2)
    val Stock2 = Color(0xFFE3D8BF)
    val Stock3 = Color(0xFFD6C9AB)
    val Ink = Color(0xFF141414)
    val Ink2 = Color(0xFF4A463E)
    val Ink3 = Color(0xFF6A6456)
    val Ghost = Color(0xFFB9B09A)
    val Red = Color(0xFFD63C20)
    val Red2 = Color(0xFFB02E16)
    val Mustard = Color(0xFFE9B531)
    /** The one green: a customs "accepted" stamp. */
    val Green = Color(0xFF2F6B45)
}

private val Scheme = lightColorScheme(
    primary = Poster.Red, onPrimary = Poster.Stock, primaryContainer = Poster.Red2, onPrimaryContainer = Poster.Stock,
    secondary = Poster.Ink, onSecondary = Poster.Stock, secondaryContainer = Poster.Stock2, onSecondaryContainer = Poster.Ink,
    tertiary = Poster.Mustard, onTertiary = Poster.Ink,
    background = Poster.Stock, onBackground = Poster.Ink, surface = Poster.Stock, onSurface = Poster.Ink,
    surfaceVariant = Poster.Stock2, onSurfaceVariant = Poster.Ink2, outline = Poster.Ink, outlineVariant = Poster.Ghost,
    error = Poster.Red2, onError = Poster.Stock,
)

/** Whether count-ups and slams play; snapshot tests turn it off to print the settled frame. */
val LocalMotion = staticCompositionLocalOf { true }

/** The quiet colour for captions on the current sheet: ink-3 on stock, stock-3 on an ink bill. */
val LocalMuted = compositionLocalOf { Poster.Ink3 }

/** Always the light sheet: a poster does not have a dark mode. */
@Composable
fun ElsewhereTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = PosterType.material(), content = content)
}
