package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDraftDao {
    @Insert
    suspend fun insert(draft: PendingDraftEntity): Long

    @Update
    suspend fun update(draft: PendingDraftEntity)

    @Delete
    suspend fun delete(draft: PendingDraftEntity)

    @Query("SELECT * FROM pending_draft ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingDraftEntity>

    @Query("SELECT * FROM pending_draft ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingDraftEntity>>

    @Query("SELECT * FROM pending_draft WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingDraftEntity?
}
