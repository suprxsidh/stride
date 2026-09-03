package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.suprxsidh.stride.data.db.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    /**
     * Advances the weigh-in watermark, preserving the Health Connect changes token.
     * Done inside a transaction so a concurrent writer of the other field can't be clobbered
     * by a stale whole-row REPLACE.
     */
    @Transaction
    suspend fun setWeightSyncWatermark(epochMs: Long) {
        val current = get()
        upsert(
            current?.copy(lastWeightSyncEpochMs = epochMs)
                ?: SyncStateEntity(hcChangesToken = null, lastWeightSyncEpochMs = epochMs)
        )
    }
}
