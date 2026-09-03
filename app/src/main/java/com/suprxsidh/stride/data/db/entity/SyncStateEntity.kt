package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row sync bookkeeping for the Health Connect weigh-in importer: [hcChangesToken] tracks
 * the Health Connect changes-feed cursor, and [lastWeightSyncEpochMs] is the watermark for the
 * last successfully imported weigh-in.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val hcChangesToken: String?,
    /** Watermark for weigh-in import only. */
    val lastWeightSyncEpochMs: Long? = null
)
