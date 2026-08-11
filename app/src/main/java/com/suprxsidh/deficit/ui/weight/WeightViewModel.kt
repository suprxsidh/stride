package com.suprxsidh.deficit.ui.weight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.TrendDirection
import com.suprxsidh.deficit.data.repository.WeighInSyncSource
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeightViewModel(
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: WeighInSyncSource? = null
) : ViewModel() {

    val rawSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRawSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rollingSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalChange: StateFlow<Double?> =
        weightRepository.observeTotalChangeSinceStart().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val trend: StateFlow<TrendDirection?> =
        weightRepository.observeFourWeekTrend().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var weightInput by mutableStateOf("")

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    fun logWeighIn() {
        val weight = weightInput.toDoubleOrNull() ?: return
        viewModelScope.launch {
            weightRepository.logWeighIn(weight)
            weightInput = ""
        }
    }

    fun syncWithHealthConnect() {
        val source = healthConnectRepository ?: return
        viewModelScope.launch {
            val count = source.syncWeighIns()
            _lastSyncResult.value = "Synced $count entries with Health Connect"
        }
    }
}
