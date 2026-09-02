package com.suprxsidh.stride.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val isIgnoringBatteryOptimizations: () -> Boolean,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    // Health Connect permission can be granted from OUTSIDE the app: the dashboard's
    // "Open Health Connect settings" button (see DashboardScreen) deep-links to the system
    // Health Connect app, and the user grants there then returns without killing Stride.
    // The sync worker is otherwise only ever scheduled from onboarding's permission-grant
    // callback or a cold MainActivity.onCreate start -- neither of which runs on this path --
    // so without this hook, granting permission via the settings deep link silently never
    // starts sync until the process is fully killed and cold-started again. Same root cause
    // as the onboarding scheduling bug, different trigger site.
    private val scheduleHealthConnectSync: () -> Unit = {}
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

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    /**
     * Rechecks both device-level status banners. Called once from [init] and again from
     * DashboardScreen's ON_RESUME lifecycle observer — Health Connect permissions and battery
     * optimization are both granted via a settings deep link outside the app, so the dashboard
     * must recheck when the user returns rather than trusting a one-shot value computed at
     * ViewModel creation (previously `healthConnectStatus` was only ever computed once in `init`
     * and never rechecked after the user granted permission this way).
     */
    fun refreshDeviceStatuses() {
        _batteryOptimizationIgnored.value = isIgnoringBatteryOptimizations()
        viewModelScope.launch {
            val previousStatus = _healthConnectStatus.value
            val newStatus = try {
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
            _healthConnectStatus.value = newStatus

            // Only fire on the transition INTO OK, not on every resume/refresh while already OK --
            // schedulePeriodic is idempotent (KEEP policy) but triggerOneOff REPLACEs the in-flight
            // one-off request, so calling it on every foreground would keep restarting sync and it
            // could never finish if resumes happen faster than a sync cycle.
            if (newStatus == HealthConnectStatus.OK && previousStatus != HealthConnectStatus.OK) {
                scheduleHealthConnectSync()
            }
        }
    }

    init {
        refreshDeviceStatuses()
    }

}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
