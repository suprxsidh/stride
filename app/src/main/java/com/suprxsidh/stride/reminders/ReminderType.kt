package com.suprxsidh.stride.reminders

/**
 * The three reminder kinds from SPEC.md §3.14/§3.15 plus the user-requested snack extension
 * (spec §6, completeness-pass design doc). Each gets its own notification channel (see
 * [NotificationChannels]) so any one type can be muted at the OS level independently.
 */
enum class ReminderType {
    WEIGH_IN,
    MEAL,
    SNACK
}
