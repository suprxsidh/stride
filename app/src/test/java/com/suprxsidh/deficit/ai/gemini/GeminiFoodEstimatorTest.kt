package com.suprxsidh.deficit.ai.gemini

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiFoodEstimatorTest {
    private lateinit var server: MockWebServer
    private lateinit var estimator: GeminiFoodEstimator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = GeminiServiceFactory.create(baseUrl = server.url("/").toString())
        estimator = GeminiFoodEstimator(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `estimate parses itemized JSON response text part`() = runTest {
        val geminiJsonBody =
            """{"items":[{"name":"2 rotis","kcal":180},{"name":"dal tadka","kcal":220}],"totalKcal":400,"confidence":"medium"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        val estimate = estimator.estimate(apiKey = "test-key", description = "2 rotis, dal tadka", photoBase64 = null)

        assertEquals(400, estimate.totalKcal)
        assertEquals(2, estimate.items.size)
        assertEquals("2 rotis", estimate.items[0].name)
    }

    @Test
    fun `estimate throws GeminiEstimationException on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertThrows(GeminiEstimationException::class.java) {
            kotlinx.coroutines.runBlocking {
                estimator.estimate("test-key", "some food", null)
            }
        }
    }

    @Test
    fun `estimate throws IllegalArgumentException when description and photo both missing`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                estimator.estimate("test-key", null, null)
            }
        }
    }
}
