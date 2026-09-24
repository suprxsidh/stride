package com.suprxsidh.stride.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.repository.GeminiEstimateResult
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.ReminderRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feature E (completeness pass, spec §6): the single [BroadcastReceiver] handling all three
 * reminder types, by [EXTRA_REMINDER_ID] + the [com.suprxsidh.stride.data.db.entity.ReminderEntity]'s
 * own `type` column, per the design doc's explicit "one BroadcastReceiver... rescheduling itself
 * +24h on receipt" instruction (rather than one receiver per type).
 *
 * Two entry points, disambiguated by [Intent.getAction]:
 * - [ACTION_ALARM_FIRED]: the scheduled alarm went off. Posts (or, for weigh-in, smart-
 *   suppresses) the reminder notification, then always reschedules for the next occurrence --
 *   see [ReminderScheduler.nextTriggerAtMillis]'s doc for why "always reschedule" and
 *   "roll to +24h" are the same code path.
 * - [ACTION_DIRECT_REPLY]: the user replied inline from the notification (never opening the
 *   app). Parses the reply and logs it via the existing [WeightRepository]/[GeminiFoodRepository]
 *   paths.
 *
 * Uses [goAsync] + a background coroutine because both paths do real suspend work (Room queries,
 * a live Gemini network call) that must not block the receiver's normal ~10s execution window in
 * a way that gets it killed before finishing, but also must not block the calling thread.
 *
 * All real dependencies are reached through the `*Provider` companion vals below rather than a
 * direct `(context.applicationContext as StrideApp).container` cast inline -- same "overridable
 * seam for tests" pattern [com.suprxsidh.stride.health.HealthConnectSyncWorker] already
 * established, so tests can substitute an in-memory-DB-backed repository instead of touching the
 * real app-scoped singleton database.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0L) return
        val action = intent.action ?: return
        val appContext = context.applicationContext
        // Platform type explicitly nullable: under a real broadcast dispatch this is always a
        // real PendingResult, but a test invoking onReceive() directly (rather than through a
        // real broadcast) leaves the framework's internal mPendingResult unset, so goAsync()
        // returns null -- the safe call below keeps that path from NPEing.
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                when (action) {
                    ACTION_ALARM_FIRED -> handleAlarmFired(appContext, reminderId)
                    ACTION_DIRECT_REPLY -> handleDirectReply(appContext, reminderId, intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle reminder broadcast (action=$action, reminderId=$reminderId)", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    // Internal (not private): tests call these directly under `runTest` rather than through
    // onReceive()'s goAsync()+detached-CoroutineScope plumbing, which can't be reliably awaited
    // from a test -- Room's own suspend DAO calls genuinely dispatch onto a separate real
    // executor thread even under Robolectric, so there is no "it all runs synchronously" shortcut
    // for the outer coroutine to rely on.
    internal suspend fun handleAlarmFired(context: Context, reminderId: Long) {
        val reminderRepository = reminderRepositoryProvider(context) ?: return
        val reminder = reminderRepository.getById(reminderId) ?: return
        val notificationManager = notificationManagerProvider(context)

        when (ReminderType.valueOf(reminder.type)) {
            ReminderType.WEIGH_IN -> {
                // Smart suppression (SPEC §3.15): never remind someone to do a thing they
                // already did -- silently skip firing if today's weigh-in already exists.
                val alreadyLoggedToday = weightRepositoryProvider(context)?.hasWeighInForToday() ?: false
                if (!alreadyLoggedToday) {
                    notificationManager.notify(
                        ReminderNotificationBuilder.notificationId(reminder.id),
                        ReminderNotificationBuilder.weighInNotification(context, reminder)
                    )
                }
            }
            ReminderType.MEAL -> notificationManager.notify(
                ReminderNotificationBuilder.notificationId(reminder.id),
                ReminderNotificationBuilder.mealOrSnackNotification(context, reminder, NotificationChannels.MEAL)
            )
            ReminderType.SNACK -> notificationManager.notify(
                ReminderNotificationBuilder.notificationId(reminder.id),
                ReminderNotificationBuilder.mealOrSnackNotification(context, reminder, NotificationChannels.SNACK)
            )
        }

        // Alarms don't repeat themselves -- every fire reschedules its own next occurrence.
        schedulerProvider(context).schedule(reminder)
    }

    internal suspend fun handleDirectReply(context: Context, reminderId: Long, intent: Intent) {
        val reminderRepository = reminderRepositoryProvider(context) ?: return
        val reminder = reminderRepository.getById(reminderId) ?: return
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ReminderNotificationBuilder.KEY_REPLY_TEXT)
            ?.toString() ?: return
        val notificationManager = notificationManagerProvider(context)

        when (ReminderType.valueOf(reminder.type)) {
            ReminderType.WEIGH_IN -> {
                val weightKg = ReminderReplyParser.parseWeightKg(replyText) ?: return
                val weightRepository = weightRepositoryProvider(context) ?: return
                // logWeighIn also triggers Feature D's adaptive-budget recompute for free (see
                // WeightRepository's constructor comment) and would write to Health Connect the
                // same way a manually-typed weigh-in in WeightScreen already does.
                weightRepository.logWeighIn(weightKg)
                notificationManager.cancel(ReminderNotificationBuilder.notificationId(reminder.id))
            }
            ReminderType.MEAL, ReminderType.SNACK -> {
                val description = ReminderReplyParser.parseMealDescription(replyText) ?: return
                val geminiFoodRepository = geminiFoodRepositoryProvider(context) ?: return
                notificationManager.cancel(ReminderNotificationBuilder.notificationId(reminder.id))
                when (val result = geminiFoodRepository.estimateMeal(description, null)) {
                    is GeminiEstimateResult.Success -> {
                        val entry = geminiFoodRepository.confirmEstimate(result.estimate)
                        notificationManager.notify(
                            ReminderNotificationBuilder.followUpNotificationId(reminder.id),
                            ReminderNotificationBuilder.mealFollowUpNotification(context, reminder, entry.name, entry.bufferedKcal)
                        )
                    }
                    is GeminiEstimateResult.Failed -> {
                        // estimateMeal() already queued this in the EXISTING pending-draft table
                        // on the exception path (SPEC §4.3) -- nothing more to do; the dashboard's
                        // pending-draft banner and MainActivity's cold-start retry pick it up.
                    }
                    GeminiEstimateResult.NoApiKey -> {
                        // estimateMeal()'s NoApiKey branch returns *without* queuing a draft (there's
                        // no key to even attempt a call with) -- but SPEC §4.3 promises nothing
                        // typed is ever lost. Fall back to the SAME pending-draft table by hand
                        // (same entity shape estimateMeal() itself would have written) rather than
                        // building a second retry mechanism.
                        pendingDraftDaoProvider(context)?.insert(
                            PendingDraftEntity(type = "MEAL_TEXT", payload = description, createdAt = System.currentTimeMillis())
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val EXTRA_REMINDER_ID = "com.suprxsidh.stride.reminders.EXTRA_REMINDER_ID"
        const val ACTION_ALARM_FIRED = "com.suprxsidh.stride.reminders.ACTION_ALARM_FIRED"
        const val ACTION_DIRECT_REPLY = "com.suprxsidh.stride.reminders.ACTION_DIRECT_REPLY"

        // Overridable seams for tests; production defaults read the real AppContainer (same
        // pattern as HealthConnectSyncWorker.repositoryProvider).
        var reminderRepositoryProvider: (Context) -> ReminderRepository? =
            { context -> (context.applicationContext as? StrideApp)?.container?.reminderRepository }
        var weightRepositoryProvider: (Context) -> WeightRepository? =
            { context -> (context.applicationContext as? StrideApp)?.container?.weightRepository }
        var geminiFoodRepositoryProvider: (Context) -> GeminiFoodRepository? =
            { context -> (context.applicationContext as? StrideApp)?.container?.geminiFoodRepository }
        var pendingDraftDaoProvider: (Context) -> PendingDraftDao? =
            { context -> (context.applicationContext as? StrideApp)?.container?.pendingDraftDao }
        var notificationManagerProvider: (Context) -> NotificationManagerCompat =
            { context -> NotificationManagerCompat.from(context) }
        var schedulerProvider: (Context) -> ReminderScheduler =
            { context -> ReminderScheduler(context) }

        /** Overridable so tests can force synchronous-ish completion (e.g. [Dispatchers.Unconfined])
         * instead of racing a real background dispatcher. */
        var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    }
}
