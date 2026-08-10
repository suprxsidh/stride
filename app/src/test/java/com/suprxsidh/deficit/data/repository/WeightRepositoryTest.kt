package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
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

        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val change = repo.observeTotalChangeSinceStart().first()
        assertEquals(-4.0, change!!, 0.001)
    }

    @Test
    fun `rolling average series delegates to RollingAverage over stored weigh-ins`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-01", weightKg = 82.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-02", weightKg = 80.0))

        val series = repo.observeRollingAverageSeries().first()
        assertEquals(2, series.size)
        assertEquals(81.0, series.last().second, 0.001) // average of the two points, both within the trailing window
    }

    @Test
    fun `four-week trend is DOWN when the reference point ~28 days back is meaningfully heavier`() = runTest {
        // Points are spaced more than 7 days apart, so each one's own rolling average equals itself --
        // this isolates the trend-window logic from the rolling-average logic already covered above.
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-13", weightKg = 85.0)) // ~28 days before the latest point
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val trend = repo.observeFourWeekTrend().first()
        assertEquals(TrendDirection.DOWN, trend)
    }

    @Test
    fun `four-week trend is null with fewer than 2 rolling-average points`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))
        assertEquals(null, repo.observeFourWeekTrend().first())
    }
}
