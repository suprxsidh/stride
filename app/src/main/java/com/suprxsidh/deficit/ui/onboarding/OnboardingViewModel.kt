package com.suprxsidh.deficit.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.repository.UserProfileRepository

class OnboardingViewModel(private val repository: UserProfileRepository) : ViewModel() {
    var heightCm by mutableStateOf("")
    var weightKg by mutableStateOf("")
    var age by mutableStateOf("")
    var sex by mutableStateOf(Sex.MALE)
    var goalWeightKg by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun submit(onDone: () -> Unit) {
        val h = heightCm.toDoubleOrNull()
        val w = weightKg.toDoubleOrNull()
        val a = age.toIntOrNull()
        if (h == null || h <= 0.0 || w == null || w <= 0.0 || a == null || a <= 0) {
            error = "Enter a valid height, weight, and age."
            return
        }
        error = null
        val goal = goalWeightKg.toDoubleOrNull()
        repository.completeOnboarding(heightCm = h, weightKg = w, age = a, sex = sex, goalWeightKg = goal)
        onDone()
    }
}
