package com.suprxsidh.deficit.ui.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimate
import com.suprxsidh.deficit.data.db.dao.MAX_PINNED_SNACKS
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.GeminiEstimateResult
import com.suprxsidh.deficit.data.repository.GeminiFoodRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val offRepository: OpenFoodFactsRepository,
    private val geminiFoodRepository: GeminiFoodRepository
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

    /** Set when [logQuickAdd] rejects the current input; null once a submit attempt validates. */
    var quickAddError by mutableStateOf<String?>(null)
        private set

    var offQuery by mutableStateOf("")
    var offResults by mutableStateOf<List<OffCacheEntity>>(emptyList())
    var offSearchInFlight by mutableStateOf(false)
    var offSearchedOnce by mutableStateOf(false)

    var customFoodName by mutableStateOf("")
    var customFoodKcal by mutableStateOf("")
    var customFoodServingLabel by mutableStateOf("")

    /** Set when [saveCustomFood] rejects the current input; null once a submit attempt validates. */
    var customFoodError by mutableStateOf<String?>(null)
        private set

    /**
     * Set when [saveCustomFood] was asked to pin a food but the app already has
     * [MAX_PINNED_SNACKS] pinned — the food still saves (unpinned) rather than being dropped
     * entirely. Previously this case failed with zero feedback: the row silently never appeared
     * in the pinned "Snacks" list (capped at [MAX_PINNED_SNACKS] there) even though nothing told
     * the user why.
     */
    var pinCapMessage by mutableStateOf<String?>(null)
        private set

    var aiDescription by mutableStateOf("")
    var aiSubmitInFlight by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set
    var reviewEstimate by mutableStateOf<GeminiFoodEstimate?>(null)
        private set

    // Observes the stored key rather than sampling it once, so saving or clearing it in Settings
    // shows/hides the AI section deterministically instead of depending on collection timing.
    val aiEstimateAvailable: StateFlow<Boolean> = geminiFoodRepository.observeAvailability()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pendingDrafts: StateFlow<List<PendingDraftEntity>> = geminiFoodRepository.observePendingDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estimates only. On success, [reviewEstimate] is populated and the UI shows the itemized review sheet. */
    fun submitAiEstimate(photoFile: File?) {
        if (aiDescription.isBlank() && photoFile == null) return
        aiSubmitInFlight = true
        aiError = null
        viewModelScope.launch {
            when (val result = geminiFoodRepository.estimateMeal(aiDescription.ifBlank { null }, photoFile)) {
                is GeminiEstimateResult.Success -> { reviewEstimate = result.estimate; aiSubmitInFlight = false }
                is GeminiEstimateResult.Failed -> { aiError = "Couldn't reach Gemini — saved as a draft, will retry automatically. (${result.message})"; aiSubmitInFlight = false }
                GeminiEstimateResult.NoApiKey -> { aiError = "Set a Gemini API key in Settings first."; aiSubmitInFlight = false }
            }
        }
    }

    /** Called when the user taps "confirm" on the review sheet, with whatever they left in its editable fields. */
    fun confirmAiEstimate(editedName: String, editedTotalKcal: Int) {
        val estimate = reviewEstimate ?: return
        viewModelScope.launch {
            geminiFoodRepository.confirmEstimate(estimate, editedName, editedTotalKcal)
            reviewEstimate = null
            aiDescription = ""
        }
    }

    fun cancelAiReview() {
        reviewEstimate = null
    }

    fun retryDrafts() {
        viewModelScope.launch { geminiFoodRepository.retryPendingDrafts() }
    }

    fun discardDraft(id: Long, kcal: Int) {
        viewModelScope.launch { geminiFoodRepository.discardDraftAsQuickAdd(id, kcal) }
    }

    fun logQuickAdd() {
        val kcal = quickAddKcal.toIntOrNull()
        if (quickAddName.isBlank() || kcal == null || kcal <= 0) {
            quickAddError = when {
                quickAddName.isBlank() -> "Enter a food name."
                else -> "Enter a valid calorie amount."
            }
            return
        }
        quickAddError = null
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
        val kcal = customFoodKcal.toIntOrNull()
        if (customFoodName.isBlank() || kcal == null || kcal <= 0) {
            customFoodError = when {
                customFoodName.isBlank() -> "Enter a name."
                else -> "Enter a valid calorie amount."
            }
            return
        }
        customFoodError = null
        pinCapMessage = null
        val name = customFoodName
        val label = customFoodServingLabel.ifBlank { "1 serving" }
        viewModelScope.launch {
            // Match by name first so re-saving an existing food (e.g. correcting its calorie
            // count) updates that row instead of inserting a duplicate — the DB has no unique
            // constraint on name, so a bare insert here previously always created a new row.
            val existing = foodRepository.findCustomFoodByName(name)
            val alreadyPinned = existing?.isPinned == true
            val effectivePinned = when {
                !isPinned -> false
                alreadyPinned -> true
                foodRepository.countPinnedCustomFoods() >= MAX_PINNED_SNACKS -> {
                    pinCapMessage = "Saved \"$name\" without pinning — you already have $MAX_PINNED_SNACKS pinned snacks. Unpin one first."
                    false
                }
                else -> true
            }
            foodRepository.upsertCustomFood(
                CustomFoodEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    kcalPerServing = kcal,
                    servingLabel = label,
                    isPinned = effectivePinned,
                )
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
