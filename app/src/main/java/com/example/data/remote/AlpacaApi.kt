package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AlpacaBarDto(
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double
)

@JsonClass(generateAdapter = true)
data class AlpacaBarsResponse(
    val bars: Map<String, List<AlpacaBarDto>>? = null
)

@JsonClass(generateAdapter = true)
data class AlpacaTradeDto(
    val p: Double? = null
)

@JsonClass(generateAdapter = true)
data class AlpacaQuoteDto(
    val bp: Double? = null,
    val ap: Double? = null
)

@JsonClass(generateAdapter = true)
data class AlpacaSnapshotDto(
    val latestTrade: AlpacaTradeDto? = null,
    val latestQuote: AlpacaQuoteDto? = null,
    val dailyBar: AlpacaBarDto? = null,
    val prevDailyBar: AlpacaBarDto? = null
)

@JsonClass(generateAdapter = true)
data class AlpacaNewsItemDto(
    val headline: String = "",
    val summary: String? = null,
    val created_at: String = "",
    val source: String? = null
)

@JsonClass(generateAdapter = true)
data class AlpacaNewsResponse(
    val news: List<AlpacaNewsItemDto>? = null
)

interface AlpacaApi {
    @GET("v2/stocks/bars")
    suspend fun getBars(
        @Header("APCA-API-KEY-ID") keyId: String,
        @Header("APCA-API-SECRET-KEY") secret: String,
        @Query("symbols") symbols: String,
        @Query("timeframe") timeframe: String = "1Day",
        @Query("limit") limit: Int = 320,
        @Query("adjustment") adjustment: String = "all",
        @Query("feed") feed: String = "iex",
        @Query("start") start: String
    ): AlpacaBarsResponse

    @GET("v2/stocks/snapshots")
    suspend fun getSnapshots(
        @Header("APCA-API-KEY-ID") keyId: String,
        @Header("APCA-API-SECRET-KEY") secret: String,
        @Query("symbols") symbols: String,
        @Query("feed") feed: String = "iex"
    ): Map<String, AlpacaSnapshotDto>

    @GET("v1beta1/news")
    suspend fun getNews(
        @Header("APCA-API-KEY-ID") keyId: String,
        @Header("APCA-API-SECRET-KEY") secret: String,
        @Query("symbols") symbols: String,
        @Query("limit") limit: Int = 8,
        @Query("start") start: String
    ): AlpacaNewsResponse
}
