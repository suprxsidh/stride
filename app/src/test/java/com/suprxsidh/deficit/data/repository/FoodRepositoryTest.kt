package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodRepositoryTest {
    private lateinit var db: DeficitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `quick add applies the 10 percent buffer and lands in today's total`() = runTest {
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao()) { LocalDateTime.of(2026, 8, 10, 20, 0) }
        repo.logQuickAdd("2 rotis, dal", 450)

        val total = repo.observeTodayBufferedTotal().first()
        assertEquals(495, total)
    }

    @Test
    fun `an entry logged at 1am counts toward the previous day, not today`() = runTest {
        val lateNightClock = { LocalDateTime.of(2026, 8, 11, 1, 0) } // 1am -> logical date is Aug 10
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao(), lateNightClock)
        repo.logQuickAdd("Late dinner", 300)

        // "today" per this same clock is still Aug 10 (before the 3am boundary), so the entry shows up
        val total = repo.observeTodayBufferedTotal().first()
        assertEquals(330, total)

        // and it's really stored against 2026-08-10, not 2026-08-11
        val stored = db.foodEntryDao().observeForDate("2026-08-10").first()
        assertEquals(1, stored.size)
    }

    @Test
    fun `logging a custom food multiplies kcal per serving by fractional servings`() = runTest {
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao()) { LocalDateTime.of(2026, 8, 10, 12, 0) }
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        val entry = repo.logCustomFood(chaas, servings = 1.5)

        assertEquals(150, entry.rawKcal)
        assertEquals(165, entry.bufferedKcal)
    }
}
