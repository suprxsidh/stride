package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

data class WeeklyCommitmentState(
    val runsThisWeek: Int,
    val target: Int,
    val floor: Int,
    val daysLeftInclusive: Int,
    val floorState: FloorState
)

class WeeklyCommitmentRepository(
    private val exerciseSessionDao: ExerciseSessionDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    companion object {
        private val RUN_EXERCISE_TYPES = setOf("56", "57") // EXERCISE_TYPE_RUNNING, EXERCISE_TYPE_RUNNING_TREADMILL
        private const val STREAK_LOOKBACK_WEEKS = 104 // 2 years; the streak scan stops at the first broken week anyway
    }

    private fun runDaysInRange(sessions: List<ExerciseSessionEntity>, start: LocalDate, end: LocalDate): Set<LocalDate> =
        sessions.asSequence()
            .filter { it.exerciseType in RUN_EXERCISE_TYPES }
            .map { LocalDate.parse(it.date) }
            .filter { !it.isBefore(start) && !it.isAfter(end) }
            .toSet()

    fun observeCurrentWeekState(target: Int, floor: Int): Flow<WeeklyCommitmentState> =
        exerciseSessionDao.observeAll().map { sessions ->
            val today = DayBoundary.logicalDate(clock())
            val start = WeekBoundary.weekStart(today)
            val end = WeekBoundary.weekEnd(today)
            val runsThisWeek = runDaysInRange(sessions, start, end).size
            val daysLeft = WeekBoundary.daysLeftInclusive(today)
            WeeklyCommitmentState(
                runsThisWeek = runsThisWeek,
                target = target,
                floor = floor,
                daysLeftInclusive = daysLeft,
                floorState = WeeklyCommitmentCalc.floorState(runsThisWeek, floor, daysLeft)
            )
        }

    suspend fun runsCompletedForWeek(weekStart: LocalDate): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        return runDaysInRange(sessions, weekStart, weekStart.plusDays(6)).size
    }

    suspend fun consecutiveFloorIntactStreakWeeks(target: Int, floor: Int): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        val currentWeekStart = WeekBoundary.weekStart(DayBoundary.logicalDate(clock()))
        val outcomes = mutableListOf<WeekOutcome>()
        var weekStart = currentWeekStart.minusWeeks(1) // most recent COMPLETED week
        for (i in 0 until STREAK_LOOKBACK_WEEKS) {
            val runsThisWeek = runDaysInRange(sessions, weekStart, weekStart.plusDays(6)).size
            val outcome = WeeklyCommitmentCalc.weekOutcome(runsThisWeek, floor, target)
            outcomes += outcome
            if (outcome == WeekOutcome.BROKEN) break
            weekStart = weekStart.minusWeeks(1)
        }
        return WeeklyCommitmentCalc.consecutiveFloorIntactStreak(outcomes)
    }

    suspend fun totalRunDaysAllTime(): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        return sessions.filter { it.exerciseType in RUN_EXERCISE_TYPES }.map { it.date }.toSet().size
    }
}
