package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.ui.theme.StrideEmber
import com.suprxsidh.deficit.ui.theme.StrideEmberDim
import com.suprxsidh.deficit.ui.theme.StrideError

private const val SQUARE_COUNT = 24

/**
 * Static checkered start-line divider — the shared unifying motif across screens. Use as a
 * section break wherever a screen currently has a `Divider()`/`HorizontalDivider()`. See spec §8.
 */
@Composable
fun StartLineDivider(modifier: Modifier = Modifier) {
    StartLineTrack(progress = 1f, checkeredWhenFull = true, modifier = modifier)
}

/**
 * Progress variant of the start-line motif — squares up to [progress] (0f..1f) render lit
 * ember, the rest render dim. Use anywhere the app currently has a `LinearProgressIndicator`
 * (budget bar, weekly target bar). See spec §8.
 *
 * [progress] is always clamped to the visible 0f..1f track, so a real value past 100% (e.g. a
 * calorie total over the soft budget) still ends the bar fully lit rather than overflowing —
 * that numeric overage is shown elsewhere (the LED readout's raw number is never capped). When
 * [overBudget] is true the fully-lit bar renders in [StrideError] instead of [StrideEmber] so a
 * full bar reads as a distinct "over" state rather than looking identical to "exactly on budget".
 */
@Composable
fun StartLineProgress(progress: Float, modifier: Modifier = Modifier, overBudget: Boolean = false) {
    StartLineTrack(
        progress = progress.coerceIn(0f, 1f),
        checkeredWhenFull = false,
        modifier = modifier,
        litColor = if (overBudget) StrideError else StrideEmber,
    )
}

@Composable
private fun StartLineTrack(
    progress: Float,
    checkeredWhenFull: Boolean,
    modifier: Modifier = Modifier,
    litColor: Color = StrideEmber,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val squareW = size.width / SQUARE_COUNT
        val litCount = (SQUARE_COUNT * progress).toInt()
        for (i in 0 until SQUARE_COUNT) {
            val lit = i < litCount
            // Checkerboard alternation only matters in pure-divider mode (checkeredWhenFull);
            // in progress mode every lit square is solid `litColor` and every unlit square is
            // dim, no alternation, so the fill boundary reads clearly as a progress edge.
            val color = when {
                checkeredWhenFull -> if (i % 2 == 0) StrideEmber else StrideEmberDim
                lit -> litColor
                else -> StrideEmberDim
            }
            drawRect(
                color = color,
                topLeft = Offset(i * squareW, 0f),
                size = Size(squareW * 0.82f, size.height),
            )
        }
    }
}
