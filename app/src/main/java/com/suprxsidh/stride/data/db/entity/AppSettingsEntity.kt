package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val geminiApiKey: String? = null,
    val weeklyRunTarget: Int = 4,
    val weeklyRunFloor: Int = 3,
    val manualBudgetOverrideKcal: Int? = null,
    val lastReviewSeenWeekStart: String? = null,
    val lastMotivationCategory: String? = null,
    val lastMotivationDate: String? = null
)
