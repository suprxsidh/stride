package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_food ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food WHERE isPinned = 1 ORDER BY name ASC LIMIT 6")
    fun observePinned(): Flow<List<CustomFoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CustomFoodEntity): Long

    @Delete
    suspend fun delete(food: CustomFoodEntity)
}
