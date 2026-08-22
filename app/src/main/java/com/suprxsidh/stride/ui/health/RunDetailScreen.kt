package com.suprxsidh.stride.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.PunchCardRow
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import java.time.LocalDate

// Not individually named in the redesign spec's migration list (spec omits it entirely), but
// it's a real 7th screen in this app and the spec's own §10 point 4 explicitly lists "a run's
// pace" as a standalone-numeric-readout example -- so it inherits the same design system as
// Food/Weight/Settings rather than staying the one screen left in default Material style.
@Composable
fun RunDetailScreen() {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: RunDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { RunDetailViewModel(app.container.healthConnectRepository!!) }
    })
    val sessions by viewModel.sessions.collectAsState()
    val paceTrend by viewModel.paceTrend.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        item { Text("Run history".uppercase(), style = MaterialTheme.typography.titleLarge) }
        item { StartLineDivider(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm)) }
        item { Text("Pace trend".uppercase(), style = MaterialTheme.typography.titleMedium) }
        item { PaceTrendChart(paceTrend, modifier = Modifier.fillMaxWidth().height(120.dp)) }
        item { Spacer(Modifier.height(Spacing.lg)) }
        if (sessions.isEmpty()) {
            item { Text("No runs synced yet.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted) }
        } else {
            items(sessions) { session -> RunRow(session) }
        }
    }
}

@Composable
private fun RunRow(session: ExerciseSessionEntity) {
    PunchCardRow(modifier = Modifier.padding(vertical = Spacing.xxs)) {
        Column {
            Text(session.date.uppercase(), style = MaterialTheme.typography.titleMedium)
            Text(
                "${session.durationMin} min" + (session.distanceM?.let { " · %.1f km".format(it / 1000.0) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            val hrLine = listOfNotNull(
                session.avgHr?.let { "avg HR $it" },
                session.maxHr?.let { "max HR $it" }
            ).joinToString(" · ")
            if (hrLine.isNotEmpty()) {
                Text(hrLine, style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun PaceTrendChart(points: List<Pair<LocalDate, Double>>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("Log a few more runs to see a pace trend.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val minPace = points.minOf { it.second }
        val maxPace = points.maxOf { it.second }
        val range = (maxPace - minPace).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, (_, pace) ->
            // Lower pace (faster) draws higher on screen.
            val y = size.height * ((pace - minPace) / range).toFloat()
            val x = stepX * index
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = accent, style = Stroke(width = 4f))
    }
}
