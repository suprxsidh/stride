package com.suprxsidh.stride.health

import java.time.Instant

data class RemoteWeightRecord(
    val hcRecordId: String,
    val time: Instant,
    val weightKg: Double
)

interface HealthDataSource {
    /** Sum of Health Connect's TotalCaloriesBurnedRecord (basal + active) in [since, until]. */
    suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int
    suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord>
    suspend fun writeWeightRecord(weightKg: Double, time: Instant): String
}
