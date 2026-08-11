package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSessionDao {
    @Query("SELECT * FROM exercise_session WHERE hcRecordId = :hcRecordId LIMIT 1")
    suspend fun getByHcRecordId(hcRecordId: String): ExerciseSessionEntity?

    @Insert
    suspend fun insert(session: ExerciseSessionEntity): Long

    @Update
    suspend fun update(session: ExerciseSessionEntity)

    @Query("SELECT * FROM exercise_session ORDER BY startTimeEpochMs DESC")
    fun observeAll(): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_session WHERE date = :date ORDER BY startTimeEpochMs DESC LIMIT 1")
    suspend fun getLatestForDate(date: String): ExerciseSessionEntity?
}
