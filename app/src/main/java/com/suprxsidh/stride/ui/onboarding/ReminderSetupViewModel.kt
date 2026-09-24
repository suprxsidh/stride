package com.suprxsidh.stride.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.data.repository.ReminderRepository
import com.suprxsidh.stride.reminders.ReminderScheduler
import com.suprxsidh.stride.reminders.ReminderType
import java.time.DateTimeException
import java.time.LocalTime

/**
 * Feature E (completeness pass, spec §6): backs the new `ReminderSetupStep` onboarding step.
 * Onboarding only ever runs once per install (a fresh profile), so unlike a Settings-screen
 * editor there's no existing DB state to load -- this just holds the SPEC-default times in
 * memory, lets the user edit/add/remove them, and persists + schedules everything once on
 * [commit]. Same plain-`OutlinedTextField`, no-dialogs style as [OnboardingViewModel] (see its
 * birthdate-field comment for why this codebase avoids Compose's date/time picker dialogs).
 */
class ReminderSetupViewModel(private val reminderRepository: ReminderRepository) : ViewModel() {

    /** One editable meal/snack row. A plain mutable class (not a `data class` with `by
     * mutableStateOf` fields) would need externally-observed state anyway, so fields are
     * `mutableStateOf` directly -- edits to an existing row's label/time recompose without
     * needing to replace the row in its containing list. */
    class TimeRow(label: String, time: String) {
        var label by mutableStateOf(label)
        var time by mutableStateOf(time)
    }

    var weighInTime by mutableStateOf("07:00")
    val mealRows = mutableStateListOf(
        TimeRow("Breakfast", "09:00"),
        TimeRow("Lunch", "13:30"),
        TimeRow("Dinner", "20:30")
    )
    val snackRows = mutableStateListOf<TimeRow>()

    var error by mutableStateOf<String?>(null)
        private set

    fun addMeal() = mealRows.add(TimeRow("Meal", "12:00"))
    fun removeMeal(row: TimeRow) { mealRows.remove(row) }
    fun addSnack() = snackRows.add(TimeRow("Snack", "16:00"))
    fun removeSnack(row: TimeRow) { snackRows.remove(row) }

    /**
     * Validates every time field, then persists a [ReminderEntity] per row and schedules its
     * first alarm via [scheduler]. Returns true on success (caller should advance/finish
     * onboarding); false leaves [error] set and the step in place.
     */
    suspend fun commit(scheduler: ReminderScheduler): Boolean {
        if (parseTime(weighInTime) == null) {
            error = "Enter a valid weigh-in time (HH:mm)."
            return false
        }
        if (mealRows.any { parseTime(it.time) == null }) {
            error = "Enter a valid time (HH:mm) for every meal."
            return false
        }
        if (snackRows.any { parseTime(it.time) == null }) {
            error = "Enter a valid time (HH:mm) for every snack."
            return false
        }
        error = null

        val toSave = buildList {
            add(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = weighInTime, label = null, enabled = true))
            mealRows.forEach { add(ReminderEntity(type = ReminderType.MEAL.name, time = it.time, label = it.label, enabled = true)) }
            snackRows.forEach { add(ReminderEntity(type = ReminderType.SNACK.name, time = it.time, label = it.label, enabled = true)) }
        }
        val saved = toSave.map { reminderRepository.save(it) }
        saved.forEach { scheduler.schedule(it) }
        return true
    }

    private fun parseTime(text: String): LocalTime? = try {
        LocalTime.parse(text.trim())
    } catch (e: DateTimeException) {
        null
    }
}
