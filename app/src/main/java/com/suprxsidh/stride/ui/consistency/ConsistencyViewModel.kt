package com.suprxsidh.stride.ui.consistency

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.repository.ConsistencyRepository
import com.suprxsidh.stride.data.repository.DayConsistency
import com.suprxsidh.stride.data.repository.SettingsRepository
import com.suprxsidh.stride.data.repository.WeekSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth

class ConsistencyViewModel(
    private val consistencyRepository: ConsistencyRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.from(DayBoundary.logicalDate(clock())))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _days = MutableStateFlow<List<DayConsistency>>(emptyList())
    val days: StateFlow<List<DayConsistency>> = _days.asStateFlow()

    private val _weeks = MutableStateFlow<List<WeekSummary>>(emptyList())
    val weeks: StateFlow<List<WeekSummary>> = _weeks.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val target = settingsRepository.observeWeeklyRunTarget().first()
                val floor = settingsRepository.observeWeeklyRunFloor().first()
                val today = DayBoundary.logicalDate(clock())
                _days.value = consistencyRepository.dailyConsistencyForMonth(_month.value)
                _weeks.value = consistencyRepository.weeklySummariesForMonth(_month.value, target, floor, today)
            } catch (e: Exception) {
                // A DB or clock hiccup here must not crash the consistency screen; it just stays
                // on whatever data (or emptiness) it last had.
                Log.w("ConsistencyViewModel", "Failed to load the consistency grid", e)
            }
        }
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
        load()
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
        load()
    }
}
