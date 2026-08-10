package com.suprxsidh.deficit.data.calc

import kotlin.math.floor

object CalorieMath {

    // Integer ceiling-division (rawKcal * 11 + 9) / 10, NOT ceil(rawKcal * 1.10) —
    // the double multiplication drifts above exact values (450*1.10 == 495.00000000000006
    // in IEEE 754), which makes ceil() overshoot by 1 on round numbers. Pure integer
    // math has no such drift.
    fun bufferedKcal(rawKcal: Int): Int = (rawKcal * 11 + 9) / 10

    fun creditedExerciseKcal(realKcal: Int): Int = floor(realKcal / 2.0).toInt()

    fun bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
    }

    fun tdee(bmr: Double): Double = bmr * 1.2

    fun softBudgetKcal(tdee: Double): Int {
        val raw = floor(tdee - 500).toInt()
        return maxOf(1500, raw)
    }
}
