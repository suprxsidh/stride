package com.suprxsidh.stride.ui.theme.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOutline
import com.suprxsidh.stride.ui.theme.StrideSurface

/**
 * A rounded rect with two semicircular notches punched into the left/right edges at vertical
 * center — reads as a ticket stub / IBM punch card. See spec §7.
 */
class PunchCardShape(
    private val notchRadiusDp: Float = 8f,
    private val cornerDp: Float = 4f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notchR = with(density) { notchRadiusDp.dp.toPx() }
        val corner = with(density) { cornerDp.dp.toPx() }
        val base = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    cornerRadius = CornerRadius(corner, corner)
                )
            )
        }
        val midY = size.height / 2f
        val leftNotch = Path().apply {
            addOval(Rect(center = Offset(0f, midY), radius = notchR))
        }
        val rightNotch = Path().apply {
            addOval(Rect(center = Offset(size.width, midY), radius = notchR))
        }
        val result = Path()
        result.op(base, leftNotch, PathOperation.Difference)
        result.op(result, rightNotch, PathOperation.Difference)
        return Outline.Generic(result)
    }
}

/**
 * A punch-card-shaped list row. [leading] is typically a small label/stamp or dot cluster,
 * [trailing] typically a tabular-mono value (kcal, weight, time). Both slots are optional so
 * this covers everything from a food-log entry to a consistency-grid week-summary line.
 *
 * The 8dp notch radius means rows need at least [Spacing.lg] horizontal padding so text never
 * collides with the punched-out curve — don't shrink it when reusing this on a narrow screen.
 */
@Composable
fun PunchCardRow(
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = PunchCardShape()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StrideSurface, shape)
            .border(1.dp, StrideOutline, shape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Row(modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs)) { content() }
        trailing?.invoke(this)
    }
}
