package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row sync bookkeeping. Exercise sessions and weigh-ins each get their OWN watermark:
 * they are imported by two independent methods that run back to back in the same worker cycle,
 * so a shared cursor meant whichever ran first advanced the "since" bound the second one reads,
 * collapsing the second one's query window to [now, now] forever after the first sync.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val hcChangesToken: String?,
    /** Watermark for exercise-session import only. */
    val lastSyncEpochMs: Long?,
    /** Watermark for weigh-in import only. */
    val lastWeightSyncEpochMs: Long? = null
)
