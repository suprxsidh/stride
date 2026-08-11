package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReviewRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repo: WeeklyReviewRepository

    // 2024-01-08 is a Monday; "now" is the following Monday, so the week of 2024-01-08..14 just ended.
    private val clock = { LocalDateTime.of(2024, 1, 15, 8, 0) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        val weeklyCommitmentRepository = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock)
        val adaptiveBudgetRepository = AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
        repo = WeeklyReviewRepository(
            db.weeklyReviewDao(), weeklyCommitmentRepository, adaptiveBudgetRepository,
            db.foodEntryDao(), db.weighInDao(), db.userProfileDao(), settingsRepository, clock
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(createdAt: Long) {
        db.userProfileDao().upsert(
            UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name,
                goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = createdAt
            )
        )
    }

    @Test
    fun `generates a review for the week that just ended, with runs, days logged, and deficit`() = runTest {
        seedProfile(createdAt = 0L) // long before the reviewed week, so the guard doesn't skip it
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-08", name = "lunch", rawKcal = 500, bufferedKcal = 550, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-09", name = "dinner", rawKcal = 400, bufferedKcal = 440, source = "manual", offBarcode = null, loggedAt = 2L))

        val review = repo.generateForCompletedWeekIfDue()

        assertEquals("2024-01-08", review?.weekStartDate)
        assertEquals(1, review?.runsCompleted)
        assertEquals(2, review?.daysLogged)
        // avg of (1850-550) and (1850-440) = avg of 1300 and 1410
        assertEquals(1355.0, requireNotNull(review?.avgDailyDeficitKcal), 0.01)
        assertEquals(WeekOutcome.BROKEN.name, review?.outcome) // 1 run < default floor of 3
    }

    @Test
    fun `calling twice does not regenerate, returns the same stored review`() = runTest {
        seedProfile(createdAt = 0L)
        val first = repo.generateForCompletedWeekIfDue()
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-new", date = "2024-01-09", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        val second = repo.generateForCompletedWeekIfDue()
        assertEquals(first?.runsCompleted, second?.runsCompleted) // unaffected by the run added after generation
    }

    @Test
    fun `skips generation entirely for a week that predates onboarding`() = runTest {
        // Profile created 2024-01-14 (Sunday), inside the reviewed week -> the whole week predates the app.
        seedProfile(createdAt = LocalDateTime.of(2024, 1, 14, 9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertNull(repo.generateForCompletedWeekIfDue())
        assertEquals(0, db.weeklyReviewDao().observeAll().first().size)
    }

    @Test
    fun `unseenReview and markReviewSeen round-trip`() = runTest {
        seedProfile(createdAt = 0L)
        val generated = repo.generateForCompletedWeekIfDue()

        assertEquals(generated?.weekStartDate, repo.unseenReview()?.weekStartDate)
        repo.markReviewSeen(requireNotNull(generated).weekStartDate)
        assertNull(repo.unseenReview())
    }
}
