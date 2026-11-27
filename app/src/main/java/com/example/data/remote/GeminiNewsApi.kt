package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GeminiPartDto(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContentDto(val parts: List<GeminiPartDto>)

@JsonClass(generateAdapter = true)
data class GeminiSchemaPropertyDto(
    val type: String,
    val items: GeminiSchemaObjectDto? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSchemaObjectDto(
    val type: String,
    val properties: Map<String, GeminiSchemaPropertyDto>? = null,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfigDto(
    val temperature: Double = 0.1,
    val responseMimeType: String = "application/json",
    val responseSchema: GeminiSchemaObjectDto
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    val contents: List<GeminiContentDto>,
    val systemInstruction: GeminiContentDto,
    val generationConfig: GeminiGenerationConfigDto
)

@JsonClass(generateAdapter = true)
data class GeminiCandidateDto(
    val content: GeminiContentDto? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidateDto>? = null
)

@JsonClass(generateAdapter = true)
data class ScoredHeadlineItemDto(
    val id: Int,
    val direction: Double,
    val materiality: Double,
    val confidence: Double? = null,
    val rationale: String = ""
)

@JsonClass(generateAdapter = true)
data class ScoredNewsPayloadDto(
    val items: List<ScoredHeadlineItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class ThreatNarrativePayloadDto(
    val category: String = "none",
    val materiality: Double = 0.0,
    val structural: Boolean = false,
    val company_specific: Boolean = false,
    val persistence_days: Int = 0,
    val confidence: Double = 0.5,
    val summary: String = ""
)

interface GeminiNewsApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun scoreHeadlines(
        @Path("model") model: String = "gemini-2.5-flash",
        @Query("key") apiKey: String,
        @Body body: GeminiGenerateRequest
    ): GeminiGenerateResponse
}
