package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WeeklyReviewCard(review: WeeklyReviewEntity, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last week", style = MaterialTheme.typography.titleMedium)
            Text("${review.runsCompleted}/${review.runFloor} floor · ${review.runsCompleted}/${review.runTarget} target")
            Text("${review.daysLogged} days logged")
            review.avgDailyDeficitKcal?.let { Text("Average deficit: ${it.roundToInt()} kcal/day") }
            review.rollingWeightChangeKg?.let {
                val direction = if (it < 0) "down" else "up"
                Text("Rolling average $direction ${"%.1f".format(abs(it))} kg")
            }
            review.budgetAdjustedToKcal?.let {
                Text("Budget adjusted to $it — your body burns less as weight drops, this keeps the deficit real.")
            }
            if (review.outcome == WeekOutcome.BROKEN.name) {
                Text("Floor missed last week. Fresh week, fresh start.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}
