package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.ui.theme.StrideEmber
import com.suprxsidh.deficit.ui.theme.StrideEmberDim

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
 */
@Composable
fun StartLineProgress(progress: Float, modifier: Modifier = Modifier) {
    StartLineTrack(progress = progress.coerceIn(0f, 1f), checkeredWhenFull = false, modifier = modifier)
}

@Composable
private fun StartLineTrack(progress: Float, checkeredWhenFull: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val squareW = size.width / SQUARE_COUNT
        val litCount = (SQUARE_COUNT * progress).toInt()
        for (i in 0 until SQUARE_COUNT) {
            val lit = i < litCount
            // Checkerboard alternation only matters in pure-divider mode (checkeredWhenFull);
            // in progress mode every lit square is solid ember and every unlit square is dim, no
            // alternation, so the fill boundary reads clearly as a progress edge.
            val color = when {
                checkeredWhenFull -> if (i % 2 == 0) StrideEmber else StrideEmberDim
                lit -> StrideEmber
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
