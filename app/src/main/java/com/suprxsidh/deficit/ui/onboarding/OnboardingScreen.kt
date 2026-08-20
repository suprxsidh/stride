package com.suprxsidh.deficit.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.health.HealthConnectManager
import com.suprxsidh.deficit.health.HealthConnectSyncWorker
import kotlinx.coroutines.launch

/** Steps in the onboarding flow, shown in order. */
private enum class OnboardingStep {
    PROFILE_ENTRY,
    HEALTH_CONNECT_SETUP,
    BATTERY_OPTIMIZATION_SETUP
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val app = LocalContext.current.applicationContext as DeficitApp
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
                mutableStateOf(com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(context))
            }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        ignored = com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            BatteryOptimizationSetupStep(
                ignored = ignored,
                onOpenSettings = { context.startActivity(com.suprxsidh.deficit.system.BatteryOptimization.batterySettingsIntent()) },
                onContinue = onComplete
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
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("A few numbers to start", style = MaterialTheme.typography.titleLarge)

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
        OutlinedTextField(
            value = viewModel.age,
            onValueChange = { viewModel.age = it },
            label = { Text("Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { scope.launch { viewModel.submit(onSubmitted) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
    }
}

@Composable
private fun HealthConnectSetupStep(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Connect Health Connect", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("1. Open Samsung Health → Settings → Data management → Health Connect sync, and turn it on.")
        Text("2. Grant this app the Exercise, Calories, Distance, Heart Rate, and Weight permissions when prompted.")
        Text("Sync can take 30–60 minutes after a run. Opening Samsung Health first speeds it up.")
        Spacer(Modifier.height(24.dp))
        if (permissionsGranted) {
            Text("Permissions granted ✓")
        } else {
            Button(onClick = onRequestPermissions) { Text("Grant Health Connect permissions") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onContinue) { Text(if (permissionsGranted) "Continue" else "Skip for now") }
    }
}

@Composable
private fun BatteryOptimizationSetupStep(
    ignored: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Protect background sync", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("This phone's battery settings can silently stop Deficit from syncing runs and weight in the background.")
        Text("1. Set Deficit's battery usage to unrestricted, or disable battery optimization for it.")
        Text("2. In Vivo's i Manager, add Deficit to auto-start apps.")
        Spacer(Modifier.height(24.dp))
        if (ignored) {
            Text("Battery optimization disabled ✓")
        } else {
            Button(onClick = onOpenSettings) { Text("Open battery settings") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onContinue) { Text(if (ignored) "Continue" else "Skip for now") }
    }
}
