package com.suprxsidh.deficit.ui.health

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import java.time.LocalDate

@Composable
fun RunDetailScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: RunDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { RunDetailViewModel(app.container.healthConnectRepository!!) }
    })
    val sessions by viewModel.sessions.collectAsState()
    val paceTrend by viewModel.paceTrend.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Run history", style = MaterialTheme.typography.titleLarge) }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Pace trend", style = MaterialTheme.typography.titleMedium) }
        item { PaceTrendChart(paceTrend, modifier = Modifier.fillMaxWidth().height(120.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
        if (sessions.isEmpty()) {
            item { Text("No runs synced yet.") }
        } else {
            items(sessions) { session -> RunRow(session) }
        }
    }
}

@Composable
private fun RunRow(session: ExerciseSessionEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(session.date, style = MaterialTheme.typography.titleSmall)
        Text("${session.durationMin} min" + (session.distanceM?.let { " · %.1f km".format(it / 1000.0) } ?: ""))
        Text(listOfNotNull(
            session.avgHr?.let { "avg HR $it" },
            session.maxHr?.let { "max HR $it" }
        ).joinToString(" · "))
    }
}

@Composable
private fun PaceTrendChart(points: List<Pair<LocalDate, Double>>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("Log a few more runs to see a pace trend.")
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
