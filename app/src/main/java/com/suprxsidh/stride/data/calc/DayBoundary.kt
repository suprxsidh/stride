package com.suprxsidh.stride.data.calc

import java.time.LocalDate
import java.time.LocalDateTime

object DayBoundary {
    const val BOUNDARY_HOUR = 3

    fun logicalDate(dateTime: LocalDateTime): LocalDate =
        if (dateTime.hour < BOUNDARY_HOUR) dateTime.toLocalDate().minusDays(1) else dateTime.toLocalDate()
}
