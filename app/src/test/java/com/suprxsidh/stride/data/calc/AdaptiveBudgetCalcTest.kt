package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBudgetCalcTest {
    @Test
    fun `recompute matches the existing BMR-TDEE-budget chain`() {
        val expectedBmr = CalorieMath.bmr(weightKg = 78.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        val expectedBudget = CalorieMath.softBudgetKcal(CalorieMath.tdee(expectedBmr))

        assertEquals(
            expectedBudget,
            AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 78.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        )
    }

    @Test
    fun `a lower rolling-average weight lowers the budget but never below 1500`() {
        val higher = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 90.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        val lower = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 60.0, heightCm = 178.0, age = 29, sex = Sex.MALE)

        assertTrue(lower < higher)
        assertTrue(lower >= 1500)
    }
}
