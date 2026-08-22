package com.suprxsidh.stride.ui.theme.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suprxsidh.stride.ui.theme.StrideEmber
import com.suprxsidh.stride.ui.theme.StrideEmberDim

private const val GRID = 4

/**
 * Bottom-nav *active*-tab indicator (spec §9): a small checkered-flag glyph — a 4x4 grid
 * alternating [StrideEmber]/[StrideEmberDim] in a checkerboard pattern, reusing the same
 * alternating-square drawing logic [StartLineDivider] uses for its "fully lit" checkered state,
 * just applied to a 2D grid instead of a 1D strip. Inactive tabs keep the stock Material icon
 * outline in `StrideOnSurfaceMuted` instead — this composable is only ever shown for the
 * selected destination; the call site decides which to render.
 *
 * [contentDescription] mirrors the accessibility label the stock `Icon` would otherwise carry
 * (e.g. the destination's label) since this is a raw `Canvas`, not an `Icon`, and gets no
 * description for free.
 */
@Composable
fun NavFlagIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, contentDescription: String? = null) {
    val descModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(modifier = descModifier.size(size)) {
        val cell = this.size.width / GRID
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val color = if ((row + col) % 2 == 0) StrideEmber else StrideEmberDim
                drawRect(
                    color = color,
                    topLeft = Offset(col * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
