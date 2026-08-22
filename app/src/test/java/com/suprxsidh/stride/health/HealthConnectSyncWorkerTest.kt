package com.suprxsidh.stride.health

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.suprxsidh.stride.StrideApp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSyncWorkerTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<StrideApp>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        HealthConnectSyncWorker.repositoryProvider = { null }
    }

    @Test
    fun `doWork returns success when no repository is available`() = runTest {
        HealthConnectSyncWorker.repositoryProvider = { null }
        val context = ApplicationProvider.getApplicationContext<StrideApp>()
        val worker = TestListenableWorkerBuilder<HealthConnectSyncWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    // Both the onboarding permission-grant callback and the dashboard's settings-deep-link
    // re-grant path call these two functions directly to work around the sync worker otherwise
    // only ever being scheduled from a cold MainActivity.onCreate start. This locks in that the
    // functions themselves actually enqueue real, named WorkManager requests -- regression
    // protection for the scheduling primitives both call sites depend on.
    @Test
    fun `schedulePeriodic enqueues the named periodic work`() {
        val context = ApplicationProvider.getApplicationContext<StrideApp>()
        HealthConnectSyncWorker.schedulePeriodic(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(HealthConnectSyncWorker.PERIODIC_WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `triggerOneOff enqueues the named one-off work`() {
        val context = ApplicationProvider.getApplicationContext<StrideApp>()
        HealthConnectSyncWorker.triggerOneOff(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(HealthConnectSyncWorker.WORK_NAME)
            .get()

        assertEquals(1, infos.size)
        assertTrue(infos.single().state.isFinished || infos.single().state == WorkInfo.State.ENQUEUED)
    }
}
