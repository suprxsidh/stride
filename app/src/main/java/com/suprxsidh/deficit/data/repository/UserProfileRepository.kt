package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.CalorieMath
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId

class UserProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()
    suspend fun getProfile(): UserProfileEntity? = userProfileDao.get()

    suspend fun completeOnboarding(
        heightCm: Double,
        weightKg: Double,
        age: Int,
        sex: Sex,
        goalWeightKg: Double? = null
    ): UserProfileEntity {
        val bmr = CalorieMath.bmr(weightKg, heightCm, age, sex)
        val tdee = CalorieMath.tdee(bmr)
        val entity = UserProfileEntity(
            heightCm = heightCm,
            weightKgAtStart = weightKg,
            age = age,
            sex = sex.name,
            goalWeightKg = goalWeightKg ?: (weightKg - 10.0),
            softBudgetKcal = CalorieMath.softBudgetKcal(tdee),
            createdAt = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        userProfileDao.upsert(entity)
        weighInDao.upsert(
            WeighInEntity(date = DayBoundary.logicalDate(clock()).toString(), weightKg = weightKg)
        )
        return entity
    }
}
