package com.suprxsidh.deficit.ui.weight

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.data.repository.WeightRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: WeightViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        viewModel = WeightViewModel(WeightRepository(db.weighInDao()))
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
}
