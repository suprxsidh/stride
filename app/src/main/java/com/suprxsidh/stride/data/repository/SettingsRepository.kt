package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        upsertCopy { it.copy(geminiApiKey = key) }
    }

    suspend fun getManualBudgetOverrideKcal(): Int? = dao.get()?.manualBudgetOverrideKcal

    suspend fun setManualBudgetOverrideKcal(kcal: Int?) = upsertCopy { it.copy(manualBudgetOverrideKcal = kcal) }

    private suspend fun upsertCopy(mutate: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = dao.get() ?: AppSettingsEntity(id = 1)
        dao.upsert(mutate(current))
    }
}
