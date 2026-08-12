package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsistencyRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: ConsistencyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ConsistencyRepository(db.exerciseSessionDao(), db.foodEntryDao(), db.userProfileDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `dailyConsistencyForMonth marks ran, loggedFood, and underBudget per day`() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1800, createdAt = 0L)
        )
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-08", name = "under", rawKcal = 1600, bufferedKcal = 1600, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-09", name = "over", rawKcal = 2000, bufferedKcal = 2000, source = "manual", offBarcode = null, loggedAt = 2L))

        val days = repo.dailyConsistencyForMonth(YearMonth.of(2024, 1))
        val jan8 = days.first { it.date == LocalDate.of(2024, 1, 8) }
        val jan9 = days.first { it.date == LocalDate.of(2024, 1, 9) }
        val jan10 = days.first { it.date == LocalDate.of(2024, 1, 10) }

        assertEquals(true, jan8.ran)
        assertEquals(true, jan8.loggedFood)
        assertEquals(true, jan8.underBudget)

        assertEquals(false, jan9.ran)
        assertEquals(true, jan9.loggedFood)
        assertEquals(false, jan9.underBudget)

        assertEquals(false, jan10.loggedFood)
        assertNull(jan10.deficitKcal)
    }

    @Test
    fun `weeklySummariesForMonth only assigns an outcome to fully-elapsed weeks`() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1800, createdAt = 0L)
        )
        // Week of 2024-01-08..14: no runs at all -> would be BROKEN once elapsed.
        val summaries = repo.weeklySummariesForMonth(YearMonth.of(2024, 1), target = 4, floor = 3, today = LocalDate.of(2024, 1, 10))

        val currentWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 8) }
        assertNull(currentWeek.outcome) // Jan 10 falls inside this week -> not yet judged

        val pastWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 1) }
        assertEquals(WeekOutcome.BROKEN, pastWeek.outcome) // fully before "today" and had 0 runs
    }
}
