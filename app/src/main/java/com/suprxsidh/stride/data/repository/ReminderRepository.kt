package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.db.dao.ReminderDao
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.reminders.ReminderType
import kotlinx.coroutines.flow.Flow

/**
 * Feature E (completeness pass, spec §6). Thin wrapper over [ReminderDao] -- default seeding and
 * type-keyed convenience live here rather than in the DAO so callers (onboarding's
 * `ReminderSetupViewModel`, the alarm/boot receivers) never construct raw SQL type strings.
 */
class ReminderRepository(private val dao: ReminderDao) {

    suspend fun getAll(): List<ReminderEntity> = dao.getAll()

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun getByType(type: ReminderType): List<ReminderEntity> = dao.getByType(type.name)

    suspend fun getById(id: Long): ReminderEntity? = dao.getById(id)

    suspend fun save(reminder: ReminderEntity): ReminderEntity {
        val id = dao.upsert(reminder)
        return if (reminder.id == 0L) reminder.copy(id = id) else reminder
    }

    suspend fun delete(reminder: ReminderEntity) = dao.delete(reminder)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * Called once, from onboarding's `ReminderSetupStep`, if no reminders exist yet. Idempotent
     * against being called twice would double the meal/snack rows, so callers must check
     * [getAll] is empty first (onboarding only runs once per install anyway).
     */
    suspend fun seedDefaults(): List<ReminderEntity> {
        val defaults = listOf(
            ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00", label = null, enabled = true),
            ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast", enabled = true),
            ReminderEntity(type = ReminderType.MEAL.name, time = "13:30", label = "Lunch", enabled = true),
            ReminderEntity(type = ReminderType.MEAL.name, time = "20:30", label = "Dinner", enabled = true)
            // No default snack rows -- a snack isn't a scheduled event by default per spec §6;
            // the user adds one explicitly in ReminderSetupStep if they want one.
        )
        return defaults.map { save(it) }
    }
}
