package com.example

import com.example.data.service.CryptoMarketService
import com.example.domain.engine.AdvisorHttpResponse
import com.example.domain.engine.AdvisorHttpTransport
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.engine.PaperBotEngine
import com.example.domain.model.BotState
import com.example.domain.model.CostEstimate
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoPanicPost
import com.example.domain.model.MarketSector
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.TradePosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CryptoFeedsEngineTest {

    @Test
    fun krakenSpotPairMapsAliasesAndNormalizes() {
        assertEquals("XBTUSD", CryptoFeedsEngine.krakenSpotPair("BTC/USD"))
        assertEquals("XDGUSD", CryptoFeedsEngine.krakenSpotPair("DOGE/USD"))
        assertEquals("ETHUSD", CryptoFeedsEngine.krakenSpotPair("eth/usd"))
        assertEquals("BTC/USD", CryptoFeedsEngine.normalizeCryptoSymbol("btc"))
        assertEquals("SOL/USD", CryptoFeedsEngine.normalizeCryptoSymbol("sol-usd"))
        assertEquals(MarketSector.CRYPTO_L1, CryptoFeedsEngine.classifyCryptoSector("BTC/USD"))
        assertEquals(MarketSector.CRYPTO_DEFI, CryptoFeedsEngine.classifyCryptoSector("LINK/USD"))
        assertEquals(MarketSector.CRYPTO_PAYMENTS, CryptoFeedsEngine.classifyCryptoSector("DOGE/USD"))
    }

    @Test
    fun parseKrakenTickersAndOhlcJson() {
        val tickerJson = """
            {
              "error": [],
              "result": {
                "XXBTZUSD": {
                  "a": ["68010.0", "1", "1.000"],
                  "b": ["67990.0", "1", "1.000"],
                  "c": ["68000.0", "0.2"],
                  "v": ["1000.0", "2500.0"],
                  "o": "66000.0"
                }
              }
            }
        """.trimIndent()
        val quotes = CryptoFeedsEngine.parseKrakenTickersJson(tickerJson, listOf("BTC/USD"))
        val btc = quotes["BTC/USD"]
        assertNotNull(btc)
        assertEquals(68000.0, btc!!.last, 0.01)
        assertTrue(btc.changePct > 3.0)
        assertTrue(btc.spreadBps > 0.0 && btc.spreadBps < 10.0)
        assertTrue(btc.dollarVol24h > 100_000_000.0)

        val candles = (0 until 65).joinToString(",") { i ->
            val px = 62000.0 + i * 100.0
            "[${1700000000 + i * 86400},\"$px\",\"${px + 300}\",\"${px - 200}\",\"${px + 50}\",\"$px\",\"1200.5\",500]"
        }
        val ohlcJson = """{"error":[],"result":{"XXBTZUSD":[$candles],"last":1706000000}}"""
        val bars = CryptoFeedsEngine.parseKrakenOhlcJson(ohlcJson)
        assertNotNull(bars)
        assertEquals(65, bars!!.c.size)
        assertEquals(62050.0, bars.c.first(), 0.01)
    }

    @Test
    fun parseKrakenFuturesAndCrowdingPercentileGating() {
        val futuresJson = """
            {
              "tickers": [
                {
                  "symbol": "PF_XBTUSD",
                  "markPrice": 68136.0,
                  "indexPrice": 68000.0,
                  "fundingRate": 0.0002,
                  "openInterest": 450000000.0,
                  "volumeQuote": 1200000000.0
                }
              ]
            }
        """.trimIndent()

        // Fewer than 30 samples -> percentile must be null and multiplier neutral 1.0
        val shortHistory = (1..12).map { i ->
            CryptoFundingHistoryPoint(
                tsMs = 1_700_000_000_000L + i * 3_600_000L,
                symbol = "BTC/USD",
                funding8h = 0.0001,
                fundingAnnual = 0.0001 * 3.0 * 365.0,
                openInterest = 400_000_000.0,
                basisBps = 5.0
            )
        }
        val snapWarming = CryptoFeedsEngine.parseKrakenFuturesJson(
            rawJson = futuresJson,
            history = shortHistory,
            nowMs = 1_700_100_000_000L
        )
        val btcWarm = snapWarming["BTC/USD"]
        assertNotNull(btcWarm)
        assertEquals(0.0002 * 3.0 * 365.0, btcWarm!!.fundingAnnual, 1e-6)
        assertEquals(20.0, btcWarm.basisBps, 0.05)
        assertNull(btcWarm.crowdingPct)
        assertEquals(1.0, btcWarm.crowdingMult, 1e-6)

        // 35 lower samples + 1 extreme latest -> percentile >= 0.95 -> mult == 0.4
        val nowMs = 1_700_500_000_000L
        val deepHistory = (0 until 35).map { i ->
            val rate = 0.00002 + i * 0.000003
            CryptoFundingHistoryPoint(
                tsMs = nowMs - (36 - i) * 3_600_000L,
                symbol = "BTC/USD",
                funding8h = rate,
                fundingAnnual = rate * 3.0 * 365.0,
                openInterest = 400_000_000.0,
                basisBps = 5.0
            )
        } + CryptoFundingHistoryPoint(
            tsMs = nowMs,
            symbol = "BTC/USD",
            funding8h = 0.0002,
            fundingAnnual = 0.0002 * 3.0 * 365.0,
            openInterest = 450_000_000.0,
            basisBps = 20.0
        )

        val pct = CryptoFeedsEngine.computeFundingPercentile(deepHistory)
        assertNotNull(pct)
        assertTrue("Expected top-5% percentile, got $pct", pct!! >= 0.95)
        val crowding = CryptoFeedsEngine.evaluateCrowding(pct)
        assertEquals(0.4, crowding.mult, 1e-6)
        assertTrue(crowding.note.contains("crowded", ignoreCase = true))
    }

    @Test
    fun cryptoPanicDeterministicScoreDecayAndGuardRules() {
        // Vote arithmetic & 12h half-life decay
        assertEquals(0.0, CryptoFeedsEngine.cryptoPanicVoteScore(0, 0, 0), 1e-6)
        assertEquals(1.0, CryptoFeedsEngine.cryptoPanicVoteScore(10, 0, 0), 1e-6)
        assertEquals(-0.8, CryptoFeedsEngine.cryptoPanicVoteScore(1, 9, 0), 1e-6)
        assertEquals(0.5, CryptoFeedsEngine.cryptoPanicDecayWeight(12.0), 1e-6)

        // Positive news must NEVER push sizeMult above 1.0
        val bullishPosts = listOf(
            CryptoPanicPost(
                title = "BTC ETF hits record inflow",
                url = "https://example.com/1",
                source = "CoinDesk",
                publishedIso = "2026-11-25T07:00:00Z",
                ageHours = 1.0,
                currencies = listOf("BTC"),
                positive = 40,
                negative = 1,
                important = 10,
                score = CryptoFeedsEngine.cryptoPanicVoteScore(40, 1, 10),
                weight = CryptoFeedsEngine.cryptoPanicDecayWeight(1.0)
            )
        )
        val bullVerdict = CryptoFeedsEngine.evaluateGuard(
            symbol = "BTC/USD",
            posts = bullishPosts,
            derivs = null
        )
        assertFalse(bullVerdict.blocked)
        assertTrue(bullVerdict.sizeMult <= 1.0)
        assertEquals(1.0, bullVerdict.sizeMult, 1e-6)

        // Hard-negative spike within 6h (<= -0.75) overrides average and blocks new entries
        val crashPosts = listOf(
            CryptoPanicPost(
                title = "Major exchange exploit hits SOL bridge",
                url = "https://example.com/2",
                source = "The Block",
                publishedIso = "2026-11-25T06:30:00Z",
                ageHours = 1.5,
                currencies = listOf("SOL"),
                positive = 1,
                negative = 25,
                important = 12,
                score = CryptoFeedsEngine.cryptoPanicVoteScore(1, 25, 12),
                weight = CryptoFeedsEngine.cryptoPanicDecayWeight(1.5)
            )
        )
        val crashVerdict = CryptoFeedsEngine.evaluateGuard(
            symbol = "SOL/USD",
            posts = crashPosts,
            derivs = CryptoDerivsSnapshot(
                symbol = "SOL/USD",
                tsIso = "2026-11-25T07:00:00Z",
                funding8h = 0.0001,
                fundingAnnual = 0.1095,
                openInterest = 100_000_000.0,
                mark = 180.0,
                index = 180.0,
                basisBps = 0.0,
                crowdingPct = 0.96,
                crowdingMult = 0.4,
                crowdingNote = "funding in top 5% — longs crowded",
                historySampleCount = 35
            )
        )
        assertTrue(crashVerdict.blocked)
        assertTrue(crashVerdict.newsScore <= -0.75)
        assertTrue(crashVerdict.sizeMult in 0.25..1.0)
    }

    @Test
    fun paperBotRespectsCryptoGuardWithoutPanicSellingHeldPositions() {
        val btcBuy = SignalRow(
            symbol = "BTC/USD",
            action = SignalAction.BUY,
            score = 0.72,
            confidence = 0.80,
            price = 68000.0,
            entry = 68000.0,
            stop = 65000.0,
            target = 74000.0,
            rr = 2.0,
            atrPct = 0.03,
            relVolume = 1.5,
            size = 0.001,
            reasons = listOf("breakout"),
            sub = mapOf("mom" to 0.7),
            conviction = 1.6,
            cost = CostEstimate(
                spreadBps = 3.0,
                impactBps = 1.0,
                roundTripBps = 4.0,
                rrNet = 1.95,
                viable = true,
                reason = "ok"
            ),
            sector = MarketSector.CRYPTO_L1
        )

        // 1. Blocked guard prevents opening a new position and logs GUARD
        val blockedGuard = mapOf(
            "BTC/USD" to CryptoGuardVerdict(
                symbol = "BTC/USD",
                sizeMult = 0.4,
                newsScore = -0.82,
                blocked = true,
                notes = listOf("hard negative news spike")
            )
        )
        val flatBot = BotState(
            equity = 100.0,
            cash = 100.0,
            initialEquity = 100.0,
            running = true
        )
        val afterBlocked = PaperBotEngine.tick(
            state = flatBot,
            rows = listOf(btcBuy),
            regimeLabel = "RISK_ON",
            cryptoGuards = blockedGuard
        )
        assertTrue(afterBlocked.positions.isEmpty())
        assertTrue(afterBlocked.log.any { it.type == "GUARD" && it.symbol == "BTC/USD" })

        // 2. Crowding guard (0.5x) shrinks position size relative to neutral (1.0x)
        val fullOpen = PaperBotEngine.tick(
            state = flatBot,
            rows = listOf(btcBuy),
            regimeLabel = "RISK_ON",
            cryptoGuards = emptyMap()
        )
        val shrunkOpen = PaperBotEngine.tick(
            state = flatBot,
            rows = listOf(btcBuy),
            regimeLabel = "RISK_ON",
            cryptoGuards = mapOf(
                "BTC/USD" to CryptoGuardVerdict(
                    symbol = "BTC/USD",
                    sizeMult = 0.5,
                    newsScore = 0.0,
                    blocked = false,
                    notes = listOf("funding elevated")
                )
            )
        )
        assertEquals(1, fullOpen.positions.size)
        assertEquals(1, shrunkOpen.positions.size)
        assertTrue(shrunkOpen.positions.first().qty < fullOpen.positions.first().qty)
        assertTrue(abs(shrunkOpen.positions.first().qty / fullOpen.positions.first().qty - 0.5) < 0.05)

        // 3. Guard turning blocked while already holding MUST NOT panic-sell the held position
        val holdingBot = BotState(
            equity = 100.0,
            cash = 50.0,
            initialEquity = 100.0,
            positions = listOf(
                TradePosition(
                    symbol = "BTC/USD",
                    qty = 0.0007,
                    entry = 68000.0,
                    stop = 65000.0,
                    target = 74000.0,
                    openedAtMs = System.currentTimeMillis() - 60_000L,
                    upnl = 0.0,
                    last = 68000.0
                )
            ),
            running = true
        )
        val afterGuardWhileHeld = PaperBotEngine.tick(
            state = holdingBot,
            rows = listOf(btcBuy.copy(price = 68100.0)),
            regimeLabel = "RISK_ON",
            cryptoGuards = blockedGuard
        )
        assertEquals(1, afterGuardWhileHeld.positions.size)
        assertEquals("BTC/USD", afterGuardWhileHeld.positions.first().symbol)
    }

    @Test
    fun cryptoMarketServiceFetchesBundleViaFakeTransport() = runBlocking {
        val fakeTransport = object : AdvisorHttpTransport {
            override suspend fun get(
                url: String,
                timeoutSec: Double
            ): AdvisorHttpResponse {
                return when {
                    url.contains("/0/public/Ticker") -> AdvisorHttpResponse(
                        code = 200,
                        body = """{"error":[],"result":{"XXBTZUSD":{"a":["68010","1","1"],"b":["67990","1","1"],"c":["68000","0.1"],"v":["100","2000"],"o":"67000"}}}"""
                    )
                    url.contains("/0/public/OHLC") -> {
                        val candles = (0 until 75).joinToString(",") { i ->
                            val px = 60000.0 + i * 100.0
                            "[${1700000000 + i * 86400},\"$px\",\"${px + 300}\",\"${px - 200}\",\"${px + 80}\",\"$px\",\"500.0\",100]"
                        }
                        AdvisorHttpResponse(
                            code = 200,
                            body = """{"error":[],"result":{"XXBTZUSD":[$candles],"last":1706480000}}"""
                        )
                    }
                    url.contains("futures.kraken.com") -> AdvisorHttpResponse(
                        code = 200,
                        body = """{"tickers":[{"symbol":"PF_XBTUSD","markPrice":68050,"indexPrice":68000,"fundingRate":0.0001,"openInterest":350000000,"volumeQuote":900000000}]}"""
                    )
                    url.contains("cryptopanic.com") -> AdvisorHttpResponse(
                        code = 200,
                        body = """{"results":[{"title":"Bitcoin momentum steady","url":"https://example.com/btc","source":{"title":"CoinDesk"},"published_at":"2026-11-25T07:00:00Z","currencies":[{"code":"BTC"}],"votes":{"positive":12,"negative":2,"important":3},"kind":"news"}]}"""
                    )
                    else -> AdvisorHttpResponse(code = 404, body = "{}")
                }
            }

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse = AdvisorHttpResponse(code = 405, body = "{}")
        }

        val service = CryptoMarketService(transport = fakeTransport)
        val bundle = service.fetchCryptoMarketBundle(
            symbols = listOf("BTC/USD"),
            cryptoPanicToken = "",
            fundingHistory = emptyList()
        )
        assertEquals("live", bundle.source)
        assertTrue(bundle.isLiveKraken)
        assertTrue(bundle.bars.containsKey("BTC/USD"))
        assertTrue(bundle.snapshots.containsKey("BTC/USD"))
        assertTrue(bundle.derivsBySymbol.containsKey("BTC/USD"))
        assertTrue(bundle.guardsBySymbol.containsKey("BTC/USD"))
        assertEquals(1, bundle.posts.size)
    }
}
