package com.suprxsidh.deficit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.deficit.data.db.dao.AppSettingsDao
import com.suprxsidh.deficit.data.db.dao.CustomFoodDao
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.OffCacheDao
import com.suprxsidh.deficit.data.db.dao.PendingDraftDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.dao.WeeklyReviewDao
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity

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
        PendingDraftEntity::class,
        WeeklyReviewEntity::class
    ],
    // v5: WeeklyReviewEntity added; AppSettingsEntity gained weekly commitment + review fields.
    // v6: AppSettingsEntity gained lastMotivationDate (final-review fix wave, gates the
    // motivation line to once per logical day). No Migration object — same convention as v4->v5:
    // fallbackToDestructiveMigration() below handles the upgrade for this single-user, sideloaded,
    // no-cloud-sync app.
    version = 6,
    exportSchema = false
)
abstract class DeficitDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun offCacheDao(): OffCacheDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao

    companion object {
        @Volatile private var INSTANCE: DeficitDatabase? = null

        fun getInstance(context: Context): DeficitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeficitDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
