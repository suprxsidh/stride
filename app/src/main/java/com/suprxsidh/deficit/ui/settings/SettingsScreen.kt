package com.suprxsidh.deficit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.ui.theme.Spacing
import com.suprxsidh.deficit.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.deficit.ui.theme.component.StartLineDivider

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.container.settingsRepository, app.container.adaptiveBudgetRepository) }
    })
    val currentKey by viewModel.geminiApiKey.collectAsState()
    var input by remember(currentKey) { mutableStateOf(currentKey ?: "") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
        Text("Settings".uppercase(), style = MaterialTheme.typography.titleLarge)
        StartLineDivider(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm))

        Text("Gemini API key".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text("Used only for AI meal estimation. Stored on this device only, sent only to Google's Gemini API.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        Row {
            Button(onClick = { viewModel.saveGeminiApiKey(input) }) { Text("Save") }
            Spacer(Modifier.width(Spacing.sm))
            OutlinedButton(onClick = { input = ""; viewModel.clearGeminiApiKey() }) { Text("Clear") }
        }

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Weekly run goal".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text("Target and hard floor for the Monday-Sunday week. Any run on any day counts equally.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        Spacer(Modifier.height(Spacing.sm))

        val target by viewModel.weeklyRunTarget.collectAsState()
        val floor by viewModel.weeklyRunFloor.collectAsState()
        var targetInput by remember(target) { mutableStateOf(target.toString()) }
        var floorInput by remember(floor) { mutableStateOf(floor.toString()) }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = targetInput, onValueChange = { targetInput = it },
                label = { Text("Target") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            OutlinedTextField(
                value = floorInput, onValueChange = { floorInput = it },
                label = { Text("Floor") }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = {
            targetInput.toIntOrNull()?.let { viewModel.saveWeeklyRunTarget(it) }
            floorInput.toIntOrNull()?.let { viewModel.saveWeeklyRunFloor(it) }
        }) { Text("Save weekly goal") }

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Calorie budget override".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text("Manually set the daily budget. Overrides the automatic weekly recompute until cleared.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        Spacer(Modifier.height(Spacing.sm))

        val override by viewModel.manualBudgetOverrideKcal.collectAsState()
        var overrideInput by remember(override) { mutableStateOf(override?.toString() ?: "") }

        OutlinedTextField(
            value = overrideInput, onValueChange = { overrideInput = it },
            label = { Text("Daily budget (kcal)") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        Row {
            Button(onClick = { overrideInput.toIntOrNull()?.let { viewModel.saveManualBudgetOverride(it) } }) {
                Text("Save override")
            }
            Spacer(Modifier.width(Spacing.sm))
            OutlinedButton(onClick = {
                viewModel.clearManualBudgetOverride()
                overrideInput = ""
            }) { Text("Clear override") }
        }
    }
}
