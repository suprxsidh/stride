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
}
