package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.OffCacheDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.OffCacheEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        OffCacheEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class
    ],
    // v8: ExerciseSessionEntity removed as part of the calorie-only scope-down -- Health Connect
    // now only reads TotalCaloriesBurnedRecord directly (HealthConnectRepository.getTodaysCaloriesBurned)
    // and WeightRecord, no longer per-exercise-session data. fallbackToDestructiveMigration()
    // below handles the upgrade, same convention as every prior version bump.
    version = 8,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun offCacheDao(): OffCacheDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao

    companion object {
        @Volatile private var INSTANCE: StrideDatabase? = null

        fun getInstance(context: Context): StrideDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StrideDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
