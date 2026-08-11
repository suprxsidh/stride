package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
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
class SyncStateDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncStateDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `get returns null before any upsert`() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun `upsert then get returns stored token`() = runTest {
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "token-abc", lastSyncEpochMs = 1_700_000_000_000L))
        val state = dao.get()
        assertEquals("token-abc", state?.hcChangesToken)
    }

    @Test
    fun `second upsert replaces the single row`() = runTest {
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "first", lastSyncEpochMs = 1L))
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "second", lastSyncEpochMs = 2L))
        assertEquals("second", dao.get()?.hcChangesToken)
    }
}
