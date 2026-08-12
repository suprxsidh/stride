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
        // Week of 2024-01-01..07 (the "past" week): one run + two logged days, one under
        // budget and one over, plus five days with no entries at all -> those five days
        // must not pull the average down (or up) toward zero deficit.
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-past-1", date = "2024-01-01", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-01", name = "under", rawKcal = 1600, bufferedKcal = 1600, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-02", name = "over", rawKcal = 2000, bufferedKcal = 2000, source = "manual", offBarcode = null, loggedAt = 2L))
        // Jan 03..07 intentionally left with no exercise or food entries.

        // Week of 2024-01-08..14: no runs at all -> would be BROKEN once elapsed.
        val summaries = repo.weeklySummariesForMonth(YearMonth.of(2024, 1), target = 4, floor = 3, today = LocalDate.of(2024, 1, 10))

        val currentWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 8) }
        assertNull(currentWeek.outcome) // Jan 10 falls inside this week -> not yet judged

        val pastWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 1) }
        assertEquals(WeekOutcome.BROKEN, pastWeek.outcome) // fully before "today" and daysRan(1) < floor(3)
        assertEquals(1, pastWeek.daysRan)
        assertEquals(2, pastWeek.daysLogged)
        // Deficit averaged only over the 2 logged days: (1800-1600=200) and (1800-2000=-200)
        // -> average 0.0. The 5 unlogged days must NOT count as 0-deficit entries dragging
        // the average toward a different value (e.g. if they wrongly counted, avg would be
        // (200 + -200 + 0*5) / 7 = 0.0 too by coincidence, so this alone wouldn't catch it —
        // the explicit daysLogged=2 assertion above is what proves only 2 days were averaged).
        assertEquals(0.0, pastWeek.avgDeficitKcal!!, 0.001)
    }

    @Test
    fun `weeklySummariesForMonth widens boundary weeks to their full 7 days and excludes phantom out-of-month weeks`() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1800, createdAt = 0L)
        )
        // February 2024: Feb 1 is a Thursday, so the week touching the start of the month
        // is 2024-01-29 (Mon) .. 2024-02-04 (Sun) — its weekStart (Jan 29) is NOT in
        // February, so it must not appear as a summary row for a February query at all.
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-jan29", date = "2024-01-29", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )

        // The week 2024-02-26 (Mon) .. 2024-03-03 (Sun) DOES have its weekStart (Feb 26)
        // inside February, so it must be reported for a February query -- but its
        // daysRan/daysLogged/avgDeficitKcal must reflect the FULL 7 days, including the
        // 3 days (Mar 1-3) that spill into March.
        listOf("2024-02-26", "2024-02-27", "2024-03-01", "2024-03-02").forEachIndexed { i, date ->
            db.exerciseSessionDao().insert(
                ExerciseSessionEntity(hcRecordId = "hc-boundary-$i", date = date, exerciseType = "56", startTimeEpochMs = 0L,
                    durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
            )
        }
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-02-26", name = "even", rawKcal = 1800, bufferedKcal = 1800, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-03-03", name = "under", rawKcal = 1000, bufferedKcal = 1000, source = "manual", offBarcode = null, loggedAt = 2L))

        val summaries = repo.weeklySummariesForMonth(YearMonth.of(2024, 2), target = 4, floor = 3, today = LocalDate.of(2024, 3, 10))

        assertEquals(true, summaries.none { it.weekStart == LocalDate.of(2024, 1, 29) }) // phantom out-of-month week excluded
        assertEquals(true, summaries.none { it.weekStart == LocalDate.of(2024, 3, 4) }) // next month's week also excluded

        val boundaryWeek = summaries.first { it.weekStart == LocalDate.of(2024, 2, 26) }
        assertEquals(4, boundaryWeek.daysRan) // Feb26, Feb27, Mar1, Mar2 -- not just the 2 in-Feb days
        assertEquals(2, boundaryWeek.daysLogged) // Feb26 + Mar3
        assertEquals(400.0, boundaryWeek.avgDeficitKcal!!, 0.001) // (1800-1800=0, 1800-1000=800) / 2 = 400
        assertEquals(WeekOutcome.TARGET_MET, boundaryWeek.outcome) // daysRan(4) >= target(4), fully elapsed by "today"
    }
}
