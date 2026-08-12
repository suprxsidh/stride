package com.suprxsidh.deficit.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.calc.MotivationCategory
import com.suprxsidh.deficit.data.calc.MotivationInputs
import com.suprxsidh.deficit.data.calc.MotivationLine
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentState
import com.suprxsidh.deficit.data.repository.WeeklyReviewRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
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
    private val weeklyCommitmentRepository: WeeklyCommitmentRepository,
    private val weeklyReviewRepository: WeeklyReviewRepository,
    private val settingsRepository: SettingsRepository,
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

    val weeklyCommitmentState: StateFlow<WeeklyCommitmentState?> =
        settingsRepository.observeWeeklyRunTarget()
            .combine(settingsRepository.observeWeeklyRunFloor()) { target, floor -> target to floor }
            .flatMapLatest { (target, floor) -> weeklyCommitmentRepository.observeCurrentWeekState(target, floor) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _motivationLine = MutableStateFlow<String?>(null)
    val motivationLine: StateFlow<String?> = _motivationLine.asStateFlow()

    // SPEC §3.7's "headline streak stat" (consecutive weeks with the floor intact) needs a
    // permanent home in the UI, not just an occasional appearance via the rotating motivation
    // line. Computed once alongside the motivation line in init (same cost, same lifecycle).
    private val _floorIntactStreakWeeks = MutableStateFlow(0)
    val floorIntactStreakWeeks: StateFlow<Int> = _floorIntactStreakWeeks.asStateFlow()

    private val _unseenWeeklyReview = MutableStateFlow<WeeklyReviewEntity?>(null)
    val unseenWeeklyReview: StateFlow<WeeklyReviewEntity?> = _unseenWeeklyReview.asStateFlow()

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

    init {
        refreshDeviceStatuses()

        viewModelScope.launch {
            try {
                weeklyReviewRepository.generateForCompletedWeekIfDue()
                _unseenWeeklyReview.value = weeklyReviewRepository.unseenReview()
            } catch (e: Exception) {
                // A DB or clock hiccup here must not block the rest of the dashboard from loading.
                Log.w("DashboardViewModel", "Failed to generate or check the weekly review", e)
            }
        }

        viewModelScope.launch {
            try {
                val target = settingsRepository.observeWeeklyRunTarget().first()
                val floor = settingsRepository.observeWeeklyRunFloor().first()
                val state = weeklyCommitmentRepository.observeCurrentWeekState(target, floor).first()
                val streak = weeklyCommitmentRepository.consecutiveFloorIntactStreakWeeks(target, floor)
                val totalRuns = weeklyCommitmentRepository.totalRunDaysAllTime()
                val weightChange = weightRepository.observeTotalChangeSinceStart().first()
                _floorIntactStreakWeeks.value = streak

                val inputs = MotivationInputs(
                    floorAtRisk = state.floorState != FloorState.OK,
                    runsThisWeek = state.runsThisWeek,
                    weeklyTarget = state.target,
                    daysLeftInclusive = state.daysLeftInclusive,
                    floorIntactStreakWeeks = streak,
                    rollingWeightChangeKg = weightChange,
                    totalRunsLogged = totalRuns
                )

                val today = DayBoundary.logicalDate(clock()).toString()
                val lastDate = settingsRepository.getLastMotivationDate()
                val storedCategory = settingsRepository.getLastMotivationCategory()
                    ?.let { runCatching { MotivationCategory.valueOf(it) }.getOrNull() }

                // SPEC §3.7: one motivation line per logical day, never the same category two
                // days running. Re-picking a category on every cold start (the old behavior)
                // could show a *different* line on a second same-day launch, because the
                // category persisted by the first launch then looked like "yesterday's"
                // category to the rotation-avoidance logic below. Gate recomputation on the
                // logical date actually having changed, and reuse today's already-chosen
                // category otherwise.
                if (lastDate == today && storedCategory != null) {
                    val reused = MotivationLine.renderIfEligible(inputs, storedCategory)
                    if (reused != null) {
                        _motivationLine.value = reused
                        return@launch
                    }
                    // storedCategory is no longer eligible (e.g. its underlying stat vanished
                    // intra-day) -- fall through and pick a fresh one below.
                }

                val (category, line) = MotivationLine.dailyLine(inputs, storedCategory)
                settingsRepository.setLastMotivationCategory(category.name)
                settingsRepository.setLastMotivationDate(today)
                _motivationLine.value = line
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to compute the motivation line", e)
            }
        }
    }

    fun dismissWeeklyReview() {
        viewModelScope.launch {
            _unseenWeeklyReview.value?.let { weeklyReviewRepository.markReviewSeen(it.weekStartDate) }
            _unseenWeeklyReview.value = null
        }
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
