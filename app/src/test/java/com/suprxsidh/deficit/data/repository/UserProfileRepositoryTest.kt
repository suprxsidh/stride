package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
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
class UserProfileRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: UserProfileRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = UserProfileRepository(db.userProfileDao()) { LocalDateTime.of(2026, 8, 10, 9, 0) }
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `completing onboarding computes soft budget from Mifflin-St Jeor and stores it`() = runTest {
        val profile = repo.completeOnboarding(heightCm = 178.0, weightKg = 80.0, age = 26, sex = Sex.MALE)
        assertEquals(1645, profile.softBudgetKcal)
        assertEquals(70.0, profile.goalWeightKg, 0.001) // default = weightKg - 10
    }

    @Test
    fun `explicit goal weight overrides the default`() = runTest {
        val profile = repo.completeOnboarding(heightCm = 165.0, weightKg = 65.0, age = 26, sex = Sex.FEMALE, goalWeightKg = 60.0)
        assertEquals(60.0, profile.goalWeightKg, 0.001)
    }
}
