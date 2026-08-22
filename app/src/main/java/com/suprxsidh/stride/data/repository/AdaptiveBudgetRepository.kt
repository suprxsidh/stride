package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.AdaptiveBudgetCalc
import com.suprxsidh.stride.data.calc.RollingAverage
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.calc.WeighInPoint
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AdaptiveBudgetRepository(
    private val userProfileDao: UserProfileDao,
    private val weighInDao: WeighInDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun recomputeIfNoOverride(): Int? {
        if (settingsRepository.getManualBudgetOverrideKcal() != null) return null
        val profile = userProfileDao.get() ?: return null
        val points = weighInDao.observeAll().first().map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) }
        val rollingAvg = RollingAverage.sevenDayRollingAverage(points).lastOrNull()?.second ?: return null
        val newBudget = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvg, profile.heightCm, profile.age, Sex.valueOf(profile.sex))
        if (newBudget == profile.softBudgetKcal) return null
        userProfileDao.upsert(profile.copy(softBudgetKcal = newBudget))
        return newBudget
    }

    suspend fun setManualOverride(kcal: Int) {
        settingsRepository.setManualBudgetOverrideKcal(kcal)
        val profile = userProfileDao.get() ?: return
        userProfileDao.upsert(profile.copy(softBudgetKcal = kcal))
    }

    suspend fun clearManualOverride() {
        settingsRepository.setManualBudgetOverrideKcal(null)
        recomputeIfNoOverride()
    }
}
