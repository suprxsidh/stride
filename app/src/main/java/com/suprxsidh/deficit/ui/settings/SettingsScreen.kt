package com.suprxsidh.deficit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
        initializer { SettingsViewModel(app.container.settingsRepository) }
    })
    val currentKey by viewModel.geminiApiKey.collectAsState()
    var input by remember(currentKey) { mutableStateOf(currentKey ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
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
    }
}
