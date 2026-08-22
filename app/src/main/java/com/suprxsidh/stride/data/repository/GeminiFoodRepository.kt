package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimate
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** Serializes [retryPendingDrafts] so two concurrent callers can't confirm the same draft twice. */
    private val retryMutex = Mutex()

    suspend fun isAvailable(): Boolean = !settingsRepository.getGeminiApiKey().isNullOrBlank()

    /**
     * Reactive counterpart to [isAvailable] — emits again when the key is saved or cleared in
     * Settings, so the UI's AI section appears/disappears deterministically instead of depending on
     * when a one-shot flow happened to be collected.
     */
    fun observeAvailability(): Flow<Boolean> =
        settingsRepository.observeGeminiApiKey().map { !it.isNullOrBlank() }

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

    /**
     * No user is present for a background retry, so a successful retry auto-saves unedited.
     *
     * Guarded by [retryMutex]: app startup and the "Retry now" button both call this independently,
     * and each used to iterate its own [PendingDraftDao.getAll] snapshot — the same draft visible in
     * both snapshots got confirmed twice, logging one meal as two food entries. A second concurrent
     * caller now waits, then re-reads the (already drained) draft list and does nothing extra.
     */
    suspend fun retryPendingDrafts(): Int = retryMutex.withLock {
        val apiKey = settingsRepository.getGeminiApiKey() ?: return@withLock 0
        var succeeded = 0
        for (draft in pendingDraftDao.getAll()) {
            val photoBase64 = draft.photoPath?.let { path -> File(path).takeIf { it.exists() }?.let { encodeBase64(it) } }
            try {
                val estimate = estimator.estimate(apiKey, draft.payload.ifBlank { null }, photoBase64)
                confirmEstimate(estimate)
                pendingDraftDao.delete(draft)
                succeeded++
            } catch (e: Exception) {
                pendingDraftDao.update(draft.copy(retryCount = draft.retryCount + 1))
            }
        }
        succeeded
    }

    suspend fun discardDraftAsQuickAdd(draftId: Long, kcal: Int) {
        val draft = pendingDraftDao.getById(draftId) ?: return
        foodRepository.logQuickAdd(draft.payload.ifBlank { "Meal" }, kcal)
        pendingDraftDao.delete(draft)
    }

    /**
     * Reading a multi-megabyte photo off disk and base64-encoding it takes hundreds of milliseconds
     * to seconds. Callers reach this from `viewModelScope`/`lifecycleScope`, i.e. the main
     * dispatcher, so it has to be moved off it explicitly or it janks/ANRs the UI.
     */
    private suspend fun encodeBase64(file: File): String = withContext(Dispatchers.IO) {
        Base64.getEncoder().encodeToString(file.readBytes())
    }
}
