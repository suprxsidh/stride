package com.suprxsidh.stride.ui.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.ai.gemini.GeminiApi
import com.suprxsidh.stride.ai.gemini.GeminiCandidate
import com.suprxsidh.stride.ai.gemini.GeminiContent
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentRequest
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentResponse
import com.suprxsidh.stride.ai.gemini.GeminiPart
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private lateinit var db: StrideDatabase
    private lateinit var foodRepository: FoodRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: FoodLogViewModel

    private class FakeSucceedingGeminiApi : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
            val json = """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
            return GeminiGenerateContentResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(text = json))))))
        }
    }

    private fun buildViewModel(geminiApi: GeminiApi = FakeSucceedingGeminiApi()): FoodLogViewModel {
        val geminiFoodRepository = GeminiFoodRepository(
            estimator = GeminiFoodEstimator(geminiApi),
            settingsRepository = settingsRepository,
            foodRepository = foodRepository,
            pendingDraftDao = db.pendingDraftDao()
        )
        return FoodLogViewModel(foodRepository, geminiFoodRepository)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        foodRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        settingsRepository = SettingsRepository(db.appSettingsDao())
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
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
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first())
    }

    @Test
    fun `logQuickAdd with blank name does nothing`() = runTest(testDispatcher) {
        viewModel.quickAddName = ""
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `logPinned logs one serving of the given custom food in a single call`() = runTest(testDispatcher) {
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        viewModel.logPinned(chaas)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertEquals(1, all.size)
        assertEquals(110, all[0].bufferedKcal)
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

    @Test
    fun `aiEstimateAvailable reflects whether a Gemini key is set`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        var viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey("a-key")
        viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.aiEstimateAvailable.value)
    }

    @Test
    fun `aiEstimateAvailable updates when the key changes without rebuilding the view model`() = runTest {
        // Was a one-shot flow, so whether the AI section appeared depended on WhileSubscribed timing
        // rather than on the stored key. Saving or clearing the key in Settings must move this.
        settingsRepository.setGeminiApiKey(null)
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey("a-key")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)
    }

    @Test
    fun `submitAiEstimate populates reviewEstimate without logging anything yet`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(180, viewModel.reviewEstimate?.totalKcal)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `confirmAiEstimate logs the entry and clears the review state`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmAiEstimate(editedName = "2 rotis, dal", editedTotalKcal = 180)
        testDispatcher.scheduler.advanceUntilIdle()

        val entries = foodRepository.observeTodayEntries().first()
        assertEquals(1, entries.size)
        assertEquals("GEMINI", entries.first().source)
        assertNull(viewModel.reviewEstimate)
        assertEquals("", viewModel.aiDescription)
    }

    @Test
    fun `cancelAiReview clears the review state without logging`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelAiReview()

        assertNull(viewModel.reviewEstimate)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
    }
}
