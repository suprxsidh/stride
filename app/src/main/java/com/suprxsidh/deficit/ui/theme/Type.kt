package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.suprxsidh.deficit.R

/**
 * JetBrains Mono (bundled, OFL) — one family for the whole app, including body copy. True
 * monospace gives tabular figures for free on every digit, which the LED-readout/punch-card
 * ledger motif depends on. See spec §3 for why a downloadable Google Font wasn't used.
 */
val StrideMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

// Uppercase transform (titleLarge, labelSmall) is applied at call sites via `.uppercase()` on
// the string — TextStyle has no case-transform property. Don't forget it when using these styles.
val DeficitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = 0.5.sp
    ),
    displayMedium = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = StrideMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp
    ),
)
