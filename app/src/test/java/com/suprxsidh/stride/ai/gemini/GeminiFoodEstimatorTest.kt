package com.suprxsidh.stride.ai.gemini

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun `estimate parses per-item and total protein grams when present`() = runTest {
        val geminiJsonBody =
            """{"items":[{"name":"2 rotis","kcal":180,"proteinG":6.0},{"name":"dal tadka","kcal":220,"proteinG":12.0}],"totalKcal":400,"totalProteinG":18.0,"confidence":"medium"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        val estimate = estimator.estimate(apiKey = "test-key", description = "2 rotis, dal tadka", photoBase64 = null)

        assertEquals(18.0, estimate.totalProteinG, 0.001)
        assertEquals(6.0, estimate.items[0].proteinG, 0.001)
        assertEquals(12.0, estimate.items[1].proteinG, 0.001)
    }

    @Test
    fun `estimate defaults protein to zero when Gemini's response omits it (pre-existing shape)`() = runTest {
        // Regression guard: responses shaped like they were before Feature C must still parse,
        // not throw, since ignoreUnknownKeys/defaults are what keeps old callers compatible.
        val geminiJsonBody =
            """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        val estimate = estimator.estimate(apiKey = "test-key", description = "2 rotis", photoBase64 = null)

        assertEquals(0.0, estimate.totalProteinG, 0.001)
        assertEquals(0.0, estimate.items[0].proteinG, 0.001)
    }

    @Test
    fun `estimate requests a proteinG field in the response schema`() = runTest {
        val geminiJsonBody =
            """{"items":[{"name":"2 rotis","kcal":180,"proteinG":6.0}],"totalKcal":180,"totalProteinG":6.0,"confidence":"medium"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        estimator.estimate(apiKey = "test-key", description = "2 rotis", photoBase64 = null)

        val recorded = server.takeRequest()
        val sentBody = recorded.body.readUtf8()
        assertTrue(sentBody.contains("\"proteinG\""))
        assertTrue(sentBody.contains("\"totalProteinG\""))
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

    @Test
    fun `estimate sends responseMimeType and camelCase field names in the request body`() = runTest {
        val geminiJsonBody =
            """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        estimator.estimate(apiKey = "test-key", description = "2 rotis", photoBase64 = null)

        val recorded = server.takeRequest()
        val sentBody = recorded.body.readUtf8()
        assertTrue(sentBody.contains("\"responseMimeType\":\"application/json\""))
        assertTrue(sentBody.contains("\"responseSchema\""))
        // must not regress back to snake_case field names
        assertTrue(!sentBody.contains("response_mime_type"))
        assertTrue(!sentBody.contains("response_schema"))
    }

    @Test
    fun `estimate sends camelCase inlineData and mimeType when a photo is included`() = runTest {
        val geminiJsonBody =
            """{"items":[{"name":"rice","kcal":200}],"totalKcal":200,"confidence":"low"}"""
        val wrapped =
            """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        estimator.estimate(apiKey = "test-key", description = null, photoBase64 = "ZmFrZS1iYXNlNjQ=")

        val recorded = server.takeRequest()
        val sentBody = recorded.body.readUtf8()
        assertTrue(sentBody.contains("\"inlineData\""))
        assertTrue(sentBody.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(!sentBody.contains("inline_data"))
        assertTrue(!sentBody.contains("mime_type"))
    }
}
