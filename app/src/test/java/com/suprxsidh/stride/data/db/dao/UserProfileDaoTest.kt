package com.suprxsidh.stride.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
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
class UserProfileDaoTest {
    private lateinit var db: StrideDatabase
    private lateinit var dao: UserProfileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `get returns null before onboarding`() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun `upsert replaces the single row (id is always 1)`() = runTest {
        dao.upsert(UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 26, sex = "MALE", goalWeightKg = 70.0, softBudgetKcal = 1645, createdAt = 1L))
        dao.upsert(UserProfileEntity(heightCm = 178.0, weightKgAtStart = 79.5, age = 26, sex = "MALE", goalWeightKg = 70.0, softBudgetKcal = 1645, createdAt = 1L))

        val result = dao.get()
        assertEquals(79.5, result!!.weightKgAtStart, 0.001)
    }
}
