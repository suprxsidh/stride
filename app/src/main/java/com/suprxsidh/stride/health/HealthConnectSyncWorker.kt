package com.suprxsidh.stride.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suprxsidh.stride.StrideApp
import java.util.concurrent.TimeUnit

class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = repositoryProvider(applicationContext) ?: return Result.success()
        return try {
            repository.syncExerciseSessions()
            repository.syncWeighIns()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "health_connect_sync"
        const val PERIODIC_WORK_NAME = "health_connect_sync_periodic"

        // Overridable seam for tests; production default reads the real AppContainer.
        var repositoryProvider: (Context) -> com.suprxsidh.stride.data.repository.HealthConnectRepository? =
            { context -> (context.applicationContext as? StrideApp)?.container?.healthConnectRepository }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerOneOff(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
