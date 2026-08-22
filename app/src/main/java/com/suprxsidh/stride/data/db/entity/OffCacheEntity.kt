package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "off_cache")
data class OffCacheEntity(
    @PrimaryKey val code: String,
    val productName: String,
    val kcalPerServing: Int,
    val servingLabel: String,
    val cachedAt: Long
)
