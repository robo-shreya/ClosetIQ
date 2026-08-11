package com.closetiq.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Nocturne is a single dark theme, so there is no light variant and no dynamic colour.
 *
 * Dynamic colour would let the device wallpaper repaint the app, which defeats the point:
 * the only saturated things on screen are meant to be the garments themselves.
 */
private val NocturneColorScheme = darkColorScheme(
    primary = Nocturne.Accent,
    onPrimary = Nocturne.Bg,
    primaryContainer = Nocturne.Accent900,
    onPrimaryContainer = Nocturne.Accent200,

    secondary = Nocturne.Accent300,
    onSecondary = Nocturne.Bg,

    background = Nocturne.Bg,
    onBackground = Nocturne.Text,

    surface = Nocturne.Surface,
    onSurface = Nocturne.Text,
    surfaceVariant = Nocturne.Neutral900,
    onSurfaceVariant = Nocturne.Neutral500,

    outline = Nocturne.Neutral800,
    outlineVariant = Nocturne.Neutral900,

    error = Nocturne.Accent300,
    errorContainer = Nocturne.Accent900,
    onErrorContainer = Nocturne.Accent200
)

@Composable
fun ClosetIQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NocturneColorScheme,
        typography = NocturneTypography,
        content = content
    )
}
