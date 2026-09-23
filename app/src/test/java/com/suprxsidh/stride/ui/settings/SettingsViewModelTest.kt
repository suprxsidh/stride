package com.suprxsidh.stride.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.SettingsRepository
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
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: SettingsViewModel
    private lateinit var adaptiveBudgetRepository: com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        adaptiveBudgetRepository = com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository(
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
    fun `saving and clearing the manual budget override updates observed state`() = runTest {
        db.userProfileDao().upsert(
            com.suprxsidh.stride.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, birthDate = "1995-01-01",
                sex = com.suprxsidh.stride.data.calc.Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
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

    @Test
    fun `saveManualBudgetOverride clamps below the 1200 kcal minimum`() = runTest {
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveManualBudgetOverride(400)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1200, viewModel.manualBudgetOverrideKcal.value)
    }
}
