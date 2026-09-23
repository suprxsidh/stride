package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.CalorieMath
import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity
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
        goalWeightKg: Double? = null,
        // Feature C (spec §4): "sane default (1.6 * weightKg), editable" — same optional-with-
        // fallback shape as goalWeightKg just above.
        proteinFloorG: Double? = null
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
            proteinFloorG = proteinFloorG ?: (1.6 * weightKg),
            createdAt = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        userProfileDao.upsert(entity)
        weighInDao.upsert(
            WeighInEntity(date = DayBoundary.logicalDate(clock()).toString(), weightKg = weightKg)
        )
        return entity
    }
}
