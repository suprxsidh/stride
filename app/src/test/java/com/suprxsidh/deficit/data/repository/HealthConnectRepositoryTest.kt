package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.health.HealthDataSource
import com.suprxsidh.deficit.health.RemoteExerciseSession
import com.suprxsidh.deficit.health.RemoteWeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class HealthConnectRepositoryTest {

    private class FakeDataSource(
        var sessions: List<RemoteExerciseSession> = emptyList(),
        var newWeights: List<RemoteWeightRecord> = emptyList()
    ) : HealthDataSource {
        val written = mutableListOf<Pair<Double, Instant>>()
        override suspend fun readExerciseSessions(since: Instant) = sessions
        override suspend fun readNewWeightRecords(since: Instant) = newWeights
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
            written.add(weightKg to time)
            return "hc-written-${written.size}"
        }
    }

    private class FakeExerciseSessionDao : ExerciseSessionDao {
        val rows = mutableMapOf<String, ExerciseSessionEntity>()
        private val flow = MutableStateFlow<List<ExerciseSessionEntity>>(emptyList())
        override suspend fun getByHcRecordId(hcRecordId: String) = rows[hcRecordId]
        override suspend fun insert(session: ExerciseSessionEntity): Long {
            rows[session.hcRecordId] = session.copy(id = rows.size + 1L)
            flow.value = rows.values.sortedByDescending { it.startTimeEpochMs }
            return rows[session.hcRecordId]!!.id
        }
        override suspend fun update(session: ExerciseSessionEntity) {
            rows[session.hcRecordId] = session
            flow.value = rows.values.sortedByDescending { it.startTimeEpochMs }
        }
        override fun observeAll(): Flow<List<ExerciseSessionEntity>> = flow
        override suspend fun getLatestForDate(date: String) = rows.values.filter { it.date == date }.maxByOrNull { it.startTimeEpochMs }
    }

    private class FakeSyncStateDao : SyncStateDao {
        var state: SyncStateEntity? = null
        override suspend fun get() = state
        override suspend fun upsert(state: SyncStateEntity) { this.state = state }
    }

    private class FakeWeighInDao : WeighInDao {
        val rows = mutableListOf<WeighInEntity>()
        private val flow = MutableStateFlow<List<WeighInEntity>>(emptyList())
        override suspend fun upsert(weighIn: WeighInEntity): Long {
            rows.removeAll { it.date == weighIn.date }
            val stored = weighIn.copy(id = rows.size + 1L)
            rows.add(stored)
            flow.value = rows.toList()
            return stored.id
        }
        override fun observeAll(): Flow<List<WeighInEntity>> = flow
        override suspend fun getForDate(date: String) = rows.find { it.date == date }
        override suspend fun getByHcRecordId(hcRecordId: String) = rows.find { it.hcRecordId == hcRecordId }
        override suspend fun getUnsyncedToHc() = rows.filter { !it.syncedToHc }
    }

    private val fixedClock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 11, 10, 0) }

    @Test
    fun `syncExerciseSessions inserts new sessions with computed analytics`() = runTest {
        val dataSource = FakeDataSource(
            sessions = listOf(
                RemoteExerciseSession(
                    hcRecordId = "hc-1",
                    exerciseType = "RUNNING",
                    startTime = Instant.parse("2026-08-11T06:00:00Z"),
                    endTime = Instant.parse("2026-08-11T06:32:00Z"),
                    distanceMeters = 5200.0,
                    kcalReal = 380,
                    heartRateSamplesBpm = listOf(140, 150, 160, 171)
                )
            )
        )
        val sessionDao = FakeExerciseSessionDao()
        val repo = HealthConnectRepository(dataSource, sessionDao, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)

        val count = repo.syncExerciseSessions()

        assertEquals(1, count)
        val stored = sessionDao.getByHcRecordId("hc-1")!!
        assertEquals(32, stored.durationMin)
        assertEquals(190, stored.kcalCredited) // 50% of 380, floored
        assertEquals(155, stored.avgHr)
        assertEquals(171, stored.maxHr)
    }

    @Test
    fun `syncExerciseSessions updates an existing session instead of duplicating`() = runTest {
        val dataSource = FakeDataSource(
            sessions = listOf(
                RemoteExerciseSession("hc-1", "RUNNING", Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-11T06:30:00Z"), 5000.0, 350, listOf(150))
            )
        )
        val sessionDao = FakeExerciseSessionDao()
        val repo = HealthConnectRepository(dataSource, sessionDao, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)
        repo.syncExerciseSessions()

        dataSource.sessions = listOf(
            RemoteExerciseSession("hc-1", "RUNNING", Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-11T06:35:00Z"), 5500.0, 400, listOf(155))
        )
        repo.syncExerciseSessions()

        assertEquals(1, sessionDao.rows.size)
        assertEquals(35, sessionDao.rows.getValue("hc-1").durationMin)
    }

    @Test
    fun `syncWeighIns pushes unsynced local entries to Health Connect`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 80.0, syncedToHc = false))
        val dataSource = FakeDataSource()
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

        val count = repo.syncWeighIns()

        assertEquals(1, count)
        assertEquals(1, dataSource.written.size)
        assertTrue(weighInDao.rows.first().syncedToHc)
    }

    @Test
    fun `syncWeighIns imports a new Health Connect weight not seen before`() = runTest {
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", Instant.parse("2026-08-10T05:00:00Z"), 79.4))
        )
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        val imported = weighInDao.getByHcRecordId("hc-w-1")
        assertEquals(79.4, imported!!.weightKg, 0.001)
        assertTrue(imported.syncedToHc)
    }

    @Test
    fun `syncWeighIns does not re-import an already-known Health Connect record`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 79.4, syncedToHc = true, hcRecordId = "hc-w-1"))
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", Instant.parse("2026-08-10T05:00:00Z"), 79.4))
        )
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.size)
    }
}
