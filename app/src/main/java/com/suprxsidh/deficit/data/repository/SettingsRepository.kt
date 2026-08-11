package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.AppSettingsDao
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        upsertCopy { it.copy(geminiApiKey = key) }
    }

    fun observeWeeklyRunTarget(): Flow<Int> = dao.observe().map { it?.weeklyRunTarget ?: 4 }

    fun observeWeeklyRunFloor(): Flow<Int> = dao.observe().map { it?.weeklyRunFloor ?: 3 }

    suspend fun setWeeklyRunTarget(target: Int) = upsertCopy { it.copy(weeklyRunTarget = target) }

    suspend fun setWeeklyRunFloor(floor: Int) = upsertCopy { it.copy(weeklyRunFloor = floor) }

    suspend fun getManualBudgetOverrideKcal(): Int? = dao.get()?.manualBudgetOverrideKcal

    suspend fun setManualBudgetOverrideKcal(kcal: Int?) = upsertCopy { it.copy(manualBudgetOverrideKcal = kcal) }

    suspend fun getLastReviewSeenWeekStart(): String? = dao.get()?.lastReviewSeenWeekStart

    suspend fun setLastReviewSeenWeekStart(weekStart: String) = upsertCopy { it.copy(lastReviewSeenWeekStart = weekStart) }

    suspend fun getLastMotivationCategory(): String? = dao.get()?.lastMotivationCategory

    suspend fun setLastMotivationCategory(category: String) = upsertCopy { it.copy(lastMotivationCategory = category) }

    private suspend fun upsertCopy(mutate: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = dao.get() ?: AppSettingsEntity(id = 1)
        dao.upsert(mutate(current))
    }
}
