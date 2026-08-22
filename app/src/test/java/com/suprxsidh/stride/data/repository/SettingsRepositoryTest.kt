package com.suprxsidh.stride.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private lateinit var db: StrideDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SettingsRepository(db.appSettingsDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `getGeminiApiKey returns null before any key is set`() = runTest {
        assertNull(repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey then getGeminiApiKey round-trips the value`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        assertEquals("test-key-123", repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey with null clears the stored key`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        repository.setGeminiApiKey(null)
        assertNull(repository.getGeminiApiKey())
    }

    @Test
    fun `weekly run target and floor default to 4 and 3, and are settable`() = runTest {
        assertEquals(4, repository.observeWeeklyRunTarget().first())
        assertEquals(3, repository.observeWeeklyRunFloor().first())

        repository.setWeeklyRunTarget(5)
        repository.setWeeklyRunFloor(4)

        assertEquals(5, repository.observeWeeklyRunTarget().first())
        assertEquals(4, repository.observeWeeklyRunFloor().first())
    }

    @Test
    fun `manual budget override is null by default and round-trips through set and clear`() = runTest {
        assertEquals(null, repository.getManualBudgetOverrideKcal())

        repository.setManualBudgetOverrideKcal(1900)
        assertEquals(1900, repository.getManualBudgetOverrideKcal())

        repository.setManualBudgetOverrideKcal(null)
        assertEquals(null, repository.getManualBudgetOverrideKcal())
    }

    @Test
    fun `last-seen review watermark and last motivation category round-trip`() = runTest {
        assertEquals(null, repository.getLastReviewSeenWeekStart())
        repository.setLastReviewSeenWeekStart("2024-01-08")
        assertEquals("2024-01-08", repository.getLastReviewSeenWeekStart())

        assertEquals(null, repository.getLastMotivationCategory())
        repository.setLastMotivationCategory("WEEKLY_PROGRESS")
        assertEquals("WEEKLY_PROGRESS", repository.getLastMotivationCategory())
    }

    // Final-review fix: DashboardViewModel needs to know *when* a motivation category was
    // last chosen (not just which one) to gate recomputation to once per logical day.
    @Test
    fun `last motivation date round-trips and defaults to null`() = runTest {
        assertEquals(null, repository.getLastMotivationDate())
        repository.setLastMotivationDate("2026-08-10")
        assertEquals("2026-08-10", repository.getLastMotivationDate())
    }
}
