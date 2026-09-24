package com.suprxsidh.stride.reminders

/**
 * Shared intent-extra keys/values for the "tap the notification body" deep links, read by
 * `MainActivity` and written by [ReminderNotificationBuilder]. Centralized here so the string
 * constants live in exactly one place across the two files.
 */
object ReminderIntents {
    const val EXTRA_OPEN_ACTION = "com.suprxsidh.stride.reminders.EXTRA_OPEN_ACTION"

    /** Opens the Weight screen (SPEC §3.15: tapping a weigh-in notification's body, not its
     * reply field, opens the weigh-in screen). */
    const val ACTION_OPEN_WEIGH_IN = "OPEN_WEIGH_IN"

    /**
     * Opens the Food Log screen (today). Used for: the meal/snack reminder body tap, the
     * meal-follow-up "tap to edit" notification, and the meal reminder's "Camera" action.
     *
     * Deliberate simplification, flagged explicitly (see final report): SPEC §4.2/the design
     * doc's Camera action says it should open "straight into photo capture" -- doing that for
     * real would mean auto-firing FoodLogScreen's camera launcher on entry, which requires
     * editing FoodLogScreen.kt/FoodLogViewModel.kt. Both are explicitly off-limits for this task
     * (protecting Task 1's history/nav work), so the Camera action instead opens Food Log
     * (today), where the existing "Add photo" button is one tap away -- not auto-launched, but
     * reached without touching either forbidden file.
     */
    const val ACTION_OPEN_FOOD_LOG = "OPEN_FOOD_LOG"
}
