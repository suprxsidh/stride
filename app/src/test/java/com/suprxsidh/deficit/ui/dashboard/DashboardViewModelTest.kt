package com.suprxsidh.deficit.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        viewModel = DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao()) { java.time.LocalDateTime.of(2026, 8, 10, 12, 0) },
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao())
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // DashboardViewModel's flows use SharingStarted.WhileSubscribed(5_000), matching the
    // convention in FoodLogViewModel/OnboardingViewModel. That policy only starts collecting
    // the upstream Room flow once something actively subscribes to the resulting StateFlow —
    // in the real app this happens immediately via DashboardScreen's collectAsState(), but in a
    // test that only reads `.value` there is no subscriber. backgroundScope.launch { ... collect {} }
    // establishes that subscriber (backgroundScope is auto-cancelled when the test ends) so the
    // upstream flow actually starts and `.value` reflects real emissions.
    @Test
    fun `profile is null before onboarding`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.profile.value)
    }

    @Test
    fun `today buffered total reflects logged entries`() = runTest(testDispatcher) {
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", offBarcode = null, loggedAt = 1L))
        backgroundScope.launch { viewModel.todayBufferedTotal.collect {} }
        // FoodRepository.observeTodayBufferedTotal() now polls the logical date on a `while (true) { delay(...) }`
        // loop (see FoodRepository.todayKeyFlow) so the day boundary re-evaluates without an app restart.
        // advanceUntilIdle() would never return against that — it keeps advancing the virtual clock through
        // every future poll forever. runCurrent() drains everything scheduled at the *current* virtual instant
        // (the initial emission + the Room query chain), which is all this test needs.
        testDispatcher.scheduler.runCurrent()
        assertEquals(220, viewModel.todayBufferedTotal.value)
    }

    @Test
    fun `after onboarding the profile reflects the computed soft budget`() = runTest(testDispatcher) {
        UserProfileRepository(db.userProfileDao(), db.weighInDao()).completeOnboarding(178.0, 80.0, 26, Sex.MALE)
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1645, viewModel.profile.value?.softBudgetKcal)
    }
}
