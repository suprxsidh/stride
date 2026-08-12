package com.suprxsidh.deficit.ui.consistency

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.repository.ConsistencyRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsistencyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: ConsistencyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).allowMainThreadQueries().build()
        // upsert/insert are suspend DAO functions; setUp() itself is not a suspend context, so the
        // synchronous seeding below runs on a runBlocking scope. The Room executor above is a
        // synchronous same-thread Executor, so this completes immediately with no risk of deadlocking
        // against the StandardTestDispatcher used for the ViewModel's own coroutines.
        runBlocking {
            db.userProfileDao().upsert(
                UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L)
            )
            db.exerciseSessionDao().insert(
                ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                    durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
            )
        }
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        val consistencyRepository = ConsistencyRepository(db.exerciseSessionDao(), db.foodEntryDao(), db.userProfileDao())
        viewModel = ConsistencyViewModel(consistencyRepository, settingsRepository, clock = { LocalDateTime.of(2024, 1, 10, 8, 0) })
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `loads the current month on init, showing the seeded run day`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.days.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2024, 1), viewModel.month.value)
        assertEquals(31, viewModel.days.value.size)
        assertEquals(true, viewModel.days.value.first { it.date.toString() == "2024-01-08" }.ran)
    }

    @Test
    fun `nextMonth and previousMonth reload for the adjacent month`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.days.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.nextMonth()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2024, 2), viewModel.month.value)
        assertEquals(29, viewModel.days.value.size) // 2024 is a leap year

        viewModel.previousMonth()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2024, 1), viewModel.month.value)
    }
}
