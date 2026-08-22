package com.suprxsidh.stride.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession> {
        val now = Instant.now()
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, now)
            )
        ).records

        return sessions.map { session ->
            val range = TimeRangeFilter.between(session.startTime, session.endTime)

            val distanceMeters = client.readRecords(
                ReadRecordsRequest(recordType = DistanceRecord::class, timeRangeFilter = range)
            ).records.sumOf { it.distance.inMeters }.takeIf { it > 0.0 }

            val heartRateSamples = client.readRecords(
                ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range)
            ).records.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }

            // Real calorie burn: sum of TotalCaloriesBurnedRecord in the session window.
            // Fall back to 0 if the watch/Samsung Health didn't report calories for this session.
            val kcalReal = client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = range
                )
            ).records.sumOf { it.energy.inKilocalories }.toInt()

            RemoteExerciseSession(
                hcRecordId = session.metadata.id,
                exerciseType = session.exerciseType.toString(),
                startTime = session.startTime,
                endTime = session.endTime,
                distanceMeters = distanceMeters,
                kcalReal = kcalReal,
                heartRateSamplesBpm = heartRateSamples
            )
        }
    }

    override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, Instant.now())
            )
        ).records
        return records.map { RemoteWeightRecord(it.metadata.id, it.time, it.weight.inKilograms) }
    }

    override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(time)
        val response = client.insertRecords(
            listOf(
                WeightRecord(
                    metadata = Metadata.manualEntry(),
                    weight = Mass.kilograms(weightKg),
                    time = time,
                    zoneOffset = zoneOffset
                )
            )
        )
        return response.recordIdsList.first()
    }
}
