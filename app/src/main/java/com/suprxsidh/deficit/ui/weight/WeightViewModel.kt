package com.suprxsidh.deficit.ui.weight

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.TrendDirection
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeighInSyncSource
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeightViewModel(
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: WeighInSyncSource? = null,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    val rawSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRawSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rollingSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalChange: StateFlow<Double?> =
        weightRepository.observeTotalChangeSinceStart().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val trend: StateFlow<TrendDirection?> =
        weightRepository.observeFourWeekTrend().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Captured during onboarding (UserProfileEntity.goalWeightKg) but never surfaced anywhere in
    // the UI until now — see the "unused goalWeightKg display" Phase 1 polish item.
    val goalWeightKg: StateFlow<Double?> =
        (userProfileRepository?.observeProfile()?.map { it?.goalWeightKg } ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var weightInput by mutableStateOf("")

    /** Set when [logWeighIn] rejects the current input; null once a submit attempt validates. */
    var weighInError by mutableStateOf<String?>(null)
        private set

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    fun logWeighIn() {
        val weight = weightInput.toDoubleOrNull()
        if (weight == null || weight <= 0) {
            weighInError = "Enter a valid weight in kg."
            return
        }
        weighInError = null
        viewModelScope.launch {
            weightRepository.logWeighIn(weight)
            weightInput = ""
        }
    }

    /**
     * False when Health Connect isn't installed on this device, in which case there is no sync
     * source at all and the UI should say so rather than offer a button that does nothing.
     */
    val healthConnectAvailable: Boolean = healthConnectRepository != null

    fun syncWithHealthConnect() {
        val source = healthConnectRepository
        if (source == null) {
            _lastSyncResult.value = "Health Connect isn't available on this device"
            return
        }
        viewModelScope.launch {
            // The repository exists whenever Health Connect is *installed*, which says nothing about
            // whether permissions were granted — onboarding lets the user skip the grant. Reading or
            // writing without them throws SecurityException, which would otherwise escape
            // viewModelScope and crash the app.
            _lastSyncResult.value = try {
                val count = source.syncWeighIns()
                "Synced $count entries with Health Connect"
            } catch (e: SecurityException) {
                Log.w(TAG, "Health Connect sync denied", e)
                "Health Connect permissions needed — grant them in Settings"
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect sync failed", e)
                "Couldn't sync with Health Connect — try again later"
            }
        }
    }

    private companion object {
        const val TAG = "WeightViewModel"
    }
}
