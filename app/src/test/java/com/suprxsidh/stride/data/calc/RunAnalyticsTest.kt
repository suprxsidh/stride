package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunAnalyticsTest {
    @Test
    fun `avgPaceSecPerKm computes seconds per km`() {
        // 32 min for 5.2 km => 1920s / 5.2km = 369.23 s/km
        val pace = RunAnalytics.avgPaceSecPerKm(durationMin = 32, distanceM = 5200.0)
        assertEquals(369.23, pace!!, 0.1)
    }

    @Test
    fun `avgPaceSecPerKm returns null for zero distance`() {
        assertNull(RunAnalytics.avgPaceSecPerKm(durationMin = 30, distanceM = 0.0))
    }

    @Test
    fun `avgHeartRate averages and rounds`() {
        assertEquals(150, RunAnalytics.avgHeartRate(listOf(140, 150, 160)))
    }

    @Test
    fun `avgHeartRate returns null for empty samples`() {
        assertNull(RunAnalytics.avgHeartRate(emptyList()))
    }

    @Test
    fun `maxHeartRate returns the peak sample`() {
        assertEquals(171, RunAnalytics.maxHeartRate(listOf(140, 171, 160)))
    }

    @Test
    fun `maxHeartRate returns null for empty samples`() {
        assertNull(RunAnalytics.maxHeartRate(emptyList()))
    }
}
