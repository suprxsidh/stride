package com.suprxsidh.deficit.ui.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val offRepository: OpenFoodFactsRepository
) : ViewModel() {

    val todayEntries: StateFlow<List<FoodEntryEntity>> =
        foodRepository.observeTodayEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pinnedFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observePinnedCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCustomFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observeAllCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var quickAddName by mutableStateOf("")
    var quickAddKcal by mutableStateOf("")

    var offQuery by mutableStateOf("")
    var offResults by mutableStateOf<List<OffCacheEntity>>(emptyList())
    var offSearchInFlight by mutableStateOf(false)
    var offSearchedOnce by mutableStateOf(false)

    var customFoodName by mutableStateOf("")
    var customFoodKcal by mutableStateOf("")
    var customFoodServingLabel by mutableStateOf("")

    fun logQuickAdd() {
        val kcal = quickAddKcal.toIntOrNull() ?: return
        if (quickAddName.isBlank()) return
        val name = quickAddName
        viewModelScope.launch {
            foodRepository.logQuickAdd(name, kcal)
            quickAddName = ""
            quickAddKcal = ""
        }
    }

    fun logPinned(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings = 1.0) }
    }

    fun searchOff() {
        val query = offQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            offSearchInFlight = true
            try {
                offResults = offRepository.search(query)
                offSearchedOnce = true
            } finally {
                offSearchInFlight = false
            }
        }
    }

    fun logOffResult(item: OffCacheEntity) {
        viewModelScope.launch { foodRepository.logOffProduct(item.productName, item.kcalPerServing, item.code) }
    }

    fun saveCustomFood(isPinned: Boolean) {
        val kcal = customFoodKcal.toIntOrNull() ?: return
        if (customFoodName.isBlank()) return
        val name = customFoodName
        val label = customFoodServingLabel.ifBlank { "1 serving" }
        viewModelScope.launch {
            foodRepository.upsertCustomFood(
                CustomFoodEntity(name = name, kcalPerServing = kcal, servingLabel = label, isPinned = isPinned)
            )
            customFoodName = ""
            customFoodKcal = ""
            customFoodServingLabel = ""
        }
    }

    fun logCustomFoodWithServings(food: CustomFoodEntity, servings: Double) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings) }
    }

    fun deleteCustomFood(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.deleteCustomFood(food) }
    }

    fun deleteFoodEntry(entry: FoodEntryEntity) {
        viewModelScope.launch { foodRepository.deleteFoodEntry(entry) }
    }
}
