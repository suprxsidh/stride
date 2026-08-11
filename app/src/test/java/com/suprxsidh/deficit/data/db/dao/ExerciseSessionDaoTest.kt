package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseSessionDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: ExerciseSessionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exerciseSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getByHcRecordId returns null when absent`() = runTest {
        assertNull(dao.getByHcRecordId("hc-1"))
    }

    @Test
    fun `insert then getByHcRecordId returns the session`() = runTest {
        dao.insert(session(hcRecordId = "hc-1"))
        val found = dao.getByHcRecordId("hc-1")
        assertEquals("hc-1", found?.hcRecordId)
        assertEquals(1800, found?.kcalReal)
    }

    @Test
    fun `observeAll orders by startTime descending`() = runTest {
        dao.insert(session(hcRecordId = "hc-1", startTimeEpochMs = 1_000L))
        dao.insert(session(hcRecordId = "hc-2", startTimeEpochMs = 2_000L))

        val all = dao.observeAll().first()

        assertEquals(2, all.size)
        assertEquals("hc-2", all[0].hcRecordId)
        assertEquals("hc-1", all[1].hcRecordId)
    }

    @Test
    fun `upsertByHcRecordId inserts when the record is new`() = runTest {
        dao.upsertByHcRecordId(session(hcRecordId = "hc-1"))

        assertEquals(1, dao.observeAll().first().size)
        assertEquals("hc-1", dao.getByHcRecordId("hc-1")?.hcRecordId)
    }

    @Test
    fun `upsertByHcRecordId updates in place instead of hitting the unique index`() = runTest {
        // hcRecordId carries a unique index and insert() uses the default ABORT strategy, so a
        // second sync (periodic and one-off work are separate chains and can overlap) used to throw
        // SQLiteConstraintException once both saw the same "new" session.
        dao.upsertByHcRecordId(session(hcRecordId = "hc-1"))
        dao.upsertByHcRecordId(session(hcRecordId = "hc-1").copy(kcalReal = 2000, durationMin = 40))

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(2000, all[0].kcalReal)
        assertEquals(40, all[0].durationMin)
    }

    @Test
    fun `upsertByHcRecordId preserves the existing row id when updating`() = runTest {
        dao.upsertByHcRecordId(session(hcRecordId = "hc-1"))
        val originalId = dao.getByHcRecordId("hc-1")!!.id

        dao.upsertByHcRecordId(session(hcRecordId = "hc-1").copy(kcalReal = 999))

        assertEquals(originalId, dao.getByHcRecordId("hc-1")!!.id)
    }

    private fun session(hcRecordId: String, startTimeEpochMs: Long = 1_700_000_000_000L) = ExerciseSessionEntity(
        hcRecordId = hcRecordId,
        date = "2026-08-11",
        exerciseType = "RUNNING",
        startTimeEpochMs = startTimeEpochMs,
        durationMin = 32,
        distanceM = 5200.0,
        avgPaceSecPerKm = 369.0,
        avgHr = 152,
        maxHr = 171,
        kcalReal = 1800,
        kcalCredited = 900
    )
}
