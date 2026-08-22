package com.suprxsidh.stride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Stride visual redesign (2026-08-20) — ember-on-black, see spec §2.
private val StrideColorScheme = darkColorScheme(
    // Primary palette (ember accent on near-black)
    primary = StrideEmber,
    onPrimary = StrideSurfaceSunken,
    primaryContainer = StrideEmberDim,
    onPrimaryContainer = StrideOnSurface,

    // Secondary palette (reuse ember-family for consistency — single-accent design)
    secondary = StrideEmberDim,
    onSecondary = StrideOnSurface,
    secondaryContainer = StrideSurfaceRaised,
    onSecondaryContainer = StrideEmber,

    // Tertiary palette (reuse ember-family for consistency)
    tertiary = StrideEmberDim,
    onTertiary = StrideOnSurface,
    tertiaryContainer = StrideSurfaceRaised,
    onTertiaryContainer = StrideEmber,

    // Error palette (warm red, shifted near ember hue)
    error = StrideError,
    onError = StrideSurfaceSunken,
    errorContainer = StrideSurfaceRaised,
    onErrorContainer = StrideError,

    // Background and surface (near-black family)
    background = StrideBackground,
    onBackground = StrideOnSurface,
    surface = StrideSurface,
    onSurface = StrideOnSurface,
    surfaceVariant = StrideSurfaceRaised,
    onSurfaceVariant = StrideOnSurfaceMuted,

    // Outline and border
    outline = StrideOutline,
    outlineVariant = StrideOutline,

    // Inverse colors (for snackbars, etc.)
    inverseSurface = StrideOnSurface,
    inverseOnSurface = StrideSurfaceSunken,
    inversePrimary = StrideEmberDim
)

@Composable
fun StrideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StrideColorScheme,
        typography = StrideTypography,
        shapes = StrideShapes,
        content = content
    )
}
