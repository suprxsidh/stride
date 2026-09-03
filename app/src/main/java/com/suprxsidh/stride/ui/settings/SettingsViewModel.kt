package com.suprxsidh.stride.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
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
