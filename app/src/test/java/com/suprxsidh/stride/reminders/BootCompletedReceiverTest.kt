package com.suprxsidh.stride.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.data.repository.ReminderRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Feature E (completeness pass, spec §6): alarms don't survive a reboot -- this locks in that
 * [BootCompletedReceiver.reschedule] actually re-schedules every stored reminder. Calls
 * [BootCompletedReceiver.reschedule] directly (bypassing `onReceive()`'s `goAsync()` plumbing),
 * same rationale as [ReminderAlarmReceiverTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompletedReceiverTest {
    private lateinit var db: StrideDatabase
    private lateinit var context: Context
    private lateinit var reminderRepository: ReminderRepository
    private val receiver = BootCompletedReceiver()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StrideDatabase::class.java).allowMainThreadQueries().build()
        reminderRepository = ReminderRepository(db.reminderDao())

        BootCompletedReceiver.reminderRepositoryProvider = { reminderRepository }
        BootCompletedReceiver.schedulerProvider = { ReminderScheduler(it) }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        db.close()
        BootCompletedReceiver.reminderRepositoryProvider =
            { c -> (c.applicationContext as? com.suprxsidh.stride.StrideApp)?.container?.reminderRepository }
        BootCompletedReceiver.schedulerProvider = { ReminderScheduler(it) }
    }

    private fun shadowAlarmManager() =
        Shadows.shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    @Test
    fun `reschedule schedules an alarm for every stored reminder`() = runTest {
        reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))
        reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))
        reminderRepository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))

        receiver.reschedule(context)

        assertEquals(3, shadowAlarmManager().scheduledAlarms.size)
    }

    @Test
    fun `reschedule does nothing when there are no stored reminders`() = runTest {
        receiver.reschedule(context)

        assertEquals(0, shadowAlarmManager().scheduledAlarms.size)
    }

    @Test
    fun `reschedule skips a disabled reminder rather than scheduling it`() = runTest {
        reminderRepository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack", enabled = false))

        receiver.reschedule(context)

        assertEquals(0, shadowAlarmManager().scheduledAlarms.size)
    }
}
