package com.suprxsidh.stride.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.health.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DateTimeException
import java.time.LocalDate

class OnboardingViewModel(private val repository: UserProfileRepository) : ViewModel() {
    var heightCm by mutableStateOf("")
    var weightKg by mutableStateOf("")
    // Feature D (completeness pass, spec §5): a raw age number goes stale the moment it's typed
    // -- a birthdate lets the stored profile's derived age keep drifting correctly forever.
    // Three plain numeric fields (day/month/year), matching this screen's existing input style
    // (OutlinedTextField everywhere, no dialogs anywhere in this codebase) rather than
    // introducing Material3's DatePickerDialog machinery for a single field.
    var birthDay by mutableStateOf("")
    var birthMonth by mutableStateOf("")
    var birthYear by mutableStateOf("")
    var sex by mutableStateOf(Sex.MALE)
    var goalWeightKg by mutableStateOf("")
    // Feature C (completeness pass, spec §4): left blank -> repository defaults to 1.6 * weightKg.
    var proteinFloorG by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    private val _healthConnectPermissionsGranted = MutableStateFlow(false)
    val healthConnectPermissionsGranted: StateFlow<Boolean> = _healthConnectPermissionsGranted.asStateFlow()

    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        _healthConnectPermissionsGranted.value = granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
    }

    suspend fun submit(onDone: () -> Unit) {
        val h = heightCm.toDoubleOrNull()
        val w = weightKg.toDoubleOrNull()
        val birthDate = parseBirthDate()
        if (h == null || h <= 0.0 || w == null || w <= 0.0 || birthDate == null) {
            error = "Enter a valid height, weight, and birth date."
            return
        }
        error = null
        val goal = goalWeightKg.toDoubleOrNull()
        val proteinFloor = proteinFloorG.toDoubleOrNull()
        repository.completeOnboarding(
            heightCm = h,
            weightKg = w,
            birthDate = birthDate,
            sex = sex,
            goalWeightKg = goal,
            proteinFloorG = proteinFloor
        )
        onDone()
    }

    private fun parseBirthDate(): LocalDate? {
        val day = birthDay.toIntOrNull() ?: return null
        val month = birthMonth.toIntOrNull() ?: return null
        val year = birthYear.toIntOrNull() ?: return null
        return try {
            val date = LocalDate.of(year, month, day)
            if (date.isAfter(LocalDate.now())) null else date
        } catch (e: DateTimeException) {
            null
        }
    }
}
