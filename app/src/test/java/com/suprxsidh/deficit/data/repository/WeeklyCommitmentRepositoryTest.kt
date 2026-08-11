package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyCommitmentRepositoryTest {
    private lateinit var db: DeficitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun runSession(date: String, hcRecordId: String, exerciseType: String = "56") = ExerciseSessionEntity(
        hcRecordId = hcRecordId, date = date, exerciseType = exerciseType, startTimeEpochMs = 0L,
        durationMin = 30, distanceM = 5000.0, avgPaceSecPerKm = null, avgHr = null, maxHr = null,
        kcalReal = 300, kcalCredited = 150
    )

    @Test
    fun `observeCurrentWeekState counts distinct running days this week, ignores non-running types`() = runTest {
        // 2024-01-01 is a Monday.
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-1")) // Monday, running
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-2")) // Tuesday, second run same-ish week
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-3")) // Tuesday again -> still 1 day
        db.exerciseSessionDao().insert(runSession("2024-01-03", "hc-4", exerciseType = "8")) // e.g. cycling -> ignored
        db.exerciseSessionDao().insert(runSession("2023-12-25", "hc-5")) // previous week -> ignored

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock = { LocalDateTime.of(2024, 1, 4, 8, 0) }) // Thursday
        val state = repo.observeCurrentWeekState(target = 4, floor = 3).first()

        assertEquals(2, state.runsThisWeek)
        assertEquals(4, state.daysLeftInclusive) // Thu..Sun
        assertEquals(FloorState.OK, state.floorState) // 1 more run needed, 4 days left
    }

    @Test
    fun `runsCompletedForWeek counts a completed week independent of the current clock`() = runTest {
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2024-01-05", "hc-2"))

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao())
        assertEquals(2, repo.runsCompletedForWeek(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun `consecutiveFloorIntactStreakWeeks stops at the first broken week going backward`() = runTest {
        // Week of 2024-01-15 (current, in progress, excluded from the streak): no runs yet.
        // Week of 2024-01-08: 3 runs -> floor met.
        db.exerciseSessionDao().insert(runSession("2024-01-08", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2024-01-09", "hc-2"))
        db.exerciseSessionDao().insert(runSession("2024-01-10", "hc-3"))
        // Week of 2024-01-01: only 1 run -> broken.
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-4"))

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock = { LocalDateTime.of(2024, 1, 15, 8, 0) })
        assertEquals(1, repo.consecutiveFloorIntactStreakWeeks(target = 4, floor = 3))
    }

    @Test
    fun `totalRunDaysAllTime counts distinct running days across all history`() = runTest {
        db.exerciseSessionDao().insert(runSession("2023-06-01", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2023-06-01", "hc-2")) // same day -> still 1
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-3"))
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-4", exerciseType = "8")) // ignored

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao())
        assertEquals(2, repo.totalRunDaysAllTime())
    }
}
