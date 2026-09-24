package com.suprxsidh.stride.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.suprxsidh.stride.MainActivity
import com.suprxsidh.stride.data.db.entity.ReminderEntity

/**
 * Feature E (completeness pass, spec §6): builds the actual [android.app.Notification] for each
 * reminder type. Kept separate from [ReminderAlarmReceiver] so the (fairly mechanical) Android
 * notification-building boilerplate doesn't crowd out the receiver's actual dispatch logic.
 */
object ReminderNotificationBuilder {
    /** Key the RemoteInput result is read back under (see [ReminderAlarmReceiver]). Shared
     * across weigh-in/meal/snack -- the receiver already knows which reminder (and therefore
     * which reply grammar) it's reading via [ReminderAlarmReceiver.EXTRA_REMINDER_ID]. */
    const val KEY_REPLY_TEXT = "reminder_reply_text"

    fun notificationId(reminderId: Long): Int = reminderId.toInt()

    fun followUpNotificationId(reminderId: Long): Int = (reminderId + 1_000_000L).toInt()

    fun weighInNotification(context: Context, reminder: ReminderEntity): android.app.Notification {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Type your weight, e.g. 81.4")
            .build()
        val replyIntent = replyPendingIntent(context, reminder)
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Log weight", replyIntent
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()

        val bodyIntent = tapPendingIntent(context, reminder.id, ReminderIntents.ACTION_OPEN_WEIGH_IN)

        return NotificationCompat.Builder(context, NotificationChannels.WEIGH_IN)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Morning weigh-in")
            .setContentText("Type your weight below, or tap to open the weigh-in screen.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(bodyIntent)
            .addAction(replyAction)
            .build()
    }

    fun mealOrSnackNotification(context: Context, reminder: ReminderEntity, channelId: String): android.app.Notification {
        val label = reminder.label ?: if (reminder.type == ReminderType.SNACK.name) "Snack" else "Meal"
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("e.g. 2 rotis and chole")
            .build()
        val replyIntent = replyPendingIntent(context, reminder)
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Type it", replyIntent
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()

        val cameraIntent = tapPendingIntent(context, reminder.id, ReminderIntents.ACTION_OPEN_FOOD_LOG, requestCodeOffset = 3)
        val cameraAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_camera, "Camera", cameraIntent
        ).build()

        val bodyIntent = tapPendingIntent(context, reminder.id, ReminderIntents.ACTION_OPEN_FOOD_LOG, requestCodeOffset = 4)

        val title = if (reminder.type == ReminderType.MEAL.name) {
            "$label soon"
        } else {
            "$label time"
        }
        val text = "Snap a photo or type it when you eat."

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(bodyIntent)
            .addAction(replyAction)
            .addAction(cameraAction)
            .build()
    }

    /** Spec §4.2: "a low-key confirmation notification showing the counted kcal with a
     * tap-to-edit". Deliberately its own [NotificationChannels.MEAL_FOLLOW_UP] channel/priority
     * so muting meal reminders doesn't also silence this. */
    fun mealFollowUpNotification(context: Context, reminder: ReminderEntity, name: String, bufferedKcal: Int): android.app.Notification {
        val bodyIntent = tapPendingIntent(context, reminder.id, ReminderIntents.ACTION_OPEN_FOOD_LOG, requestCodeOffset = 5)
        return NotificationCompat.Builder(context, NotificationChannels.MEAL_FOLLOW_UP)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Logged: $name")
            .setContentText("Counted $bufferedKcal kcal — tap to edit")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(bodyIntent)
            .build()
    }

    private fun replyPendingIntent(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_DIRECT_REPLY
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val requestCode = ReminderScheduler.alarmRequestCode(reminder.id) + 1
        return PendingIntent.getBroadcast(context, requestCode, intent, ReminderScheduler.mutableFlags())
    }

    private fun tapPendingIntent(context: Context, reminderId: Long, openAction: String, requestCodeOffset: Int = 2): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderIntents.EXTRA_OPEN_ACTION, openAction)
        }
        val requestCode = ReminderScheduler.alarmRequestCode(reminderId) + requestCodeOffset
        return PendingIntent.getActivity(context, requestCode, intent, ReminderScheduler.immutableFlags())
    }
}
