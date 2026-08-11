package com.suprxsidh.deficit.health

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.suprxsidh.deficit.DeficitApp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSyncWorkerTest {

    @After
    fun tearDown() {
        HealthConnectSyncWorker.repositoryProvider = { null }
    }

    @Test
    fun `doWork returns success when no repository is available`() = runTest {
        HealthConnectSyncWorker.repositoryProvider = { null }
        val context = ApplicationProvider.getApplicationContext<DeficitApp>()
        val worker = TestListenableWorkerBuilder<HealthConnectSyncWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
