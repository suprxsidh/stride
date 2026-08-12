package com.suprxsidh.deficit.ui.consistency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.repository.DayConsistency
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ConsistencyScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: ConsistencyViewModel = viewModel(factory = viewModelFactory {
        initializer { ConsistencyViewModel(app.container.consistencyRepository, app.container.settingsRepository) }
    })

    val month by viewModel.month.collectAsState()
    val days by viewModel.days.collectAsState()
    val weeks by viewModel.weeks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = viewModel::previousMonth) { Text("< Prev") }
            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = viewModel::nextMonth) { Text("Next >") }
        }
        Spacer(Modifier.height(16.dp))

        val byWeekStart = days.groupBy { WeekBoundary.weekStart(it.date) }.toSortedMap()
        byWeekStart.forEach { (weekStart, weekDays) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val byDate = weekDays.associateBy { it.date }
                for (offset in 0..6) {
                    val date = weekStart.plusDays(offset.toLong())
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        byDate[date]?.let { DayCell(it) } ?: (if (date.month == month.month) DayCell(DayConsistency(date, false, false, false, null)) else Unit)
                    }
                }
            }
            val summary = weeks.firstOrNull { it.weekStart == weekStart }
            summary?.let {
                val outcomeNote = when (it.outcome) {
                    WeekOutcome.BROKEN -> " · floor missed"
                    WeekOutcome.FLOOR_MET -> " · floor met"
                    WeekOutcome.TARGET_MET -> " · target met"
                    null -> ""
                }
                Text(
                    "${it.daysRan} ran · ${it.daysLogged} logged$outcomeNote",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
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
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    )
}
