package com.suprxsidh.stride.data.calc

import java.time.LocalDate
import java.time.Period

object AdaptiveBudgetCalc {
    /**
     * Feature D (completeness pass, spec §5): age is now derived from [birthDate] at call time
     * rather than read from a stored int, so the budget drifts correctly as the user ages, not
     * only when weight changes. [CalorieMath.bmr] itself is unchanged -- it still takes a plain
     * `age: Int` -- the int is computed here, at this call site, from the birthdate.
     */
    fun recomputeSoftBudgetKcal(
        rollingAvgWeightKg: Double,
        heightCm: Double,
        birthDate: LocalDate,
        sex: Sex,
        today: LocalDate = LocalDate.now()
    ): Int {
        val age = Period.between(birthDate, today).years
        val bmr = CalorieMath.bmr(rollingAvgWeightKg, heightCm, age, sex)
        val tdee = CalorieMath.tdee(bmr)
        return CalorieMath.softBudgetKcal(tdee)
    }
}
