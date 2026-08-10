package com.suprxsidh.deficit.food.off

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class OpenFoodFactsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: DeficitDatabase
    private lateinit var repo: OpenFoodFactsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = OpenFoodFactsServiceFactory.create(baseUrl = server.url("/").toString())
        repo = OpenFoodFactsRepository(api, db.offCacheDao()) { 1_000L }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun `search parses products and caches them by code`() = runTest {
        val body = """
            {"products": [
                {"code": "8901058851031", "product_name": "Amul Chaas", "nutriments": {"energy-kcal_serving": 45.0}},
                {"code": "8901030812345", "product_name": "No kcal data", "nutriments": {}}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val results = repo.search("chaas")

        assertEquals(1, results.size) // the product with no usable kcal field is dropped
        assertEquals("Amul Chaas", results[0].productName)
        assertEquals(45, results[0].kcalPerServing)

        val cached = db.offCacheDao().get("8901058851031")
        assertEquals(1_000L, cached!!.cachedAt)
    }

    @Test
    fun `search on network failure returns an empty list, not a crash`() = runTest {
        server.shutdown() // nothing listening -> connection refused
        val results = repo.search("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search on a non-2xx response returns an empty list, not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val results = repo.search("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search on malformed JSON returns an empty list, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))
        val results = repo.search("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `a failed search falls back to the local cache instead of showing no results`() = runTest {
        val body = """
            {"products": [
                {"code": "8901058851031", "product_name": "Amul Chaas", "nutriments": {"energy-kcal_serving": 45.0}}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
        val firstResults = repo.search("chaas")
        assertEquals(1, firstResults.size) // cached successfully

        // Now simulate offline/failure: the exact same query should still surface the previously
        // cached product instead of an empty list.
        server.enqueue(MockResponse().setResponseCode(500))
        val offlineResults = repo.search("chaas")

        assertEquals(1, offlineResults.size)
        assertEquals("Amul Chaas", offlineResults[0].productName)
    }
}
