package com.suprxsidh.stride.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodEntryDaoTest {
    private lateinit var db: StrideDatabase
    private lateinit var dao: FoodEntryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.foodEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `buffered total for date sums only that date's entries`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Roti+Dal", rawKcal = 450, bufferedKcal = 495, source = "QUICK", loggedAt = 1L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Chaas", rawKcal = 80, bufferedKcal = 88, source = "CUSTOM", loggedAt = 2L))
        dao.insert(FoodEntryEntity(date = "2026-08-11", name = "Other day", rawKcal = 300, bufferedKcal = 330, source = "QUICK", loggedAt = 3L))

        val total = dao.observeBufferedTotalForDate("2026-08-10").first()
        assertEquals(583, total)
    }

    @Test
    fun `buffered total for a date with no entries is zero, not null`() = runTest {
        val total = dao.observeBufferedTotalForDate("2026-01-01").first()
        assertEquals(0, total)
    }

    @Test
    fun `observeForDate returns entries ordered by loggedAt`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Second", rawKcal = 100, bufferedKcal = 110, source = "QUICK", loggedAt = 200L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "First", rawKcal = 50, bufferedKcal = 55, source = "QUICK", loggedAt = 100L))

        val entries = dao.observeForDate("2026-08-10").first()
        assertEquals(listOf("First", "Second"), entries.map { it.name })
    }
}
