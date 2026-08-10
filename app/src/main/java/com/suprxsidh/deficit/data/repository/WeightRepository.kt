package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.RollingAverage
import com.suprxsidh.deficit.data.calc.WeighInPoint
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

enum class TrendDirection { UP, DOWN, FLAT }

class WeightRepository(
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    suspend fun logWeighIn(weightKg: Double): WeighInEntity {
        val date = DayBoundary.logicalDate(clock()).toString()
        val existing = weighInDao.getForDate(date)
        val entity = existing?.copy(weightKg = weightKg) ?: WeighInEntity(date = date, weightKg = weightKg)
        val newId = weighInDao.upsert(entity)
        return if (existing != null) entity else entity.copy(id = newId)
    }

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
