package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Atomic insert-or-update keyed on [ExerciseSessionEntity.hcRecordId] (which carries a unique
     * index). The periodic and one-off sync work are separate unique-work chains and can run
     * concurrently; doing the check-then-write in the caller let both observe "new" and both
     * insert, and the loser hit SQLiteConstraintException. Inside a transaction the two callers
     * serialize, so the second one sees the first one's row and updates it instead.
     */
    @Transaction
    suspend fun upsertByHcRecordId(session: ExerciseSessionEntity) {
        val existing = getByHcRecordId(session.hcRecordId)
        if (existing != null) update(session.copy(id = existing.id)) else insert(session)
    }

    @Query("SELECT * FROM exercise_session ORDER BY startTimeEpochMs DESC")
    fun observeAll(): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_session WHERE date = :date ORDER BY startTimeEpochMs DESC LIMIT 1")
    suspend fun getLatestForDate(date: String): ExerciseSessionEntity?
}
