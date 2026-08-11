package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.ai.gemini.GeminiApi
import com.suprxsidh.deficit.ai.gemini.GeminiCandidate
import com.suprxsidh.deficit.ai.gemini.GeminiContent
import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.deficit.ai.gemini.GeminiGenerateContentRequest
import com.suprxsidh.deficit.ai.gemini.GeminiGenerateContentResponse
import com.suprxsidh.deficit.ai.gemini.GeminiPart
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiFoodRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var foodRepository: FoodRepository

    private class FakeGeminiApi(private val succeed: Boolean) : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
            if (!succeed) throw java.io.IOException("network down")
            val json = """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
            return GeminiGenerateContentResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(text = json))))))
        }
    }

    private fun buildRepo(succeed: Boolean): GeminiFoodRepository {
        val estimator = GeminiFoodEstimator(FakeGeminiApi(succeed))
        return GeminiFoodRepository(estimator, settingsRepository, foodRepository, db.pendingDraftDao())
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        foodRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        settingsRepository.setGeminiApiKey("test-key")
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `estimateMeal returns NoApiKey when no key is set`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.NoApiKey)
    }

    @Test
    fun `estimateMeal returns the itemized estimate without logging anything`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.Success)
        val estimate = (result as GeminiEstimateResult.Success).estimate
        assertEquals(180, estimate.totalKcal)
        assertEquals(1, estimate.items.size)
        assertEquals(0, foodRepository.observeTodayEntries().first().size) // not saved yet
    }

    @Test
    fun `confirmEstimate logs a buffered food entry using the estimate's own values`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        val estimate = (result as GeminiEstimateResult.Success).estimate

        val entry = buildRepo(succeed = true).confirmEstimate(estimate)

        assertEquals(180, entry.rawKcal)
        assertEquals(198, entry.bufferedKcal) // +10%
        assertEquals("GEMINI", entry.source)
        assertEquals(1, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `confirmEstimate uses the user's edited name and kcal when provided`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        val estimate = (result as GeminiEstimateResult.Success).estimate

        val entry = buildRepo(succeed = true).confirmEstimate(estimate, editedName = "2 rotis (edited)", editedTotalKcal = 220)

        assertEquals("2 rotis (edited)", entry.name)
        assertEquals(220, entry.rawKcal)
        assertEquals(242, entry.bufferedKcal) // +10% of the edited value, not the original
    }

    @Test
    fun `estimateMeal saves a pending draft on failure and logs nothing`() = runTest {
        val repo = buildRepo(succeed = false)
        val result = repo.estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.Failed)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
        assertEquals(1, db.pendingDraftDao().getAll().size)
    }

    @Test
    fun `retryPendingDrafts auto-logs the draft and removes it once Gemini succeeds`() = runTest {
        buildRepo(succeed = false).estimateMeal("2 rotis", null)
        assertEquals(1, db.pendingDraftDao().getAll().size)

        val succeeded = buildRepo(succeed = true).retryPendingDrafts()

        assertEquals(1, succeeded)
        assertEquals(0, db.pendingDraftDao().getAll().size)
        assertEquals(1, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `two concurrent retries of the same draft only log the meal once`() = runTest {
        buildRepo(succeed = false).estimateMeal("2 rotis", null)
        assertEquals(1, db.pendingDraftDao().getAll().size)

        // App startup and the "Retry now" button both call retryPendingDrafts() on the single
        // AppContainer-scoped repository. Without an in-flight guard each iterated its own
        // getAll() snapshot and the same draft got confirmed twice — one meal, two food entries.
        val repo = buildRepo(succeed = true)
        val totals = coroutineScope {
            val first = async { repo.retryPendingDrafts() }
            val second = async { repo.retryPendingDrafts() }
            first.await() + second.await()
        }

        assertEquals(1, totals)
        assertEquals(0, db.pendingDraftDao().getAll().size)
        assertEquals(1, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `discardDraftAsQuickAdd logs a quick-add entry and removes the draft`() = runTest {
        buildRepo(succeed = false).estimateMeal("mystery meal", null)
        val draft = db.pendingDraftDao().getAll().first()

        buildRepo(succeed = false).discardDraftAsQuickAdd(draft.id, kcal = 300)

        assertEquals(0, db.pendingDraftDao().getAll().size)
        val entries = foodRepository.observeTodayEntries().first()
        assertEquals(1, entries.size)
        assertEquals("QUICK", entries.first().source)
    }
}
