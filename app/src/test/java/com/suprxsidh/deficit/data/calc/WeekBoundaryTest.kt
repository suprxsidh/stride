package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekBoundaryTest {
    // 2024-01-01 is a Monday (fixed reference point, unambiguous to verify).
    @Test
    fun `weekStart and weekEnd for a Monday`() {
        val monday = LocalDate.of(2024, 1, 1)
        assertEquals(monday, WeekBoundary.weekStart(monday))
        assertEquals(LocalDate.of(2024, 1, 7), WeekBoundary.weekEnd(monday))
    }

    @Test
    fun `weekStart and weekEnd for a mid-week day resolve to the same Mon-Sun span`() {
        val thursday = LocalDate.of(2024, 1, 4)
        assertEquals(LocalDate.of(2024, 1, 1), WeekBoundary.weekStart(thursday))
        assertEquals(LocalDate.of(2024, 1, 7), WeekBoundary.weekEnd(thursday))
    }

    @Test
    fun `weekStart and weekEnd for a Sunday resolve to the week that is ending`() {
        val sunday = LocalDate.of(2024, 1, 7)
        assertEquals(LocalDate.of(2024, 1, 1), WeekBoundary.weekStart(sunday))
        assertEquals(sunday, WeekBoundary.weekEnd(sunday))
    }

    @Test
    fun `daysLeftInclusive counts today through Sunday`() {
        assertEquals(7, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 1))) // Monday
        assertEquals(4, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 4))) // Thursday
        assertEquals(1, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 7))) // Sunday
    }
}
