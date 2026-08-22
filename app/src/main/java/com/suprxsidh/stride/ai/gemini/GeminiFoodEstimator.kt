package com.suprxsidh.stride.ai.gemini

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class GeminiFoodEstimator(
    private val api: GeminiApi,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun estimate(
        apiKey: String,
        description: String?,
        photoBase64: String?,
        model: String = DEFAULT_MODEL
    ): GeminiFoodEstimate {
        require(!description.isNullOrBlank() || !photoBase64.isNullOrBlank()) {
            "Need a text description or a photo to estimate a meal"
        }
        val parts = buildList {
            add(GeminiPart(text = PROMPT + (description?.let { "\n\nMeal: $it" } ?: "")))
            if (photoBase64 != null) add(GeminiPart(inlineData = GeminiInlineData("image/jpeg", photoBase64)))
        }
        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts)),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = FOOD_ESTIMATE_SCHEMA
            )
        )
        val response = try {
            api.generateContent(model, apiKey, request)
        } catch (e: HttpException) {
            throw GeminiEstimationException("Gemini request failed: HTTP ${e.code()}", e)
        } catch (e: IOException) {
            throw GeminiEstimationException("Gemini request failed: ${e.message}", e)
        }
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
            ?: throw GeminiEstimationException("Empty response from Gemini")
        return try {
            json.decodeFromString(GeminiFoodEstimate.serializer(), text)
        } catch (e: Exception) {
            throw GeminiEstimationException("Could not parse Gemini's response as the expected JSON shape", e)
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"

        private val PROMPT = """
            You are estimating calories for an Indian home-cooked meal. Return ONLY the requested JSON.
            Rules:
            - When portion size or ingredients are ambiguous, always estimate on the HIGH end, never the low end.
            - Assume standard Indian home-cooking defaults (ghee/oil used, typical home portion sizes) unless the description says otherwise.
            - "items" should list each distinct food item with its own kcal estimate.
            - "totalKcal" is the sum across items.
            - "confidence" is one of "low", "medium", "high".
        """.trimIndent()

        val FOOD_ESTIMATE_SCHEMA = GeminiSchema(
            type = "object",
            properties = mapOf(
                "items" to GeminiSchema(
                    type = "array",
                    items = GeminiSchema(
                        type = "object",
                        properties = mapOf(
                            "name" to GeminiSchema(type = "string"),
                            "kcal" to GeminiSchema(type = "integer")
                        ),
                        required = listOf("name", "kcal")
                    )
                ),
                "totalKcal" to GeminiSchema(type = "integer"),
                "confidence" to GeminiSchema(type = "string")
            ),
            required = listOf("items", "totalKcal", "confidence")
        )
    }
}
