package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import java.time.LocalDate

@Composable
fun DashboardScreen(onQuickAdd: () -> Unit) {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(app.container.foodRepository, app.container.userProfileRepository, app.container.weightRepository)
        }
    })

    val profile by viewModel.profile.collectAsState()
    val total by viewModel.todayBufferedTotal.collectAsState()
    val series by viewModel.rollingAverageSeries.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)

        val budget = profile?.softBudgetKcal ?: 0
        Text(if (budget > 0) "$total / $budget kcal counted" else "$total kcal counted")
        if (budget > 0) {
            LinearProgressIndicator(
                progress = { (total.toFloat() / budget.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        Text("Weight (7-day average)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        WeightSparkline(points = series.takeLast(30))

        Button(onClick = onQuickAdd, modifier = Modifier.padding(top = 16.dp)) {
            Text("Log food")
        }
    }
}

@Composable
fun WeightSparkline(points: List<Pair<LocalDate, Double>>) {
    if (points.size < 2) {
        Text("Log a couple of weigh-ins to see your trend here.")
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val values = points.map { it.second }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, (_, value) ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}
