package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entry")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val rawKcal: Int,
    val bufferedKcal: Int,
    val source: String,
    val offBarcode: String?,
    val loggedAt: Long
)
