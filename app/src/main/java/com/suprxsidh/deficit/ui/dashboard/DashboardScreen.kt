package com.suprxsidh.deficit.ui.dashboard

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.health.HealthConnectSyncWorker
import java.time.LocalDate

@Composable
fun DashboardScreen(onQuickAdd: () -> Unit, onViewRunHistory: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                { com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(app) },
                app.container.weeklyCommitmentRepository,
                app.container.weeklyReviewRepository,
                app.container.settingsRepository,
                scheduleHealthConnectSync = {
                    HealthConnectSyncWorker.schedulePeriodic(app)
                    HealthConnectSyncWorker.triggerOneOff(app)
                }
            )
        }
    })

    val profile by viewModel.profile.collectAsState()
    val total by viewModel.todayBufferedTotal.collectAsState()
    val series by viewModel.rollingAverageSeries.collectAsState()
    val todaysRun by viewModel.todaysRun.collectAsState()
    val hcStatus by viewModel.healthConnectStatus.collectAsState()
    val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsState()
    val context = LocalContext.current

    // Health Connect permissions and battery optimization are both granted via a settings
    // deep link outside the app, so recheck both banners whenever the user returns to the
    // dashboard rather than trusting the one-shot values computed when the ViewModel was created.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshDeviceStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)

        val budget = profile?.softBudgetKcal ?: 0
        Text(if (budget > 0) "$total / $budget kcal counted" else "$total kcal counted")
        if (budget > 0) {
            LinearProgressIndicator(
                progress = { (total.toFloat() / budget.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        val unseenReview by viewModel.unseenWeeklyReview.collectAsState()
        unseenReview?.let { WeeklyReviewCard(it, onDismiss = viewModel::dismissWeeklyReview) }

        val weeklyState by viewModel.weeklyCommitmentState.collectAsState()
        val floorIntactStreakWeeks by viewModel.floorIntactStreakWeeks.collectAsState()
        weeklyState?.let { WeeklyCommitmentCard(it, floorIntactStreakWeeks) }

        val motivationLine by viewModel.motivationLine.collectAsState()
        motivationLine?.let { MotivationCard(it) }

        Text("Weight (7-day average)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        WeightSparkline(points = series.takeLast(30))

        todaysRun?.let { run ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's run", style = MaterialTheme.typography.titleMedium)
                    Text("${run.durationMin} min" + (run.distanceM?.let { " · %.1f km".format(it / 1000.0) } ?: ""))
                    Text("${run.kcalReal} kcal · credited: ${run.kcalCredited} kcal (50%)")
                }
            }
        }

        // Unconditional entry point: must not depend on todaysRun being non-null, since a
        // user can have historical runs synced without having logged one today. Gated on the
        // repository actually existing so this never leads to the `!!` in RunDetailScreen
        // being reached with a null repository.
        if (app.container.healthConnectRepository != null) {
            TextButton(onClick = onViewRunHistory) {
                Text("View run history")
            }
        }

        when (hcStatus) {
            HealthConnectStatus.PERMISSIONS_NEEDED -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Health Connect permissions needed to sync runs and weight.")
                    TextButton(onClick = {
                        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    }) { Text("Open Health Connect settings") }
                }
            }
            HealthConnectStatus.UNAVAILABLE -> Text(
                "Health Connect isn't available on this device — install it from the Play Store to sync runs and weight.",
                style = MaterialTheme.typography.bodySmall
            )
            HealthConnectStatus.OK -> {}
        }

        if (!batteryIgnored) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Battery optimization may silently stop background sync.")
                    TextButton(onClick = {
                        context.startActivity(com.suprxsidh.deficit.system.BatteryOptimization.batterySettingsIntent())
                    }) { Text("Open battery settings") }
                }
            }
        }

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
