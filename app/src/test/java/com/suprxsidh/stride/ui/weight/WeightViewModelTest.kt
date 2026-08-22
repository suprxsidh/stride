package com.suprxsidh.stride.ui.weight

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.data.repository.WeighInSyncSource
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: StrideDatabase
    private lateinit var repository: WeightRepository
    private lateinit var viewModel: WeightViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        repository = WeightRepository(db.weighInDao())
        viewModel = WeightViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `logWeighIn stores the entry and clears the input`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.rawSeries.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.weightInput = "81.4"
        viewModel.logWeighIn()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.weightInput)
        assertEquals(1, viewModel.rawSeries.value.size)
        assertEquals(81.4, viewModel.rawSeries.value[0].second, 0.001)
    }

    @Test
    fun `logWeighIn with non-numeric input is a no-op`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.rawSeries.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.weightInput = "not a number"
        viewModel.logWeighIn()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.rawSeries.value.size)
    }

    @Test
    fun `total change reflects the repository's computed delta`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.totalChange.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        db.weighInDao().upsert(WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(WeighInEntity(date = "2026-08-10", weightKg = 81.0))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(-4.0, viewModel.totalChange.value!!, 0.001)
    }

    @Test
    fun `syncWithHealthConnect reports how many entries were synced`() = runTest(testDispatcher) {
        val viewModel = WeightViewModel(
            weightRepository = repository,
            healthConnectRepository = FakeHealthConnectRepositoryReturning(2)
        )
        viewModel.syncWithHealthConnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Synced 2 entries with Health Connect", viewModel.lastSyncResult.value)
    }

    @Test
    fun `syncWithHealthConnect reports missing permissions instead of crashing`() = runTest(testDispatcher) {
        // The repository is non-null whenever Health Connect is merely *installed*; onboarding lets
        // the user skip the permission grant, and reading without it throws SecurityException. That
        // used to escape viewModelScope uncaught and take the app down.
        val viewModel = WeightViewModel(
            weightRepository = repository,
            healthConnectRepository = FakeHealthConnectRepositoryThrowing(SecurityException("not granted"))
        )
        viewModel.syncWithHealthConnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Health Connect permissions needed — grant them in Settings", viewModel.lastSyncResult.value)
    }

    @Test
    fun `syncWithHealthConnect reports a generic failure for any other error`() = runTest(testDispatcher) {
        val viewModel = WeightViewModel(
            weightRepository = repository,
            healthConnectRepository = FakeHealthConnectRepositoryThrowing(java.io.IOException("provider down"))
        )
        viewModel.syncWithHealthConnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Couldn't sync with Health Connect — try again later", viewModel.lastSyncResult.value)
    }

    @Test
    fun `syncWithHealthConnect tells the user when Health Connect isn't installed at all`() = runTest(testDispatcher) {
        assertFalse(viewModel.healthConnectAvailable)

        viewModel.syncWithHealthConnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Health Connect isn't available on this device", viewModel.lastSyncResult.value)
    }

    @Test
    fun `healthConnectAvailable is true when a sync source is present`() {
        val viewModel = WeightViewModel(repository, FakeHealthConnectRepositoryReturning(0))
        assertTrue(viewModel.healthConnectAvailable)
    }
}

private class FakeHealthConnectRepositoryReturning(private val count: Int) : WeighInSyncSource {
    override suspend fun syncWeighIns(): Int = count
}

private class FakeHealthConnectRepositoryThrowing(private val error: Throwable) : WeighInSyncSource {
    override suspend fun syncWeighIns(): Int = throw error
}
