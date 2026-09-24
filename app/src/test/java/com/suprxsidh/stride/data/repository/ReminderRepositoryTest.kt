package com.suprxsidh.stride.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.reminders.ReminderType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderRepositoryTest {
    private lateinit var db: StrideDatabase
    private lateinit var repository: ReminderRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReminderRepository(db.reminderDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `seedDefaults inserts one weigh-in and three meals matching SPEC defaults`() = runTest {
        val seeded = repository.seedDefaults()

        assertEquals(4, seeded.size)
        val weighIns = seeded.filter { it.type == ReminderType.WEIGH_IN.name }
        assertEquals(1, weighIns.size)
        assertEquals("07:00", weighIns.single().time)

        val meals = seeded.filter { it.type == ReminderType.MEAL.name }.sortedBy { it.time }
        assertEquals(listOf("Breakfast", "Lunch", "Dinner").sorted(), meals.mapNotNull { it.label }.sorted())
        assertEquals(listOf("09:00", "13:30", "20:30"), meals.map { it.time })

        assertTrue(seeded.none { it.type == ReminderType.SNACK.name })
    }

    @Test
    fun `save assigns a real id to a new reminder`() = runTest {
        val saved = repository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))
        assertTrue(saved.id > 0L)
        assertEquals(saved, repository.getById(saved.id))
    }

    @Test
    fun `save with an existing id updates in place rather than inserting a duplicate`() = runTest {
        val saved = repository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))
        repository.save(saved.copy(time = "17:00"))

        assertEquals(1, repository.getAll().size)
        assertEquals("17:00", repository.getById(saved.id)!!.time)
    }

    @Test
    fun `getByType filters correctly`() = runTest {
        repository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))
        repository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))

        assertEquals(1, repository.getByType(ReminderType.MEAL).size)
        assertEquals(1, repository.getByType(ReminderType.SNACK).size)
        assertEquals(0, repository.getByType(ReminderType.WEIGH_IN).size)
    }

    @Test
    fun `delete removes the reminder`() = runTest {
        val saved = repository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))
        repository.delete(saved)
        assertEquals(0, repository.getAll().size)
    }
}
