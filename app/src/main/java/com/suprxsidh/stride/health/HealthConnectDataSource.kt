package com.suprxsidh.stride.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int {
        return client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, until)
            )
        ).records.sumOf { it.energy.inKilocalories }.toInt()
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
