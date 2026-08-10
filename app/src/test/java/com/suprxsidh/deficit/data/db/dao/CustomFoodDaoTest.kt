package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomFoodDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: CustomFoodDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.customFoodDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observePinned only returns pinned foods, capped at 6`() = runTest {
        repeat(7) { i ->
            dao.upsert(CustomFoodEntity(name = "Pinned$i", kcalPerServing = 100, servingLabel = "1 cup", isPinned = true))
        }
        dao.upsert(CustomFoodEntity(name = "NotPinned", kcalPerServing = 50, servingLabel = "1 pc", isPinned = false))

        val pinned = dao.observePinned().first()
        assertEquals(6, pinned.size)
        assertTrue(pinned.all { it.name.startsWith("Pinned") })
    }

    @Test
    fun `observeAll includes both pinned and unpinned, ordered by name`() = runTest {
        dao.upsert(CustomFoodEntity(name = "Zucchini snack", kcalPerServing = 40, servingLabel = "1 cup"))
        dao.upsert(CustomFoodEntity(name = "Almonds", kcalPerServing = 160, servingLabel = "28g"))

        val all = dao.observeAll().first()
        assertEquals(listOf("Almonds", "Zucchini snack"), all.map { it.name })
    }
}
