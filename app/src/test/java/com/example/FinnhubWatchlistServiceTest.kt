package com.example

import com.example.data.local.WatchlistDataStore
import com.example.data.remote.FinnhubQuoteDto
import com.example.data.service.FinnhubQuoteFetcher
import com.example.data.service.FinnhubWatchlistQuoteService
import com.example.data.service.WatchlistQuoteSnippet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinnhubWatchlistServiceTest {

    @Test
    fun parseTickers_sanitizesDeduplicatesAndFiltersInvalidSymbols() {
        val parsed = WatchlistDataStore.parseTickers(" nvda, \$aapl, brk.b , NVDA, !!!, 123BAD, msft ")
        assertEquals(listOf("NVDA", "AAPL", "BRK.B", "MSFT"), parsed)
    }

    @Test
    fun mapFinnhubDto_calculatesChangeAndDayRangePositionAccurately() {
        val dto = FinnhubQuoteDto(
            c = 135.0,
            d = 5.0,
            dp = 3.846,
            h = 140.0,
            l = 120.0,
            o = 128.0,
            pc = 130.0,
            t = 1_795_608_000L
        )
        val snippet = FinnhubWatchlistQuoteService.mapFinnhubDto("nvda", dto)
        assertNotNull(snippet)
        assertEquals("NVDA", snippet!!.symbol)
        assertEquals(135.0, snippet.currentPrice, 1e-6)
        assertEquals(5.0, snippet.changeDollar, 1e-6)
        assertEquals(3.846, snippet.changePct, 1e-4)
        assertEquals(0.75f, snippet.dayRangePosition, 1e-4f)
        assertTrue(snippet.isFinnhubLive)

        // Zero price from Finnhub (invalid ticker) returns null cleanly
        assertNull(FinnhubWatchlistQuoteService.mapFinnhubDto("INVALID", FinnhubQuoteDto(c = 0.0)))
    }

    @Test
    fun fetchQuotes_usesFinnhubWhenKeyPresentAndFallsBackCleanlyWithoutKey() = runBlocking {
        val fakeFetcher = object : FinnhubQuoteFetcher {
            override suspend fun fetchFinnhubQuote(symbol: String, token: String): FinnhubQuoteDto? {
                return when (symbol) {
                    "NVDA" -> FinnhubQuoteDto(c = 142.50, d = 4.20, dp = 3.04, h = 144.0, l = 138.0, o = 139.0, pc = 138.30, t = 1_795_608_000L)
                    "AAPL" -> FinnhubQuoteDto(c = 228.10, d = -1.40, dp = -0.61, h = 230.0, l = 227.5, o = 229.5, pc = 229.50, t = 1_795_608_000L)
                    else -> null
                }
            }

            override suspend fun fetchFallbackQuote(symbol: String): WatchlistQuoteSnippet {
                return WatchlistQuoteSnippet(
                    symbol = symbol,
                    currentPrice = 100.0,
                    changeDollar = 1.5,
                    changePct = 1.52,
                    dayHigh = 101.0,
                    dayLow = 98.5,
                    dayOpen = 98.5,
                    prevClose = 98.5,
                    updatedAtMs = 1_795_608_000_000L,
                    sourceLabel = "YAHOO LIVE",
                    isFinnhubLive = false
                )
            }
        }

        val service = FinnhubWatchlistQuoteService(fetcher = fakeFetcher, clockMs = { 1_795_608_000_000L })

        val finnhubResult = service.fetchQuotes(listOf("NVDA", "AAPL"), userFinnhubKey = "test-finnhub-token")
        assertTrue(finnhubResult.finnhubConnected)
        assertEquals("FINNHUB LIVE", finnhubResult.feedLabel)
        assertEquals(142.50, finnhubResult.quotesBySymbol["NVDA"]!!.currentPrice, 1e-6)
        assertEquals(-0.61, finnhubResult.quotesBySymbol["AAPL"]!!.changePct, 1e-4)
        assertNull(finnhubResult.warning)

        val noKeyResult = service.fetchQuotes(listOf("NVDA"), userFinnhubKey = "")
        assertFalse(noKeyResult.finnhubConnected)
        assertEquals("LIVE FALLBACK", noKeyResult.feedLabel)
        assertEquals(100.0, noKeyResult.quotesBySymbol["NVDA"]!!.currentPrice, 1e-6)
        assertNotNull(noKeyResult.warning)
    }
}
