package com.example.data.service

import com.example.domain.engine.AdvisorHttpTransport
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.engine.CryptoTraderEngine
import com.example.domain.engine.OkHttpAdvisorTransport
import com.example.domain.engine.QuantEngine
import com.example.domain.model.BarSeries
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoEventClassification
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoPanicPost
import com.example.domain.model.SnapshotQuote
import com.example.domain.model.StablecoinPegRow
import com.example.domain.model.StablecoinPegStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 24/7 public cryptocurrency market data service built on:
 * - Kraken Spot public REST API (`/0/public/Ticker`, `/0/public/OHLC`) — no API key required
 * - Kraken Spot stablecoin peg monitor (`USDTUSD`, `USDCUSD`) — 1.5% depeg guard
 * - Kraken Futures public REST API (`/derivatives/api/v3/tickers`) — perpetual funding, OI, basis
 * - CryptoPanic public/token news feed (`/api/free/v1/posts/`) + categorical risk-event gate
 *
 * Strictly market-data & paper-simulation only (zero real-money order execution).
 */
class CryptoMarketService(
    private val transport: AdvisorHttpTransport = OkHttpAdvisorTransport(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    data class CryptoScanBundle(
        val bars: Map<String, BarSeries>,
        val snapshots: Map<String, SnapshotQuote>,
        val derivsBySymbol: Map<String, CryptoDerivsSnapshot>,
        val posts: List<CryptoPanicPost>,
        val guardsBySymbol: Map<String, CryptoGuardVerdict>,
        val pegStatus: StablecoinPegStatus = StablecoinPegStatus(),
        val lastBarEpochSecBySymbol: Map<String, Long> = emptyMap(),
        val source: String,
        val isLiveKraken: Boolean
    )

    /**
     * Fetches 24/7 Kraken Spot daily OHLC + live Tickers, Kraken stablecoin peg quotes,
     * Kraken Futures funding/OI/basis, and CryptoPanic news posts in parallel, then computes
     * per-pair Guard verdicts (including categorical risk-event gating for risk-keyword hits).
     */
    suspend fun fetchCryptoMarketBundle(
        symbols: List<String>,
        cryptoPanicToken: String = "",
        fundingHistory: List<CryptoFundingHistoryPoint> = emptyList(),
        classifyHeadline: (suspend (String) -> CryptoEventClassification)? = null
    ): CryptoScanBundle = withContext(Dispatchers.IO) {
        val nowMs = clockMs()
        val cleanSyms = (listOf("BTC/USD") + symbols)
            .map { CryptoFeedsEngine.normalizeCryptoSymbol(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(18)

        coroutineScope {
            val tickerDeferred = async { fetchKrakenTickers(cleanSyms) }
            val pegDeferred = async { fetchStablecoinPegStatus(nowMs) }
            val futuresDeferred = async { fetchKrakenFutures(fundingHistory, nowMs) }
            val newsDeferred = async { fetchCryptoPanicPosts(token = cryptoPanicToken, limit = 18, nowMs = nowMs) }
            val ohlcDeferred = cleanSyms.map { sym ->
                async { sym to fetchKrakenOhlcWithTimestamp(sym, intervalMinutes = 1440) }
            }

            val liveTickers = tickerDeferred.await()
            val pegStatus = pegDeferred.await()
            val liveDerivs = futuresDeferred.await()
            val livePosts = newsDeferred.await()
            val liveBarsRaw = ohlcDeferred.awaitAll().toMap()

            val bars = LinkedHashMap<String, BarSeries>()
            val snaps = LinkedHashMap<String, SnapshotQuote>()
            val lastBarTsBySym = LinkedHashMap<String, Long>()
            var liveBarHits = 0

            cleanSyms.forEach { sym ->
                val fetched = liveBarsRaw[sym]
                if (fetched != null) {
                    bars[sym] = fetched.first
                    if (fetched.second > 0L) {
                        lastBarTsBySym[sym] = fetched.second
                    }
                    liveBarHits++
                } else {
                    bars[sym] = QuantEngine.genSeries(sym)
                }

                val liveTicker = liveTickers[sym]
                if (liveTicker != null) {
                    snaps[sym] = liveTicker.toSnapshotQuote()
                } else {
                    val lastClose = bars[sym]?.c?.lastOrNull() ?: 100.0
                    val prevClose = bars[sym]?.c?.let { if (it.size >= 2) it[it.size - 2] else lastClose } ?: lastClose
                    val chg = if (prevClose > 0.0) ((lastClose - prevClose) / prevClose) * 100.0 else 0.0
                    snaps[sym] = SnapshotQuote(
                        price = lastClose,
                        changePct = chg,
                        spreadBps = 6.0,
                        dollarVol = 1.5e8
                    )
                }
            }

            val posts = livePosts.ifEmpty { CryptoFeedsEngine.fallbackCryptoPosts(nowMs) }

            // Classify only risk-keyword headlines (deduplicated across pairs) per `crypto_events.py`
            val riskHeadlines = posts
                .map { it.title }
                .distinct()
                .filter { CryptoTraderEngine.hasRiskKeyword(it) }
                .take(8)
            val eventByHeadline = LinkedHashMap<String, CryptoEventClassification>()
            for (hl in riskHeadlines) {
                eventByHeadline[hl] = if (classifyHeadline != null) {
                    runCatching { classifyHeadline(hl) }
                        .getOrElse { CryptoTraderEngine.classifyHeadlineDeterministic(hl) }
                } else {
                    CryptoTraderEngine.classifyHeadlineDeterministic(hl)
                }
            }

            val guards = LinkedHashMap<String, CryptoGuardVerdict>()
            cleanSyms.forEach { sym ->
                val code = CryptoFeedsEngine.baseAsset(sym)
                val topPostsForSym = posts
                    .filter { post ->
                        post.currencies.isEmpty() || post.currencies.any { it.equals(code, ignoreCase = true) }
                    }
                    .take(3)
                val evs = topPostsForSym.mapNotNull { eventByHeadline[it.title] }
                guards[sym] = CryptoFeedsEngine.evaluateGuard(
                    symbol = sym,
                    posts = posts,
                    derivs = liveDerivs[sym],
                    eventClassifications = evs
                )
            }

            val isLive = liveBarHits >= 1 || liveTickers.isNotEmpty()
            CryptoScanBundle(
                bars = bars,
                snapshots = snaps,
                derivsBySymbol = liveDerivs,
                posts = posts,
                guardsBySymbol = guards,
                pegStatus = pegStatus,
                lastBarEpochSecBySymbol = lastBarTsBySym,
                source = if (isLive) "live" else "demo",
                isLiveKraken = isLive
            )
        }
    }

    /**
     * Exact `peg_check()` from `cryptotrader.py 2.0`: queries Kraken public Ticker for
     * `USDTUSD` and `USDCUSD` and flags a breach when either deviates > 1.5% (150 bps) from $1.00.
     */
    suspend fun fetchStablecoinPegStatus(nowMs: Long = clockMs()): StablecoinPegStatus =
        withContext(Dispatchers.IO) {
            val url = "${CryptoFeedsEngine.KRAKEN_SPOT_PUBLIC_BASE}/Ticker?pair=USDTUSD,USDCUSD"
            runCatching {
                val resp = transport.get(url, timeoutSec = 10.0)
                if (resp.code in 200..299 && resp.body.isNotBlank()) {
                    CryptoTraderEngine.parsePegCheckJson(resp.body, nowMs = nowMs)
                } else {
                    StablecoinPegStatus(
                        rows = listOf(
                            StablecoinPegRow("USDT/USD", error = "HTTP ${resp.code}"),
                            StablecoinPegRow("USDC/USD", error = "HTTP ${resp.code}")
                        ),
                        anyBreach = false,
                        checkedAtMs = nowMs
                    )
                }
            }.getOrElse { exc ->
                val msg = exc.message?.take(60) ?: "unavailable"
                StablecoinPegStatus(
                    rows = listOf(
                        StablecoinPegRow("USDT/USD", error = msg),
                        StablecoinPegRow("USDC/USD", error = msg)
                    ),
                    anyBreach = false,
                    checkedAtMs = nowMs
                )
            }
        }

    /**
     * Lightweight 24/7 batch price poll via Kraken `/0/public/Ticker` (single HTTP call for all pairs).
     */
    suspend fun fetchLatestCryptoPrices(symbols: List<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        val clean = symbols.map { CryptoFeedsEngine.normalizeCryptoSymbol(it) }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return@withContext emptyMap()
        fetchKrakenTickers(clean).mapValues { it.value.last }
    }

    suspend fun fetchKrakenTickers(symbols: List<String>): Map<String, CryptoFeedsEngine.KrakenTickerQuote> {
        if (symbols.isEmpty()) return emptyMap()
        val pairsParam = symbols.joinToString(",") { CryptoFeedsEngine.krakenSpotPair(it) }
        val encoded = URLEncoder.encode(pairsParam, "UTF-8")
        val url = "${CryptoFeedsEngine.KRAKEN_SPOT_PUBLIC_BASE}/Ticker?pair=$encoded"
        return runCatching {
            val resp = transport.get(url, timeoutSec = 10.0)
            if (resp.code in 200..299 && resp.body.isNotBlank()) {
                CryptoFeedsEngine.parseKrakenTickersJson(resp.body, symbols)
            } else {
                emptyMap()
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun fetchKrakenOhlc(symbol: String, intervalMinutes: Int = 1440): BarSeries? =
        fetchKrakenOhlcWithTimestamp(symbol, intervalMinutes)?.first

    suspend fun fetchKrakenOhlcWithTimestamp(
        symbol: String,
        intervalMinutes: Int = 1440
    ): Pair<BarSeries, Long>? {
        val pair = URLEncoder.encode(CryptoFeedsEngine.krakenSpotPair(symbol), "UTF-8")
        val url = "${CryptoFeedsEngine.KRAKEN_SPOT_PUBLIC_BASE}/OHLC?pair=$pair&interval=$intervalMinutes"
        return runCatching {
            val resp = transport.get(url, timeoutSec = 12.0)
            if (resp.code in 200..299 && resp.body.isNotBlank()) {
                CryptoFeedsEngine.parseKrakenOhlcWithTimestampJson(resp.body)
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun fetchKrakenFutures(
        history: List<CryptoFundingHistoryPoint> = emptyList(),
        nowMs: Long = clockMs()
    ): Map<String, CryptoDerivsSnapshot> {
        return runCatching {
            val resp = transport.get(CryptoFeedsEngine.KRAKEN_FUTURES_TICKERS_URL, timeoutSec = 12.0)
            if (resp.code in 200..299 && resp.body.isNotBlank()) {
                CryptoFeedsEngine.parseKrakenFuturesJson(resp.body, history, nowMs)
            } else {
                emptyMap()
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun fetchCryptoPanicPosts(
        currencies: String = "",
        token: String = "",
        limit: Int = 20,
        nowMs: Long = clockMs()
    ): List<CryptoPanicPost> {
        val cleanToken = token.trim()
        val cleanCurr = currencies.trim().uppercase()
        for (base in CryptoFeedsEngine.CRYPTOPANIC_BASES) {
            val queryParts = mutableListOf("kind=news")
            if (cleanCurr.isNotEmpty()) {
                queryParts.add("currencies=${URLEncoder.encode(cleanCurr, "UTF-8")}")
            }
            if (cleanToken.isNotEmpty()) {
                queryParts.add("auth_token=${URLEncoder.encode(cleanToken, "UTF-8")}")
            } else {
                queryParts.add("public=true")
            }
            val url = "$base/posts/?${queryParts.joinToString("&")}"
            val parsed = runCatching {
                val resp = transport.get(url, timeoutSec = 12.0)
                if (resp.code in 200..299 && resp.body.isNotBlank()) {
                    CryptoFeedsEngine.parseCryptoPanicPostsJson(resp.body, limit = limit, nowMs = nowMs)
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }
}
