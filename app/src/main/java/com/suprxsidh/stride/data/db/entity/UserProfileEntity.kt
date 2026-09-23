package com.suprxsidh.stride.data.db.entity

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
    // Feature C (completeness pass, spec §4): protein floor in grams. Defaults to 0.0 purely so
    // pre-existing tests that construct this entity without it (age/birthdate migration is a
    // separate later task, not touched here) keep compiling; every real profile written via
    // UserProfileRepository.completeOnboarding always sets a real computed value.
    val proteinFloorG: Double = 0.0,
    val createdAt: Long
)
