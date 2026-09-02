package com.suprxsidh.stride.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _caloriesBurnedToday = MutableStateFlow<Int?>(null)
    val caloriesBurnedToday: StateFlow<Int?> = _caloriesBurnedToday.asStateFlow()

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

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
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
            _healthConnectStatus.value = newStatus

            if (newStatus == HealthConnectStatus.OK && previousStatus != HealthConnectStatus.OK) {
                scheduleHealthConnectSync()
            }

            if (newStatus == HealthConnectStatus.OK) {
                _caloriesBurnedToday.value = try {
                    healthConnectRepository?.getTodaysCaloriesBurned()
                } catch (e: Exception) {
                    Log.w("DashboardViewModel", "Failed to read today's calories burned from Health Connect", e)
                    null
                }
            }
        }
    }

    init {
        refreshDeviceStatuses()
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
