package com.suprxsidh.stride.ui.theme.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideEmber
import com.suprxsidh.stride.ui.theme.StrideEmberGlow
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.StrideOutline
import com.suprxsidh.stride.ui.theme.StrideSurfaceRaised
import com.suprxsidh.stride.ui.theme.StrideSurfaceSunken

/**
 * A seven-segment-LED-style stat readout: a recessed black bezel with a glowing ember number.
 * [value] must already be formatted (e.g. "1,842" or "-0.4") — this composable only draws, it
 * does not format numbers. [label] is rendered above the number, uppercase, muted.
 *
 * Glow is faked with 4 offset low-alpha copies of the digit text behind the real one, rather
 * than a real blur — `RenderEffect` needs API 31+ and this project's minSdk is 28.
 *
 * See docs/superpowers/specs/2026-08-20-visual-redesign-spec.md §6.
 */
@Composable
fun LedReadout(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
) {
    Box(
        modifier = modifier
            .background(StrideSurfaceRaised, RoundedCornerShape(8.dp))
            .padding(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .background(StrideSurfaceSunken, RoundedCornerShape(4.dp))
                .border(1.dp, StrideOutline, RoundedCornerShape(4.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Column {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = StrideOnSurfaceMuted,
                )
                Box {
                    val offsets = listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f)
                    offsets.forEach { (dx, dy) ->
                        Text(
                            text = value,
                            style = style,
                            color = StrideEmberGlow,
                            modifier = Modifier.glowOffset(dx, dy),
                        )
                    }
                    Text(text = value, style = style, color = StrideEmber)
                }
            }
        }
    }
}

private fun Modifier.glowOffset(dx: Float, dy: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(dx.toInt(), dy.toInt())
            }
        }
    )
