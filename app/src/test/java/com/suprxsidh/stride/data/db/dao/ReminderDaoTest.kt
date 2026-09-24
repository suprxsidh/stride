package com.suprxsidh.stride.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.ReminderEntity
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
class ReminderDaoTest {
    private lateinit var db: StrideDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reminderDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `upsert then getById returns the reminder`() = runTest {
        val id = dao.upsert(ReminderEntity(type = "WEIGH_IN", time = "07:00", label = null, enabled = true))
        val result = dao.getById(id)
        assertEquals("07:00", result!!.time)
    }

    @Test
    fun `getById on a missing id returns null`() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun `getByType only returns rows of that type, ordered by time`() = runTest {
        dao.upsert(ReminderEntity(type = "MEAL", time = "20:30", label = "Dinner"))
        dao.upsert(ReminderEntity(type = "MEAL", time = "09:00", label = "Breakfast"))
        dao.upsert(ReminderEntity(type = "WEIGH_IN", time = "07:00", label = null))

        val meals = dao.getByType("MEAL")
        assertEquals(listOf("Breakfast", "Dinner"), meals.map { it.label })
    }

    @Test
    fun `update persists a changed time and enabled flag`() = runTest {
        val id = dao.upsert(ReminderEntity(type = "SNACK", time = "16:00", label = "Snack"))
        val saved = dao.getById(id)!!
        dao.update(saved.copy(time = "17:30", enabled = false))

        val updated = dao.getById(id)!!
        assertEquals("17:30", updated.time)
        assertEquals(false, updated.enabled)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val id = dao.upsert(ReminderEntity(type = "SNACK", time = "16:00", label = "Snack"))
        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun `deleteById removes the row without fetching it first`() = runTest {
        val id = dao.upsert(ReminderEntity(type = "MEAL", time = "12:00", label = "Lunch"))
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun `observeAll emits every row across all types`() = runTest {
        dao.upsert(ReminderEntity(type = "WEIGH_IN", time = "07:00", label = null))
        dao.upsert(ReminderEntity(type = "MEAL", time = "09:00", label = "Breakfast"))
        dao.upsert(ReminderEntity(type = "SNACK", time = "16:00", label = "Snack"))

        assertEquals(3, dao.observeAll().first().size)
    }
}
