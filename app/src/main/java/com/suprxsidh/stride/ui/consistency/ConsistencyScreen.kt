package com.suprxsidh.stride.ui.consistency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.data.calc.WeekBoundary
import com.suprxsidh.stride.data.calc.WeekOutcome
import com.suprxsidh.stride.data.repository.DayConsistency
import com.suprxsidh.stride.data.repository.WeekSummary
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.StrideOutline
import com.suprxsidh.stride.ui.theme.StridePositive
import com.suprxsidh.stride.ui.theme.component.PunchCardRow
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConsistencyScreen() {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: ConsistencyViewModel = viewModel(factory = viewModelFactory {
        initializer { ConsistencyViewModel(app.container.consistencyRepository, app.container.settingsRepository) }
    })

    val month by viewModel.month.collectAsState()
    val days by viewModel.days.collectAsState()
    val weeks by viewModel.weeks.collectAsState()

    // Whole-branch review finding: this screen had no scroll wrapper even though a month can
    // render up to 6 week rows (day-grid + PunchCardRow summary each) -- a real overflow risk
    // on shorter phones that no single screen's own migration review would have caught. Every
    // other screen in the app already scrolls (Dashboard, Food log, Weight, Settings, Run
    // history); this was the one exception.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::previousMonth) { Text("< PREV") }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}".uppercase(),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = viewModel::nextMonth) { Text("NEXT >") }
        }
        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm))

        val byWeekStart = days.groupBy { WeekBoundary.weekStart(it.date) }.toSortedMap()
        byWeekStart.forEach { (weekStart, weekDays) ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
                val byDate = weekDays.associateBy { it.date }
                for (offset in 0..6) {
                    val date = weekStart.plusDays(offset.toLong())
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(Spacing.xxs),
                        contentAlignment = Alignment.Center,
                    ) {
                        byDate[date]?.let { DayCell(it) }
                    }
                }
            }
            // ConsistencyRepository.weeklySummariesForMonth deliberately omits weeks whose
            // weekStart falls outside the target month (a week mostly in the previous month,
            // e.g. the first row of February starting Jan 29) to avoid misclassifying a
            // partial week against the 7-day floor/target math. That means some grid rows
            // here have no matching entry in `weeks` — render that as an explicit, visually
            // muted "not enough data" line (via weekSummaryText below) rather than silently
            // showing nothing.
            val summary = weeks.firstOrNull { it.weekStart == weekStart }
            PunchCardRow(modifier = Modifier.padding(bottom = Spacing.sm)) {
                Text(
                    weekSummaryText(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary == null) StrideOnSurfaceMuted else Color.Unspecified,
                )
            }
        }
    }
}

/**
 * Formats a week's grid-row summary line per SPEC §3.6 ("5/7 days ran, 6/7 logged, avg
 * deficit ~X kcal"). Pure and Compose-free so it can be unit tested directly: no Robolectric
 * or Compose test harness required. Returns a "not enough data yet" placeholder when
 * [summary] is null — see the call site's comment for why a grid row can lack one.
 *
 * NOTE: exact string format is covered by ConsistencyScreenTest — do not change without
 * updating that test.
 */
internal fun weekSummaryText(summary: WeekSummary?): String {
    if (summary == null) return "Not enough data yet"
    val outcomeNote = when (summary.outcome) {
        WeekOutcome.BROKEN -> " · floor missed"
        WeekOutcome.FLOOR_MET -> " · floor met"
        WeekOutcome.TARGET_MET -> " · target met"
        null -> ""
    }
    val avgDeficitNote = summary.avgDeficitKcal?.let { " · avg deficit ~${it.roundToInt()} kcal" } ?: ""
    return "${summary.daysRan}/7 ran · ${summary.daysLogged}/7 logged$avgDeficitNote$outcomeNote"
}

@Composable
private fun DayCell(day: DayConsistency) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
        Row {
            Dot(active = day.ran)
            Dot(active = day.loggedFood)
            Dot(active = day.underBudget)
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .height(6.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (active) StridePositive else StrideOutline)
    )
}
