package com.suprxsidh.deficit.data.calc

object RunAnalytics {
    fun avgPaceSecPerKm(durationMin: Int, distanceM: Double): Double? {
        if (distanceM <= 0.0) return null
        val km = distanceM / 1000.0
        return (durationMin * 60.0) / km
    }

    fun avgHeartRate(samplesBpm: List<Int>): Int? {
        if (samplesBpm.isEmpty()) return null
        return samplesBpm.average().let { Math.round(it).toInt() }
    }

    fun maxHeartRate(samplesBpm: List<Int>): Int? {
        return samplesBpm.maxOrNull()
    }
}
