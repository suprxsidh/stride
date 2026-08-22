package com.suprxsidh.stride.data.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WeekBoundary {
    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(6)

    fun daysLeftInclusive(date: LocalDate): Int =
        ChronoUnit.DAYS.between(date, weekEnd(date)).toInt() + 1
}
