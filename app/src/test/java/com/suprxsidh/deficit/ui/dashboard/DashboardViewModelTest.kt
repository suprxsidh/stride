package com.suprxsidh.deficit.ui.dashboard

import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import com.suprxsidh.deficit.health.HealthDataSource
import com.suprxsidh.deficit.health.RemoteExerciseSession
import com.suprxsidh.deficit.health.RemoteWeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: DashboardViewModel
    private lateinit var healthDao: ExerciseSessionDao

    // Never exercises sync (these tests only read observeExerciseSessions() off the Room DAO
    // via HealthConnectRepository), so every method is an unreachable no-op.
    private class NoOpHealthDataSource : HealthDataSource {
        override suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession> = emptyList()
        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> = emptyList()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String = ""
    }

    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true },
        isIgnoringBatteryOptimizations: () -> Boolean = { false }
    ): DashboardViewModel {
        val settingsRepository = com.suprxsidh.deficit.data.repository.SettingsRepository(db.appSettingsDao())
        val weeklyCommitmentRepository = com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository(db.exerciseSessionDao(), clock)
        val adaptiveBudgetRepository = com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
        val weeklyReviewRepository = com.suprxsidh.deficit.data.repository.WeeklyReviewRepository(
            db.weeklyReviewDao(), weeklyCommitmentRepository, adaptiveBudgetRepository,
            db.foodEntryDao(), db.weighInDao(), db.userProfileDao(), settingsRepository, clock
        )
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(NoOpHealthDataSource(), db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            isIgnoringBatteryOptimizations,
            weeklyCommitmentRepository,
            weeklyReviewRepository,
            settingsRepository,
            clock
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        healthDao = db.exerciseSessionDao()
        viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // DashboardViewModel's flows use SharingStarted.WhileSubscribed(5_000), matching the
    // convention in FoodLogViewModel/OnboardingViewModel. That policy only starts collecting
    // the upstream Room flow once something actively subscribes to the resulting StateFlow —
    // in the real app this happens immediately via DashboardScreen's collectAsState(), but in a
    // test that only reads `.value` there is no subscriber. backgroundScope.launch { ... collect {} }
    // establishes that subscriber (backgroundScope is auto-cancelled when the test ends) so the
    // upstream flow actually starts and `.value` reflects real emissions.
    @Test
    fun `profile is null before onboarding`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.profile.value)
    }

    @Test
    fun `today buffered total reflects logged entries`() = runTest(testDispatcher) {
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", offBarcode = null, loggedAt = 1L))
        backgroundScope.launch { viewModel.todayBufferedTotal.collect {} }
        // FoodRepository.observeTodayBufferedTotal() now polls the logical date on a `while (true) { delay(...) }`
        // loop (see FoodRepository.todayKeyFlow) so the day boundary re-evaluates without an app restart.
        // advanceUntilIdle() would never return against that — it keeps advancing the virtual clock through
        // every future poll forever. runCurrent() drains everything scheduled at the *current* virtual instant
        // (the initial emission + the Room query chain), which is all this test needs.
        testDispatcher.scheduler.runCurrent()
        assertEquals(220, viewModel.todayBufferedTotal.value)
    }

    @Test
    fun `after onboarding the profile reflects the computed soft budget`() = runTest(testDispatcher) {
        UserProfileRepository(db.userProfileDao(), db.weighInDao()).completeOnboarding(178.0, 80.0, 26, Sex.MALE)
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1645, viewModel.profile.value?.softBudgetKcal)
    }

    @Test
    fun `todaysRun exposes the latest session for today's date`() = runTest(testDispatcher) {
        val today = "2026-08-11"
        val session = ExerciseSessionEntity(
            id = 1, hcRecordId = "hc-1", date = today, exerciseType = "RUNNING",
            startTimeEpochMs = 1L, durationMin = 30, distanceM = 5000.0,
            avgPaceSecPerKm = 360.0, avgHr = 150, maxHr = 170, kcalReal = 350, kcalCredited = 175
        )
        healthDao.insert(session)
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        backgroundScope.launch { viewModel.todaysRun.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("hc-1", viewModel.todaysRun.value?.hcRecordId)
        assertEquals(175, viewModel.todaysRun.value?.kcalCredited)
    }

    @Test
    fun `todaysRun is null when no session logged today`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        backgroundScope.launch { viewModel.todaysRun.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.todaysRun.value)
    }

    @Test
    fun `healthConnectStatus is UNAVAILABLE when the SDK isn't available`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = 2, // HealthConnectClient.SDK_UNAVAILABLE
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.UNAVAILABLE, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is PERMISSIONS_NEEDED when available but not granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { false }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is OK when available and granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `weeklyCommitmentState reflects this week's run days against the default target and floor`() = runTest(testDispatcher) {
        // 2026-08-10 is the Monday of the week the fixture clock sits in.
        healthDao.insert(
            ExerciseSessionEntity(
                hcRecordId = "hc-1", date = "2026-08-10", exerciseType = "56", startTimeEpochMs = 1L,
                durationMin = 30, distanceM = 5000.0, avgPaceSecPerKm = 360.0, avgHr = 150, maxHr = 170, kcalReal = 350, kcalCredited = 175
            )
        )
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
        backgroundScope.launch { viewModel.weeklyCommitmentState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.weeklyCommitmentState.value
        assertEquals(1, state?.runsThisWeek)
        assertEquals(4, state?.target)
        assertEquals(3, state?.floor)
    }

    @Test
    fun `motivationLine is populated after load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("0/4 runs this week · 7 days left.", viewModel.motivationLine.value)
    }

    @Test
    fun `batteryOptimizationIgnored reflects the lambda's value on load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            isIgnoringBatteryOptimizations = { true }
        )
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses rechecks both healthConnectStatus and batteryOptimizationIgnored`() = runTest(testDispatcher) {
        var permissionsGranted = false
        var batteryIgnored = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            isIgnoringBatteryOptimizations = { batteryIgnored }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(false, viewModel.batteryOptimizationIgnored.value)

        // Simulate the user granting Health Connect permissions and disabling battery
        // optimization via their respective settings deep links, then resuming the app.
        permissionsGranted = true
        batteryIgnored = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `unseenWeeklyReview surfaces a freshly generated review, dismissing clears it`() = runTest(testDispatcher) {
        db.userProfileDao().upsert(
            com.suprxsidh.deficit.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name,
                goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
            )
        )
        // "Now" is Monday 2024-01-15 -> the week of 2024-01-08..14 just ended.
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2024, 1, 15, 8, 0) })
        testDispatcher.scheduler.advanceUntilIdle()

        val review = viewModel.unseenWeeklyReview.value
        assertEquals("2024-01-08", review?.weekStartDate)

        viewModel.dismissWeeklyReview()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.unseenWeeklyReview.value)
    }

    @Test
    fun `floorIntactStreakWeeks exposes the repository's streak value`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.floorIntactStreakWeeks.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // No prior weeks in the DB at all -> the most recent completed week is BROKEN
        // (0 runs < floor), so the streak is 0. This is mainly a wiring check: the same
        // value the ViewModel already computes for the motivation line must also be
        // surfaced permanently for WeeklyCommitmentCard (final-review fix).
        assertEquals(0, viewModel.floorIntactStreakWeeks.value)
    }

    // Final-review fix: the motivation category used to be re-rolled on every cold start with
    // no date check, so a second same-day launch could show a *different* line than the first
    // (see DashboardViewModel's init: the category persisted by launch #1 then looked like
    // "yesterday's" category to the rotation-avoidance logic, forcing launch #2 to skip to the
    // next eligible category). These tests insert an old run so two categories are eligible
    // (WEEKLY_PROGRESS and TOTAL_RUNS) so a rotation would actually be observable.
    @Test
    fun `motivation line does not rotate on a second cold start the same logical day`() = runTest(testDispatcher) {
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(
                hcRecordId = "hc-old", date = "2020-01-01", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null,
                kcalReal = 300, kcalCredited = 150
            )
        )
        // `viewModel` (built in setUp with clock 2026-08-10 12:00) hasn't run its init
        // coroutine yet -- StandardTestDispatcher only drains on advanceUntilIdle -- so this
        // first drain is cold start #1, and it sees the run inserted above.
        testDispatcher.scheduler.advanceUntilIdle()
        val firstLine = viewModel.motivationLine.value
        assertEquals("0/4 runs this week · 7 days left.", firstLine)

        val secondColdStart = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 20, 0) }) // later, same logical day
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(firstLine, secondColdStart.motivationLine.value)
    }

    @Test
    fun `motivation line rotates once the logical date actually changes`() = runTest(testDispatcher) {
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(
                hcRecordId = "hc-old", date = "2020-01-01", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null,
                kcalReal = 300, kcalCredited = 150
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val dayOneLine = viewModel.motivationLine.value
        assertEquals("0/4 runs this week · 7 days left.", dayOneLine)

        val dayTwo = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("1 runs logged since you started.", dayTwo.motivationLine.value)
    }
}
