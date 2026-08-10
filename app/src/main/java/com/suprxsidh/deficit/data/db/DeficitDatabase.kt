package com.suprxsidh.deficit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.deficit.data.db.dao.CustomFoodDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity

@Database(
    entities = [UserProfileEntity::class, FoodEntryEntity::class, CustomFoodEntity::class, WeighInEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DeficitDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao

    companion object {
        @Volatile private var INSTANCE: DeficitDatabase? = null

        fun getInstance(context: Context): DeficitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeficitDatabase::class.java,
                    "deficit.db"
                ).build().also { INSTANCE = it }
            }
    }
}
