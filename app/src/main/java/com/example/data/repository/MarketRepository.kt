package com.example.data.repository

import android.util.Log
import com.example.data.remote.AlpacaApi
import com.example.data.remote.FinnhubApi
import com.example.data.remote.FinnhubArticleDto
import com.example.data.remote.GeminiContentDto
import com.example.data.remote.GeminiGenerateRequest
import com.example.data.remote.GeminiGenerationConfigDto
import com.example.data.remote.GeminiNewsApi
import com.example.data.remote.GeminiPartDto
import com.example.data.remote.GeminiSchemaObjectDto
import com.example.data.remote.GeminiSchemaPropertyDto
import com.example.data.remote.ScoredNewsPayloadDto
import com.example.data.remote.ThreatNarrativePayloadDto
import com.example.data.remote.VixFeedParser
import com.example.data.remote.YahooChartClient
import com.example.data.service.CryptoMarketService
import com.example.domain.engine.AdvisorConfig
import com.example.domain.engine.AdvisorEngine
import com.example.domain.engine.AiCatalystAssessment
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.engine.FinnhubCatalystEngine
import com.example.domain.engine.QuantEngine
import com.example.domain.engine.RawFinnhubArticle
import com.example.domain.engine.TickerIndicatorEngine
import com.example.domain.engine.TickerIndicatorQuote
import com.example.domain.engine.VixAlertEngine
import com.example.engine.ThreatCard
import com.example.engine.ThreatContext
import com.example.engine.ThreatEngine
import com.example.engine.ThreatPosition
import com.example.domain.model.AiProviderId
import com.example.domain.model.AnnotatedNewsArticle
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.CatalystScoreSource
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoEventClassification
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoPanicPost
import com.example.domain.model.StablecoinPegStatus
import com.example.domain.model.MarketMode
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.ScoredNewsItem
import com.example.domain.model.SignalRow
import com.example.domain.model.SnapshotQuote
import com.example.domain.model.SymbolNewsSummary
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatCategory
import com.example.domain.model.ThreatNarrativeResult
import com.example.domain.model.ThreatPositionContext
import com.example.domain.model.VixSnapshot
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ScanResult(
    val rows: List<SignalRow>,
    val regime: MarketRegime,
    val news: List<NewsHeadline>,
    val source: String,
    val bars: Map<String, BarSeries> = emptyMap(),
    val cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    val cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    val cryptoPanicPosts: List<CryptoPanicPost> = emptyList(),
    val cryptoPegStatus: StablecoinPegStatus = StablecoinPegStatus(),
    val cryptoLastBarEpochSecBySymbol: Map<String, Long> = emptyMap()
)

class MarketRepository {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val scoredAdapter = moshi.adapter(ScoredNewsPayloadDto::class.java)
    private val threatAdapter = moshi.adapter(ThreatNarrativePayloadDto::class.java)

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val alpacaApi: AlpacaApi = Retrofit.Builder()
        .baseUrl("https://data.alpaca.markets/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AlpacaApi::class.java)

    private val geminiApi: GeminiNewsApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiNewsApi::class.java)

    private val finnhubApi: FinnhubApi = Retrofit.Builder()
        .baseUrl("https://finnhub.io/api/v1/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(FinnhubApi::class.java)

    private val yahooChartClient = YahooChartClient(okHttp)
    private val cryptoService = CryptoMarketService()

    data class TickerIndicatorsFetchResult(
        val quotes: List<TickerIndicatorQuote>,
        val feedLabel: String,
        val isLiveApi: Boolean,
        val fetchedAtMs: Long,
        val warning: String? = null
    )

    suspend fun fetchCryptoStablecoinPeg(): StablecoinPegStatus =
        cryptoService.fetchStablecoinPegStatus()

    suspend fun scan(
        cfg: AppConfig,
        fundingHistory: List<CryptoFundingHistoryPoint> = emptyList(),
        classifyCryptoHeadline: (suspend (String) -> CryptoEventClassification)? = null
    ): ScanResult {
        if (cfg.marketMode == MarketMode.CRYPTO) {
            return scanCrypto(
                symbols = (CryptoFeedsEngine.CRYPTO_UNIVERSE + cfg.cryptoWatchlist).distinct(),
                cfg = cfg,
                fundingHistory = fundingHistory,
                classifyCryptoHeadline = classifyCryptoHeadline
            )
        }
        if (cfg.demo || cfg.alpacaId.isBlank() || cfg.alpacaSecret.isBlank()) {
            val allSyms = (QuantEngine.UNIVERSE + cfg.watchlist).distinct().take(100)
            val bars = LinkedHashMap<String, BarSeries>()
            allSyms.forEach { bars[it] = QuantEngine.genSeries(it) }
            val snap = QuantEngine.demoSnap(allSyms)
            val out = QuantEngine.runEngine(bars, snap, cfg)
            val news = QuantEngine.demoNews(out.rows)
            val newsMap = QuantEngine.demoNewsMap(news)
            val rowsWithNews = QuantEngine.applyNews(out.rows, newsMap)
            return ScanResult(
                rows = rowsWithNews,
                regime = out.regime,
                news = news,
                source = "demo",
                bars = bars
            )
        }

        val syms = (QuantEngine.UNIVERSE + cfg.watchlist).distinct().take(100)
        val (bars, snap) = coroutineScope {
            val barsDeferred = async { fetchBars(syms, cfg) }
            val snapDeferred = async { fetchSnapshots(syms, cfg) }
            barsDeferred.await() to snapDeferred.await()
        }
        if (bars.isEmpty()) {
            throw IllegalStateException("No bars returned — check your Alpaca keys.")
        }
        val out = QuantEngine.runEngine(bars, snap, cfg)
        val newsSymbols = (out.rows.take(10).map { it.symbol } + cfg.watchlist)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(15)
        val headlines = fetchNews(newsSymbols, cfg)
        val geminiCfg = resolveEnabledGemini(cfg)
        val newsMap = if (geminiCfg != null) {
            scoreNews(headlines, geminiCfg.first, geminiCfg.second)
        } else {
            emptyMap()
        }
        val rowsWithNews = QuantEngine.applyNews(out.rows, newsMap)
        return ScanResult(
            rows = rowsWithNews,
            regime = out.regime,
            news = headlines,
            source = "live",
            bars = bars
        )
    }

    private suspend fun scanCrypto(
        symbols: List<String>,
        cfg: AppConfig,
        fundingHistory: List<CryptoFundingHistoryPoint> = emptyList(),
        classifyCryptoHeadline: (suspend (String) -> CryptoEventClassification)? = null
    ): ScanResult {
        val bundle = cryptoService.fetchCryptoMarketBundle(
            symbols = symbols,
            cryptoPanicToken = cfg.cryptoPanicToken,
            fundingHistory = fundingHistory,
            classifyHeadline = classifyCryptoHeadline
        )
        val out = QuantEngine.runEngine(bundle.bars, bundle.snapshots, cfg)
        val headlines = CryptoFeedsEngine.toNewsHeadlines(bundle.posts)
        val geminiCfg = resolveEnabledGemini(cfg)
        val newsMap = if (geminiCfg != null && headlines.isNotEmpty()) {
            scoreNews(headlines.take(15), geminiCfg.first, geminiCfg.second)
                .ifEmpty { buildCryptoVoteNewsMap(bundle.posts, bundle.guardsBySymbol) }
        } else {
            buildCryptoVoteNewsMap(bundle.posts, bundle.guardsBySymbol)
        }
        val rowsWithNews = QuantEngine.applyNews(out.rows, newsMap)
        return ScanResult(
            rows = rowsWithNews,
            regime = out.regime,
            news = headlines,
            source = bundle.source,
            bars = bundle.bars,
            cryptoDerivsBySymbol = bundle.derivsBySymbol,
            cryptoGuardsBySymbol = bundle.guardsBySymbol,
            cryptoPanicPosts = bundle.posts,
            cryptoPegStatus = bundle.pegStatus,
            cryptoLastBarEpochSecBySymbol = bundle.lastBarEpochSecBySymbol
        )
    }

    private fun buildCryptoVoteNewsMap(
        posts: List<CryptoPanicPost>,
        guards: Map<String, CryptoGuardVerdict>
    ): Map<String, SymbolNewsSummary> {
        val out = LinkedHashMap<String, SymbolNewsSummary>()
        val byBase = posts.groupBy { it.currencies.firstOrNull()?.uppercase(Locale.US) ?: "BTC" }
        guards.forEach { (sym, guard) ->
            val base = sym.substringBefore("/").uppercase(Locale.US)
            val symPosts = byBase[base].orEmpty()
            val scored = symPosts.take(5).map { p ->
                ScoredNewsItem(
                    title = p.title,
                    direction = p.score,
                    materiality = if (p.important > 0) 0.72 else 0.48,
                    rationale = "CryptoPanic votes (${p.positive}+ / ${p.negative}-)",
                    decay = CryptoFeedsEngine.cryptoPanicDecayWeight(p.ageHours)
                )
            }
            if (scored.isNotEmpty() || guard.newsScore != 0.0) {
                out[sym] = SymbolNewsSummary(
                    direction = guard.newsScore,
                    materiality = if (guard.blocked) 0.85 else 0.55,
                    signed = guard.newsScore,
                    items = scored
                )
            }
        }
        return out
    }

    suspend fun fetchLatestCryptoPrices(symbols: List<String>): Map<String, Double> =
        cryptoService.fetchLatestCryptoPrices(symbols)

    /**
     * On-demand quantitative equity scanner fetch for a user-specified basket of tickers.
     * Fetches live Alpaca daily bars + real-time snapshot quotes when configured, or deterministic
     * multi-factor market series in demo mode, and runs the 6-factor [QuantEngine].
     */
    suspend fun scanCustomSymbols(
        symbols: List<String>,
        cfg: AppConfig
    ): ScanResult {
        if (cfg.marketMode == MarketMode.CRYPTO) {
            return scanCrypto(
                symbols = symbols.ifEmpty { CryptoFeedsEngine.CRYPTO_UNIVERSE },
                cfg = cfg
            )
        }
        val cleanSyms = symbols
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(15)
        if (cleanSyms.isEmpty()) {
            return scan(cfg)
        }
        val withBench = (listOf("SPY") + cleanSyms).distinct()

        if (cfg.demo || cfg.alpacaId.isBlank() || cfg.alpacaSecret.isBlank()) {
            val bars = LinkedHashMap<String, BarSeries>()
            withBench.forEach { bars[it] = QuantEngine.genSeries(it) }
            val snap = QuantEngine.demoSnap(withBench)
            val out = QuantEngine.runEngine(bars, snap, cfg)
            val news = QuantEngine.demoNews(out.rows)
            val newsMap = QuantEngine.demoNewsMap(news)
            val rowsWithNews = QuantEngine.applyNews(out.rows, newsMap)
            return ScanResult(
                rows = rowsWithNews,
                regime = out.regime,
                news = news,
                source = "demo",
                bars = bars
            )
        }

        val (bars, snap) = coroutineScope {
            val barsDeferred = async { fetchBars(withBench, cfg) }
            val snapDeferred = async { fetchSnapshots(withBench, cfg) }
            barsDeferred.await() to snapDeferred.await()
        }
        if (bars.isEmpty()) {
            throw IllegalStateException("No market data returned for ${cleanSyms.joinToString(", ")} — check tickers or Alpaca keys.")
        }
        val out = QuantEngine.runEngine(bars, snap, cfg)
        val headlines = fetchNews(cleanSyms.take(8), cfg)
        val geminiCfg = resolveEnabledGemini(cfg)
        val newsMap = if (geminiCfg != null) {
            scoreNews(headlines, geminiCfg.first, geminiCfg.second)
        } else {
            emptyMap()
        }
        val rowsWithNews = QuantEngine.applyNews(out.rows, newsMap)
        return ScanResult(
            rows = rowsWithNews,
            regime = out.regime,
            news = headlines,
            source = "live",
            bars = bars
        )
    }

    /**
     * Fetches real-time prices and daily close series for [symbols] to compute live RSI(14) and
     * Moving Average (EMA 9/21 & SMA 20/50) crossover signals.
     *
     * Priority order:
     * 1. Alpaca live bars + real-time snapshots when Alpaca credentials are configured.
     * 2. Yahoo Finance v8 Chart REST API (live quotes + 6mo daily closes) overlaid with Finnhub
     *    `/quote` when a Finnhub key is configured.
     * 3. Cached/deterministic fallback only if network APIs are unreachable.
     */
    suspend fun fetchTickerIndicators(
        symbols: List<String>,
        cfg: AppConfig,
        cachedBars: Map<String, BarSeries> = emptyMap()
    ): TickerIndicatorsFetchResult {
        val cleanSyms = symbols
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)
            .ifEmpty { TickerIndicatorEngine.DEFAULT_TICKERS }

        val nowMs = System.currentTimeMillis()
        val finnhubKey = cfg.finnhubKey.trim().ifBlank { AdvisorConfig.fromBuildConfig().cleanFinnhubKey }
        val hasAlpacaLive = !cfg.demo && cfg.alpacaId.isNotBlank() && cfg.alpacaSecret.isNotBlank()

        // Tier 1: Alpaca Live Bars + Snapshots
        if (hasAlpacaLive) {
            try {
                val (bars, snaps) = coroutineScope {
                    val barsDef = async { fetchBars(cleanSyms, cfg) }
                    val snapsDef = async { fetchSnapshots(cleanSyms, cfg) }
                    barsDef.await() to snapsDef.await()
                }
                if (bars.isNotEmpty() || snaps.isNotEmpty()) {
                    val quotes = cleanSyms.mapNotNull { sym ->
                        val series = bars[sym]?.c ?: cachedBars[sym]?.c ?: QuantEngine.genSeries(sym).c
                        val snap = snaps[sym]
                        val price = snap?.price?.takeIf { it > 0.0 } ?: series.lastOrNull() ?: return@mapNotNull null
                        val prevClose = if (snap != null && snap.changePct.isFinite()) {
                            price / (1.0 + snap.changePct / 100.0)
                        } else {
                            series.getOrNull(series.size - 2) ?: price
                        }
                        val dayHigh = bars[sym]?.h?.lastOrNull() ?: price
                        val dayLow = bars[sym]?.l?.lastOrNull() ?: price
                        TickerIndicatorEngine.evaluateSymbol(
                            symbol = sym,
                            historicalCloses = series,
                            currentPrice = price,
                            previousClose = prevClose,
                            dayHigh = dayHigh,
                            dayLow = dayLow,
                            sourceLabel = "ALPACA LIVE",
                            updatedAtMs = nowMs
                        )
                    }
                    if (quotes.isNotEmpty()) {
                        return TickerIndicatorsFetchResult(
                            quotes = quotes,
                            feedLabel = "ALPACA LIVE API",
                            isLiveApi = true,
                            fetchedAtMs = nowMs
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Alpaca indicator fetch fell back: ${e.message}")
            }
        }

        // Tier 2: Yahoo Finance v8 Chart REST API (live prices + 6mo closes) + optional Finnhub /quote overlay
        val yahooResults = coroutineScope {
            cleanSyms.map { sym ->
                async { sym to yahooChartClient.fetchChart(sym) }
            }.awaitAll().toMap()
        }

        val finnhubQuotes = if (finnhubKey.isNotBlank()) {
            coroutineScope {
                cleanSyms.take(12).map { sym ->
                    async {
                        try {
                            val dto = finnhubApi.getQuote(symbol = sym, token = finnhubKey)
                            if (dto.c > 0.0) sym to dto else null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        } else {
            emptyMap()
        }

        var liveCount = 0
        val quotes = cleanSyms.map { sym ->
            val yahoo = yahooResults[sym]
            val fh = finnhubQuotes[sym]
            when {
                fh != null -> {
                    liveCount++
                    val histCloses = yahoo?.closes?.takeIf { it.size >= 15 }
                        ?: cachedBars[sym]?.c?.takeIf { it.size >= 15 }
                        ?: QuantEngine.genSeries(sym).c
                    TickerIndicatorEngine.evaluateSymbol(
                        symbol = sym,
                        historicalCloses = histCloses,
                        currentPrice = fh.c,
                        previousClose = fh.pc.takeIf { it > 0.0 } ?: yahoo?.previousClose ?: fh.c,
                        dayHigh = fh.h.takeIf { it > 0.0 } ?: fh.c,
                        dayLow = fh.l.takeIf { it > 0.0 } ?: fh.c,
                        sourceLabel = "FINNHUB LIVE",
                        updatedAtMs = nowMs
                    )
                }
                yahoo != null -> {
                    liveCount++
                    TickerIndicatorEngine.evaluateSymbol(
                        symbol = sym,
                        historicalCloses = yahoo.closes,
                        currentPrice = yahoo.currentPrice,
                        previousClose = yahoo.previousClose,
                        dayHigh = yahoo.dayHigh,
                        dayLow = yahoo.dayLow,
                        sourceLabel = "YAHOO LIVE",
                        updatedAtMs = yahoo.updatedAtMs
                    )
                }
                else -> {
                    val fallbackSeries = cachedBars[sym] ?: QuantEngine.genSeries(sym)
                    val closes = fallbackSeries.c
                    val basePrice = closes.lastOrNull() ?: 100.0
                    val prevClose = closes.getOrNull(closes.size - 2) ?: basePrice
                    TickerIndicatorEngine.evaluateSymbol(
                        symbol = sym,
                        historicalCloses = closes,
                        currentPrice = basePrice,
                        previousClose = prevClose,
                        dayHigh = fallbackSeries.h.lastOrNull() ?: basePrice,
                        dayLow = fallbackSeries.l.lastOrNull() ?: basePrice,
                        sourceLabel = "FALLBACK",
                        updatedAtMs = nowMs
                    )
                }
            }
        }

        val feedLabel = when {
            finnhubQuotes.isNotEmpty() -> "FINNHUB + CHART API"
            liveCount > 0 -> "LIVE MARKET API"
            else -> "OFFLINE FALLBACK"
        }
        val warning = if (liveCount == 0) {
            "Live quote API unreachable — showing cached indicator series. Pull down to retry."
        } else {
            null
        }

        return TickerIndicatorsFetchResult(
            quotes = quotes,
            feedLabel = feedLabel,
            isLiveApi = liveCount > 0,
            fetchedAtMs = nowMs,
            warning = warning
        )
    }

    data class FinnhubFetchOutcome(
        val articles: List<AnnotatedNewsArticle>,
        val finnhubConnected: Boolean,
        val aiScoringLabel: String,
        val errorMessage: String? = null
    )

    suspend fun fetchAnnotatedFinnhubNews(
        trackedSymbols: List<String>,
        cfg: AppConfig,
        advisorEngine: AdvisorEngine
    ): FinnhubFetchOutcome {
        if (cfg.marketMode == MarketMode.CRYPTO) {
            val nowMs = System.currentTimeMillis()
            val currencies = trackedSymbols
                .map { CryptoFeedsEngine.normalizeCryptoSymbol(it).substringBefore("/") }
                .filter { it.isNotBlank() }
                .distinct()
                .take(6)
                .joinToString(",")
            val livePosts = cryptoService.fetchCryptoPanicPosts(
                currencies = currencies,
                token = cfg.cryptoPanicToken,
                limit = 22,
                nowMs = nowMs
            )
            val posts = livePosts.ifEmpty { CryptoFeedsEngine.fallbackCryptoPosts(nowMs) }
            val annotated = CryptoFeedsEngine.toAnnotatedArticles(posts, nowMs)
            return FinnhubFetchOutcome(
                articles = annotated,
                finnhubConnected = true,
                aiScoringLabel = if (livePosts.isNotEmpty()) "CRYPTOPANIC LIVE" else "CRYPTOPANIC GUARD"
            )
        }
        val finnhubKey = cfg.finnhubKey.trim().ifBlank { AdvisorConfig.fromBuildConfig().cleanFinnhubKey }
        if (finnhubKey.isBlank()) {
            return FinnhubFetchOutcome(
                articles = emptyList(),
                finnhubConnected = false,
                aiScoringLabel = "FINNHUB KEY NEEDED"
            )
        }

        val nowMs = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val toDate = dateFmt.format(Date(nowMs))
        val fromDate = dateFmt.format(Date(nowMs - 3L * 24L * 3_600_000L))
        val targetSyms = trackedSymbols
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(5)

        val collected = ArrayList<RawFinnhubArticle>()
        var anySuccess = false
        var lastErr: String? = null

        coroutineScope {
            val generalDeferred = async {
                try {
                    val list = finnhubApi.getMarketNews(category = "general", token = finnhubKey)
                    anySuccess = true
                    list.mapNotNull { dto -> mapFinnhubDto(dto, fallbackSymbol = "MACRO", nowMs = nowMs) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastErr = e.message ?: "Finnhub general news request failed"
                    Log.w(TAG, "Finnhub general news failed: ${e.message}")
                    emptyList()
                }
            }

            val companyDeferreds = targetSyms.map { sym ->
                async {
                    try {
                        val list = finnhubApi.getCompanyNews(
                            symbol = sym,
                            fromDate = fromDate,
                            toDate = toDate,
                            token = finnhubKey
                        )
                        anySuccess = true
                        list.take(5).mapNotNull { dto -> mapFinnhubDto(dto, fallbackSymbol = sym, nowMs = nowMs) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Finnhub company news failed ($sym): ${e.message}")
                        emptyList()
                    }
                }
            }

            collected.addAll(companyDeferreds.awaitAll().flatten())
            collected.addAll(generalDeferred.await())
        }

        val deduped = LinkedHashMap<String, RawFinnhubArticle>()
        collected
            .sortedByDescending { it.publishedAtMs }
            .forEach { art ->
                val normKey = art.headline.trim().lowercase(Locale.US)
                if (normKey.isNotEmpty() && !deduped.containsKey(normKey)) {
                    deduped[normKey] = art
                }
            }

        val topRaw = deduped.values.take(25)
        if (topRaw.isEmpty()) {
            return FinnhubFetchOutcome(
                articles = emptyList(),
                finnhubConnected = anySuccess,
                aiScoringLabel = if (anySuccess) "FINNHUB LIVE" else "FINNHUB ERROR",
                errorMessage = if (!anySuccess) (lastErr ?: "Unable to reach Finnhub API") else null
            )
        }

        // Respect the user's active AI provider & ON/OFF switches
        val activeProv = cfg.aiRouting.activeProvider
        val activeSettings = cfg.aiRouting.settingsFor(activeProv)
        val geminiCfg = resolveEnabledGemini(cfg)
        val (assessments, scoreSource) = when {
            !activeSettings.enabled -> {
                emptyMap<Int, AiCatalystAssessment>() to CatalystScoreSource.FINNHUB_MODEL
            }
            activeProv == AiProviderId.GEMINI && geminiCfg != null -> {
                val aiMap = scoreFinnhubWithGemini(topRaw, geminiCfg.first, geminiCfg.second)
                if (aiMap.isNotEmpty()) aiMap to CatalystScoreSource.AI_GEMINI
                else emptyMap<Int, AiCatalystAssessment>() to CatalystScoreSource.FINNHUB_MODEL
            }
            activeProv != AiProviderId.GEMINI && advisorEngine.enabled -> {
                val aiMap = advisorEngine.scoreFinnhubArticles(topRaw)
                if (aiMap.isNotEmpty()) aiMap to CatalystScoreSource.AI_ADVISOR
                else emptyMap<Int, AiCatalystAssessment>() to CatalystScoreSource.FINNHUB_MODEL
            }
            geminiCfg != null -> {
                val aiMap = scoreFinnhubWithGemini(topRaw, geminiCfg.first, geminiCfg.second)
                if (aiMap.isNotEmpty()) aiMap to CatalystScoreSource.AI_GEMINI
                else emptyMap<Int, AiCatalystAssessment>() to CatalystScoreSource.FINNHUB_MODEL
            }
            else -> emptyMap<Int, AiCatalystAssessment>() to CatalystScoreSource.FINNHUB_MODEL
        }

        val annotated = FinnhubCatalystEngine.annotateWithAiScores(
            rawArticles = topRaw,
            aiAssessmentsByIndex = assessments,
            scoreSource = scoreSource,
            nowMs = nowMs
        )

        return FinnhubFetchOutcome(
            articles = annotated,
            finnhubConnected = true,
            aiScoringLabel = scoreSource.badgeLabel
        )
    }

    private fun mapFinnhubDto(
        dto: FinnhubArticleDto,
        fallbackSymbol: String,
        nowMs: Long
    ): RawFinnhubArticle? {
        val cleanHeadline = dto.headline.trim()
        if (cleanHeadline.isEmpty()) return null
        val relatedTicker = dto.related
            .split(",")
            .map { it.trim().uppercase(Locale.US) }
            .firstOrNull { it.length in 1..6 && it.all { ch -> ch.isLetter() || ch == '.' } }
        val sym = when {
            fallbackSymbol != "MACRO" -> fallbackSymbol
            !relatedTicker.isNullOrBlank() -> relatedTicker
            else -> "MACRO"
        }
        val pubMs = if (dto.datetime > 1_000_000_000L) dto.datetime * 1000L else nowMs
        return RawFinnhubArticle(
            id = if (dto.id != 0L) "FH_${dto.id}_$sym" else "FH_${cleanHeadline.hashCode()}_$sym",
            symbol = sym,
            headline = cleanHeadline,
            summary = dto.summary.trim().take(260),
            source = dto.source.trim().ifBlank { "Finnhub" },
            category = dto.category.trim().ifBlank { "general" },
            url = dto.url.trim(),
            publishedAtMs = pubMs
        )
    }

    private fun resolveEnabledGemini(cfg: AppConfig): Pair<String, String>? {
        val gs = cfg.aiRouting.settingsFor(AiProviderId.GEMINI)
        if (!gs.enabled) return null
        val key = gs.apiKey.trim().ifBlank { cfg.geminiKey.trim() }
        if (key.isBlank()) return null
        val model = gs.selectedModelId.trim().ifBlank { "gemini-2.5-flash" }
        return key to model
    }

    private suspend fun scoreFinnhubWithGemini(
        articles: List<RawFinnhubArticle>,
        geminiKey: String,
        geminiModel: String = "gemini-2.5-flash"
    ): Map<Int, AiCatalystAssessment> {
        if (geminiKey.isBlank() || articles.isEmpty()) return emptyMap()
        val batch = articles.take(18)
        val lines = batch.mapIndexed { idx, art ->
            val sum = art.summary.replace('\n', ' ').take(150)
            "$idx | ${art.symbol} | ${art.source} | ${art.headline}${if (sum.isNotBlank()) " — $sum" else ""}"
        }.joinToString("\n")

        val itemSchema = GeminiSchemaObjectDto(
            type = "OBJECT",
            properties = mapOf(
                "id" to GeminiSchemaPropertyDto("INTEGER"),
                "direction" to GeminiSchemaPropertyDto("NUMBER"),
                "materiality" to GeminiSchemaPropertyDto("NUMBER"),
                "confidence" to GeminiSchemaPropertyDto("NUMBER"),
                "rationale" to GeminiSchemaPropertyDto("STRING")
            ),
            required = listOf("id", "direction", "materiality", "confidence", "rationale")
        )

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(
                        GeminiPartDto(
                            "Score each Finnhub financial news article for market catalyst impact.\n" +
                                "Rules: score ONLY what is stated; never invent facts. direction -1..1. " +
                                "materiality 0..1 (0=noise, 1=thesis-changing). Analyst tweaks <=0.30. " +
                                "rationale <=15 words.\n\n" + lines
                        )
                    )
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto("You are a precise equity catalyst news scorer. Output JSON only."))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.1,
                responseMimeType = "application/json",
                responseSchema = GeminiSchemaObjectDto(
                    type = "OBJECT",
                    properties = mapOf(
                        "items" to GeminiSchemaPropertyDto(type = "ARRAY", items = itemSchema)
                    ),
                    required = listOf("items")
                )
            )
        )

        return try {
            val response = withRetry(tries = 1) {
                geminiApi.scoreHeadlines(model = geminiModel, apiKey = geminiKey.trim(), body = request)
            }
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "{}"
            val parsed = scoredAdapter.fromJson(text) ?: return emptyMap()
            val out = LinkedHashMap<Int, AiCatalystAssessment>()
            parsed.items.orEmpty().forEach { item ->
                if (item.id in batch.indices) {
                    out[item.id] = AiCatalystAssessment(
                        direction = item.direction,
                        materiality = item.materiality,
                        confidence = item.confidence ?: 0.78,
                        rationale = item.rationale
                    )
                }
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Finnhub catalyst scoring failed: ${e.message}")
            emptyMap()
        }
    }

    suspend fun fetchLatestPrices(symbols: List<String>, cfg: AppConfig): Map<String, Double> {
        if (symbols.isEmpty() || cfg.demo || cfg.alpacaId.isBlank() || cfg.alpacaSecret.isBlank()) {
            return emptyMap()
        }
        return try {
            val res = alpacaApi.getSnapshots(
                keyId = cfg.alpacaId,
                secret = cfg.alpacaSecret,
                symbols = symbols.distinct().joinToString(",")
            )
            res.mapNotNull { (sym, snap) ->
                val price = snap.latestTrade?.p ?: snap.dailyBar?.c
                if (price != null && price > 0.0) sym to price else null
            }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "live quotes failed: ${e.message}")
            emptyMap()
        }
    }

    fun stepDemoQuotes(rows: List<SignalRow>): List<SignalRow> {
        return rows.map { row ->
            val isCrypto = row.symbol.contains("/")
            val volStep = (row.atrPct.coerceIn(0.5, if (isCrypto) 12.0 else 6.0) / 100.0) * 0.18
            val deltaPct = (kotlin.random.Random.nextDouble() - 0.48) * volStep
            val rawNext = (row.price * (1.0 + deltaPct)).coerceAtLeast(if (isCrypto) 0.01 else 0.5)
            val scale = if (isCrypto && rawNext < 5.0) 10000.0 else 100.0
            val nextPrice = kotlin.math.round(rawNext * scale) / scale
            val nextSeries = if (row.series.isNotEmpty()) {
                (row.series.drop(1) + nextPrice)
            } else {
                listOf(nextPrice)
            }
            row.copy(price = nextPrice, series = nextSeries)
        }
    }

    fun fetchVixSnapshot(
        cfg: AppConfig,
        regime: MarketRegime,
        previous: VixSnapshot?
    ): VixSnapshot {
        if (cfg.demo) {
            return if (previous != null && previous.source == "DEMO") {
                VixAlertEngine.stepDemoVix(previous)
            } else {
                VixAlertEngine.demoBaselineFromRegime(regime)
            }
        }
        val live = fetchLiveVixFromNetwork(previous)
        if (live != null) return live
        return previous?.let { VixAlertEngine.stepDemoVix(it) }
            ?: VixAlertEngine.demoBaselineFromRegime(regime)
    }

    private fun fetchLiveVixFromNetwork(previous: VixSnapshot?): VixSnapshot? {
        // 1. Official CBOE delayed intraday quote feed
        try {
            val req = Request.Builder()
                .url("https://cdn.cboe.com/api/global/delayed_quotes/quotes/_VIX.json")
                .header("User-Agent", "MarketScan/1.0")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        VixFeedParser.parseCboeJson(body, previous)?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CBOE VIX fetch failed: ${e.message}")
        }

        // 2. Yahoo Finance 5-minute intraday VIX chart fallback
        try {
            val req = Request.Builder()
                .url("https://query1.finance.yahoo.com/v8/finance/chart/%5EVIX?interval=5m&range=1d")
                .header("User-Agent", "MarketScan/1.0")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        VixFeedParser.parseYahooJson(body, previous)?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Yahoo VIX fetch failed: ${e.message}")
        }
        return null
    }

    private suspend fun <T> withRetry(tries: Int = 2, block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (i in 0..tries) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (i < tries) delay(500L * (i + 1))
            }
        }
        throw lastError ?: IllegalStateException("Request failed")
    }

    private suspend fun fetchBars(symbols: List<String>, cfg: AppConfig): Map<String, BarSeries> {
        val out = LinkedHashMap<String, BarSeries>()
        val start = Instant.now().minus(400, ChronoUnit.DAYS).toString().take(10)
        for (chunk in symbols.chunked(50)) {
            try {
                val res = withRetry {
                    alpacaApi.getBars(
                        keyId = cfg.alpacaId,
                        secret = cfg.alpacaSecret,
                        symbols = chunk.joinToString(","),
                        start = start
                    )
                }
                res.bars.orEmpty().forEach { (sym, arr) ->
                    if (arr.size >= 220) {
                        out[sym] = BarSeries(
                            o = arr.map { it.o },
                            h = arr.map { it.h },
                            l = arr.map { it.l },
                            c = arr.map { it.c },
                            v = arr.map { it.v }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "bars chunk failed: ${e.message}")
            }
        }
        return out
    }

    private suspend fun fetchSnapshots(symbols: List<String>, cfg: AppConfig): Map<String, SnapshotQuote> {
        val out = LinkedHashMap<String, SnapshotQuote>()
        for (chunk in symbols.chunked(50)) {
            try {
                val res = withRetry {
                    alpacaApi.getSnapshots(
                        keyId = cfg.alpacaId,
                        secret = cfg.alpacaSecret,
                        symbols = chunk.joinToString(",")
                    )
                }
                res.forEach { (sym, s) ->
                    val price = s.latestTrade?.p ?: s.dailyBar?.c ?: 0.0
                    val prev = s.prevDailyBar?.c ?: price
                    var spread = 10.0
                    val bp = s.latestQuote?.bp ?: 0.0
                    val ap = s.latestQuote?.ap ?: 0.0
                    if (bp > 0.0 && ap > 0.0) {
                        val mid = (bp + ap) / 2.0
                        if (mid > 0.0) spread = (ap - bp) / mid * 10000.0
                    }
                    out[sym] = SnapshotQuote(
                        price = price,
                        changePct = if (prev != 0.0) (price / prev - 1.0) * 100.0 else 0.0,
                        spreadBps = (spread * 10.0).roundToInt() / 10.0,
                        dollarVol = (s.dailyBar?.c ?: price) * (s.dailyBar?.v ?: 0.0)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "snapshot chunk failed: ${e.message}")
            }
        }
        return out
    }

    private suspend fun fetchNews(symbols: List<String>, cfg: AppConfig): List<NewsHeadline> {
        val out = ArrayList<NewsHeadline>()
        val start = Instant.now().minus(48, ChronoUnit.HOURS).toString()
        for (sym in symbols) {
            try {
                val res = withRetry(tries = 1) {
                    alpacaApi.getNews(
                        keyId = cfg.alpacaId,
                        secret = cfg.alpacaSecret,
                        symbols = sym,
                        start = start
                    )
                }
                res.news.orEmpty().forEach { n ->
                    out.add(
                        NewsHeadline(
                            sym = sym,
                            title = n.headline,
                            summary = (n.summary ?: "").take(300),
                            publishedIso = n.created_at,
                            source = n.source ?: ""
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "news failed $sym: ${e.message}")
            }
        }
        return out
    }

    private suspend fun scoreNews(
        headlines: List<NewsHeadline>,
        geminiKey: String,
        geminiModel: String = "gemini-2.5-flash"
    ): Map<String, SymbolNewsSummary> {
        if (geminiKey.isBlank() || headlines.isEmpty()) return emptyMap()

        val lines = headlines.mapIndexed { i, h ->
            "$i | ${h.sym} | ${h.publishedIso.take(16)} | ${h.title}"
        }.joinToString("\n")

        val itemSchema = GeminiSchemaObjectDto(
            type = "OBJECT",
            properties = mapOf(
                "id" to GeminiSchemaPropertyDto("INTEGER"),
                "direction" to GeminiSchemaPropertyDto("NUMBER"),
                "materiality" to GeminiSchemaPropertyDto("NUMBER"),
                "confidence" to GeminiSchemaPropertyDto("NUMBER"),
                "rationale" to GeminiSchemaPropertyDto("STRING")
            ),
            required = listOf("id", "direction", "materiality", "confidence", "rationale")
        )

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(
                        GeminiPartDto(
                            "Score each headline for a systematic trading desk.\n" +
                                "Rules: score ONLY what is stated; never invent facts. direction -1..1. " +
                                "materiality 0..1 (0=noise, 1=thesis-changing). Analyst target tweaks <=0.30. " +
                                "Uncertainty lowers both. rationale <=15 words.\n\n" + lines
                        )
                    )
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto("You are a precise equity news scorer. Output JSON only."))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.1,
                responseMimeType = "application/json",
                responseSchema = GeminiSchemaObjectDto(
                    type = "OBJECT",
                    properties = mapOf(
                        "items" to GeminiSchemaPropertyDto(type = "ARRAY", items = itemSchema)
                    ),
                    required = listOf("items")
                )
            )
        )

        return try {
            val response = withRetry(tries = 1) {
                geminiApi.scoreHeadlines(model = geminiModel, apiKey = geminiKey.trim(), body = request)
            }
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "{}"
            val parsed = scoredAdapter.fromJson(text) ?: return emptyMap()
            val nowMs = System.currentTimeMillis()
            data class Acc(var signed: Double = 0.0, var mat: Double = 0.0, var n: Int = 0, val items: MutableList<ScoredNewsItem> = mutableListOf())
            val bySym = LinkedHashMap<String, Acc>()

            for (it in parsed.items.orEmpty()) {
                val h = headlines.getOrNull(it.id) ?: continue
                val ageH = try {
                    if (h.publishedIso.isNotBlank()) {
                        (nowMs - Instant.parse(h.publishedIso).toEpochMilli()) / 3_600_000.0
                    } else 24.0
                } catch (_: Exception) {
                    24.0
                }
                val decay = 0.5.pow(max(ageH, 0.0) / 18.0)
                val acc = bySym.getOrPut(h.sym) { Acc() }
                acc.signed += it.direction * it.materiality * (it.confidence ?: 0.7) * decay
                acc.mat += it.materiality * decay
                acc.n++
                acc.items.add(
                    ScoredNewsItem(
                        title = h.title,
                        direction = it.direction,
                        materiality = it.materiality,
                        rationale = it.rationale,
                        decay = (decay * 100.0).roundToInt() / 100.0
                    )
                )
            }

            bySym.mapValues { (_, v) ->
                val n = max(v.n, 1)
                SymbolNewsSummary(
                    direction = QuantEngine.clamp(v.signed / n, -1.0, 1.0),
                    materiality = QuantEngine.clamp(v.mat / n, 0.0, 1.0),
                    signed = QuantEngine.clamp(v.signed / sqrt(n.toDouble()), -1.5, 1.5),
                    items = v.items.sortedByDescending { it.materiality }.take(5)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gemini failed: ${e.message}")
            emptyMap()
        }
    }

    suspend fun assessThreatCard(
        symbol: String,
        cachedBars: BarSeries?,
        peerBars: List<BarSeries>,
        benchBars: BarSeries?,
        cachedHeadlines: List<NewsHeadline>,
        position: ThreatPosition?,
        price: Double?,
        cfg: AppConfig
    ): ThreatCard? {
        val cleanSym = symbol.trim().uppercase()
        val resolvedBars = cachedBars ?: resolveBarsForSymbol(cleanSym, cfg) ?: return null
        val symHeadlines = cachedHeadlines.filter { it.sym.equals(cleanSym, ignoreCase = true) }
            .ifEmpty {
                if (!cfg.demo && cfg.alpacaId.isNotBlank() && cfg.alpacaSecret.isNotBlank()) {
                    fetchNews(listOf(cleanSym), cfg)
                } else {
                    emptyList()
                }
            }
        val geminiCfg = resolveEnabledGemini(cfg)
        val engine = ThreatEngine(
            narrativeClassifier = if (geminiCfg != null) {
                ThreatEngine.NarrativeClassifier { sym, list ->
                    classifyThreatWithGemini(sym, list, geminiCfg.first, geminiCfg.second)
                        ?.let { ThreatEngine.fromDomainNarrative(it, isLiveClassified = true) }
                }
            } else {
                null
            }
        )
        return engine.assess(
            symbol = cleanSym,
            bars = resolvedBars,
            headlines = symHeadlines,
            position = position,
            ctx = ThreatContext(
                benchBars = benchBars,
                peerBars = peerBars,
                priceOverride = price
            )
        )
    }

    suspend fun resolveBarsForSymbol(symbol: String, cfg: AppConfig): BarSeries? {
        val cleanSym = symbol.trim().uppercase()
        if (cleanSym.isEmpty()) return null
        if (!cfg.demo && cfg.alpacaId.isNotBlank() && cfg.alpacaSecret.isNotBlank()) {
            val fetched = fetchBars(listOf(cleanSym), cfg)[cleanSym]
            if (fetched != null) return fetched
        }
        return QuantEngine.genSeries(cleanSym)
    }

    suspend fun assessThreat(
        symbol: String,
        bars: BarSeries?,
        peerBars: List<BarSeries>,
        benchBars: BarSeries?,
        headlines: List<NewsHeadline>,
        position: ThreatPositionContext?,
        price: Double?,
        cfg: AppConfig
    ): ThreatAssessment? {
        val card = assessThreatCard(
            symbol = symbol,
            cachedBars = bars,
            peerBars = peerBars,
            benchBars = benchBars,
            cachedHeadlines = headlines,
            position = ThreatEngine.fromDomainPosition(position),
            price = price,
            cfg = cfg
        ) ?: return null
        return ThreatEngine.toDomainAssessment(card)
    }

    private suspend fun classifyThreatWithGemini(
        symbol: String,
        headlines: List<NewsHeadline>,
        geminiKey: String,
        geminiModel: String = "gemini-2.5-flash"
    ): ThreatNarrativeResult? {
        val taxonomyKeys = ThreatCategory.entries.joinToString(", ") { it.key }
        val lines = headlines.take(8).mapIndexed { idx, h ->
            "$idx | ${h.publishedIso.take(16)} | ${h.title}${if (h.summary.isNotBlank()) " — ${h.summary.take(160)}" else ""}"
        }.joinToString("\n")

        val schema = GeminiSchemaObjectDto(
            type = "OBJECT",
            properties = mapOf(
                "category" to GeminiSchemaPropertyDto("STRING"),
                "materiality" to GeminiSchemaPropertyDto("NUMBER"),
                "structural" to GeminiSchemaPropertyDto("BOOLEAN"),
                "company_specific" to GeminiSchemaPropertyDto("BOOLEAN"),
                "persistence_days" to GeminiSchemaPropertyDto("INTEGER"),
                "confidence" to GeminiSchemaPropertyDto("NUMBER"),
                "summary" to GeminiSchemaPropertyDto("STRING")
            ),
            required = listOf(
                "category",
                "materiality",
                "structural",
                "company_specific",
                "persistence_days",
                "confidence",
                "summary"
            )
        )

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(
                        GeminiPartDto(
                            "Classify downside threat risk for $symbol from ONLY these headlines.\n" +
                                "Valid category keys: $taxonomyKeys.\n" +
                                "Use 'none' if headlines are benign/positive. " +
                                "materiality 0..1, confidence 0..1, persistence_days 0..120, summary <= 22 words.\n\n$lines"
                        )
                    )
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto("You are an institutional equity risk desk classifier. Output JSON only."))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.1,
                responseMimeType = "application/json",
                responseSchema = schema
            )
        )

        return try {
            val response = withRetry(tries = 1) {
                geminiApi.scoreHeadlines(model = geminiModel, apiKey = geminiKey.trim(), body = request)
            }
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null
            val dto = threatAdapter.fromJson(text) ?: return null
            val cat = ThreatCategory.fromKey(dto.category)
            val mat = QuantEngine.clamp(dto.materiality, 0.0, 1.0)
            val conf = QuantEngine.clamp(dto.confidence, 0.1, 1.0)
            ThreatNarrativeResult(
                category = cat,
                materiality = mat,
                structural = dto.structural || cat.structural,
                companySpecific = dto.company_specific,
                persistenceDays = if (dto.persistence_days > 0) dto.persistence_days else cat.persistenceDays,
                confidence = conf,
                summary = dto.summary.ifBlank { cat.label },
                severityPrior = cat.severity,
                degraded = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gemini threat classify failed ($symbol): ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "MarketRepository"
    }
}
