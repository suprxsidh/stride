package com.suprxsidh.deficit.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        viewModel = SettingsViewModel(SettingsRepository(db.appSettingsDao()))
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `saveGeminiApiKey updates the observed state`() = runTest {
        backgroundScope.launch { viewModel.geminiApiKey.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveGeminiApiKey("my-key")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("my-key", viewModel.geminiApiKey.value)
    }
}
