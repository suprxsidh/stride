package com.suprxsidh.stride.ai.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String,
    val responseSchema: GeminiSchema
)

@Serializable
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    val required: List<String>? = null
)

@Serializable
data class GeminiGenerateContentResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiFoodEstimate(
    val items: List<GeminiFoodItem>,
    val totalKcal: Int,
    // Feature C (completeness pass, spec §4): protein floor. Defaults to 0.0 so responses from
    // before this field existed (and any test fixture JSON that doesn't set it) still decode.
    val totalProteinG: Double = 0.0,
    val confidence: String
)

@Serializable
data class GeminiFoodItem(val name: String, val kcal: Int, val proteinG: Double = 0.0)

class GeminiEstimationException(message: String, cause: Throwable? = null) : Exception(message, cause)
