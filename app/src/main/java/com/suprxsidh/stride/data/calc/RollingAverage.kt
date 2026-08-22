package com.suprxsidh.stride.data.calc

import java.time.LocalDate

data class WeighInPoint(val date: LocalDate, val weightKg: Double)

object RollingAverage {

    fun sevenDayRollingAverage(points: List<WeighInPoint>): List<Pair<LocalDate, Double>> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { point ->
            val windowStart = point.date.minusDays(6)
            val window = sorted.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(point.date) }
            point.date to window.map { it.weightKg }.average()
        }
    }
}
