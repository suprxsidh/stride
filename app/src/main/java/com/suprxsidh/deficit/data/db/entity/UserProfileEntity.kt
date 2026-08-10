package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val heightCm: Double,
    val weightKgAtStart: Double,
    val age: Int,
    val sex: String,
    val goalWeightKg: Double,
    val softBudgetKcal: Int,
    val createdAt: Long
)
