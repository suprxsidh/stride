package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveBudgetRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repo: AdaptiveBudgetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        repo = AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(weightKg: Double, softBudgetKcal: Int) {
        db.userProfileDao().upsert(
            UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = weightKg, age = 29, sex = Sex.MALE.name,
                goalWeightKg = weightKg - 10, softBudgetKcal = softBudgetKcal, createdAt = 0L
            )
        )
    }

    @Test
    fun `recompute is a no-op with no profile or no weigh-ins`() = runTest {
        assertNull(repo.recomputeIfNoOverride())
        seedProfile(weightKg = 80.0, softBudgetKcal = 1850)
        assertNull(repo.recomputeIfNoOverride()) // still no weigh-ins to average
    }

    @Test
    fun `recompute updates the profile's budget when the rolling average has moved it`() = runTest {
        seedProfile(weightKg = 90.0, softBudgetKcal = 2000) // stale, set as if from a much higher starting weight
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }

        val newBudget = repo.recomputeIfNoOverride()
        assertEquals(newBudget, db.userProfileDao().get()?.softBudgetKcal)
        assert(newBudget != null && newBudget < 2000)
    }

    @Test
    fun `recompute is skipped while a manual override is set`() = runTest {
        seedProfile(weightKg = 90.0, softBudgetKcal = 2000)
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }
        settingsRepository.setManualBudgetOverrideKcal(1900)

        assertNull(repo.recomputeIfNoOverride())
        assertEquals(2000, db.userProfileDao().get()?.softBudgetKcal) // untouched, override still governs
    }

    @Test
    fun `setManualOverride writes straight through to the profile budget, clear triggers a fresh recompute`() = runTest {
        seedProfile(weightKg = 78.0, softBudgetKcal = 1850)
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }

        repo.setManualOverride(1700)
        assertEquals(1700, db.userProfileDao().get()?.softBudgetKcal)
        assertEquals(1700, settingsRepository.getManualBudgetOverrideKcal())

        repo.clearManualOverride()
        assertNull(settingsRepository.getManualBudgetOverrideKcal())
        assertEquals(1850, db.userProfileDao().get()?.softBudgetKcal) // recomputed from the still-78kg rolling average
    }
}
