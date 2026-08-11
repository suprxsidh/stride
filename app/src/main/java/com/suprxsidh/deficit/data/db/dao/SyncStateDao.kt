package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    /**
     * Advances the exercise-session watermark only, preserving the weigh-in watermark.
     * Done inside a transaction so a concurrent writer of the other field can't be clobbered
     * by a stale whole-row REPLACE.
     */
    @Transaction
    suspend fun setExerciseSyncWatermark(epochMs: Long) {
        val current = get()
        upsert(
            current?.copy(lastSyncEpochMs = epochMs)
                ?: SyncStateEntity(hcChangesToken = null, lastSyncEpochMs = epochMs, lastWeightSyncEpochMs = null)
        )
    }

    /** Advances the weigh-in watermark only, preserving the exercise-session watermark. */
    @Transaction
    suspend fun setWeightSyncWatermark(epochMs: Long) {
        val current = get()
        upsert(
            current?.copy(lastWeightSyncEpochMs = epochMs)
                ?: SyncStateEntity(hcChangesToken = null, lastSyncEpochMs = null, lastWeightSyncEpochMs = epochMs)
        )
    }
}
