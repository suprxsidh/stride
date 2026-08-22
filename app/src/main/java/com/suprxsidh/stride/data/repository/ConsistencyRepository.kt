package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.RunTypes
import com.suprxsidh.stride.data.calc.WeekBoundary
import com.suprxsidh.stride.data.calc.WeekOutcome
import com.suprxsidh.stride.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.stride.data.db.dao.ExerciseSessionDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

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
    suspend fun dailyConsistencyForMonth(month: YearMonth): List<DayConsistency> =
        dailyConsistencyForRange(month.atDay(1), month.atEndOfMonth())

    suspend fun weeklySummariesForMonth(month: YearMonth, target: Int, floor: Int, today: LocalDate): List<WeekSummary> {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        // Widen to whole weeks so a week straddling the month boundary is evaluated over
        // its full 7 days (pulling in adjacent-month days for run/food/budget lookups),
        // not just the subset that happens to fall inside this calendar month.
        val rangeStart = WeekBoundary.weekStart(monthStart)
        val rangeEnd = WeekBoundary.weekEnd(monthEnd)
        val days = dailyConsistencyForRange(rangeStart, rangeEnd)
        val currentWeekStart = WeekBoundary.weekStart(today)

        return days.groupBy { WeekBoundary.weekStart(it.date) }
            // Only report weeks whose weekStart actually falls within the target month —
            // a week mostly in the previous/next month (weekStart outside this month) is
            // dropped rather than returned as a phantom partial row.
            .filterKeys { weekStart -> !weekStart.isBefore(monthStart) && !weekStart.isAfter(monthEnd) }
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

    private suspend fun dailyConsistencyForRange(start: LocalDate, end: LocalDate): List<DayConsistency> {
        val sessions = exerciseSessionDao.observeAll().first()
        val entries = foodEntryDao.getForDateRange(start.toString(), end.toString())
        val budget = userProfileDao.get()?.softBudgetKcal
        val runDays = sessions.filter { it.exerciseType in RunTypes.EXERCISE_TYPES }.map { LocalDate.parse(it.date) }.toSet()
        val entriesByDay = entries.groupBy { it.date }
        val dayCount = ChronoUnit.DAYS.between(start, end).toInt() + 1

        return (0 until dayCount).map { offset ->
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
}
