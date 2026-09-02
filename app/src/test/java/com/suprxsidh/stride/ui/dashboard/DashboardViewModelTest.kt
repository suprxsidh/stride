package com.suprxsidh.stride.ui.dashboard

import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import com.suprxsidh.stride.health.HealthDataSource
import com.suprxsidh.stride.health.RemoteWeightRecord
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
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: DashboardViewModel

    private class FakeHealthDataSource(var caloriesBurned: Int = 0) : HealthDataSource {
        override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int = caloriesBurned
        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> = emptyList()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String = ""
    }

    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true },
        isIgnoringBatteryOptimizations: () -> Boolean = { false },
        scheduleHealthConnectSync: () -> Unit = {},
        caloriesBurned: Int = 0
    ): DashboardViewModel {
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(FakeHealthDataSource(caloriesBurned), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            isIgnoringBatteryOptimizations,
            clock,
            scheduleHealthConnectSync
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

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
    fun `caloriesBurnedToday is populated from Health Connect once status is OK`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { true },
            caloriesBurned = 1380
        )
        backgroundScope.launch { viewModel.caloriesBurnedToday.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1380, viewModel.caloriesBurnedToday.value)
    }

    @Test
    fun `caloriesBurnedToday stays null while permissions are needed`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { false },
            caloriesBurned = 1380
        )
        backgroundScope.launch { viewModel.caloriesBurnedToday.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.caloriesBurnedToday.value)
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

        permissionsGranted = true
        batteryIgnored = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses schedules HC sync on the transition into OK`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        var permissionsGranted = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(0, scheduleCalls)

        permissionsGranted = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)

        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, scheduleCalls)
    }

    @Test
    fun `refreshDeviceStatuses does not schedule HC sync when permission is already OK at init`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { true },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)
    }
}
