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
    // Feature C (completeness pass, spec §4): protein estimate in grams for this entry. Defaults
    // to 0.0 so pre-existing call sites (quick-add, custom food, older tests) that don't supply
    // a protein value keep compiling — only the Gemini estimation path currently populates it.
    val proteinG: Double = 0.0,
    val source: String,
    val loggedAt: Long
)
