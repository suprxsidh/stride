package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentState

@Composable
fun WeeklyCommitmentCard(state: WeeklyCommitmentState, floorIntactStreakWeeks: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${state.runsThisWeek}/${state.target} runs this week · ${state.daysLeftInclusive} days left",
                style = MaterialTheme.typography.titleMedium
            )
            // SPEC §3.7's headline streak stat needs a permanent, always-visible home rather
            // than only surfacing occasionally through the rotating motivation line.
            Text(
                if (floorIntactStreakWeeks > 0) {
                    "$floorIntactStreakWeeks week${if (floorIntactStreakWeeks == 1) "" else "s"} with floor intact"
                } else {
                    "No streak yet"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            when (state.floorState) {
                FloorState.AT_RISK -> Text(
                    "Run today or tomorrow to protect your floor.",
                    color = MaterialTheme.colorScheme.error
                )
                FloorState.IMPOSSIBLE -> Text(
                    "This week's floor is out of reach. Refocus on next week.",
                    color = MaterialTheme.colorScheme.error
                )
                FloorState.OK -> {}
            }
        }
    }
}
