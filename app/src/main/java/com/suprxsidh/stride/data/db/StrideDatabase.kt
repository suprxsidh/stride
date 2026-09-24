package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.ReminderDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class,
        ReminderEntity::class
    ],
    // v9: OffCacheEntity removed and FoodEntryEntity lost offBarcode -- Open Food Facts search is
    // cut, Gemini (photo or text) is now the only food-logging path. fallbackToDestructiveMigration()
    // below handles the upgrade, same convention as every prior version bump.
    // v10: SyncStateEntity lost lastSyncEpochMs -- the exercise-session watermark it backed was
    // dead code once the exercise sync it fed was cut; only the weigh-in watermark remains.
    // fallbackToDestructiveMigration() below handles the upgrade, same convention as every prior
    // version bump.
    // v11: completeness pass Feature C (protein floor) -- FoodEntryEntity gained proteinG,
    // UserProfileEntity gained proteinFloorG. fallbackToDestructiveMigration() below handles the
    // upgrade, same convention as every prior version bump.
    // v12: completeness pass Feature D (adaptive budget actually adapts) -- UserProfileEntity's
    // static `age: Int` replaced with `birthDate: String` (ISO date), so age can be derived at
    // call time instead of going stale. fallbackToDestructiveMigration() below handles the
    // upgrade, same convention as every prior version bump.
    // v13: completeness pass Feature E (reminders) -- new ReminderEntity table (one row per
    // weigh-in/meal/snack reminder instance; AppSettingsEntity's fixed single-row shape doesn't
    // fit meal/snack's variable add/remove count, see spec §8). fallbackToDestructiveMigration()
    // below handles the upgrade, same convention as every prior version bump.
    version = 13,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao
    abstract fun reminderDao(): ReminderDao

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
