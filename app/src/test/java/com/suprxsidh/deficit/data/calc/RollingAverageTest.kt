package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RollingAverageTest {

    @Test
    fun `single point average equals itself`() {
        val points = listOf(WeighInPoint(LocalDate.of(2026, 8, 1), 80.0))
        val result = RollingAverage.sevenDayRollingAverage(points)
        assertEquals(1, result.size)
        assertEquals(80.0, result[0].second, 0.001)
    }

    @Test
    fun `average only includes points within trailing 7 days`() {
        val points = listOf(
            WeighInPoint(LocalDate.of(2026, 8, 1), 82.0),
            WeighInPoint(LocalDate.of(2026, 8, 5), 81.0),
            WeighInPoint(LocalDate.of(2026, 8, 12), 79.0) // more than 7 days after Aug 1 — falls outside window ending Aug 12
        )
        val result = RollingAverage.sevenDayRollingAverage(points)
        val lastPoint = result.last()
        assertEquals(LocalDate.of(2026, 8, 12), lastPoint.first)
        // window for Aug 12 is Aug 6..Aug 12: only the Aug 12 point itself qualifies
        assertEquals(79.0, lastPoint.second, 0.001)
    }

    @Test
    fun `gaps do not skew the average toward missing days`() {
        val points = listOf(
            WeighInPoint(LocalDate.of(2026, 8, 1), 80.0),
            WeighInPoint(LocalDate.of(2026, 8, 2), 80.0),
            WeighInPoint(LocalDate.of(2026, 8, 7), 78.0)
        )
        val result = RollingAverage.sevenDayRollingAverage(points)
        val lastPoint = result.last()
        assertEquals(LocalDate.of(2026, 8, 7), lastPoint.first)
        // window Aug 1..Aug 7 includes all three logged points, averaged over 3 (not 7)
        assertEquals((80.0 + 80.0 + 78.0) / 3, lastPoint.second, 0.001)
    }
}
