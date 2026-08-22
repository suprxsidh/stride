package com.suprxsidh.stride.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.suprxsidh.stride.data.calc.FloorState
import com.suprxsidh.stride.data.repository.WeeklyCommitmentState
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.LedReadout
import com.suprxsidh.stride.ui.theme.component.PunchCardRow

@Composable
fun WeeklyCommitmentCard(state: WeeklyCommitmentState, floorIntactStreakWeeks: Int) {
    PunchCardRow(
        modifier = Modifier.padding(top = Spacing.xs),
        trailing = {
            LedReadout(
                value = floorIntactStreakWeeks.toString(),
                label = if (floorIntactStreakWeeks == 1) "wk streak" else "wks streak",
                style = MaterialTheme.typography.displayMedium,
            )
        },
    ) {
        Column {
            Text(
                "${state.runsThisWeek}/${state.target} runs this week",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${state.daysLeftInclusive} days left",
                style = MaterialTheme.typography.bodyMedium,
                color = StrideOnSurfaceMuted,
            )
            // SPEC §3.7's headline streak stat needs a permanent, always-visible home rather
            // than only surfacing occasionally through the rotating motivation line — moved
            // into the LedReadout trailing slot above rather than a text line.
            when (state.floorState) {
                FloorState.AT_RISK -> Text(
                    "Run today or tomorrow to protect your floor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                FloorState.IMPOSSIBLE -> Text(
                    "This week's floor is out of reach. Refocus on next week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                FloorState.OK -> {}
            }
        }
    }
}
