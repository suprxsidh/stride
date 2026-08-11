package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.CalorieMath
import com.suprxsidh.deficit.data.calc.RunAnalytics
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.health.HealthDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthConnectRepository(
    private val dataSource: HealthDataSource,
    private val exerciseSessionDao: ExerciseSessionDao,
    private val syncStateDao: SyncStateDao,
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeExerciseSessions(): Flow<List<ExerciseSessionEntity>> = exerciseSessionDao.observeAll()

    suspend fun getLatestSessionForDate(date: String): ExerciseSessionEntity? =
        exerciseSessionDao.getLatestForDate(date)

    suspend fun syncExerciseSessions(): Int {
        val state = syncStateDao.get()
        val since = state?.lastSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: clock().minusDays(30).atZone(ZoneId.systemDefault()).toInstant()

        val remoteSessions = dataSource.readExerciseSessions(since)
        var count = 0
        for (remote in remoteSessions) {
            val durationMin = Duration.between(remote.startTime, remote.endTime).toMinutes().toInt()
            val avgPace = remote.distanceMeters?.let { RunAnalytics.avgPaceSecPerKm(durationMin, it) }
            val entity = ExerciseSessionEntity(
                hcRecordId = remote.hcRecordId,
                date = remote.startTime.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter),
                exerciseType = remote.exerciseType,
                startTimeEpochMs = remote.startTime.toEpochMilli(),
                durationMin = durationMin,
                distanceM = remote.distanceMeters,
                avgPaceSecPerKm = avgPace,
                avgHr = RunAnalytics.avgHeartRate(remote.heartRateSamplesBpm),
                maxHr = RunAnalytics.maxHeartRate(remote.heartRateSamplesBpm),
                kcalReal = remote.kcalReal,
                kcalCredited = CalorieMath.creditedExerciseKcal(remote.kcalReal)
            )
            val existing = exerciseSessionDao.getByHcRecordId(remote.hcRecordId)
            if (existing != null) {
                exerciseSessionDao.update(entity.copy(id = existing.id))
            } else {
                exerciseSessionDao.insert(entity)
            }
            count++
        }

        syncStateDao.upsert(
            SyncStateEntity(
                id = 1,
                hcChangesToken = state?.hcChangesToken,
                lastSyncEpochMs = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        return count
    }

    suspend fun syncWeighIns(): Int {
        var count = 0
        for (unsynced in weighInDao.getUnsyncedToHc()) {
            val time = LocalDateTime.parse(unsynced.date + "T07:00:00").atZone(ZoneId.systemDefault()).toInstant()
            val hcId = dataSource.writeWeightRecord(unsynced.weightKg, time)
            weighInDao.upsert(unsynced.copy(syncedToHc = true, hcRecordId = hcId))
            count++
        }

        val lastSync = syncStateDao.get()?.lastSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: clock().minusDays(30).atZone(ZoneId.systemDefault()).toInstant()
        for (remote in dataSource.readNewWeightRecords(lastSync)) {
            if (weighInDao.getByHcRecordId(remote.hcRecordId) != null) continue
            val date = remote.time.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
            weighInDao.upsert(
                WeighInEntity(date = date, weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
            )
            count++
        }
        return count
    }
}
