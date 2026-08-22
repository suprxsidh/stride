package com.suprxsidh.stride.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class RunDetailViewModel(
    private val healthConnectRepository: HealthConnectRepository
) : ViewModel() {

    val sessions: StateFlow<List<ExerciseSessionEntity>> = healthConnectRepository.observeExerciseSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Note: dates are parsed from the entity's `date` field (a "yyyy-MM-dd" string set at
    // sync time from the exercise session's local calendar day), not derived from
    // startTimeEpochMs — the latter is a UTC instant and converting it to a zoned LocalDate
    // does not reliably reproduce the session's intended calendar date.
    val paceTrend: StateFlow<List<Pair<LocalDate, Double>>> = healthConnectRepository.observeExerciseSessions()
        .map { sessions ->
            sessions
                .filter { it.distanceM != null && it.avgPaceSecPerKm != null }
                .map { LocalDate.parse(it.date) to it.avgPaceSecPerKm!! }
                .sortedBy { it.first }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
