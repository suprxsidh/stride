package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.health.HealthDataSource
import com.suprxsidh.stride.health.RemoteWeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectRepositoryTest {

    private class FakeDataSource(
        var caloriesBurned: Int = 0,
        var newWeights: List<RemoteWeightRecord> = emptyList()
    ) : HealthDataSource {
        val written = mutableListOf<Pair<Double, Instant>>()
        val weightSince = mutableListOf<Instant>()
        var lastCaloriesWindow: Pair<Instant, Instant>? = null

        override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int {
            lastCaloriesWindow = since to until
            return caloriesBurned
        }

        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> {
            weightSince.add(since)
            return newWeights.filter { !it.time.isBefore(since) }
        }

        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
            written.add(weightKg to time)
            return "hc-written-${written.size}"
        }
    }

    private class FakeSyncStateDao : SyncStateDao {
        var state: SyncStateEntity? = null
        override suspend fun get() = state
        override suspend fun upsert(state: SyncStateEntity) { this.state = state }
    }

    private class FakeWeighInDao : WeighInDao {
        val rows = mutableListOf<WeighInEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<WeighInEntity>>(emptyList())

        override suspend fun upsert(weighIn: WeighInEntity): Long {
            val stored = if (weighIn.id != 0L) {
                rows.removeAll { it.id == weighIn.id }
                weighIn
            } else {
                weighIn.copy(id = nextId++)
            }
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

    private fun now(): Instant = fixedClock().atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `getTodaysCaloriesBurned reads the window from today's 3am boundary to now`() = runTest {
        val dataSource = FakeDataSource(caloriesBurned = 1400)
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)

        val burned = repo.getTodaysCaloriesBurned()

        assertEquals(1400, burned)
        val expectedStart = LocalDateTime.of(2026, 8, 11, 3, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expectedStart, dataSource.lastCaloriesWindow?.first)
        assertEquals(now(), dataSource.lastCaloriesWindow?.second)
    }

    @Test
    fun `getTodaysCaloriesBurned before the 3am boundary uses the previous logical day's start`() = runTest {
        val earlyClock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 11, 1, 30) }
        val dataSource = FakeDataSource(caloriesBurned = 200)
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), FakeWeighInDao(), earlyClock)

        repo.getTodaysCaloriesBurned()

        val expectedStart = LocalDateTime.of(2026, 8, 10, 3, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expectedStart, dataSource.lastCaloriesWindow?.first)
    }

    @Test
    fun `syncWeighIns pushes unsynced local entries to Health Connect`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 80.0, syncedToHc = false))
        val dataSource = FakeDataSource()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

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
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

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
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.size)
    }

    @Test
    fun `a weigh-in imported before 3am lands on the previous logical day`() = runTest {
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", LocalDateTime.of(2026, 8, 11, 2, 0).atZone(ZoneId.systemDefault()).toInstant(), 79.0))
        )
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals("2026-08-10", weighInDao.getByHcRecordId("hc-w-1")!!.date)
    }

    @Test
    fun `importing a weight for a date that already has an entry updates it in place`() = runTest {
        val weighInDao = FakeWeighInDao()
        val existingId = weighInDao.upsert(
            WeighInEntity(date = "2026-08-11", weightKg = 80.0, syncedToHc = true, hcRecordId = "hc-mine")
        )
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-scale", LocalDateTime.of(2026, 8, 11, 7, 0).atZone(ZoneId.systemDefault()).toInstant(), 79.1))
        )
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.count { it.date == "2026-08-11" })
        val row = weighInDao.getForDate("2026-08-11")!!
        assertEquals(existingId, row.id)
        assertEquals(79.1, row.weightKg, 0.001)
        assertEquals("hc-scale", row.hcRecordId)
        assertTrue(row.syncedToHc)
    }

    @Test
    fun `repeated sync cycles keep importing new weigh-ins`() = runTest {
        val dataSource = FakeDataSource()
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        dataSource.newWeights = listOf(RemoteWeightRecord("hc-w-2", now().minus(Duration.ofHours(2)), 78.9))
        val imported = repo.syncWeighIns()

        assertEquals(1, imported)
        assertEquals(78.9, weighInDao.getByHcRecordId("hc-w-2")!!.weightKg, 0.001)
    }

    @Test
    fun `the weight read window is widened backwards but the stored watermark is not`() = runTest {
        val dataSource = FakeDataSource()
        val syncState = FakeSyncStateDao()
        val repo = HealthConnectRepository(dataSource, syncState, FakeWeighInDao(), fixedClock)

        repo.syncWeighIns()
        repo.syncWeighIns()

        assertEquals(now().toEpochMilli(), syncState.state!!.lastWeightSyncEpochMs)
        assertTrue(dataSource.weightSince.last().isBefore(now().minus(Duration.ofHours(24))))
    }
}
