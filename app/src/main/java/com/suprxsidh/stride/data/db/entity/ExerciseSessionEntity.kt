package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_session", indices = [Index(value = ["hcRecordId"], unique = true)])
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hcRecordId: String,
    val date: String,
    val exerciseType: String,
    val startTimeEpochMs: Long,
    val durationMin: Int,
    val distanceM: Double?,
    val avgPaceSecPerKm: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val kcalReal: Int,
    val kcalCredited: Int
)
