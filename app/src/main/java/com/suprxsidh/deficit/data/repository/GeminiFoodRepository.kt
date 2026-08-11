package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimate
import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.deficit.data.db.dao.PendingDraftDao
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Base64

sealed class GeminiEstimateResult {
    object NoApiKey : GeminiEstimateResult()
    data class Success(val estimate: GeminiFoodEstimate) : GeminiEstimateResult()
    data class Failed(val draftId: Long, val message: String) : GeminiEstimateResult()
}

class GeminiFoodRepository(
    private val estimator: GeminiFoodEstimator,
    private val settingsRepository: SettingsRepository,
    private val foodRepository: FoodRepository,
    private val pendingDraftDao: PendingDraftDao
) {
    suspend fun isAvailable(): Boolean = !settingsRepository.getGeminiApiKey().isNullOrBlank()

    fun observePendingDrafts(): Flow<List<PendingDraftEntity>> = pendingDraftDao.observeAll()

    /**
     * Estimates only — does not save. The caller (UI) shows the itemized result for
     * one-tap confirm/edit, then calls [confirmEstimate] once the user accepts it.
     */
    suspend fun estimateMeal(description: String?, photoFile: File?): GeminiEstimateResult {
        val apiKey = settingsRepository.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) return GeminiEstimateResult.NoApiKey

        val photoBase64 = photoFile?.let { encodeBase64(it) }
        return try {
            val estimate = estimator.estimate(apiKey, description, photoBase64)
            GeminiEstimateResult.Success(estimate)
        } catch (e: Exception) {
            val draftId = pendingDraftDao.insert(
                PendingDraftEntity(
                    type = if (photoFile != null) "MEAL_PHOTO" else "MEAL_TEXT",
                    payload = description.orEmpty(),
                    photoPath = photoFile?.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
            )
            GeminiEstimateResult.Failed(draftId, e.message ?: "Unknown error")
        }
    }

    /** Saves a reviewed (optionally edited) estimate. Called once the user taps "confirm". */
    suspend fun confirmEstimate(estimate: GeminiFoodEstimate, editedName: String? = null, editedTotalKcal: Int? = null): FoodEntryEntity {
        val name = editedName ?: estimate.items.joinToString(", ") { it.name }
        val kcal = editedTotalKcal ?: estimate.totalKcal
        return foodRepository.logGeminiEstimate(name, kcal)
    }

    /** No user is present for a background retry, so a successful retry auto-saves unedited. */
    suspend fun retryPendingDrafts(): Int {
        val apiKey = settingsRepository.getGeminiApiKey() ?: return 0
        var succeeded = 0
        for (draft in pendingDraftDao.getAll()) {
            val photoBase64 = draft.photoPath?.let { path -> File(path).takeIf { it.exists() }?.let(::encodeBase64) }
            try {
                val estimate = estimator.estimate(apiKey, draft.payload.ifBlank { null }, photoBase64)
                confirmEstimate(estimate)
                pendingDraftDao.delete(draft)
                succeeded++
            } catch (e: Exception) {
                pendingDraftDao.update(draft.copy(retryCount = draft.retryCount + 1))
            }
        }
        return succeeded
    }

    suspend fun discardDraftAsQuickAdd(draftId: Long, kcal: Int) {
        val draft = pendingDraftDao.getById(draftId) ?: return
        foodRepository.logQuickAdd(draft.payload.ifBlank { "Meal" }, kcal)
        pendingDraftDao.delete(draft)
    }

    private fun encodeBase64(file: File): String = Base64.getEncoder().encodeToString(file.readBytes())
}
