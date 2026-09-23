package com.suprxsidh.stride.ui.food

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.LedReadout
import com.suprxsidh.stride.ui.theme.component.PunchCardRow
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FOOD_LOG_DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * @param initialDate History (Feature A): the day this screen opens on. Null (the default) means
 * "today" per [com.suprxsidh.stride.data.repository.FoodRepository.currentLogicalDate] — Dashboard's
 * quick-add still always opens today by passing nothing here.
 */
@Composable
fun FoodLogScreen(initialDate: LocalDate? = null) {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer {
            FoodLogViewModel(
                app.container.foodRepository,
                app.container.geminiFoodRepository,
                initialDate
            )
        }
    })

    val selectedDate by viewModel.selectedDate.collectAsState()
    val total by viewModel.bufferedTotalForSelectedDate.collectAsState()
    val entries by viewModel.entriesForSelectedDate.collectAsState()
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

    val isToday = selectedDate == app.container.foodRepository.currentLogicalDate()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { viewModel.goToPreviousDay() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
                }
                Text(
                    text = if (isToday) "Today" else selectedDate.format(FOOD_LOG_DATE_LABEL_FORMAT),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { viewModel.goToNextDay() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
                }
            }
        }
        item {
            LedReadout(
                value = total.toString(),
                label = if (isToday) "kcal counted today" else "kcal counted on ${selectedDate.format(FOOD_LOG_DATE_LABEL_FORMAT)}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayMedium,
            )
        }

        // Logging new food always writes to today (FoodRepository's log* functions are still
        // hardcoded to "today" per spec §2 -- only browsing/edit/delete gained a date param), so
        // these input sections only make sense while today is the day being viewed. Browsing a
        // past day only shows its entries (below) with edit/delete.
        if (isToday) {
            if (pendingDrafts.isNotEmpty()) {
                item {
                    PunchCardRow(modifier = Modifier.padding(top = Spacing.sm)) {
                        Column {
                            Text("${pendingDrafts.size} meal(s) waiting to sync".uppercase(), style = MaterialTheme.typography.titleMedium)
                            Text("Nothing you typed is lost — this'll retry automatically, or:", style = MaterialTheme.typography.bodyMedium)
                            Row(modifier = Modifier.padding(top = Spacing.xs)) {
                                Button(onClick = { viewModel.retryDrafts() }) { Text("Retry now") }
                                Spacer(Modifier.width(Spacing.xs))
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
                item { Text("Snacks".uppercase(), style = MaterialTheme.typography.titleMedium) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(pinned) { food ->
                            AssistChip(onClick = { viewModel.logPinned(food) }, label = { Text(food.name) })
                        }
                    }
                }
            }

            if (aiAvailable) {
                item { StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
                item { Text("Describe your meal".uppercase(), style = MaterialTheme.typography.titleMedium) }
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
                        Spacer(Modifier.width(Spacing.xs))
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

            item { StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
            item { Text("Quick add".uppercase(), style = MaterialTheme.typography.titleMedium) }
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
            viewModel.quickAddError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            item { StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
            item { Text("Custom foods".uppercase(), style = MaterialTheme.typography.titleMedium) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Button(onClick = { viewModel.saveCustomFood(isPinned = false) }) { Text("Save") }
                    Button(onClick = { viewModel.saveCustomFood(isPinned = true) }) { Text("Save + pin") }
                }
            }
            viewModel.customFoodError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            viewModel.pinCapMessage?.let { message ->
                item { Text(message, color = StrideOnSurfaceMuted, style = MaterialTheme.typography.bodySmall) }
            }
            items(customFoods) { food ->
                var servingsText by remember(food.name) { mutableStateOf("1") }
                PunchCardRow(
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                    },
                ) {
                    Text("${food.name} (${food.kcalPerServing} kcal / ${food.servingLabel})", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
        item {
            val header = when {
                entries.isEmpty() && isToday -> "Nothing logged yet today."
                entries.isEmpty() -> "Nothing logged on ${selectedDate.format(FOOD_LOG_DATE_LABEL_FORMAT)}."
                isToday -> "Logged today"
                else -> "Logged on ${selectedDate.format(FOOD_LOG_DATE_LABEL_FORMAT)}"
            }
            Text(header.uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        items(entries) { entry ->
            PunchCardRow(
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TextButton(onClick = { viewModel.startEditingEntry(entry) }) { Text("Edit") }
                        TextButton(onClick = { viewModel.deleteFoodEntry(entry) }) { Text("Delete") }
                    }
                },
            ) {
                Text("${entry.name}: logged ${entry.rawKcal} → counted ${entry.bufferedKcal}", style = MaterialTheme.typography.bodyMedium)
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

    // History (Feature A): editing an entry reuses this quick-add-style dialog, pre-filled from
    // the entry tapped in the list above (any day, not just today).
    viewModel.editingEntry?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelEditingEntry() },
            title = { Text("Edit entry") },
            text = {
                Column {
                    OutlinedTextField(
                        value = viewModel.editName,
                        onValueChange = { viewModel.editName = it },
                        label = { Text("Food name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viewModel.editKcal,
                        onValueChange = { viewModel.editKcal = it.filter(Char::isDigit) },
                        label = { Text("Calories (+10% buffer applied on save)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    viewModel.editError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveEditedEntry() }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelEditingEntry() }) { Text("Cancel") } }
        )
    }
}
