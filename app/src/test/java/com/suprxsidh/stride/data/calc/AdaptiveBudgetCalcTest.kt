package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdaptiveBudgetCalcTest {
    // A fixed "today" so age (derived from birthDate) is deterministic across test runs --
    // matches the clock-injection convention already used elsewhere in this repo layer.
    private val today = LocalDate.of(2024, 1, 1)
    private val birthDateForAge29 = today.minusYears(29)

    @Test
    fun `recompute matches the existing BMR-TDEE-budget chain`() {
        val expectedBmr = CalorieMath.bmr(weightKg = 78.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        val expectedBudget = CalorieMath.softBudgetKcal(CalorieMath.tdee(expectedBmr))

        assertEquals(
            expectedBudget,
            AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
                rollingAvgWeightKg = 78.0, heightCm = 178.0, birthDate = birthDateForAge29, sex = Sex.MALE, today = today
            )
        )
    }

    @Test
    fun `a lower rolling-average weight lowers the budget but never below 1500`() {
        val higher = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 90.0, heightCm = 178.0, birthDate = birthDateForAge29, sex = Sex.MALE, today = today
        )
        val lower = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 60.0, heightCm = 178.0, birthDate = birthDateForAge29, sex = Sex.MALE, today = today
        )

        assertTrue(lower < higher)
        assertTrue(lower >= 1500)
    }

    @Test
    fun `age is derived from birthDate and today, not a stored int -- an older birthdate lowers the budget for the same weight`() {
        val youngerBudget = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 78.0, heightCm = 178.0, birthDate = today.minusYears(29), sex = Sex.MALE, today = today
        )
        val olderBudget = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 78.0, heightCm = 178.0, birthDate = today.minusYears(45), sex = Sex.MALE, today = today
        )

        assertTrue(olderBudget < youngerBudget)
    }

    @Test
    fun `advancing today across a birthday changes the derived age and therefore the budget`() {
        val birthDate = LocalDate.of(1995, 6, 15)
        val justBeforeBirthday = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 78.0, heightCm = 178.0, birthDate = birthDate, sex = Sex.MALE, today = LocalDate.of(2024, 6, 14)
        )
        val justAfterBirthday = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(
            rollingAvgWeightKg = 78.0, heightCm = 178.0, birthDate = birthDate, sex = Sex.MALE, today = LocalDate.of(2024, 6, 15)
        )

        assertTrue(justAfterBirthday < justBeforeBirthday)
    }
}
