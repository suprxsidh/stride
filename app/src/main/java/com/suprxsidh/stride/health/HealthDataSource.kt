package com.suprxsidh.stride.health

import java.time.Instant

data class RemoteExerciseSession(
    val hcRecordId: String,
    val exerciseType: String,
    val startTime: Instant,
    val endTime: Instant,
    val distanceMeters: Double?,
    val kcalReal: Int,
    val heartRateSamplesBpm: List<Int>
)

data class RemoteWeightRecord(
    val hcRecordId: String,
    val time: Instant,
    val weightKg: Double
)

interface HealthDataSource {
    suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession>
    suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord>
    suspend fun writeWeightRecord(weightKg: Double, time: Instant): String
}
