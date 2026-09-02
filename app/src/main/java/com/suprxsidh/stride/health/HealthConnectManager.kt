package com.suprxsidh.stride.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

object HealthConnectManager {
    /**
     * Exactly the record types [com.suprxsidh.stride.health.HealthConnectDataSource] actually
     * touches, and nothing more. [hasAllPermissions] is a `containsAll` check, so every extra type
     * listed here is another checkbox the user can decline in the Health Connect consent screen to
     * permanently block sync.
     */
    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class)
    )

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun getClient(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(context: Context): Boolean =
        getClient(context).permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)

    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()
}
