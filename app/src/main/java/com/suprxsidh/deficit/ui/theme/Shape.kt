package com.suprxsidh.deficit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Small, squared-off corners everywhere (2-8dp) — deliberate anti-Material choice, see spec §5.
 * Only for ordinary Card/Button/TextField surfaces; LedReadout and PunchCardRow use their own
 * shapes.
 */
val StrideShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
