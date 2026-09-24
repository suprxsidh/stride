package com.suprxsidh.stride.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Feature E (completeness pass, spec §6): scheduling-math + AlarmManager-wiring coverage using
 * Robolectric's `ShadowAlarmManager` -- per the task brief, this verifies next-fire-time
 * *computation* and that the right shadow method gets called with the right time, not real OS
 * delivery (which Robolectric can't simulate and this task explicitly can't verify on-device).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private val fixedNow = LocalDateTime.of(2026, 9, 24, 12, 0) // a Thursday, noon

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = Shadows.shadowOf(alarmManager)
        // API 34 (>= S) defaults to false unless told otherwise -- most tests below want the
        // "happy path" exact-alarm grant, matching this app's manifest-declared permission.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun scheduler() = ReminderScheduler(context, clock = { fixedNow })

    // --- Pure scheduling math (nextTriggerAtMillis) ---------------------------------------

    @Test
    fun `weigh-in fires at exactly its configured time later today`() {
        val reminder = ReminderEntity(id = 1, type = ReminderType.WEIGH_IN.name, time = "18:00")
        val expected = LocalDateTime.of(2026, 9, 24, 18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, fixedNow))
    }

    @Test
    fun `weigh-in rolls to tomorrow when today's time has already passed`() {
        val reminder = ReminderEntity(id = 1, type = ReminderType.WEIGH_IN.name, time = "07:00")
        val expected = LocalDateTime.of(2026, 9, 25, 7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, fixedNow))
    }

    @Test
    fun `meal fires 45 minutes before its configured time`() {
        val reminder = ReminderEntity(id = 1, type = ReminderType.MEAL.name, time = "20:30")
        val expected = LocalDateTime.of(2026, 9, 24, 19, 45).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, fixedNow))
    }

    @Test
    fun `meal lead time can roll the fire time to a different day than the meal time itself`() {
        // Meal at 00:20 minus 45 min lead time = 23:35 the PREVIOUS day -- worth locking in that
        // the lead-time subtraction happens before the "already passed today" comparison, not after.
        val reminder = ReminderEntity(id = 1, type = ReminderType.MEAL.name, time = "00:20")
        val now = LocalDateTime.of(2026, 9, 24, 10, 0)
        val expected = LocalDateTime.of(2026, 9, 24, 23, 35).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, now))
    }

    @Test
    fun `snack fires at exactly its configured time, no lead time`() {
        val reminder = ReminderEntity(id = 1, type = ReminderType.SNACK.name, time = "16:00")
        val expected = LocalDateTime.of(2026, 9, 24, 16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, fixedNow))
    }

    @Test
    fun `a fire time exactly equal to now rolls to tomorrow, not an immediate repeat`() {
        val reminder = ReminderEntity(id = 1, type = ReminderType.WEIGH_IN.name, time = "12:00")
        val expected = LocalDateTime.of(2026, 9, 25, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, scheduler().nextTriggerAtMillis(reminder, fixedNow))
    }

    // --- AlarmManager wiring -----------------------------------------------------------------

    @Test
    fun `schedule sets an exact-and-allow-while-idle alarm at the computed trigger time`() {
        val reminder = ReminderEntity(id = 5, type = ReminderType.WEIGH_IN.name, time = "18:00")
        scheduler().schedule(reminder)

        // peekNextScheduledAlarm (not getNextScheduledAlarm) so this inspection doesn't consume
        // the queued alarm out from under a later assertion in the same test.
        val alarm = shadowAlarmManager.peekNextScheduledAlarm()
        assertTrue(alarm != null)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm!!.type)
        assertEquals(scheduler().nextTriggerAtMillis(reminder, fixedNow), alarm.triggerAtTime)
    }

    @Test
    fun `schedule falls back to an inexact alarm when exact-alarm scheduling isn't allowed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val reminder = ReminderEntity(id = 5, type = ReminderType.WEIGH_IN.name, time = "18:00")
        scheduler().schedule(reminder)

        // Still schedules SOMETHING (never silently drops the reminder) even without the
        // special-access grant.
        val alarm = shadowAlarmManager.peekNextScheduledAlarm()
        assertTrue(alarm != null)
        assertEquals(scheduler().nextTriggerAtMillis(reminder, fixedNow), alarm!!.triggerAtTime)
    }

    @Test
    fun `schedule on a disabled reminder cancels rather than schedules`() {
        val reminder = ReminderEntity(id = 5, type = ReminderType.WEIGH_IN.name, time = "18:00", enabled = false)
        // Pre-populate a pending alarm, as if it had been scheduled while still enabled.
        scheduler().schedule(reminder.copy(enabled = true))
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        scheduler().schedule(reminder)

        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun `cancel removes a previously scheduled alarm for that reminder id`() {
        val reminder = ReminderEntity(id = 7, type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast")
        scheduler().schedule(reminder)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        scheduler().cancel(reminder.id)

        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun `rescheduleAll schedules every reminder in the list`() {
        val reminders = listOf(
            ReminderEntity(id = 1, type = ReminderType.WEIGH_IN.name, time = "07:00"),
            ReminderEntity(id = 2, type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"),
            ReminderEntity(id = 3, type = ReminderType.SNACK.name, time = "16:00", label = "Snack")
        )
        scheduler().rescheduleAll(reminders)

        assertEquals(3, shadowAlarmManager.scheduledAlarms.size)
    }
}
