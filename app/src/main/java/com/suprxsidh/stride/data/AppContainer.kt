package com.suprxsidh.stride.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.ai.gemini.GeminiServiceFactory
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import com.suprxsidh.stride.health.HealthConnectDataSource
import com.suprxsidh.stride.health.HealthConnectManager

class AppContainer(private val context: Context) {
    private val database = StrideDatabase.getInstance(context)
    val userProfileRepository = UserProfileRepository(database.userProfileDao(), database.weighInDao())
    val foodRepository = FoodRepository(database.foodEntryDao(), database.customFoodDao())
    val weightRepository = WeightRepository(database.weighInDao())
    val settingsRepository = SettingsRepository(database.appSettingsDao())
    val adaptiveBudgetRepository = AdaptiveBudgetRepository(database.userProfileDao(), database.weighInDao(), settingsRepository)
    val geminiFoodRepository = GeminiFoodRepository(
        estimator = GeminiFoodEstimator(GeminiServiceFactory.create()),
        settingsRepository = settingsRepository,
        foodRepository = foodRepository,
        pendingDraftDao = database.pendingDraftDao()
    )

    val healthConnectAvailability: Int = HealthConnectManager.availability(context)

    // Nullable: Health Connect may not be installed/available on this device. Only construct
    // the real client-backed data source when the SDK reports SDK_AVAILABLE; otherwise leave
    // this null and have callers (Tasks 6/7/8/9) null-check and show an "unavailable" UI state
    // instead of crashing AppContainer construction.
    val healthConnectRepository: HealthConnectRepository? =
        if (healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectRepository(
                dataSource = HealthConnectDataSource(HealthConnectManager.getClient(context)),
                syncStateDao = database.syncStateDao(),
                weighInDao = database.weighInDao()
            )
        } else {
            null
        }

    suspend fun hasHealthConnectPermissions(): Boolean =
        healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE && HealthConnectManager.hasAllPermissions(context)
}
