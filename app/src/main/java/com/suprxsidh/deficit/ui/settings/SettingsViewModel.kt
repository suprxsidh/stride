package com.suprxsidh.deficit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val geminiApiKey: StateFlow<String?> = settingsRepository.observeGeminiApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key.trim().ifBlank { null }) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }
    }
}
