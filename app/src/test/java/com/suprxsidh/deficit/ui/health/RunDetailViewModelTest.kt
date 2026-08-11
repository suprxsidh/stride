package com.suprxsidh.deficit.ui.health

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.health.HealthDataSource
import com.suprxsidh.deficit.health.RemoteExerciseSession
import com.suprxsidh.deficit.health.RemoteWeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase

    private object NoOpDataSource : HealthDataSource {
        override suspend fun readExerciseSessions(since: Instant) = emptyList<RemoteExerciseSession>()
        override suspend fun readNewWeightRecords(since: Instant) = emptyList<RemoteWeightRecord>()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant) = ""
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `paceTrend excludes sessions without distance and sorts ascending by date`() = runTest {
        db.exerciseSessionDao().insert(session("hc-1", date = "2026-08-05", startMs = 5, pace = 380.0))
        db.exerciseSessionDao().insert(session("hc-2", date = "2026-08-11", startMs = 11, pace = 360.0))
        db.exerciseSessionDao().insert(session("hc-3", date = "2026-08-08", startMs = 8, pace = null))

        val repo = HealthConnectRepository(NoOpDataSource, db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao())
        val viewModel = RunDetailViewModel(repo)
        backgroundScope.launch { viewModel.paceTrend.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.paceTrend.value.size)
        assertEquals("2026-08-05", viewModel.paceTrend.value[0].first.toString())
        assertEquals("2026-08-11", viewModel.paceTrend.value[1].first.toString())
    }

    private fun session(hcRecordId: String, date: String, startMs: Long, pace: Double?) = ExerciseSessionEntity(
        hcRecordId = hcRecordId, date = date, exerciseType = "RUNNING", startTimeEpochMs = startMs,
        durationMin = 30, distanceM = if (pace != null) 5000.0 else null, avgPaceSecPerKm = pace,
        avgHr = 150, maxHr = 170, kcalReal = 300, kcalCredited = 150
    )
}
