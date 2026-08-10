package com.suprxsidh.deficit.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp

@Composable
fun FoodLogScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer { FoodLogViewModel(app.container.foodRepository, app.container.openFoodFactsRepository) }
    })

    val total by viewModel.todayBufferedTotal.collectAsState()
    val entries by viewModel.todayEntries.collectAsState()
    val pinned by viewModel.pinnedFoods.collectAsState()
    val customFoods by viewModel.allCustomFoods.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Today: $total kcal counted", style = MaterialTheme.typography.titleLarge) }

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

        item { Divider() }
        item { Text("Quick add", style = MaterialTheme.typography.bodyLarge) }
        item {
            OutlinedTextField(value = viewModel.quickAddName, onValueChange = { viewModel.quickAddName = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = viewModel.quickAddKcal, onValueChange = { viewModel.quickAddKcal = it }, label = { Text("Calories") }, modifier = Modifier.fillMaxWidth())
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
            OutlinedTextField(value = viewModel.customFoodKcal, onValueChange = { viewModel.customFoodKcal = it }, label = { Text("Calories per serving") }, modifier = Modifier.fillMaxWidth())
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${food.name} (${food.kcalPerServing} kcal / ${food.servingLabel})")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.logCustomFoodWithServings(food, 1.0) }) { Text("Log") }
                    Button(onClick = { viewModel.deleteCustomFood(food) }) { Text("Delete") }
                }
            }
        }

        item { Divider() }
        item { Text(if (entries.isEmpty()) "Nothing logged yet today." else "Logged today", style = MaterialTheme.typography.bodyLarge) }
        items(entries) { entry ->
            Text("${entry.name}: logged ${entry.rawKcal} → counted ${entry.bufferedKcal}")
        }
    }
}
