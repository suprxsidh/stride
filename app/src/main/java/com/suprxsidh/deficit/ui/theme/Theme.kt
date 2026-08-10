package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DeficitColorScheme = darkColorScheme(
    // Primary palette (bright accent on black)
    primary = DeficitAccent,
    onPrimary = DeficitBlack,
    primaryContainer = DeficitAccentDim,
    onPrimaryContainer = DeficitOnBlack,

    // Secondary palette (reuse accent-family for consistency)
    secondary = DeficitAccentDim,
    onSecondary = DeficitBlack,
    secondaryContainer = DeficitSurfaceVariant,
    onSecondaryContainer = DeficitAccent,

    // Tertiary palette (reuse accent-family for consistency)
    tertiary = DeficitAccentDim,
    onTertiary = DeficitBlack,
    tertiaryContainer = DeficitSurfaceVariant,
    onTertiaryContainer = DeficitAccent,

    // Error palette (muted red on black)
    error = DeficitError,
    onError = DeficitBlack,
    errorContainer = DeficitErrorDim,
    onErrorContainer = DeficitOnBlack,

    // Background and surface (solid black family)
    background = DeficitBlack,
    onBackground = DeficitOnBlack,
    surface = DeficitSurface,
    onSurface = DeficitOnBlack,
    surfaceVariant = DeficitSurfaceVariant,
    onSurfaceVariant = DeficitOutlineVariant,

    // Outline and border (muted gray)
    outline = DeficitOutline,
    outlineVariant = DeficitOutlineVariant,

    // Inverse colors (for snackbars, etc.)
    inverseSurface = DeficitInverseSurface,
    inverseOnSurface = DeficitBlack,
    inversePrimary = DeficitAccentDim
)

@Composable
fun DeficitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeficitColorScheme,
        typography = DeficitTypography,
        content = content
    )
}
