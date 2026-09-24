package com.suprxsidh.stride.reminders

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Feature E (completeness pass, spec §6). One channel per reminder type -- SPEC.md §3.16's
 * stated reasoning ("each type gets its own Android notification channel so any one can be
 * muted at the OS level without touching the others") plus the independent "low-priority
 * follow-up" channel for the post-meal-log kcal confirmation (spec §6/§4.2), which deliberately
 * is NOT the same channel as the meal nudge itself -- muting meal *reminders* shouldn't also
 * silence the "here's what got logged" confirmation, and vice versa.
 *
 * [NotificationManagerCompat.createNotificationChannelsCompat] is a no-op below API 26, so this
 * is safe to call unconditionally on every app start (called from `StrideApp.onCreate`).
 */
object NotificationChannels {
    const val WEIGH_IN = "weigh_in"
    const val MEAL = "meal"
    const val SNACK = "snack"
    const val MEAL_FOLLOW_UP = "meal_follow_up"

    fun createAll(context: Context) {
        val channels = listOf(
            NotificationChannelCompat.Builder(WEIGH_IN, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Weigh-in reminder")
                .setDescription("Daily morning weigh-in nudge with a direct-reply field")
                .build(),
            NotificationChannelCompat.Builder(MEAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Meal reminders")
                .setDescription("Nudges shortly before each configured meal time")
                .build(),
            NotificationChannelCompat.Builder(SNACK, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Snack reminders")
                .setDescription("Nudges at each configured snack time")
                .build(),
            NotificationChannelCompat.Builder(MEAL_FOLLOW_UP, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Meal log confirmations")
                .setDescription("Low-priority confirmation once a direct-reply meal is counted")
                .build()
        )
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }
}
