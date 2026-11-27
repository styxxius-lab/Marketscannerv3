package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class FinnhubArticleDto(
    val id: Long = 0L,
    val category: String = "",
    val datetime: Long = 0L,
    val headline: String = "",
    val image: String = "",
    val related: String = "",
    val source: String = "",
    val summary: String = "",
    val url: String = ""
)

@JsonClass(generateAdapter = true)
data class FinnhubQuoteDto(
    val c: Double = 0.0,   // Current price
    val d: Double? = null, // Change
    val dp: Double? = null,// Percent change
    val h: Double = 0.0,   // High price of the day
    val l: Double = 0.0,   // Low price of the day
    val o: Double = 0.0,   // Open price of the day
    val pc: Double = 0.0,  // Previous close price
    val t: Long = 0L       // Unix timestamp
)

interface FinnhubApi {
    @GET("news")
    suspend fun getMarketNews(
        @Query("category") category: String = "general",
        @Query("token") token: String
    ): List<FinnhubArticleDto>

    @GET("company-news")
    suspend fun getCompanyNews(
        @Query("symbol") symbol: String,
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("token") token: String
    ): List<FinnhubArticleDto>

    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String,
        @Query("token") token: String
    ): FinnhubQuoteDto
}
