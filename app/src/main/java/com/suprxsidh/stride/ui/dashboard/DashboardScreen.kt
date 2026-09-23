package com.suprxsidh.stride.ui.dashboard

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
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.health.HealthConnectSyncWorker
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.LedReadout
import com.suprxsidh.stride.ui.theme.component.PunchCardRow
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import com.suprxsidh.stride.ui.theme.component.StartLineProgress
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(onQuickAdd: () -> Unit) {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                { com.suprxsidh.stride.system.BatteryOptimization.isIgnoringBatteryOptimizations(app) },
                scheduleHealthConnectSync = {
                    HealthConnectSyncWorker.schedulePeriodic(app)
                    HealthConnectSyncWorker.triggerOneOff(app)
                },
                adaptiveBudgetRepository = app.container.adaptiveBudgetRepository
            )
        }
    })

    val profile by viewModel.profile.collectAsState()
    val total by viewModel.todayBufferedTotal.collectAsState()
    val proteinTotal by viewModel.todayProteinTotal.collectAsState()
    val rollingDeficit by viewModel.rollingDeficitKcal.collectAsState()
    val series by viewModel.rollingAverageSeries.collectAsState()
    val caloriesBurnedToday by viewModel.caloriesBurnedToday.collectAsState()
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        Text("Today".uppercase(), style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            val budget = profile?.softBudgetKcal ?: 0

            LedReadout(
                value = total.toString(),
                label = if (budget > 0) "kcal logged · soft target $budget" else "kcal logged today",
                modifier = Modifier.fillMaxWidth(),
            )

            if (budget > 0) {
                StartLineProgress(
                    progress = (total.toFloat() / budget.toFloat()).coerceIn(0f, 1f),
                    overBudget = total > budget,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            // Feature C (spec §4): protein floor bar, right next to the calorie budget bar. A
            // floor is the opposite direction of a budget (you want to be AT or ABOVE it, not
            // under it) so this deliberately never passes overBudget=true -- a full bar here
            // always reads as "good", never as an error state.
            val proteinFloor = profile?.proteinFloorG ?: 0.0
            if (proteinFloor > 0.0) {
                PunchCardRow(modifier = Modifier.padding(top = Spacing.sm)) {
                    Column {
                        Text(
                            "Protein: ${proteinTotal.roundToInt()}g / ${proteinFloor.roundToInt()}g floor",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        StartLineProgress(
                            progress = (proteinTotal / proteinFloor).toFloat().coerceIn(0f, 1f),
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            // Feature B (spec §3): rolling 7-day net-deficit card, under the today budget bar.
            rollingDeficit?.let { deficit ->
                LedReadout(
                    value = if (deficit >= 0) "+$deficit" else deficit.toString(),
                    label = "7-day net (banked deficit)",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }

            caloriesBurnedToday?.let { burned ->
                Text(
                    "Burned today: $burned kcal (Health Connect)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideOnSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Weight (7-day average)".uppercase(), style = MaterialTheme.typography.titleMedium)
        WeightSparkline(points = series.takeLast(30))

        when (hcStatus) {
            HealthConnectStatus.PERMISSIONS_NEEDED -> PunchCardRow(modifier = Modifier.padding(top = Spacing.md)) {
                Column {
                    Text("Health Connect permissions needed to sync calories burned and weight.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    }) { Text("Open Health Connect settings") }
                }
            }
            HealthConnectStatus.UNAVAILABLE -> Text(
                "Health Connect isn't available on this device — install it from the Play Store to sync calories burned and weight.",
                style = MaterialTheme.typography.bodySmall,
                color = StrideOnSurfaceMuted,
            )
            HealthConnectStatus.OK -> {}
        }

        if (!batteryIgnored) {
            PunchCardRow(modifier = Modifier.padding(top = Spacing.xs)) {
                Column {
                    Text("Battery optimization may silently stop background sync.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        context.startActivity(com.suprxsidh.stride.system.BatteryOptimization.batterySettingsIntent())
                    }) { Text("Open battery settings") }
                }
            }
        }

        Button(onClick = onQuickAdd, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
            Text("Log food".uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun WeightSparkline(points: List<Pair<LocalDate, Double>>) {
    if (points.size < 2) {
        Text("Log a couple of weigh-ins to see your trend here.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = Spacing.xs)) {
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
