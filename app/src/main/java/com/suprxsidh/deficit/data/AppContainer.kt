package com.suprxsidh.deficit.data

import android.content.Context
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository

class AppContainer(context: Context) {
    private val database = DeficitDatabase.getInstance(context)
    val userProfileRepository = UserProfileRepository(database.userProfileDao())
    val foodRepository = FoodRepository(database.foodEntryDao(), database.customFoodDao())
    val weightRepository = WeightRepository(database.weighInDao())
}
