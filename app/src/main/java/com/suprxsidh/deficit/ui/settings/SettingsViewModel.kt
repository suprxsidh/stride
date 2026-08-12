package com.suprxsidh.deficit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository
) : ViewModel() {

    val geminiApiKey: StateFlow<String?> =
        settingsRepository.observeGeminiApiKey().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyRunTarget: StateFlow<Int> =
        settingsRepository.observeWeeklyRunTarget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 4)

    val weeklyRunFloor: StateFlow<Int> =
        settingsRepository.observeWeeklyRunFloor().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    private val _manualBudgetOverrideKcal = MutableStateFlow<Int?>(null)
    val manualBudgetOverrideKcal: StateFlow<Int?> = _manualBudgetOverrideKcal.asStateFlow()

    init {
        viewModelScope.launch {
            _manualBudgetOverrideKcal.value = settingsRepository.getManualBudgetOverrideKcal()
        }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key.trim().ifBlank { null }) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }
    }

    private companion object {
        const val MIN_MANUAL_BUDGET_KCAL = 1200
    }

    // Weekly run target/floor are user-typed integers with no UI-level input restriction, and a
    // bad value here (0, negative, floor > target) would permanently wedge the floor/target math
    // in WeeklyCommitmentCalc with no recovery path short of reinstalling. Clamp to a sane 1..7
    // range (a week has 7 days) and preserve the floor <= target invariant by nudging whichever
    // value wasn't just edited, using the other's *current* StateFlow value.
    fun saveWeeklyRunTarget(target: Int) {
        val clampedTarget = target.coerceIn(1, 7)
        viewModelScope.launch {
            val currentFloor = weeklyRunFloor.value
            settingsRepository.setWeeklyRunTarget(clampedTarget)
            if (currentFloor > clampedTarget) {
                settingsRepository.setWeeklyRunFloor(clampedTarget)
            }
        }
    }

    fun saveWeeklyRunFloor(floor: Int) {
        val clampedFloor = floor.coerceIn(1, 7)
        viewModelScope.launch {
            val currentTarget = weeklyRunTarget.value
            settingsRepository.setWeeklyRunFloor(clampedFloor)
            if (clampedFloor > currentTarget) {
                settingsRepository.setWeeklyRunTarget(clampedFloor)
            }
        }
    }

    fun saveManualBudgetOverride(kcal: Int) {
        val clampedKcal = maxOf(kcal, MIN_MANUAL_BUDGET_KCAL)
        viewModelScope.launch {
            adaptiveBudgetRepository.setManualOverride(clampedKcal)
            _manualBudgetOverrideKcal.value = clampedKcal
        }
    }

    fun clearManualBudgetOverride() {
        viewModelScope.launch {
            adaptiveBudgetRepository.clearManualOverride()
            _manualBudgetOverrideKcal.value = null
        }
    }
}
