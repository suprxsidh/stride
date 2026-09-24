package com.suprxsidh.stride.ui.onboarding

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.ReminderRepository
import com.suprxsidh.stride.reminders.ReminderScheduler
import com.suprxsidh.stride.reminders.ReminderType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSetupViewModelTest {
    private lateinit var db: StrideDatabase
    private lateinit var context: Context
    private lateinit var viewModel: ReminderSetupViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StrideDatabase::class.java).allowMainThreadQueries().build()
        viewModel = ReminderSetupViewModel(ReminderRepository(db.reminderDao()))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `starts with the SPEC default weigh-in and meal times`() {
        assertEquals("07:00", viewModel.weighInTime)
        assertEquals(
            listOf("Breakfast" to "09:00", "Lunch" to "13:30", "Dinner" to "20:30"),
            viewModel.mealRows.map { it.label to it.time }
        )
        assertTrue(viewModel.snackRows.isEmpty())
    }

    @Test
    fun `addMeal and removeMeal mutate the meal list`() {
        val before = viewModel.mealRows.size
        viewModel.addMeal()
        assertEquals(before + 1, viewModel.mealRows.size)

        val added = viewModel.mealRows.last()
        viewModel.removeMeal(added)
        assertEquals(before, viewModel.mealRows.size)
    }

    @Test
    fun `addSnack and removeSnack mutate the snack list`() {
        viewModel.addSnack()
        assertEquals(1, viewModel.snackRows.size)

        viewModel.removeSnack(viewModel.snackRows.single())
        assertTrue(viewModel.snackRows.isEmpty())
    }

    @Test
    fun `commit rejects an invalid weigh-in time and persists nothing`() = runTest {
        viewModel.weighInTime = "not a time"
        val scheduler = ReminderScheduler(context)

        val success = viewModel.commit(scheduler)

        assertEquals(false, success)
        assertNotNull(viewModel.error)
        assertEquals(0, ReminderRepository(db.reminderDao()).getAll().size)
    }

    @Test
    fun `commit rejects an invalid meal time`() = runTest {
        viewModel.mealRows.first().time = "25:99"

        val success = viewModel.commit(ReminderScheduler(context))

        assertEquals(false, success)
        assertNotNull(viewModel.error)
    }

    @Test
    fun `commit persists every row and schedules an alarm for each`() = runTest {
        viewModel.addSnack()
        val expectedCount = 1 /* weigh-in */ + viewModel.mealRows.size + viewModel.snackRows.size

        val success = viewModel.commit(ReminderScheduler(context))

        assertTrue(success)
        assertNull(viewModel.error)
        val saved = ReminderRepository(db.reminderDao()).getAll()
        assertEquals(expectedCount, saved.size)
        assertEquals(1, saved.count { it.type == ReminderType.WEIGH_IN.name })

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(expectedCount, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
    }
}
