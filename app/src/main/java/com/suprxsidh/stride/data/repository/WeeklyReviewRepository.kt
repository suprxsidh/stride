package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.calc.RollingAverage
import com.suprxsidh.stride.data.calc.WeekBoundary
import com.suprxsidh.stride.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.stride.data.calc.WeighInPoint
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.dao.WeeklyReviewDao
import com.suprxsidh.stride.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyReviewRepository(
    private val weeklyReviewDao: WeeklyReviewDao,
    private val weeklyCommitmentRepository: WeeklyCommitmentRepository,
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository,
    private val foodEntryDao: FoodEntryDao,
    private val weighInDao: WeighInDao,
    private val userProfileDao: UserProfileDao,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private fun rollingAvgOnOrBefore(series: List<Pair<LocalDate, Double>>, date: LocalDate): Double? =
        series.filter { !it.first.isAfter(date) }.maxByOrNull { it.first }?.second

    private suspend fun rollingWeightChangeForWeek(weekStart: LocalDate, weekEnd: LocalDate): Double? {
        val points = weighInDao.observeAll().first().map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) }
        val series = RollingAverage.sevenDayRollingAverage(points)
        val before = rollingAvgOnOrBefore(series, weekStart.minusDays(1))
        val after = rollingAvgOnOrBefore(series, weekEnd)
        return if (before == null || after == null) null else after - before
    }

    suspend fun generateForCompletedWeekIfDue(): WeeklyReviewEntity? {
        val profile = userProfileDao.get() ?: return null
        val today = DayBoundary.logicalDate(clock())
        val weekStart = WeekBoundary.weekStart(today).minusWeeks(1)
        val weekEnd = weekStart.plusDays(6)

        val createdAtDate = DayBoundary.logicalDate(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(profile.createdAt), ZoneId.systemDefault())
        )
        if (!weekEnd.isAfter(createdAtDate)) return null // inclusive: onboarding on the week's last day still counts as "predates real usage"

        val existing = weeklyReviewDao.getByWeekStart(weekStart.toString())
        if (existing != null) return existing

        val target = settingsRepository.observeWeeklyRunTarget().first()
        val floor = settingsRepository.observeWeeklyRunFloor().first()
        val runs = weeklyCommitmentRepository.runsCompletedForWeek(weekStart)

        val entries = foodEntryDao.getForDateRange(weekStart.toString(), weekEnd.toString())
        val byDay = entries.groupBy { it.date }
        val daysLogged = byDay.size
        val avgDeficit = if (daysLogged == 0) null else
            byDay.values.map { dayEntries -> profile.softBudgetKcal - dayEntries.sumOf { it.bufferedKcal } }.average()

        val rollingChange = rollingWeightChangeForWeek(weekStart, weekEnd)

        val budgetBefore = profile.softBudgetKcal
        val newBudget = adaptiveBudgetRepository.recomputeIfNoOverride()
        val budgetAdjustedTo = newBudget?.takeIf { it != budgetBefore }

        val outcome = WeeklyCommitmentCalc.weekOutcome(runs, floor, target)

        val review = WeeklyReviewEntity(
            weekStartDate = weekStart.toString(),
            runsCompleted = runs,
            runFloor = floor,
            runTarget = target,
            daysLogged = daysLogged,
            avgDailyDeficitKcal = avgDeficit,
            rollingWeightChangeKg = rollingChange,
            budgetAdjustedToKcal = budgetAdjustedTo,
            outcome = outcome.name,
            generatedAt = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        weeklyReviewDao.upsertByWeekStart(review)
        return review
    }

    fun observeHistory(): Flow<List<WeeklyReviewEntity>> = weeklyReviewDao.observeAll()

    suspend fun unseenReview(): WeeklyReviewEntity? {
        val latest = weeklyReviewDao.observeAll().first().firstOrNull() ?: return null
        val lastSeen = settingsRepository.getLastReviewSeenWeekStart()
        return if (latest.weekStartDate != lastSeen) latest else null
    }

    suspend fun markReviewSeen(weekStartDate: String) = settingsRepository.setLastReviewSeenWeekStart(weekStartDate)
}
