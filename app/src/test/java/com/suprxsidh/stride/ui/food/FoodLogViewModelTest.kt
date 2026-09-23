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
import java.time.LocalDateTime

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

    private fun buildViewModel(
        geminiApi: GeminiApi = FakeSucceedingGeminiApi(),
        initialDate: java.time.LocalDate? = null
    ): FoodLogViewModel {
        val geminiFoodRepository = GeminiFoodRepository(
            estimator = GeminiFoodEstimator(geminiApi),
            settingsRepository = settingsRepository,
            foodRepository = foodRepository,
            pendingDraftDao = db.pendingDraftDao()
        )
        return FoodLogViewModel(foodRepository, geminiFoodRepository, initialDate)
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

    // History (Feature A) tests below.

    @Test
    fun `selectedDate defaults to today per the repository's logical date`() = runTest(testDispatcher) {
        val today = foodRepository.currentLogicalDate()
        assertEquals(today, viewModel.selectedDate.value)
    }

    @Test
    fun `an explicit initialDate is honored instead of defaulting to today`() = runTest(testDispatcher) {
        val pastDate = java.time.LocalDate.of(2020, 1, 1)
        val viewModel = buildViewModel(initialDate = pastDate)
        assertEquals(pastDate, viewModel.selectedDate.value)
    }

    @Test
    fun `goToPreviousDay and goToNextDay move the selected date and the entries it drives`() = runTest(testDispatcher) {
        val today = foodRepository.currentLogicalDate()
        val yesterday = today.minusDays(1)
        // Seed yesterday via a second FoodRepository pinned to a fixed clock on that day, sharing
        // the same underlying DAOs -- a plain insert against the real target date, rather than
        // logging to today and moving it, since this is the same DB the viewModel's own
        // FoodRepository reads from.
        val yesterdaysClock = { LocalDateTime.of(yesterday.year, yesterday.monthValue, yesterday.dayOfMonth, 20, 0) }
        val yesterdayRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = yesterdaysClock)
        yesterdayRepository.logQuickAdd("Yesterday's dinner", 300)

        backgroundScope.launch { viewModel.entriesForSelectedDate.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.entriesForSelectedDate.value.size)

        viewModel.goToPreviousDay()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(yesterday, viewModel.selectedDate.value)
        assertEquals(1, viewModel.entriesForSelectedDate.value.size)
        assertEquals("Yesterday's dinner", viewModel.entriesForSelectedDate.value[0].name)

        viewModel.goToNextDay()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(today, viewModel.selectedDate.value)
        assertEquals(0, viewModel.entriesForSelectedDate.value.size)
    }

    @Test
    fun `startEditingEntry pre-fills the edit fields from the tapped entry`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()
        val entry = foodRepository.observeTodayEntries().first().first()

        viewModel.startEditingEntry(entry)

        assertEquals(entry, viewModel.editingEntry)
        assertEquals("Poha", viewModel.editName)
        assertEquals("300", viewModel.editKcal)
        assertNull(viewModel.editError)
    }

    @Test
    fun `saveEditedEntry updates name and buffered kcal in place and clears edit state`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()
        val entry = foodRepository.observeTodayEntries().first().first()

        viewModel.startEditingEntry(entry)
        viewModel.editName = "Poha with peanuts"
        viewModel.editKcal = "350"
        viewModel.saveEditedEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = foodRepository.observeTodayEntries().first().first()
        assertEquals(entry.id, updated.id)
        assertEquals("Poha with peanuts", updated.name)
        assertEquals(350, updated.rawKcal)
        assertEquals(385, updated.bufferedKcal) // 350 * 1.1
        assertNull(viewModel.editingEntry)
        assertEquals("", viewModel.editName)
        assertEquals("", viewModel.editKcal)
    }

    @Test
    fun `saveEditedEntry with blank name sets an error and does not update the entry`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()
        val entry = foodRepository.observeTodayEntries().first().first()

        viewModel.startEditingEntry(entry)
        viewModel.editName = ""
        viewModel.saveEditedEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter a food name.", viewModel.editError)
        assertEquals(entry, viewModel.editingEntry)
        val unchanged = foodRepository.observeTodayEntries().first().first()
        assertEquals("Poha", unchanged.name)
    }

    @Test
    fun `cancelEditingEntry clears edit state without changing the entry`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()
        val entry = foodRepository.observeTodayEntries().first().first()

        viewModel.startEditingEntry(entry)
        viewModel.editName = "Something else"
        viewModel.cancelEditingEntry()

        assertNull(viewModel.editingEntry)
        assertEquals("", viewModel.editName)
        assertEquals("", viewModel.editKcal)
        val unchanged = foodRepository.observeTodayEntries().first().first()
        assertEquals("Poha", unchanged.name)
    }
}
