package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

/**
 * Max snacks the "Snacks" quick-pin row will ever show. Kept as a shared constant rather than a
 * bare `6` in [observePinned]'s query so callers that need to warn the user before hitting the
 * cap (see [countPinned]) can't drift from the actual query limit.
 */
const val MAX_PINNED_SNACKS = 6

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_food ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food WHERE isPinned = 1 ORDER BY name ASC LIMIT $MAX_PINNED_SNACKS")
    fun observePinned(): Flow<List<CustomFoodEntity>>

    /** Case-insensitive lookup used to treat "save" on an existing name as an edit, not a new row. */
    @Query("SELECT * FROM custom_food WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CustomFoodEntity?

    /**
     * Unlike [observePinned] (capped at [MAX_PINNED_SNACKS] for display), this counts every row
     * actually flagged `isPinned`, so a caller can detect "already at the cap" even though the
     * pinned list itself never renders more than [MAX_PINNED_SNACKS].
     */
    @Query("SELECT COUNT(*) FROM custom_food WHERE isPinned = 1")
    suspend fun countPinned(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CustomFoodEntity): Long

    @Delete
    suspend fun delete(food: CustomFoodEntity)
}
