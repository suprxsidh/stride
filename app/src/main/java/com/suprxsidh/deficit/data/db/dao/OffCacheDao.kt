package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OffCacheDao {
    @Query("SELECT * FROM off_cache WHERE code = :code LIMIT 1")
    suspend fun get(code: String): OffCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OffCacheEntity)

    @Query("SELECT * FROM off_cache ORDER BY cachedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<OffCacheEntity>>

    @Query("SELECT * FROM off_cache WHERE productName LIKE '%' || :query || '%' ORDER BY cachedAt DESC LIMIT 20")
    suspend fun searchCached(query: String): List<OffCacheEntity>
}
