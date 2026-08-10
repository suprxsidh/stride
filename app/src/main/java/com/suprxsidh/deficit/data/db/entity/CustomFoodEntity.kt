package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_food")
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcalPerServing: Int,
    val servingLabel: String,
    val isPinned: Boolean = false
)
