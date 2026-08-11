package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
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
class WeeklyReviewDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: WeeklyReviewDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.weeklyReviewDao()
    }

    @After
    fun tearDown() = db.close()

    private fun review(weekStartDate: String, runsCompleted: Int = 3) = WeeklyReviewEntity(
        weekStartDate = weekStartDate,
        runsCompleted = runsCompleted,
        runFloor = 3,
        runTarget = 4,
        daysLogged = 5,
        avgDailyDeficitKcal = 420.0,
        rollingWeightChangeKg = -0.5,
        budgetAdjustedToKcal = null,
        outcome = "FLOOR_MET",
        generatedAt = 1_000L
    )

    @Test
    fun `getByWeekStart returns null when absent`() = runTest {
        assertNull(dao.getByWeekStart("2024-01-01"))
    }

    @Test
    fun `upsertByWeekStart inserts then replaces the same week`() = runTest {
        dao.upsertByWeekStart(review("2024-01-01", runsCompleted = 2))
        dao.upsertByWeekStart(review("2024-01-01", runsCompleted = 4))

        val stored = dao.getByWeekStart("2024-01-01")
        assertEquals(4, stored?.runsCompleted)
    }

    @Test
    fun `observeAll orders by weekStartDate descending`() = runTest {
        dao.upsertByWeekStart(review("2024-01-01"))
        dao.upsertByWeekStart(review("2024-01-08"))

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("2024-01-08", all[0].weekStartDate)
        assertEquals("2024-01-01", all[1].weekStartDate)
    }
}
