package com.example.data.service

import com.example.BuildConfig
import com.example.data.remote.FinnhubApi
import com.example.data.remote.FinnhubQuoteDto
import com.example.data.remote.YahooChartClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WatchlistQuoteSnippet(
    val symbol: String,
    val currentPrice: Double,
    val changeDollar: Double,
    val changePct: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val dayOpen: Double,
    val prevClose: Double,
    val updatedAtMs: Long,
    val sourceLabel: String,
    val isFinnhubLive: Boolean,
    val errorMessage: String? = null
) {
    /**
     * Normalized position (0f..1f) of [currentPrice] within the intraday [dayLow]..[dayHigh] range.
     */
    val dayRangePosition: Float
        get() {
            val span = dayHigh - dayLow
            if (!span.isFinite() || span <= 1e-6) return 0.5f
            return ((currentPrice - dayLow) / span).toFloat().coerceIn(0f, 1f)
        }
}

data class WatchlistQuotesBatchResult(
    val quotesBySymbol: Map<String, WatchlistQuoteSnippet>,
    val finnhubConnected: Boolean,
    val feedLabel: String,
    val fetchedAtMs: Long,
    val warning: String? = null
)

/**
 * Abstraction for fetching single-ticker Finnhub and fallback quotes so the service is fully unit-testable.
 */
interface FinnhubQuoteFetcher {
    suspend fun fetchFinnhubQuote(symbol: String, token: String): FinnhubQuoteDto?
    suspend fun fetchFallbackQuote(symbol: String): WatchlistQuoteSnippet?
}

class DefaultFinnhubQuoteFetcher : FinnhubQuoteFetcher {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val finnhubApi: FinnhubApi = Retrofit.Builder()
        .baseUrl("https://finnhub.io/api/v1/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(FinnhubApi::class.java)
    private val yahooClient = YahooChartClient(okHttp)

    override suspend fun fetchFinnhubQuote(symbol: String, token: String): FinnhubQuoteDto? {
        return finnhubApi.getQuote(symbol = symbol, token = token)
    }

    override suspend fun fetchFallbackQuote(symbol: String): WatchlistQuoteSnippet? {
        val series = yahooClient.fetchChart(symbol = symbol, range = "5d", interval = "1d") ?: return null
        val prev = series.previousClose.takeIf { it > 0.0 } ?: series.currentPrice
        val delta = series.currentPrice - prev
        val pct = if (prev > 0.0) (delta / prev) * 100.0 else 0.0
        return WatchlistQuoteSnippet(
            symbol = symbol,
            currentPrice = series.currentPrice,
            changeDollar = delta,
            changePct = pct,
            dayHigh = maxOf(series.dayHigh, series.currentPrice),
            dayLow = minOf(series.dayLow, series.currentPrice),
            dayOpen = prev,
            prevClose = prev,
            updatedAtMs = series.updatedAtMs,
            sourceLabel = "YAHOO LIVE",
            isFinnhubLive = false
        )
    }
}

/**
 * Fetches real-time quote snippets for the user's favorite watchlist tickers via the
 * Finnhub `/api/v1/quote` REST API (with automatic live fallback if no Finnhub API key
 * is entered yet).
 */
class FinnhubWatchlistQuoteService(
    private val fetcher: FinnhubQuoteFetcher = DefaultFinnhubQuoteFetcher(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun fetchQuotes(
        symbols: List<String>,
        userFinnhubKey: String
    ): WatchlistQuotesBatchResult = withContext(Dispatchers.IO) {
        val nowMs = clockMs()
        val cleanSymbols = symbols
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()

        if (cleanSymbols.isEmpty()) {
            return@withContext WatchlistQuotesBatchResult(
                quotesBySymbol = emptyMap(),
                finnhubConnected = false,
                feedLabel = "EMPTY",
                fetchedAtMs = nowMs
            )
        }

        val token = resolveFinnhubToken(userFinnhubKey)
        val hasFinnhubKey = token.isNotBlank()

        var finnhubHits = 0
        var finnhubErrors = 0

        val snippets: List<WatchlistQuoteSnippet> = coroutineScope {
            cleanSymbols.map { sym ->
                async {
                    if (hasFinnhubKey) {
                        val dto = try {
                            fetcher.fetchFinnhubQuote(sym, token)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            finnhubErrors++
                            null
                        }
                        val mapped = dto?.let { mapFinnhubDto(sym, it, nowMs) }
                        if (mapped != null) {
                            finnhubHits++
                            return@async mapped
                        }
                    }

                    // Fallback to public live quote feed if Finnhub key isn't set or returned no quote
                    val fallback = try {
                        fetcher.fetchFallbackQuote(sym)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    fallback ?: WatchlistQuoteSnippet(
                        symbol = sym,
                        currentPrice = 0.0,
                        changeDollar = 0.0,
                        changePct = 0.0,
                        dayHigh = 0.0,
                        dayLow = 0.0,
                        dayOpen = 0.0,
                        prevClose = 0.0,
                        updatedAtMs = nowMs,
                        sourceLabel = "UNAVAILABLE",
                        isFinnhubLive = false,
                        errorMessage = "Quote unavailable"
                    )
                }
            }.awaitAll()
        }

        val map = LinkedHashMap<String, WatchlistQuoteSnippet>(snippets.size)
        snippets.forEach { map[it.symbol] = it }

        val finnhubConnected = finnhubHits > 0
        val feedLabel = when {
            finnhubConnected -> "FINNHUB LIVE"
            snippets.any { it.currentPrice > 0.0 } -> "LIVE FALLBACK"
            else -> "OFFLINE"
        }
        val warning = when {
            !hasFinnhubKey -> "Add your Finnhub API key in Settings for direct Finnhub real-time quotes."
            finnhubErrors > 0 && !finnhubConnected -> "Finnhub quote request failed — check your Finnhub API key or rate limit."
            else -> null
        }

        WatchlistQuotesBatchResult(
            quotesBySymbol = map,
            finnhubConnected = finnhubConnected,
            feedLabel = feedLabel,
            fetchedAtMs = nowMs,
            warning = warning
        )
    }

    companion object {
        fun mapFinnhubDto(
            symbol: String,
            dto: FinnhubQuoteDto,
            nowMs: Long = System.currentTimeMillis()
        ): WatchlistQuoteSnippet? {
            if (!dto.c.isFinite() || dto.c <= 0.0) return null
            val prevClose = dto.pc.takeIf { it.isFinite() && it > 0.0 } ?: dto.c
            val changeDollar = dto.d?.takeIf { it.isFinite() } ?: (dto.c - prevClose)
            val changePct = dto.dp?.takeIf { it.isFinite() }
                ?: if (prevClose > 0.0) (changeDollar / prevClose) * 100.0 else 0.0
            val dayHigh = maxOf(dto.h.takeIf { it.isFinite() && it > 0.0 } ?: dto.c, dto.c)
            val dayLow = minOf(dto.l.takeIf { it.isFinite() && it > 0.0 } ?: dto.c, dto.c)
            val dayOpen = dto.o.takeIf { it.isFinite() && it > 0.0 } ?: prevClose
            val updatedMs = if (dto.t > 0L) dto.t * 1000L else nowMs

            return WatchlistQuoteSnippet(
                symbol = symbol.trim().uppercase(Locale.US),
                currentPrice = dto.c,
                changeDollar = changeDollar,
                changePct = changePct,
                dayHigh = dayHigh,
                dayLow = dayLow,
                dayOpen = dayOpen,
                prevClose = prevClose,
                updatedAtMs = updatedMs,
                sourceLabel = "FINNHUB LIVE",
                isFinnhubLive = true
            )
        }

        fun resolveFinnhubToken(userKey: String): String {
            val cleanUser = userKey.trim()
            if (cleanUser.isNotEmpty()) return cleanUser
            val buildKey = BuildConfig.FINNHUB_KEY.trim()
            val up = buildKey.uppercase(Locale.US)
            if (buildKey.isEmpty() || up.startsWith("YOUR_") || up.startsWith("MY_") || up.startsWith("REPLACE_") || up.startsWith("<")) {
                return ""
            }
            return buildKey
        }
    }
}
