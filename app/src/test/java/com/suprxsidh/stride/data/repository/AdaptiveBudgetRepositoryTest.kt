package com.suprxsidh.stride.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveBudgetRepositoryTest {
    private lateinit var db: StrideDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repo: AdaptiveBudgetRepository

    // Fixed "today" so age (derived from birthDate) is deterministic -- otherwise the expected
    // bmr/budget numbers below would drift with the real wall-clock date the tests happen to run on.
    private val today = LocalDate.of(2024, 1, 8)
    // Exactly age 29 as of [today] (same month/day, 2024 - 1995 = 29).
    private val birthDateForAge29 = "1995-01-08"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        repo = AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository, today = { today })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(weightKg: Double, softBudgetKcal: Int, birthDate: String = birthDateForAge29) {
        db.userProfileDao().upsert(
            UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = weightKg, birthDate = birthDate, sex = Sex.MALE.name,
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
        // Recomputed from the still-78kg rolling average: bmr=1752.5, tdee=2103.0, floor(2103-500)=1603.
        assertEquals(1603, db.userProfileDao().get()?.softBudgetKcal)
    }

    // Feature D (completeness pass, spec §5): the whole point of this feature -- a recompute
    // fires from weight *or* age drift now. This test holds weight/rolling-average fixed and
    // only advances "today" a year, simulating a birthday passing with no new weigh-in event.
    @Test
    fun `recompute reacts to age drift alone -- a birthday passing changes the budget with no new weigh-in`() = runTest {
        seedProfile(weightKg = 78.0, softBudgetKcal = 1850) // stale on purpose
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }

        val budgetAtAge29 = repo.recomputeIfNoOverride()
        assertEquals(1603, budgetAtAge29) // bmr=1752.5, tdee=2103.0, floor(2103-500)=1603 (age 29)

        val repoOneYearLater = AdaptiveBudgetRepository(
            db.userProfileDao(), db.weighInDao(), settingsRepository, today = { today.plusYears(1) }
        )
        val budgetAtAge30 = repoOneYearLater.recomputeIfNoOverride()
        // Same weight, one year older: bmr drops by 5, tdee by 6, budget by 6.
        assertEquals(1597, budgetAtAge30)
    }
}
