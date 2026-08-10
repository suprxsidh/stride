package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeighInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weighIn: WeighInEntity): Long

    @Query("SELECT * FROM weigh_in ORDER BY date ASC")
    fun observeAll(): Flow<List<WeighInEntity>>

    @Query("SELECT * FROM weigh_in WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): WeighInEntity?
}
