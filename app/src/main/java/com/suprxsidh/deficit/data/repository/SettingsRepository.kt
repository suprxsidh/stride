package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.AppSettingsDao
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        dao.upsert(AppSettingsEntity(id = 1, geminiApiKey = key))
    }
}
