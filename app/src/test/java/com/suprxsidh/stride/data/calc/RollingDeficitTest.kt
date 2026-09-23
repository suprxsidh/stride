package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RollingDeficitTest {

    private fun days(start: LocalDate, count: Int): List<LocalDate> =
        (0 until count).map { start.plusDays(it.toLong()) }

    @Test
    fun `a full week under budget every day banks 7 times the daily surplus`() {
        val dates = days(LocalDate.of(2026, 9, 1), 7)
        val consumed = dates.associateWith { 1400 } // 200 under a 1600 budget, every day
        assertEquals(1400, RollingDeficit.compute(dates, budgetKcal = 1600, consumedByDate = consumed))
    }

    @Test
    fun `a gap day with no logged entries counts as a full day of budget banked, not skipped`() {
        val dates = days(LocalDate.of(2026, 9, 1), 3)
        // Only the first and last day have entries -- the middle day was never logged at all.
        val consumed = mapOf(
            dates[0] to 1600, // exactly on budget -> 0 net that day
            dates[2] to 1600
        )
        // Missing middle day: budget - 0 = full 1600 banked.
        assertEquals(1600, RollingDeficit.compute(dates, budgetKcal = 1600, consumedByDate = consumed))
    }

    @Test
    fun `an all-gap window banks a full budget for every single day`() {
        val dates = days(LocalDate.of(2026, 9, 1), 7)
        assertEquals(7 * 1600, RollingDeficit.compute(dates, budgetKcal = 1600, consumedByDate = emptyMap()))
    }

    @Test
    fun `a partial week of only 3 days (new account) still sums correctly over just those dates`() {
        val dates = days(LocalDate.of(2026, 9, 20), 3)
        val consumed = dates.associateWith { 1800 } // 200 over a 1600 budget, every day
        assertEquals(-600, RollingDeficit.compute(dates, budgetKcal = 1600, consumedByDate = consumed))
    }

    @Test
    fun `an empty date list sums to zero`() {
        assertEquals(0, RollingDeficit.compute(emptyList(), budgetKcal = 1600, consumedByDate = emptyMap()))
    }

    @Test
    fun `a net surplus week returns a negative result`() {
        val dates = days(LocalDate.of(2026, 9, 1), 2)
        val consumed = dates.associateWith { 2000 }
        assertEquals(-800, RollingDeficit.compute(dates, budgetKcal = 1600, consumedByDate = consumed))
    }
}
