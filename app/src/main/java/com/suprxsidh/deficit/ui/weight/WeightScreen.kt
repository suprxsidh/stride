package com.suprxsidh.deficit.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.repository.TrendDirection
import com.suprxsidh.deficit.ui.theme.Spacing
import com.suprxsidh.deficit.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.deficit.ui.theme.component.LedReadout
import com.suprxsidh.deficit.ui.theme.component.StartLineDivider
import java.time.LocalDate

@Composable
fun WeightScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: WeightViewModel = viewModel(factory = viewModelFactory {
        initializer { WeightViewModel(app.container.weightRepository, app.container.healthConnectRepository) }
    })

    val raw by viewModel.rawSeries.collectAsState()
    val rolling by viewModel.rollingSeries.collectAsState()
    val totalChange by viewModel.totalChange.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val lastSyncResult by viewModel.lastSyncResult.collectAsState()

    // No scroll existed here before the redesign touched this screen -- with a fixed-height
    // chart plus two text blocks below it, content can overflow a small screen. Adding scroll
    // matches every other screen in the app (Dashboard, Food log, Settings) rather than leaving
    // this the one screen that clips.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        Text("Weight".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm))

        if (viewModel.healthConnectAvailable) {
            TextButton(onClick = { viewModel.syncWithHealthConnect() }) {
                Text("Sync with Health Connect")
            }
        } else {
            Text("Health Connect isn't available on this device", style = MaterialTheme.typography.bodySmall, color = StrideOnSurfaceMuted)
        }
        lastSyncResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = StrideOnSurfaceMuted)
        }

        Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
            OutlinedTextField(
                value = viewModel.weightInput,
                onValueChange = { viewModel.weightInput = it },
                label = { Text("Today's weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(onClick = { viewModel.logWeighIn() }) { Text("Log weigh-in") }

        if (raw.size < 2) {
            Text("Log a couple of weigh-ins to see your chart.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted, modifier = Modifier.padding(top = Spacing.lg))
        } else {
            WeightChart(raw = raw, rolling = rolling, modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = Spacing.lg))
        }

        totalChange?.let {
            val direction = if (it <= 0) "down" else "up"
            val sign = if (it <= 0) "-" else "+"
            LedReadout(
                value = "$sign${"%.1f".format(kotlin.math.abs(it))}",
                label = "kg total change ($direction since start)",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
        trend?.let {
            val label = when (it) {
                TrendDirection.DOWN -> "Trending down over the last 4 weeks"
                TrendDirection.UP -> "Trending up over the last 4 weeks"
                TrendDirection.FLAT -> "Holding steady over the last 4 weeks"
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun WeightChart(
    raw: List<Pair<LocalDate, Double>>,
    rolling: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = accent.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val allValues = raw.map { it.second }
        val minV = allValues.min()
        val maxV = allValues.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (raw.size - 1).coerceAtLeast(1)

        raw.forEachIndexed { index, (_, value) ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            drawCircle(color = faint, radius = 4f, center = androidx.compose.ui.geometry.Offset(x, y))
        }

        if (rolling.size >= 2) {
            val path = androidx.compose.ui.graphics.Path()
            val rollingStepX = size.width / (rolling.size - 1)
            rolling.forEachIndexed { index, (_, value) ->
                val x = index * rollingStepX
                val y = size.height - ((value - minV) / range * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = accent, style = Stroke(width = 5f))
        }
    }
}
