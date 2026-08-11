package com.suprxsidh.deficit.data.calc

object AdaptiveBudgetCalc {
    fun recomputeSoftBudgetKcal(rollingAvgWeightKg: Double, heightCm: Double, age: Int, sex: Sex): Int {
        val bmr = CalorieMath.bmr(rollingAvgWeightKg, heightCm, age, sex)
        val tdee = CalorieMath.tdee(bmr)
        return CalorieMath.softBudgetKcal(tdee)
    }
}
