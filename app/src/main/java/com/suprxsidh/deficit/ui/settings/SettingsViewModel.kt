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

    fun saveWeeklyRunTarget(target: Int) {
        viewModelScope.launch { settingsRepository.setWeeklyRunTarget(target) }
    }

    fun saveWeeklyRunFloor(floor: Int) {
        viewModelScope.launch { settingsRepository.setWeeklyRunFloor(floor) }
    }

    fun saveManualBudgetOverride(kcal: Int) {
        viewModelScope.launch {
            adaptiveBudgetRepository.setManualOverride(kcal)
            _manualBudgetOverrideKcal.value = kcal
        }
    }

    fun clearManualBudgetOverride() {
        viewModelScope.launch {
            adaptiveBudgetRepository.clearManualOverride()
            _manualBudgetOverrideKcal.value = null
        }
    }
}
