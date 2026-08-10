package com.suprxsidh.deficit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    weightRepository: WeightRepository
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
