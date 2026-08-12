package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

data class DayConsistency(
    val date: LocalDate,
    val ran: Boolean,
    val loggedFood: Boolean,
    val underBudget: Boolean,
    val deficitKcal: Double?
)

data class WeekSummary(
    val weekStart: LocalDate,
    val daysRan: Int,
    val daysLogged: Int,
    val avgDeficitKcal: Double?,
    val outcome: WeekOutcome?
)

class ConsistencyRepository(
    private val exerciseSessionDao: ExerciseSessionDao,
    private val foodEntryDao: FoodEntryDao,
    private val userProfileDao: UserProfileDao
) {
    companion object {
        private val RUN_EXERCISE_TYPES = setOf("56", "57") // EXERCISE_TYPE_RUNNING, EXERCISE_TYPE_RUNNING_TREADMILL
    }

    suspend fun dailyConsistencyForMonth(month: YearMonth): List<DayConsistency> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val sessions = exerciseSessionDao.observeAll().first()
        val entries = foodEntryDao.getForDateRange(start.toString(), end.toString())
        val budget = userProfileDao.get()?.softBudgetKcal
        val runDays = sessions.filter { it.exerciseType in RUN_EXERCISE_TYPES }.map { LocalDate.parse(it.date) }.toSet()
        val entriesByDay = entries.groupBy { it.date }

        return (0 until end.dayOfMonth).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dayEntries = entriesByDay[date.toString()].orEmpty()
            val loggedFood = dayEntries.isNotEmpty()
            val deficitInt = if (loggedFood && budget != null) budget - dayEntries.sumOf { it.bufferedKcal } else null
            DayConsistency(
                date = date,
                ran = date in runDays,
                loggedFood = loggedFood,
                underBudget = deficitInt != null && deficitInt >= 0,
                deficitKcal = deficitInt?.toDouble()
            )
        }
    }

    suspend fun weeklySummariesForMonth(month: YearMonth, target: Int, floor: Int, today: LocalDate): List<WeekSummary> {
        val days = dailyConsistencyForMonth(month)
        val currentWeekStart = WeekBoundary.weekStart(today)
        return days.groupBy { WeekBoundary.weekStart(it.date) }
            .toSortedMap()
            .map { (weekStart, weekDays) ->
                val daysRan = weekDays.count { it.ran }
                val logged = weekDays.filter { it.loggedFood }
                val weekEnd = weekStart.plusDays(6)
                val outcome = if (weekEnd.isBefore(currentWeekStart)) {
                    WeeklyCommitmentCalc.weekOutcome(daysRan, floor, target)
                } else {
                    null
                }
                WeekSummary(
                    weekStart = weekStart,
                    daysRan = daysRan,
                    daysLogged = logged.size,
                    avgDeficitKcal = logged.mapNotNull { it.deficitKcal }.takeIf { it.isNotEmpty() }?.average(),
                    outcome = outcome
                )
            }
    }
}
