package com.suprxsidh.stride.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightRepositoryTest {
    private lateinit var db: StrideDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WeightRepository(db.weighInDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `logging a second weigh-in same day overwrites, not duplicates`() = runTest {
        val sameDayClock = { LocalDateTime.of(2026, 8, 10, 7, 0) }
        val repoWithClock = WeightRepository(db.weighInDao(), sameDayClock)
        repoWithClock.logWeighIn(81.8)
        repoWithClock.logWeighIn(81.4) // correction later same morning

        val all = db.weighInDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(81.4, all[0].weightKg, 0.001)
    }

    @Test
    fun `total change since start is latest minus first, null with fewer than 2 points`() = runTest {
        assertEquals(null, repo.observeTotalChangeSinceStart().first())

        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val change = repo.observeTotalChangeSinceStart().first()
        assertEquals(-4.0, change!!, 0.001)
    }

    @Test
    fun `rolling average series delegates to RollingAverage over stored weigh-ins`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-08-01", weightKg = 82.0))
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-08-02", weightKg = 80.0))

        val series = repo.observeRollingAverageSeries().first()
        assertEquals(2, series.size)
        assertEquals(81.0, series.last().second, 0.001) // average of the two points, both within the trailing window
    }

    @Test
    fun `four-week trend is DOWN when the reference point ~28 days back is meaningfully heavier`() = runTest {
        // Points are spaced more than 7 days apart, so each one's own rolling average equals itself --
        // this isolates the trend-window logic from the rolling-average logic already covered above.
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-07-13", weightKg = 85.0)) // ~28 days before the latest point
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val trend = repo.observeFourWeekTrend().first()
        assertEquals(TrendDirection.DOWN, trend)
    }

    @Test
    fun `four-week trend is null with fewer than 2 rolling-average points`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.stride.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))
        assertEquals(null, repo.observeFourWeekTrend().first())
    }

    // Feature D (completeness pass, spec §5): recomputeIfNoOverride() previously only ever fired
    // from AdaptiveBudgetRepository.clearManualOverride() -- a weigh-in never fed back into the
    // adaptive budget at all. Wired at this (repository) layer -- see the constructor comment on
    // WeightRepository for why here and not WeightViewModel.
    @Test
    fun `logWeighIn triggers the wired AdaptiveBudgetRepository recompute`() = runTest {
        val fixedClock = { LocalDateTime.of(2026, 8, 10, 7, 0) }
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        val adaptiveBudgetRepository = AdaptiveBudgetRepository(
            db.userProfileDao(), db.weighInDao(), settingsRepository, today = { LocalDate.of(2026, 8, 10) }
        )
        db.userProfileDao().upsert(
            com.suprxsidh.stride.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 90.0, birthDate = "1997-08-10", // age 29 as of 2026-08-10
                sex = com.suprxsidh.stride.data.calc.Sex.MALE.name, goalWeightKg = 80.0, softBudgetKcal = 2000, createdAt = 0L
            )
        )
        val repoWithBudget = WeightRepository(db.weighInDao(), fixedClock, adaptiveBudgetRepository)

        repoWithBudget.logWeighIn(78.0)

        // bmr=1752.5, tdee=2103.0, floor(2103-500)=1603 -- recomputed purely as a side effect of
        // logging the weigh-in, no separate manual recompute call.
        assertEquals(1603, db.userProfileDao().get()?.softBudgetKcal)
    }

    @Test
    fun `logWeighIn does not crash when no AdaptiveBudgetRepository is wired`() = runTest {
        repo.logWeighIn(80.0) // repo built in setUp has no adaptiveBudgetRepository (defaults to null)
        assertEquals(80.0, db.weighInDao().observeAll().first().first().weightKg, 0.001)
    }
}
