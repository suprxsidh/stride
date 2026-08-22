package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_review", indices = [Index(value = ["weekStartDate"], unique = true)])
data class WeeklyReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDate: String,
    val runsCompleted: Int,
    val runFloor: Int,
    val runTarget: Int,
    val daysLogged: Int,
    val avgDailyDeficitKcal: Double?,
    val rollingWeightChangeKg: Double?,
    val budgetAdjustedToKcal: Int?,
    val outcome: String,
    val generatedAt: Long
)
