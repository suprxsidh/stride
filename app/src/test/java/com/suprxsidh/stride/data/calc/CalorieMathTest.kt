package com.suprxsidh.stride.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieMathTest {

    @Test
    fun `buffered kcal adds 10 percent rounded up`() {
        assertEquals(495, CalorieMath.bufferedKcal(450))
        assertEquals(257, CalorieMath.bufferedKcal(233)) // 256.3 -> ceil verifies rounding path
        assertEquals(0, CalorieMath.bufferedKcal(0))
    }

    @Test
    fun `credited exercise kcal is half of real, rounded down`() {
        assertEquals(150, CalorieMath.creditedExerciseKcal(300))
        assertEquals(150, CalorieMath.creditedExerciseKcal(301))
        assertEquals(0, CalorieMath.creditedExerciseKcal(0))
    }

    @Test
    fun `bmr uses Mifflin-St Jeor with sex-specific constant`() {
        val maleBmr = CalorieMath.bmr(weightKg = 80.0, heightCm = 178.0, age = 26, sex = Sex.MALE)
        assertEquals(1787.5, maleBmr, 0.01)

        val femaleBmr = CalorieMath.bmr(weightKg = 65.0, heightCm = 165.0, age = 26, sex = Sex.FEMALE)
        assertEquals(1390.25, femaleBmr, 0.01)
    }

    @Test
    fun `tdee is bmr times 1_2 sedentary always`() {
        assertEquals(2145.0, CalorieMath.tdee(1787.5), 0.01)
    }

    @Test
    fun `soft budget is tdee minus 500 floored at 1500`() {
        assertEquals(1645, CalorieMath.softBudgetKcal(2145.0))
        assertEquals(1500, CalorieMath.softBudgetKcal(1900.0)) // 1900-500=1400 < floor
        assertEquals(1500, CalorieMath.softBudgetKcal(1200.0))
    }
}
