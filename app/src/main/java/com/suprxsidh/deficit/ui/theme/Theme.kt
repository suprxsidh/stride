package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DeficitColorScheme = darkColorScheme(
    primary = DeficitAccent,
    onPrimary = DeficitBlack,
    background = DeficitBlack,
    onBackground = DeficitOnBlack,
    surface = DeficitSurface,
    onSurface = DeficitOnBlack,
    error = DeficitError
)

@Composable
fun DeficitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeficitColorScheme,
        typography = DeficitTypography,
        content = content
    )
}
