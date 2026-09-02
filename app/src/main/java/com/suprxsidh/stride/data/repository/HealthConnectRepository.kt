package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.health.HealthDataSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface WeighInSyncSource {
    suspend fun syncWeighIns(): Int
}

class HealthConnectRepository(
    private val dataSource: HealthDataSource,
    private val syncStateDao: SyncStateDao,
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : WeighInSyncSource {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Calories burned so far in today's logical day (the same 3am boundary food entries and
     * weigh-ins use). Health Connect's TotalCaloriesBurnedRecord already includes both basal and
     * active burn, so this doubles as a live "how many calories do I have today" figure without a
     * separate per-exercise credit calculation.
     */
    suspend fun getTodaysCaloriesBurned(): Int {
        val startOfDay = DayBoundary.logicalDate(clock()).atTime(3, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val now = clock().atZone(ZoneId.systemDefault()).toInstant()
        return dataSource.readTotalCaloriesBurned(startOfDay, now)
    }

    override suspend fun syncWeighIns(): Int {
        var count = 0
        for (unsynced in weighInDao.getUnsyncedToHc()) {
            val time = LocalDateTime.parse(unsynced.date + "T07:00:00").atZone(ZoneId.systemDefault()).toInstant()
            val hcId = dataSource.writeWeightRecord(unsynced.weightKg, time)
            weighInDao.upsert(unsynced.copy(syncedToHc = true, hcRecordId = hcId))
            count++
        }

        val since = syncStateDao.get()?.lastWeightSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: firstSyncFloor()
        val watermark = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        for (remote in dataSource.readNewWeightRecords(readSince(since))) {
            if (weighInDao.getByHcRecordId(remote.hcRecordId) != null) continue
            val date = logicalDateOf(remote.time)
            val existing = weighInDao.getForDate(date)
            if (existing != null) {
                weighInDao.upsert(
                    existing.copy(weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
                )
            } else {
                weighInDao.upsert(
                    WeighInEntity(date = date, weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
                )
            }
            count++
        }

        syncStateDao.setWeightSyncWatermark(watermark)
        return count
    }

    private fun firstSyncFloor(): Instant =
        clock().minusDays(FIRST_SYNC_LOOKBACK_DAYS).atZone(ZoneId.systemDefault()).toInstant()

    /**
     * Widens the *read* window (never the stored watermark) backwards. Samsung Health publishes a
     * completed record into Health Connect 30-60 minutes after the fact, and other providers batch
     * even later -- with the watermark as the exact left edge, anything published after the sync
     * that recorded it would fall before the next window and be dropped forever. Weigh-ins are
     * deduped on their Health Connect record id, so re-reading overlapping time is idempotent.
     */
    private fun readSince(since: Instant): Instant = since.minus(SYNC_LOOKBACK)

    /**
     * Health Connect timestamps must go through the same 3am day boundary the rest of the app
     * uses, otherwise a weigh-in between midnight and 3am is stamped with the next calendar day
     * and never matches "today".
     */
    private fun logicalDateOf(instant: Instant): String =
        DayBoundary.logicalDate(instant.atZone(ZoneId.systemDefault()).toLocalDateTime()).format(dateFormatter)

    private companion object {
        val SYNC_LOOKBACK: Duration = Duration.ofHours(48)
        const val FIRST_SYNC_LOOKBACK_DAYS = 30L
    }
}
