package com.suprxsidh.deficit.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaysRun: StateFlow<ExerciseSessionEntity?> =
        (healthConnectRepository?.observeExerciseSessions() ?: flowOf(emptyList()))
            .map { sessions ->
                val today = DayBoundary.logicalDate(clock()).toString()
                sessions.filter { it.date == today }.maxByOrNull { it.startTimeEpochMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    init {
        viewModelScope.launch {
            _healthConnectStatus.value = try {
                when {
                    healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                    !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                    else -> HealthConnectStatus.OK
                }
            } catch (e: Exception) {
                // Querying granted permissions talks to the Health Connect provider and can fail.
                // Fall back to the "needs attention" state rather than crashing the dashboard.
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
        }
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
