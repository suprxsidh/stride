package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.stride.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertByWeekStart(review: WeeklyReviewEntity)

    @Query("SELECT * FROM weekly_review WHERE weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getByWeekStart(weekStartDate: String): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_review ORDER BY weekStartDate DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>
}
