package com.suprxsidh.deficit.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: SettingsViewModel
    private lateinit var adaptiveBudgetRepository: com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        adaptiveBudgetRepository = com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository(
            db.userProfileDao(), db.weighInDao(), settingsRepository
        )
        viewModel = SettingsViewModel(settingsRepository, adaptiveBudgetRepository)
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `saveGeminiApiKey updates the observed state`() = runTest {
        backgroundScope.launch { viewModel.geminiApiKey.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveGeminiApiKey("my-key")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("my-key", viewModel.geminiApiKey.value)
    }

    @Test
    fun `weekly target and floor default to 4 and 3, saving updates observed state`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, viewModel.weeklyRunTarget.value)
        assertEquals(3, viewModel.weeklyRunFloor.value)

        viewModel.saveWeeklyRunTarget(5)
        viewModel.saveWeeklyRunFloor(4)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.weeklyRunTarget.value)
        assertEquals(4, viewModel.weeklyRunFloor.value)
    }

    @Test
    fun `saving and clearing the manual budget override updates observed state`() = runTest {
        db.userProfileDao().upsert(
            com.suprxsidh.deficit.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29,
                sex = com.suprxsidh.deficit.data.calc.Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
            )
        )
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)

        viewModel.saveManualBudgetOverride(1700)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1700, viewModel.manualBudgetOverrideKcal.value)

        viewModel.clearManualBudgetOverride()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)
    }

    // Final-review fix: weekly target/floor were previously persisted as-typed with no
    // validation, so a stray 0 or a huge number could permanently wedge the floor/target math
    // with no recovery path. These pin the 1..7 clamp and the floor <= target invariant.

    @Test
    fun `saveWeeklyRunTarget clamps values outside 1 to 7`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveWeeklyRunTarget(0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.weeklyRunTarget.value)

        viewModel.saveWeeklyRunTarget(99)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(7, viewModel.weeklyRunTarget.value)
    }

    @Test
    fun `saveWeeklyRunFloor clamps values outside 1 to 7`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveWeeklyRunFloor(-5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.weeklyRunFloor.value)

        viewModel.saveWeeklyRunFloor(20)
        testDispatcher.scheduler.advanceUntilIdle()
        // target was still the 4 default, which is < the clamped floor of 7, so the target
        // must be raised alongside it to preserve floor <= target.
        assertEquals(7, viewModel.weeklyRunFloor.value)
        assertEquals(7, viewModel.weeklyRunTarget.value)
    }

    @Test
    fun `saveWeeklyRunTarget below the current floor pulls the floor down to match`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveWeeklyRunFloor(5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, viewModel.weeklyRunFloor.value)
        assertEquals(5, viewModel.weeklyRunTarget.value) // raised from default 4 to preserve invariant

        viewModel.saveWeeklyRunTarget(2)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.weeklyRunTarget.value)
        assertEquals(2, viewModel.weeklyRunFloor.value) // pulled down: floor must not exceed target
    }

    @Test
    fun `saveWeeklyRunFloor above the current target pushes the target up to match`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveWeeklyRunTarget(4)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveWeeklyRunFloor(6)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.weeklyRunFloor.value)
        assertEquals(6, viewModel.weeklyRunTarget.value) // pushed up: target must not be below floor
    }

    @Test
    fun `saveManualBudgetOverride clamps below the 1200 kcal minimum`() = runTest {
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveManualBudgetOverride(400)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1200, viewModel.manualBudgetOverrideKcal.value)
    }
}
