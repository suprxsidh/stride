package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT COALESCE(SUM(bufferedKcal), 0) FROM food_entry WHERE date = :date")
    fun observeBufferedTotalForDate(date: String): Flow<Int>

    // Feature C (spec §4): protein floor progress bar on the dashboard.
    @Query("SELECT COALESCE(SUM(proteinG), 0.0) FROM food_entry WHERE date = :date")
    fun observeProteinTotalForDate(date: String): Flow<Double>

    // Feature B (spec §3): one grouped query for the whole 7-day rolling-deficit window instead
    // of 7 separate per-day flows. Date strings are ISO (yyyy-MM-dd) so lexicographic BETWEEN
    // matches chronological order.
    @Query(
        "SELECT date, COALESCE(SUM(bufferedKcal), 0) AS total FROM food_entry " +
            "WHERE date BETWEEN :start AND :end GROUP BY date"
    )
    fun observeBufferedTotalsForRange(start: String, end: String): Flow<List<DateBufferedTotal>>

    @Delete
    suspend fun delete(entry: FoodEntryEntity)
}

/** Row shape for [FoodEntryDao.observeBufferedTotalsForRange]'s grouped query. */
data class DateBufferedTotal(val date: String, val total: Int)
