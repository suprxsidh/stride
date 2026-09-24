package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.calc.RollingAverage
import com.suprxsidh.stride.data.calc.WeighInPoint
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

enum class TrendDirection { UP, DOWN, FLAT }

class WeightRepository(
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    // Feature D (completeness pass, spec §5): recomputeIfNoOverride() previously only ever ran
    // from AdaptiveBudgetRepository.clearManualOverride() -- a weigh-in never fed back into the
    // adaptive budget at all. Wired at this layer (not WeightViewModel) rather than the
    // ViewModel so any future caller that logs a weigh-in directly through the repository (e.g.
    // a reminder's direct-reply background path, spec §6) gets the recompute for free too. No
    // circular dependency: AdaptiveBudgetRepository depends only on UserProfileDao/WeighInDao/
    // SettingsRepository, never on WeightRepository itself. Nullable/defaulted so every existing
    // test construction of WeightRepository keeps compiling unchanged.
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository? = null
) {
    suspend fun logWeighIn(weightKg: Double): WeighInEntity {
        val date = DayBoundary.logicalDate(clock()).toString()
        val existing = weighInDao.getForDate(date)
        val entity = existing?.copy(weightKg = weightKg) ?: WeighInEntity(date = date, weightKg = weightKg)
        val newId = weighInDao.upsert(entity)
        val result = if (existing != null) entity else entity.copy(id = newId)
        adaptiveBudgetRepository?.recomputeIfNoOverride()
        return result
    }

    /**
     * Feature E (completeness pass, spec §6): the weigh-in reminder's smart-suppression check --
     * "if a weigh-in already exists for today ... the notification is silently skipped" (SPEC
     * §3.15). Reuses the same [DayBoundary] logical-date rule [logWeighIn] itself uses, so a
     * weigh-in logged after midnight but before the 3am boundary still correctly counts as
     * "today" for suppression purposes too.
     */
    suspend fun hasWeighInForToday(): Boolean =
        weighInDao.getForDate(DayBoundary.logicalDate(clock()).toString()) != null

    fun observeRollingAverageSeries(): Flow<List<Pair<LocalDate, Double>>> =
        weighInDao.observeAll().map { entries ->
            RollingAverage.sevenDayRollingAverage(entries.map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) })
        }

    fun observeRawSeries(): Flow<List<Pair<LocalDate, Double>>> =
        weighInDao.observeAll().map { entries -> entries.map { LocalDate.parse(it.date) to it.weightKg } }

    fun observeTotalChangeSinceStart(): Flow<Double?> =
        weighInDao.observeAll().map { entries ->
            if (entries.size < 2) null else entries.last().weightKg - entries.first().weightKg
        }

    fun observeFourWeekTrend(): Flow<TrendDirection?> =
        observeRollingAverageSeries().map { series ->
            if (series.size < 2) return@map null
            val latest = series.last()
            val fourWeeksAgoTarget = latest.first.minusDays(28)
            val reference = series.firstOrNull { !it.first.isBefore(fourWeeksAgoTarget) } ?: series.first()
            if (reference.first == latest.first) return@map null
            val delta = latest.second - reference.second
            when {
                delta <= -0.2 -> TrendDirection.DOWN
                delta >= 0.2 -> TrendDirection.UP
                else -> TrendDirection.FLAT
            }
        }
}
