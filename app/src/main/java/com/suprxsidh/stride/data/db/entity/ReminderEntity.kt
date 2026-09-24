package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Feature E (completeness pass, spec §6): one row per reminder instance. `AppSettingsEntity` is
 * a fixed single-row table (copy-on-write) and doesn't fit meal/snack reminders' "add/remove"
 * requirement (a variable count) -- see spec §8. Weigh-in has exactly one row; meal and snack
 * can each have any number.
 *
 * @param type one of "WEIGH_IN" | "MEAL" | "SNACK" (see [com.suprxsidh.stride.reminders.ReminderType]).
 * @param time the time of day this reminder is anchored to, "HH:mm" 24h, ISO [java.time.LocalTime]
 *   format -- for MEAL this is the meal time itself, the actual notification fires
 *   [com.suprxsidh.stride.reminders.ReminderScheduler.MEAL_LEAD_TIME_MINUTES] earlier (spec §6:
 *   "fires a lead-time before the meal time", not stated as itself editable, unlike the meal
 *   time list).
 * @param label user-facing name (e.g. "Breakfast", "Afternoon snack"); null for WEIGH_IN, which
 *   has no label concept.
 */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val time: String,
    val label: String? = null,
    val enabled: Boolean = true
)
