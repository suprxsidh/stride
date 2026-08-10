package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
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
class WeighInDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: WeighInDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.weighInDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getForDate returns the weigh-in`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 81.4))
        val result = dao.getForDate("2026-08-10")
        assertEquals(81.4, result!!.weightKg, 0.001)
    }

    @Test
    fun `getForDate on a day with no entry returns null`() = runTest {
        assertNull(dao.getForDate("2099-01-01"))
    }

    @Test
    fun `observeAll returns entries ordered by date ascending`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-11", weightKg = 81.0))
        dao.upsert(WeighInEntity(date = "2026-08-09", weightKg = 82.0))
        val all = dao.observeAll().first()
        assertEquals(listOf("2026-08-09", "2026-08-11"), all.map { it.date })
    }
}
