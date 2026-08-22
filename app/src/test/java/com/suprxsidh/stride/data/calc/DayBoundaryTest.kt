package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayBoundaryTest {

    @Test
    fun `entry logged at 11pm belongs to that calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 10, 23, 0)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged at 1am belongs to the previous calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 1, 0)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged exactly at 3am belongs to the new calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 3, 0)
        assertEquals(LocalDate.of(2026, 8, 11), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged at 2_59am belongs to the previous calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 2, 59)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }
}
