package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reminder ORDER BY type ASC, time ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminder ORDER BY type ASC, time ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE type = :type ORDER BY time ASC")
    suspend fun getByType(type: String): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE type = :type ORDER BY time ASC")
    fun observeByType(type: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?
}
