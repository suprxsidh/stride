package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT COALESCE(SUM(bufferedKcal), 0) FROM food_entry WHERE date = :date")
    fun observeBufferedTotalForDate(date: String): Flow<Int>

    @Delete
    suspend fun delete(entry: FoodEntryEntity)
}
