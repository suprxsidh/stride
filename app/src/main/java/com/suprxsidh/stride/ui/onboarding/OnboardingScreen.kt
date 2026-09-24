package com.suprxsidh.stride.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.health.HealthConnectManager
import com.suprxsidh.stride.health.HealthConnectSyncWorker
import com.suprxsidh.stride.reminders.ReminderScheduler
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.StridePositive
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import kotlinx.coroutines.launch

/** Steps in the onboarding flow, shown in order. */
private enum class OnboardingStep {
    PROFILE_ENTRY,
    HEALTH_CONNECT_SETUP,
    BATTERY_OPTIMIZATION_SETUP,
    // Feature E (completeness pass, spec §6): placed last, after battery optimization, per the
    // design doc's explicit ordering.
    REMINDER_SETUP
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: OnboardingViewModel = viewModel(factory = viewModelFactory {
        initializer { OnboardingViewModel(app.container.userProfileRepository) }
    })
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(OnboardingStep.PROFILE_ENTRY) }

    when (step) {
        OnboardingStep.PROFILE_ENTRY -> ProfileEntryStep(
            viewModel = viewModel,
            onSubmitted = { step = OnboardingStep.HEALTH_CONNECT_SETUP },
            scope = scope
        )
        OnboardingStep.HEALTH_CONNECT_SETUP -> {
            val permissionsGranted by viewModel.healthConnectPermissionsGranted.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                HealthConnectManager.requestPermissionsContract()
            ) { granted ->
                viewModel.onHealthConnectPermissionsResult(granted)
                // The sync worker was previously only ever scheduled from MainActivity.onCreate,
                // gated on permissions already being granted at that exact moment -- so granting
                // permission here never actually enqueued the worker until the app was fully
                // killed and cold-started again (a real, previously-shipped bug). Schedule
                // directly from the grant callback so sync starts immediately on this path too.
                if (granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
                    HealthConnectSyncWorker.schedulePeriodic(app)
                    HealthConnectSyncWorker.triggerOneOff(app)
                }
            }
            HealthConnectSetupStep(
                permissionsGranted = permissionsGranted,
                onRequestPermissions = { permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS) },
                onContinue = { step = OnboardingStep.BATTERY_OPTIMIZATION_SETUP }
            )
        }
        OnboardingStep.BATTERY_OPTIMIZATION_SETUP -> {
            val context = LocalContext.current
            var ignored by remember {
                mutableStateOf(com.suprxsidh.stride.system.BatteryOptimization.isIgnoringBatteryOptimizations(context))
            }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        ignored = com.suprxsidh.stride.system.BatteryOptimization.isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            BatteryOptimizationSetupStep(
                ignored = ignored,
                onOpenSettings = { context.startActivity(com.suprxsidh.stride.system.BatteryOptimization.batterySettingsIntent()) },
                onContinue = { step = OnboardingStep.REMINDER_SETUP }
            )
        }
        OnboardingStep.REMINDER_SETUP -> {
            val app = LocalContext.current.applicationContext as StrideApp
            val reminderViewModel: ReminderSetupViewModel = viewModel(factory = viewModelFactory {
                initializer { ReminderSetupViewModel(app.container.reminderRepository) }
            })
            val context = LocalContext.current
            var notificationsGranted by remember {
                mutableStateOf(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> notificationsGranted = granted }

            ReminderSetupStep(
                viewModel = reminderViewModel,
                notificationsGranted = notificationsGranted,
                showNotificationPermissionRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                onRequestNotificationPermission = {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                onFinish = {
                    scope.launch {
                        if (reminderViewModel.commit(ReminderScheduler(app))) {
                            onComplete()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileEntryStep(
    viewModel: OnboardingViewModel,
    onSubmitted: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // Whole-branch review finding: 5 fields + chips + button with no scroll wrapper can overflow
    // a small screen once the keyboard is up -- every other screen in the app scrolls.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("A few numbers to start".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider()

        OutlinedTextField(
            value = viewModel.heightCm,
            onValueChange = { viewModel.heightCm = it },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.weightKg,
            onValueChange = { viewModel.weightKg = it },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Birth date", style = MaterialTheme.typography.bodySmall, color = StrideOnSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(
                value = viewModel.birthDay,
                onValueChange = { viewModel.birthDay = it },
                label = { Text("Day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = viewModel.birthMonth,
                onValueChange = { viewModel.birthMonth = it },
                label = { Text("Month") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = viewModel.birthYear,
                onValueChange = { viewModel.birthYear = it },
                label = { Text("Year") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(selected = viewModel.sex == Sex.MALE, onClick = { viewModel.sex = Sex.MALE }, label = { Text("Male") })
            FilterChip(selected = viewModel.sex == Sex.FEMALE, onClick = { viewModel.sex = Sex.FEMALE }, label = { Text("Female") })
        }
        OutlinedTextField(
            value = viewModel.goalWeightKg,
            onValueChange = { viewModel.goalWeightKg = it },
            label = { Text("Goal weight (kg) — optional, defaults to current − 10") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.proteinFloorG,
            onValueChange = { viewModel.proteinFloorG = it },
            label = { Text("Protein floor (g) — optional, defaults to 1.6× your weight in kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { scope.launch { viewModel.submit(onSubmitted) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Get started".uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HealthConnectSetupStep(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text("Connect Health Connect".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider(modifier = Modifier.padding(top = Spacing.sm))
        Spacer(Modifier.height(Spacing.md))
        Text(
            "1. Open Samsung Health → Settings → Data management → Health Connect sync, and turn it on.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "2. Grant this app the Calories and Weight permissions when prompted.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Sync can take 30–60 minutes to catch up. Opening Samsung Health first speeds it up.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideOnSurfaceMuted,
        )
        Spacer(Modifier.height(Spacing.lg))
        if (permissionsGranted) {
            Text("Permissions granted ✓", color = StridePositive, style = MaterialTheme.typography.bodyLarge)
        } else {
            Button(onClick = onRequestPermissions) { Text("Grant Health Connect permissions") }
        }
        Spacer(Modifier.height(Spacing.md))
        TextButton(onClick = onContinue) { Text(if (permissionsGranted) "Continue" else "Skip for now") }
    }
}

@Composable
private fun BatteryOptimizationSetupStep(
    ignored: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text("Protect background sync".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider(modifier = Modifier.padding(top = Spacing.sm))
        Spacer(Modifier.height(Spacing.md))
        Text(
            "This phone's battery settings can silently stop Stride from syncing runs and weight in the background.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("1. Set Stride's battery usage to unrestricted, or disable battery optimization for it.", style = MaterialTheme.typography.bodyMedium)
        Text("2. In Vivo's i Manager, add Stride to auto-start apps.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.lg))
        if (ignored) {
            Text("Battery optimization disabled ✓", color = StridePositive, style = MaterialTheme.typography.bodyLarge)
        } else {
            Button(onClick = onOpenSettings) { Text("Open battery settings") }
        }
        Spacer(Modifier.height(Spacing.md))
        TextButton(onClick = onContinue) { Text(if (ignored) "Continue" else "Skip for now") }
    }
}

/**
 * Feature E (completeness pass, spec §6): the last onboarding step. Requests `POST_NOTIFICATIONS`
 * (API 33+ only -- below that, notifications need no runtime grant) and lets the user accept the
 * SPEC defaults or edit/add/remove reminder times for all three types before finishing.
 */
@Composable
private fun ReminderSetupStep(
    viewModel: ReminderSetupViewModel,
    notificationsGranted: Boolean,
    showNotificationPermissionRequest: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Set up reminders".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider()
        Text(
            "Defaults are below -- edit any time, or add/remove meals and snacks. All of this is " +
                "editable later too.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showNotificationPermissionRequest) {
            if (notificationsGranted) {
                Text("Notifications allowed ✓", color = StridePositive, style = MaterialTheme.typography.bodyLarge)
            } else {
                Button(onClick = onRequestNotificationPermission) { Text("Allow notifications") }
            }
        }

        Text("Weigh-in".uppercase(), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = viewModel.weighInTime,
            onValueChange = { viewModel.weighInTime = it },
            label = { Text("Time (HH:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm))
        Text("Meals".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text(
            "A quiet nudge fires 45 minutes before each meal time.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideOnSurfaceMuted,
        )
        viewModel.mealRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = row.label,
                    onValueChange = { row.label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = row.time,
                    onValueChange = { row.time = it },
                    label = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
                TextButton(onClick = { viewModel.removeMeal(row) }) { Text("Remove") }
            }
        }
        TextButton(onClick = { viewModel.addMeal() }) { Text("Add meal") }

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm))
        Text("Snacks".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text(
            "Fires exactly at the configured time -- no lead time.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideOnSurfaceMuted,
        )
        viewModel.snackRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = row.label,
                    onValueChange = { row.label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = row.time,
                    onValueChange = { row.time = it },
                    label = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
                TextButton(onClick = { viewModel.removeSnack(row) }) { Text("Remove") }
            }
        }
        TextButton(onClick = { viewModel.addSnack() }) { Text("Add snack") }

        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(Spacing.md))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Finish setup".uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
