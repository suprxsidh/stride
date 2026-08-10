package com.suprxsidh.deficit.ui.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodLogViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: FoodLogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        val foodRepo = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        val offRepo = OpenFoodFactsRepository(
            OpenFoodFactsServiceFactory.create(baseUrl = server.url("/").toString()),
            db.offCacheDao()
        )
        viewModel = FoodLogViewModel(foodRepo, offRepo)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `logQuickAdd inserts a buffered entry and clears the input fields`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.quickAddName)
        assertEquals("", viewModel.quickAddKcal)
        assertEquals(330, db.foodEntryDao().observeBufferedTotalForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first())
    }

    @Test
    fun `logQuickAdd with blank name does nothing`() = runTest(testDispatcher) {
        viewModel.quickAddName = ""
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `logPinned logs one serving of the given custom food in a single call`() = runTest(testDispatcher) {
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        viewModel.logPinned(chaas)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertEquals(1, all.size)
        assertEquals(110, all[0].bufferedKcal)
    }

    @Test
    fun `searchOff populates results and marks a search as having happened`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"products": [{"code": "123", "product_name": "Test", "nutriments": {"energy-kcal_serving": 50.0}}]}""").setResponseCode(200))
        viewModel.offQuery = "test"
        viewModel.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()
        // The OkHttp/Retrofit call hits MockWebServer on a real background thread that isn't
        // driven by the virtual test scheduler, so the response can still be in flight the
        // instant advanceUntilIdle() returns. Poll (bounded) until the real I/O lands, flushing
        // the scheduler each time so the resumed continuation actually runs.
        var attempts = 0
        while (viewModel.offSearchInFlight && attempts < 200) {
            Thread.sleep(5)
            testDispatcher.scheduler.advanceUntilIdle()
            attempts++
        }

        assertEquals(1, viewModel.offResults.size)
        assertTrue(viewModel.offSearchedOnce)
        assertEquals(false, viewModel.offSearchInFlight)
    }

    @Test
    fun `saveCustomFood with blank name is a no-op`() = runTest(testDispatcher) {
        viewModel.customFoodName = ""
        viewModel.customFoodKcal = "100"
        viewModel.saveCustomFood(isPinned = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.customFoodDao().observeAll().first()
        assertTrue(all.isEmpty())
    }
}
