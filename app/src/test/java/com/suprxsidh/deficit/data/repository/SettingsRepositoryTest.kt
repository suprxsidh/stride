package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
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
class SettingsRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SettingsRepository(db.appSettingsDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `getGeminiApiKey returns null before any key is set`() = runTest {
        assertNull(repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey then getGeminiApiKey round-trips the value`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        assertEquals("test-key-123", repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey with null clears the stored key`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        repository.setGeminiApiKey(null)
        assertNull(repository.getGeminiApiKey())
    }
}
