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

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.container.settingsRepository, app.container.adaptiveBudgetRepository) }
    })
    val currentKey by viewModel.geminiApiKey.collectAsState()
    var input by remember(currentKey) { mutableStateOf(currentKey ?: "") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        Text("Used only for AI meal estimation. Stored on this device only, sent only to Google's Gemini API.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { viewModel.saveGeminiApiKey(input) }) { Text("Save") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { input = ""; viewModel.clearGeminiApiKey() }) { Text("Clear") }
        }

        Spacer(Modifier.height(32.dp))
        Text("Weekly run goal", style = MaterialTheme.typography.titleMedium)
        Text("Target and hard floor for the Monday-Sunday week. Any run on any day counts equally.")
        Spacer(Modifier.height(12.dp))

        val target by viewModel.weeklyRunTarget.collectAsState()
        val floor by viewModel.weeklyRunFloor.collectAsState()
        var targetInput by remember(target) { mutableStateOf(target.toString()) }
        var floorInput by remember(floor) { mutableStateOf(floor.toString()) }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = targetInput, onValueChange = { targetInput = it },
                label = { Text("Target") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = floorInput, onValueChange = { floorInput = it },
                label = { Text("Floor") }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            targetInput.toIntOrNull()?.let { viewModel.saveWeeklyRunTarget(it) }
            floorInput.toIntOrNull()?.let { viewModel.saveWeeklyRunFloor(it) }
        }) { Text("Save weekly goal") }

        Spacer(Modifier.height(32.dp))
        Text("Calorie budget override", style = MaterialTheme.typography.titleMedium)
        Text("Manually set the daily budget. Overrides the automatic weekly recompute until cleared.")
        Spacer(Modifier.height(12.dp))

        val override by viewModel.manualBudgetOverrideKcal.collectAsState()
        var overrideInput by remember(override) { mutableStateOf(override?.toString() ?: "") }

        OutlinedTextField(
            value = overrideInput, onValueChange = { overrideInput = it },
            label = { Text("Daily budget (kcal)") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { overrideInput.toIntOrNull()?.let { viewModel.saveManualBudgetOverride(it) } }) {
                Text("Save override")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                viewModel.clearManualBudgetOverride()
                overrideInput = ""
            }) { Text("Clear override") }
        }
    }
}
