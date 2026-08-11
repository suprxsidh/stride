package com.suprxsidh.deficit.ui.food

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import java.io.File

@Composable
fun FoodLogScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer {
            FoodLogViewModel(
                app.container.foodRepository,
                app.container.openFoodFactsRepository,
                app.container.geminiFoodRepository
            )
        }
    })

    val total by viewModel.todayBufferedTotal.collectAsState()
    val entries by viewModel.todayEntries.collectAsState()
    val pinned by viewModel.pinnedFoods.collectAsState()
    val customFoods by viewModel.allCustomFoods.collectAsState()
    val aiAvailable by viewModel.aiEstimateAvailable.collectAsState()
    val pendingDrafts by viewModel.pendingDrafts.collectAsState()
    val context = LocalContext.current
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var draftAwaitingKcal by remember { mutableStateOf<Long?>(null) }
    var draftKcalInput by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) pendingPhotoFile = null
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Today: $total kcal counted", style = MaterialTheme.typography.titleLarge) }

        if (pendingDrafts.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${pendingDrafts.size} meal(s) waiting to sync", style = MaterialTheme.typography.titleSmall)
                        Text("Nothing you typed is lost — this'll retry automatically, or:")
                        Row {
                            Button(onClick = { viewModel.retryDrafts() }) { Text("Retry now") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                draftAwaitingKcal = pendingDrafts.first().id
                                draftKcalInput = ""
                            }) { Text("Just quick-add it") }
                        }
                    }
                }
            }
        }

        if (pinned.isNotEmpty()) {
            item { Text("Snacks", style = MaterialTheme.typography.bodyLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pinned) { food ->
                        AssistChip(onClick = { viewModel.logPinned(food) }, label = { Text(food.name) })
                    }
                }
            }
        }

        if (aiAvailable) {
            item { Divider() }
            item { Text("Describe your meal", style = MaterialTheme.typography.bodyLarge) }
            item {
                OutlinedTextField(
                    value = viewModel.aiDescription,
                    onValueChange = { viewModel.aiDescription = it },
                    label = { Text("e.g. 2 rotis, dal tadka, cucumber salad") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row {
                    Button(
                        onClick = { viewModel.submitAiEstimate(pendingPhotoFile) },
                        enabled = !viewModel.aiSubmitInFlight
                    ) { Text(if (viewModel.aiSubmitInFlight) "Estimating..." else "Estimate") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        val file = File(File(context.cacheDir, "meal_photos").apply { mkdirs() }, "meal_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingPhotoFile = file
                        cameraLauncher.launch(uri)
                    }) { Text("Add photo") }
                }
            }
            viewModel.aiError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        }

        item { Divider() }
        item { Text("Quick add", style = MaterialTheme.typography.bodyLarge) }
        item {
            OutlinedTextField(value = viewModel.quickAddName, onValueChange = { viewModel.quickAddName = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = viewModel.quickAddKcal,
                onValueChange = { viewModel.quickAddKcal = it },
                label = { Text("Calories") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Button(onClick = { viewModel.logQuickAdd() }) { Text("Add") } }

        item { Divider() }
        item { Text("Search packaged foods (Open Food Facts)", style = MaterialTheme.typography.bodyLarge) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = viewModel.offQuery, onValueChange = { viewModel.offQuery = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = { viewModel.searchOff() }) { Text("Search") } }
        if (viewModel.offSearchInFlight) {
            item { CircularProgressIndicator() }
        } else if (viewModel.offSearchedOnce && viewModel.offResults.isEmpty()) {
            item { Text("No results — check spelling or your connection.") }
        }
        items(viewModel.offResults) { result ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${result.productName} (${result.kcalPerServing} kcal / ${result.servingLabel})")
                Button(onClick = { viewModel.logOffResult(result) }) { Text("Log") }
            }
        }

        item { Divider() }
        item { Text("Custom foods", style = MaterialTheme.typography.bodyLarge) }
        item {
            OutlinedTextField(value = viewModel.customFoodName, onValueChange = { viewModel.customFoodName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = viewModel.customFoodKcal,
                onValueChange = { viewModel.customFoodKcal = it },
                label = { Text("Calories per serving") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(value = viewModel.customFoodServingLabel, onValueChange = { viewModel.customFoodServingLabel = it }, label = { Text("Serving label (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveCustomFood(isPinned = false) }) { Text("Save") }
                Button(onClick = { viewModel.saveCustomFood(isPinned = true) }) { Text("Save + pin") }
            }
        }
        items(customFoods) { food ->
            var servingsText by remember(food.name) { mutableStateOf("1") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${food.name} (${food.kcalPerServing} kcal / ${food.servingLabel})")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = servingsText,
                        onValueChange = { servingsText = it },
                        label = { Text("Servings") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                    Button(onClick = {
                        viewModel.logCustomFoodWithServings(food, servingsText.toDoubleOrNull() ?: 1.0)
                    }) { Text("Log") }
                    Button(onClick = { viewModel.deleteCustomFood(food) }) { Text("Delete") }
                }
            }
        }

        item { Divider() }
        item { Text(if (entries.isEmpty()) "Nothing logged yet today." else "Logged today", style = MaterialTheme.typography.bodyLarge) }
        items(entries) { entry ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${entry.name}: logged ${entry.rawKcal} → counted ${entry.bufferedKcal}")
                TextButton(onClick = { viewModel.deleteFoodEntry(entry) }) { Text("Delete") }
            }
        }
    }

    // "Just quick-add it" needs the user's own calorie estimate — the app has none to offer
    // once Gemini has failed, so this dialog is the actual escape hatch, not a placeholder.
    draftAwaitingKcal?.let { draftId ->
        AlertDialog(
            onDismissRequest = { draftAwaitingKcal = null },
            title = { Text("Quick-add this meal") },
            text = {
                OutlinedTextField(
                    value = draftKcalInput,
                    onValueChange = { draftKcalInput = it.filter(Char::isDigit) },
                    label = { Text("Calories") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftKcalInput.toIntOrNull()?.let { kcal -> viewModel.discardDraft(draftId, kcal) }
                        draftAwaitingKcal = null
                    },
                    enabled = draftKcalInput.toIntOrNull() != null
                ) { Text("Log it") }
            },
            dismissButton = { TextButton(onClick = { draftAwaitingKcal = null }) { Text("Cancel") } }
        )
    }

    // Itemized review sheet — SPEC.md §3.3: "show itemized estimate for one-tap confirm/edit before saving."
    viewModel.reviewEstimate?.let { estimate ->
        var editedName by remember(estimate) { mutableStateOf(estimate.items.joinToString(", ") { it.name }) }
        var editedKcalInput by remember(estimate) { mutableStateOf(estimate.totalKcal.toString()) }

        AlertDialog(
            onDismissRequest = { viewModel.cancelAiReview(); pendingPhotoFile = null },
            title = { Text("Confirm meal (confidence: ${estimate.confidence})") },
            text = {
                Column {
                    estimate.items.forEach { item -> Text("${item.name} — ${item.kcal} kcal") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(
                        value = editedKcalInput,
                        onValueChange = { editedKcalInput = it.filter(Char::isDigit) },
                        label = { Text("Total kcal (+10% buffer applied on save)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editedKcalInput.toIntOrNull()?.let { kcal -> viewModel.confirmAiEstimate(editedName, kcal) }
                        pendingPhotoFile = null
                    },
                    enabled = editedKcalInput.toIntOrNull() != null
                ) { Text("Confirm & log") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelAiReview(); pendingPhotoFile = null }) { Text("Cancel") } }
        )
    }
}
