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
    private val settingsRepository: SettingsRepository,
    // Feature D (completeness pass, spec §5): injectable so tests can pin "today" instead of
    // depending on the real wall clock -- age (derived from birthDate) is otherwise only stable
    // within a single calendar day of test runs. Same pattern as the `clock`/`today` lambdas
    // already used elsewhere in this repo layer (e.g. WeightRepository, UserProfileRepository).
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    suspend fun recomputeIfNoOverride(): Int? {
        if (settingsRepository.getManualBudgetOverrideKcal() != null) return null
        val profile = userProfileDao.get() ?: return null
        val points = weighInDao.observeAll().first().map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) }
        val rollingAvg = RollingAverage.sevenDayRollingAverage(points).lastOrNull()?.second ?: return null
        val newBudget = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvg, profile.heightCm, LocalDate.parse(profile.birthDate), Sex.valueOf(profile.sex), today()
        )
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
