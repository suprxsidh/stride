package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.ExerciseSessionDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.OffCacheDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
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
        ExerciseSessionEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class
    ],
    // v7: WeeklyReviewEntity removed and AppSettingsEntity lost its weekly-commitment/review/
    // motivation fields as part of the calorie-only scope-down (see
    // docs/superpowers/specs/2026-09-02-calorie-only-scope-down-design.md). Still no Migration
    // object -- fallbackToDestructiveMigration() below handles the upgrade, same convention as
    // every prior version bump on this single-user, sideloaded, no-cloud-sync app.
    version = 7,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun offCacheDao(): OffCacheDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
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
