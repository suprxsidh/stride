package com.suprxsidh.deficit.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsServiceFactory
import com.suprxsidh.deficit.health.HealthConnectDataSource
import com.suprxsidh.deficit.health.HealthConnectManager

class AppContainer(private val context: Context) {
    private val database = DeficitDatabase.getInstance(context)
    val userProfileRepository = UserProfileRepository(database.userProfileDao(), database.weighInDao())
    val foodRepository = FoodRepository(database.foodEntryDao(), database.customFoodDao())
    val weightRepository = WeightRepository(database.weighInDao())
    val settingsRepository = SettingsRepository(database.appSettingsDao())
    val openFoodFactsRepository = OpenFoodFactsRepository(
        OpenFoodFactsServiceFactory.create(),
        database.offCacheDao()
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
                exerciseSessionDao = database.exerciseSessionDao(),
                syncStateDao = database.syncStateDao(),
                weighInDao = database.weighInDao()
            )
        } else {
            null
        }

    suspend fun hasHealthConnectPermissions(): Boolean =
        healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE && HealthConnectManager.hasAllPermissions(context)
}
