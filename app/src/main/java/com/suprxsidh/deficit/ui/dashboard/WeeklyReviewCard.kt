package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import com.suprxsidh.deficit.ui.theme.Spacing
import com.suprxsidh.deficit.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.deficit.ui.theme.component.PunchCardRow
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WeeklyReviewCard(review: WeeklyReviewEntity, onDismiss: () -> Unit) {
    PunchCardRow(modifier = Modifier.padding(top = Spacing.xs)) {
        Column {
            Text("Last week".uppercase(), style = MaterialTheme.typography.titleMedium)
            Text(
                "${review.runsCompleted}/${review.runFloor} floor · ${review.runsCompleted}/${review.runTarget} target",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("${review.daysLogged} days logged", style = MaterialTheme.typography.bodyMedium)
            review.avgDailyDeficitKcal?.let {
                Text(
                    "Average deficit: ${it.roundToInt()} kcal/day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideOnSurfaceMuted,
                )
            }
            review.rollingWeightChangeKg?.let {
                val direction = if (it < 0) "down" else "up"
                Text(
                    "Rolling average $direction ${"%.1f".format(abs(it))} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideOnSurfaceMuted,
                )
            }
            review.budgetAdjustedToKcal?.let {
                Text(
                    "Budget adjusted to $it — your body burns less as weight drops, this keeps the deficit real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrideOnSurfaceMuted,
                )
            }
            if (review.outcome == WeekOutcome.BROKEN.name) {
                Text(
                    "Floor missed last week. Fresh week, fresh start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}
