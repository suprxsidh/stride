package com.suprxsidh.stride.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feature E (completeness pass, spec §6): `AlarmManager` alarms do not survive a device reboot
 * -- this reschedules every reminder in the DB from scratch once the device finishes booting.
 * Registered in the manifest with a `BOOT_COMPLETED` intent-filter; `RECEIVE_BOOT_COMPLETED` is
 * a normal permission the OS grants automatically once declared, no runtime request needed.
 *
 * Reaches its dependencies through the overridable `*Provider` companion vals, same pattern as
 * [ReminderAlarmReceiver] and [com.suprxsidh.stride.health.HealthConnectSyncWorker].
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        // See ReminderAlarmReceiver's identical comment: null under a direct (non-broadcast)
        // test invocation, always real in production.
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                reschedule(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule reminders after boot", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    // Internal (not private): tests call this directly under `runTest` rather than through
    // onReceive()'s goAsync()+detached-CoroutineScope plumbing -- see ReminderAlarmReceiver's
    // identical comment on handleAlarmFired for why.
    internal suspend fun reschedule(context: Context) {
        val reminders = reminderRepositoryProvider(context)?.getAll().orEmpty()
        if (reminders.isNotEmpty()) {
            schedulerProvider(context).rescheduleAll(reminders)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"

        var reminderRepositoryProvider: (Context) -> ReminderRepository? =
            { context -> (context.applicationContext as? StrideApp)?.container?.reminderRepository }
        var schedulerProvider: (Context) -> ReminderScheduler =
            { context -> ReminderScheduler(context) }
        var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    }
}
