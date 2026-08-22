package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_draft")
data class PendingDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "MEAL_TEXT" | "MEAL_PHOTO"
    val payload: String, // free-text description; blank if photo-only
    val photoPath: String? = null, // absolute path to a cached jpeg; null if text-only
    val createdAt: Long,
    val retryCount: Int = 0
)
