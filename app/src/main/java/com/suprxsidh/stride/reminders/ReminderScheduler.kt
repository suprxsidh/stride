package com.suprxsidh.stride.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Feature E (completeness pass, spec §6): `AlarmManager` exact alarms only -- SPEC §4.1 is
 * explicit that WorkManager is unreliable for time-of-day reminders on the target OEM (Vivo/
 * OriginOS) and is reserved for Health Connect sync only. Uses `setExactAndAllowWhileIdle`
 * (requires `SCHEDULE_EXACT_ALARM`, requested in the manifest) so a reminder still fires while
 * the device is dozing.
 *
 * One [ReminderAlarmReceiver] handles every reminder type; this class only computes trigger
 * times and builds/cancels the matching [PendingIntent]s. [nextTriggerAtMillis] is pulled out as
 * its own function (rather than inlined into [schedule]) specifically so scheduling math can be
 * unit-tested without touching a real/shadow `AlarmManager` at all.
 */
class ReminderScheduler(
    private val context: Context,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    /**
     * Schedules (or, if disabled, cancels) the next fire of [reminder]. Idempotent: calling this
     * twice for the same reminder id just replaces the pending alarm (same request code).
     */
    fun schedule(reminder: ReminderEntity) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = nextTriggerAtMillis(reminder)
        val pendingIntent = alarmPendingIntent(reminder.id)
        try {
            // SCHEDULE_EXACT_ALARM is requested in the manifest (SPEC §4.1), but on API 31+ it's
            // a "special access" the user can still revoke/never grant in system settings --
            // this task's onboarding only wires POST_NOTIFICATIONS, not a settings deep-link for
            // this permission (flagged in the final report as a known gap, same category as
            // "unverified on a real device"). Falling back to an inexact-but-idle-tolerant alarm
            // rather than silently scheduling nothing, or crashing, if it's ever not granted.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to schedule an exact alarm for reminder ${reminder.id}", e)
        }
    }

    /** Reschedules every reminder in [reminders] -- used on boot and defensively on cold start. */
    fun rescheduleAll(reminders: List<ReminderEntity>) {
        reminders.forEach { schedule(it) }
    }

    fun cancel(reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(reminderId))
    }

    /**
     * The next epoch-millis at which [reminder] should fire, given [now] (defaults to this
     * scheduler's clock). MEAL fires [MEAL_LEAD_TIME_MINUTES] before `reminder.time`; WEIGH_IN
     * and SNACK fire exactly at it. If that time-of-day has already passed today, rolls to
     * tomorrow -- this is also exactly the reschedule-on-receipt math (see
     * [ReminderAlarmReceiver]): the receiver always reschedules by calling [schedule] again,
     * which lands here with `now` just after the alarm fired, so "already passed today" always
     * rolls correctly to +24h without a separate "+24h" code path to keep in sync with this one.
     */
    fun nextTriggerAtMillis(reminder: ReminderEntity, now: LocalDateTime = clock()): Long {
        val rawTime = LocalTime.parse(reminder.time)
        val fireTime = if (reminder.type == ReminderType.MEAL.name) {
            rawTime.minusMinutes(MEAL_LEAD_TIME_MINUTES.toLong())
        } else {
            rawTime
        }
        var target = LocalDateTime.of(now.toLocalDate(), fireTime)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun alarmPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_ALARM_FIRED
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(context, alarmRequestCode(reminderId), intent, immutableFlags())
    }

    companion object {
        private const val TAG = "ReminderScheduler"

        /** Spec §6: "fire a lead-time (default 45 min) before the meal time." Not itself stated
         * as user-editable (unlike the meal-time list), so kept as a constant rather than a new
         * per-reminder or per-settings field -- see the comment on [ReminderEntity.time]. */
        const val MEAL_LEAD_TIME_MINUTES = 45

        /** Distinct request-code space from [ReminderNotificationBuilder]'s reply/action
         * PendingIntents (which use their own offset) so cancelling an alarm never accidentally
         * matches a notification-action PendingIntent for the same reminder id. */
        fun alarmRequestCode(reminderId: Long): Int = (reminderId * 10 + 1).toInt()

        fun immutableFlags(): Int {
            val mutabilityFlag = 0 // alarm-fired intents carry no RemoteInput results; always immutable.
            return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or mutabilityFlag
        }

        fun mutableFlags(): Int {
            val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        }
    }
}
