package com.example.domain.engine

import com.example.domain.model.AnnotatedNewsArticle
import com.example.domain.model.BarSeries
import com.example.domain.model.CatalystScoreSource
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoEventCategory
import com.example.domain.model.CryptoEventClassification
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoPanicPost
import com.example.domain.model.MarketSector
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SnapshotQuote
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure Kotlin port of `derivs.py` (Kraken Futures perpetual funding, open interest, basis,
 * 30+ sample crowding percentiles) and `feeds.py` (Kraken Spot pair mapping, OHLC/Ticker parsing,
 * CryptoPanic deterministic community-vote scoring, and the entry-only Risk Guard).
 *
 * All endpoints are public, key-free market data feeds reachable from the US 24/7.
 */
object CryptoFeedsEngine {

    const val KRAKEN_SPOT_PUBLIC_BASE = "https://api.kraken.com/0/public"
    const val KRAKEN_FUTURES_TICKERS_URL = "https://futures.kraken.com/derivatives/api/v3/tickers"
    val CRYPTOPANIC_BASES = listOf(
        "https://cryptopanic.com/api/free/v1",
        "https://cryptopanic.com/api/v1"
    )

    const val MIN_HISTORY_FOR_PERCENTILE = 30
    const val HISTORY_WINDOW_DAYS = 120L

    /** Kraken Spot & Futures crypto universe covering derivs.py, feeds.py, and cryptotrader.py 2.0. */
    val CRYPTO_UNIVERSE = listOf(
        "BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD",
        "ADA/USD", "DOT/USD", "LINK/USD", "AVAX/USD",
        "LTC/USD", "BCH/USD", "DOGE/USD", "XLM/USD",
        "AAVE/USD", "UNI/USD", "GRT/USD", "MKR/USD",
        "CRV/USD", "ALGO/USD", "XTZ/USD"
    )

    /** Kraken Futures perpetual contract symbol -> normalized spot pair. */
    val KRAKEN_FUTURES_MAP = mapOf(
        "PF_XBTUSD" to "BTC/USD",
        "PF_ETHUSD" to "ETH/USD",
        "PF_SOLUSD" to "SOL/USD",
        "PF_XRPUSD" to "XRP/USD",
        "PF_ADAUSD" to "ADA/USD",
        "PF_DOTUSD" to "DOT/USD",
        "PF_LINKUSD" to "LINK/USD",
        "PF_AVAXUSD" to "AVAX/USD",
        "PF_LTCUSD" to "LTC/USD",
        "PF_BCHUSD" to "BCH/USD",
        "PF_DOGEUSD" to "DOGE/USD",
        "PF_XLMUSD" to "XLM/USD"
    )

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()

    /** Normalizes user input such as "btc", "BTCUSD", "btc/usd" -> "BTC/USD". */
    fun normalizeCryptoSymbol(raw: String): String {
        val clean = raw.trim().uppercase(Locale.US).replace("-", "/")
        if (clean.isEmpty()) return ""
        if (clean.contains("/")) {
            val parts = clean.split("/")
            val base = parts.getOrNull(0).orEmpty().filter { it.isLetterOrDigit() }
            val quote = parts.getOrNull(1).orEmpty().filter { it.isLetterOrDigit() }.ifBlank { "USD" }
            return if (base.isNotEmpty()) "$base/$quote" else ""
        }
        val alnum = clean.filter { it.isLetterOrDigit() }
        if (alnum.isEmpty()) return ""
        return if (alnum.endsWith("USD") && alnum.length > 3) {
            "${alnum.removeSuffix("USD")}/USD"
        } else {
            "$alnum/USD"
        }
    }

    /** "BTC/USD" -> "XBTUSD", "DOGE/USD" -> "XDGUSD" (Kraken's legacy base codes). */
    fun krakenSpotPair(symbol: String): String {
        val norm = normalizeCryptoSymbol(symbol)
        val parts = norm.split("/")
        val base = parts.getOrNull(0).orEmpty()
        val quote = parts.getOrNull(1).orEmpty().ifBlank { "USD" }
        val krakenBase = when (base) {
            "BTC" -> "XBT"
            "DOGE" -> "XDG"
            else -> base
        }
        return "$krakenBase$quote"
    }

    fun baseAsset(symbol: String): String =
        normalizeCryptoSymbol(symbol).substringBefore("/")

    fun classifyCryptoSector(symbol: String): MarketSector = when (baseAsset(symbol)) {
        "BTC", "ETH", "SOL", "AVAX", "ADA", "DOT" -> MarketSector.CRYPTO_L1
        "LINK", "UNI", "AAVE", "ARB", "OP", "MATIC", "POL" -> MarketSector.CRYPTO_DEFI
        "XRP", "LTC", "BCH", "DOGE", "XLM", "SHIB" -> MarketSector.CRYPTO_PAYMENTS
        else -> MarketSector.CRYPTO_L1
    }

    // ── Kraken Spot Parsing ──────────────────────────────────────────────────────────────

    data class KrakenTickerQuote(
        val symbol: String,
        val bid: Double,
        val ask: Double,
        val last: Double,
        val volume24h: Double,
        val high24h: Double,
        val low24h: Double,
        val open24h: Double
    ) {
        val changePct: Double
            get() = if (open24h > 0.0) ((last - open24h) / open24h) * 100.0 else 0.0

        val spreadBps: Double
            get() {
                val mid = (bid + ask) / 2.0
                return if (mid > 0.0 && ask >= bid) ((ask - bid) / mid) * 10_000.0 else 8.0
            }

        val dollarVol24h: Double
            get() = max(volume24h * last, 1.0)

        fun toSnapshotQuote(): SnapshotQuote = SnapshotQuote(
            price = last,
            changePct = changePct,
            spreadBps = spreadBps,
            dollarVol = dollarVol24h
        )
    }

    /**
     * Parses Kraken `/0/public/Ticker` JSON response for one or more pairs.
     */
    fun parseKrakenTickersJson(
        rawJson: String,
        requestedSymbols: List<String>
    ): Map<String, KrakenTickerQuote> {
        val root = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return emptyMap()
        val result = root["result"] as? Map<*, *> ?: return emptyMap()

        val pairLookup = LinkedHashMap<String, String>()
        requestedSymbols.forEach { sym ->
            val norm = normalizeCryptoSymbol(sym)
            val kp = krakenSpotPair(norm)
            pairLookup[kp] = norm
            // Kraken often prefixes legacy pairs with X / Z (e.g., XXBTZUSD, XETHZUSD)
            val base = kp.removeSuffix("USD")
            pairLookup["X${base}ZUSD"] = norm
            pairLookup["X${base}USD"] = norm
            pairLookup["${base}ZUSD"] = norm
        }

        val out = LinkedHashMap<String, KrakenTickerQuote>()
        for ((kRaw, vRaw) in result) {
            val k = kRaw?.toString().orEmpty()
            if (k == "last") continue
            val row = vRaw as? Map<*, *> ?: continue
            val matchedSym = pairLookup[k]
                ?: if (requestedSymbols.size == 1) normalizeCryptoSymbol(requestedSymbols.first()) else null
                ?: continue

            val ask = firstNum(row["a"])
            val bid = firstNum(row["b"])
            val last = firstNum(row["c"])
            val vol24h = nthNum(row["v"], 1)
            val high24h = nthNum(row["h"], 1)
            val low24h = nthNum(row["l"], 1)
            val open = toFiniteDouble(row["o"])
            if (last > 0.0) {
                out[matchedSym] = KrakenTickerQuote(
                    symbol = matchedSym,
                    bid = if (bid > 0.0) bid else last,
                    ask = if (ask > 0.0) ask else last,
                    last = last,
                    volume24h = vol24h,
                    high24h = if (high24h > 0.0) high24h else last,
                    low24h = if (low24h > 0.0) low24h else last,
                    open24h = if (open > 0.0) open else last
                )
            }
        }
        return out
    }

    /**
     * Parses Kraken `/0/public/OHLC` JSON response into a [BarSeries].
     */
    fun parseKrakenOhlcJson(rawJson: String): BarSeries? =
        parseKrakenOhlcWithTimestampJson(rawJson)?.first

    /**
     * Parses Kraken `/0/public/OHLC` JSON response into a [BarSeries] plus the latest bar's
     * epoch timestamp (seconds) for feed-freshness checks (`feed_age` in `cryptotrader.py 2.0`).
     */
    fun parseKrakenOhlcWithTimestampJson(rawJson: String): Pair<BarSeries, Long>? {
        val root = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val result = root["result"] as? Map<*, *> ?: return null
        val pairKey = result.keys.mapNotNull { it?.toString() }.firstOrNull { it != "last" } ?: return null
        val rows = result[pairKey] as? List<*> ?: return null
        if (rows.isEmpty()) return null

        val o = ArrayList<Double>(rows.size)
        val h = ArrayList<Double>(rows.size)
        val l = ArrayList<Double>(rows.size)
        val c = ArrayList<Double>(rows.size)
        val v = ArrayList<Double>(rows.size)
        var lastTsSec = 0L

        for (item in rows) {
            val arr = item as? List<*> ?: continue
            if (arr.size < 7) continue
            val tsSec = toFiniteDouble(arr[0]).toLong()
            val op = toFiniteDouble(arr[1])
            val hi = toFiniteDouble(arr[2])
            val lo = toFiniteDouble(arr[3])
            val cl = toFiniteDouble(arr[4])
            val vol = toFiniteDouble(arr[6])
            if (cl > 0.0) {
                o.add(if (op > 0.0) op else cl)
                h.add(if (hi > 0.0) hi else cl)
                l.add(if (lo > 0.0) lo else cl)
                c.add(cl)
                v.add(max(vol, 0.0))
                if (tsSec > lastTsSec) lastTsSec = tsSec
            }
        }
        return if (c.size >= 60) BarSeries(o, h, l, c, v) to lastTsSec else null
    }

    // ── Kraken Futures (derivs.py) ───────────────────────────────────────────────────────

    data class CrowdingResult(
        val mult: Double,
        val percentile: Double?,
        val note: String
    )

    /**
     * Parses Kraken Futures `/derivatives/api/v3/tickers` into [CryptoDerivsSnapshot] keyed by spot pair,
     * computing `funding_annual = funding_8h * 3 * 365`, `basis_bps = (mark / index - 1) * 10_000`,
     * and attaching crowding percentiles when >= 30 historical samples exist.
     */
    fun parseKrakenFuturesJson(
        rawJson: String,
        history: List<CryptoFundingHistoryPoint> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, CryptoDerivsSnapshot> {
        val root = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return emptyMap()
        val tickers = root["tickers"] as? List<*> ?: return emptyMap()
        val tsIso = Instant.ofEpochMilli(nowMs).toString()

        val out = LinkedHashMap<String, CryptoDerivsSnapshot>()
        for (item in tickers) {
            val row = item as? Map<*, *> ?: continue
            val rawSym = row["symbol"]?.toString().orEmpty()
            val sym = KRAKEN_FUTURES_MAP[rawSym] ?: continue
            val mark = toFiniteDouble(row["markPrice"])
            val index = toFiniteDouble(row["indexPrice"])
            val rate = toFiniteDouble(row["fundingRate"])
            val fundingAnnual = rate * 3.0 * 365.0
            val openInterest = toFiniteDouble(row["openInterest"])
            val volumeQuote = toFiniteDouble(row["volumeQuote"])
            val rawBasis = toFiniteDouble(row["basis"])
            val basis = if (rawBasis == 0.0 && index > 0.0) mark - index else rawBasis
            val basisBps = if (index > 0.0) (basis / index) * 10_000.0 else 0.0

            val symHistory = historyForSymbol(history, sym, nowMs)
            val pct = computeFundingPercentile(symHistory, fundingAnnual)
            val crowding = evaluateCrowding(pct)

            out[sym] = CryptoDerivsSnapshot(
                symbol = sym,
                tsIso = tsIso,
                funding8h = rate,
                fundingAnnual = fundingAnnual,
                openInterest = openInterest,
                mark = mark,
                index = index,
                basisBps = basisBps,
                volumeQuote = volumeQuote,
                crowdingPct = crowding.percentile,
                crowdingMult = crowding.mult,
                crowdingNote = crowding.note,
                historySampleCount = symHistory.size
            )
        }
        return out
    }

    fun historyForSymbol(
        history: List<CryptoFundingHistoryPoint>,
        symbol: String,
        nowMs: Long = System.currentTimeMillis(),
        days: Long = HISTORY_WINDOW_DAYS
    ): List<CryptoFundingHistoryPoint> {
        val cutoff = nowMs - days * 86_400_000L
        return history.filter { it.symbol == symbol && it.tsMs >= cutoff }
    }

    /**
     * Exact `derivs.py` percentile rule: returns null when fewer than 30 stored samples exist
     * ("a percentile over 12 points is decoration").
     */
    fun computeFundingPercentile(
        symbolHistory: List<CryptoFundingHistoryPoint>,
        latestFundingAnnual: Double? = null
    ): Double? {
        if (symbolHistory.size < MIN_HISTORY_FOR_PERCENTILE) return null
        val vals = symbolHistory.map { it.fundingAnnual }
        val latest = latestFundingAnnual ?: vals.last()
        val rankCount = vals.count { it <= latest }
        return rankCount.toDouble() / vals.size.toDouble()
    }

    /**
     * Exact `derivs.py` crowding rule: returns a size multiplier in [0.4, 1.0].
     * Crowded longs shrink position size; never blocks outright.
     */
    fun evaluateCrowding(percentile: Double?): CrowdingResult {
        if (percentile == null) {
            return CrowdingResult(1.0, null, "insufficient history (<30 pts) — neutral")
        }
        val rounded = (percentile * 1000.0).roundToInt() / 1000.0
        return when {
            percentile >= 0.95 -> CrowdingResult(0.4, rounded, "funding in top 5% — longs crowded")
            percentile >= 0.85 -> CrowdingResult(0.7, rounded, "funding elevated")
            percentile <= 0.05 -> CrowdingResult(1.0, rounded, "funding negative — shorts paying")
            else -> CrowdingResult(1.0, rounded, "normal")
        }
    }

    fun appendFundingHistory(
        existing: List<CryptoFundingHistoryPoint>,
        snapshots: Map<String, CryptoDerivsSnapshot>,
        nowMs: Long = System.currentTimeMillis()
    ): List<CryptoFundingHistoryPoint> {
        val cutoff = nowMs - HISTORY_WINDOW_DAYS * 86_400_000L
        val kept = existing.filter { it.tsMs >= cutoff }.toMutableList()
        snapshots.values.forEach { snap ->
            // Throttle recordings to at most once every 30 minutes per symbol so percentiles track real time
            val lastForSym = kept.lastOrNull { it.symbol == snap.symbol }
            if (lastForSym == null || (nowMs - lastForSym.tsMs) >= 1_800_000L) {
                kept.add(
                    CryptoFundingHistoryPoint(
                        tsMs = nowMs,
                        symbol = snap.symbol,
                        funding8h = snap.funding8h,
                        fundingAnnual = snap.fundingAnnual,
                        openInterest = snap.openInterest,
                        basisBps = snap.basisBps
                    )
                )
            }
        }
        return kept.takeLast(3600)
    }

    // ── CryptoPanic Deterministic Scoring & Guard (feeds.py) ─────────────────────────────

    /**
     * Exact `feeds.py` CryptoPanic deterministic community-vote score:
     * `base = (pos - neg) / (pos + neg)` amplified by `1 + min(important, 5) * 0.12`, clamped to [-1, 1].
     */
    fun cryptoPanicVoteScore(positive: Int, negative: Int, important: Int): Double {
        val total = positive + negative
        val base = if (total <= 0) 0.0 else (positive - negative).toDouble() / total.toDouble()
        val amp = 1.0 + min(max(important, 0), 5) * 0.12
        return (base * amp).coerceIn(-1.0, 1.0)
    }

    /** 12h half-life exponential recency decay from `feeds.py`. */
    fun cryptoPanicDecayWeight(ageHours: Double): Double =
        0.5.pow(max(0.0, ageHours) / 12.0)

    fun parseCryptoPanicPostsJson(
        rawJson: String,
        limit: Int = 20,
        maxAgeHours: Double = 24.0,
        nowMs: Long = System.currentTimeMillis()
    ): List<CryptoPanicPost> {
        val root = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return emptyList()
        val results = root["results"] as? List<*> ?: return emptyList()
        val out = ArrayList<CryptoPanicPost>()

        for (entry in results) {
            if (out.size >= limit) break
            val row = entry as? Map<*, *> ?: continue
            val title = row["title"]?.toString()?.trim().orEmpty().take(220)
            if (title.isBlank()) continue

            val pubRaw = row["published_at"]?.toString()?.trim().orEmpty()
            val pubMs = runCatching { Instant.parse(pubRaw).toEpochMilli() }.getOrElse { nowMs }
            val ageHours = max(0.0, (nowMs - pubMs) / 3_600_000.0)
            if (ageHours > maxAgeHours) continue

            val votes = row["votes"] as? Map<*, *>
            val pos = (votes?.get("positive") as? Number)?.toInt() ?: 0
            val neg = (votes?.get("negative") as? Number)?.toInt() ?: 0
            val imp = (votes?.get("important") as? Number)?.toInt() ?: 0
            val score = cryptoPanicVoteScore(pos, neg, imp)
            val weight = cryptoPanicDecayWeight(ageHours)

            val src = row["source"] as? Map<*, *>
            val sourceTitle = (src?.get("title") ?: src?.get("domain"))?.toString()?.trim().orEmpty().take(28)
            val curList = (row["currencies"] as? List<*>)
                ?.mapNotNull { (it as? Map<*, *>)?.get("code")?.toString()?.trim()?.uppercase(Locale.US) }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            out.add(
                CryptoPanicPost(
                    title = title,
                    url = row["url"]?.toString()?.trim().orEmpty(),
                    source = sourceTitle.ifBlank { "CryptoPanic" },
                    publishedIso = pubRaw.ifBlank { Instant.ofEpochMilli(nowMs).toString() },
                    ageHours = (ageHours * 100.0).roundToInt() / 100.0,
                    currencies = curList,
                    positive = pos,
                    negative = neg,
                    important = imp,
                    score = (score * 1000.0).roundToInt() / 1000.0,
                    weight = (weight * 1000.0).roundToInt() / 1000.0,
                    kind = row["kind"]?.toString()?.trim().orEmpty().ifBlank { "news" }
                )
            )
        }
        return out
    }

    /**
     * Exact `feeds.py` Guard.news_score:
     * Importance-weighted mean, plus hard-negative override when any post has
     * `score <= -0.60 && important >= 2 && ageHours <= 6.0` -> caps aggregate at `-0.75`.
     */
    fun guardNewsScore(posts: List<CryptoPanicPost>): Double {
        if (posts.isEmpty()) return 0.0
        var num = 0.0
        var den = 0.0
        for (p in posts) {
            val w = p.weight * (1.0 + p.important)
            num += p.score * w
            den += w
        }
        var agg = if (den > 0.0) num / den else 0.0
        val hasHardNegativeSpike = posts.any {
            it.score <= -0.60 && it.important >= 2 && it.ageHours <= 6.0
        }
        if (hasHardNegativeSpike) {
            agg = min(agg, -0.75)
        }
        return agg.coerceIn(-1.0, 1.0)
    }

    /**
     * Exact `feeds.py` + `derivs.py` combined Entry Guard:
     * - Returns `sizeMult` in `[0.25, 1.0]` (never increases size on positive news).
     * - Sets `blocked = true` when `newsScore <= -0.75` (blocks new entries; never panic-sells held positions).
     */
    fun evaluateGuard(
        symbol: String,
        posts: List<CryptoPanicPost>,
        derivs: CryptoDerivsSnapshot? = null,
        eventClassifications: List<CryptoEventClassification> = emptyList()
    ): CryptoGuardVerdict {
        val code = baseAsset(symbol)
        val relevantPosts = posts.filter { post ->
            post.currencies.isEmpty() || post.currencies.any { it.equals(code, ignoreCase = true) }
        }
        val ns = guardNewsScore(relevantPosts)

        var mult = 1.0
        var blocked = ns <= -0.75
        val notes = mutableListOf<String>()

        when {
            ns <= -0.75 -> {
                mult = min(mult, 0.25)
                notes.add("hard negative news cluster")
            }
            ns <= -0.40 -> {
                mult = min(mult, 0.55)
                notes.add("negative news")
            }
            ns >= 0.50 -> {
                notes.add("positive news (no size increase — ever)")
            }
        }

        // Categorical risk events from `crypto_events.py` — can only shrink size or block entry
        var worstEvent: CryptoEventClassification? = null
        eventClassifications.filter { it.isAdverse }.forEach { ev ->
            mult = min(mult, ev.mult)
            blocked = blocked || ev.block
            notes.add("${ev.category.name} (${String.format(Locale.US, "%.2f", ev.confidence)})")
            if (worstEvent == null || ev.mult < worstEvent!!.mult || (ev.block && !worstEvent!!.block)) {
                worstEvent = ev
            }
        }

        if (derivs != null) {
            val fa = derivs.fundingAnnual
            when {
                fa > 0.50 -> {
                    mult = min(mult, 0.45)
                    notes.add("funding ${(fa * 100.0).roundToInt()}% annual — longs crowded")
                }
                fa > 0.25 -> {
                    mult = min(mult, 0.70)
                    notes.add("funding ${(fa * 100.0).roundToInt()}% annual — elevated")
                }
            }
            if (derivs.crowdingPct != null && derivs.crowdingMult < mult) {
                mult = min(mult, derivs.crowdingMult)
                notes.add(derivs.crowdingNote)
            }
        }

        val topHeadlines = relevantPosts
            .sortedWith(compareByDescending<CryptoPanicPost> { it.important }.thenBy { it.ageHours })
            .take(3)
            .map { it.title }

        val effectiveMult = if (blocked && (worstEvent?.mult == 0.0)) {
            0.0
        } else {
            ((max(0.25, mult)) * 1000.0).roundToInt() / 1000.0
        }

        return CryptoGuardVerdict(
            symbol = normalizeCryptoSymbol(symbol),
            sizeMult = effectiveMult,
            newsScore = (ns * 1000.0).roundToInt() / 1000.0,
            blocked = blocked,
            notes = notes,
            headlines = topHeadlines,
            fundingAnnual = derivs?.fundingAnnual,
            crowdingPct = derivs?.crowdingPct,
            crowdingNote = derivs?.crowdingNote.orEmpty(),
            eventCategory = worstEvent?.category?.name ?: CryptoEventCategory.NONE.name,
            eventConfidence = worstEvent?.confidence ?: 0.0,
            eventSpan = worstEvent?.span.orEmpty(),
            eventWhy = worstEvent?.category?.why.orEmpty()
        )
    }

    fun toNewsHeadlines(posts: List<CryptoPanicPost>): List<NewsHeadline> =
        posts.map { post ->
            val primaryCode = post.currencies.firstOrNull().orEmpty().ifBlank { "BTC" }
            NewsHeadline(
                sym = "$primaryCode/USD",
                title = post.title,
                summary = "CryptoPanic votes: +${post.positive} / -${post.negative} · imp ${post.important} · score ${"%+.2f".format(Locale.US, post.score)}",
                publishedIso = post.publishedIso,
                source = post.source
            )
        }

    fun toAnnotatedArticles(posts: List<CryptoPanicPost>, nowMs: Long = System.currentTimeMillis()): List<AnnotatedNewsArticle> =
        posts.mapIndexed { idx, post ->
            val primaryCode = post.currencies.firstOrNull().orEmpty().ifBlank { "BTC" }
            val sym = "$primaryCode/USD"
            val pubMs = runCatching { Instant.parse(post.publishedIso).toEpochMilli() }.getOrDefault(nowMs)
            val materiality = (0.35 + min(post.important, 5) * 0.11 + abs(post.score) * 0.25).coerceIn(0.2, 0.96)
            val catalystScore = (post.score * materiality * post.weight * 100.0).roundToInt().coerceIn(-100, 100)
            val rationale = buildString {
                append("Community votes +${post.positive}/-${post.negative}")
                if (post.important > 0) append(" (${post.important} important)")
                append(" · 12h decay ${(post.weight * 100).roundToInt()}%")
            }
            AnnotatedNewsArticle(
                id = "CP_${sym}_${idx}_${post.title.hashCode()}",
                symbol = sym,
                headline = post.title,
                summary = "Kraken 24/7 Guard score: ${"%+.2f".format(Locale.US, post.score)}",
                source = post.source,
                category = "crypto",
                url = post.url,
                publishedAtMs = pubMs,
                catalystScore = catalystScore,
                direction = post.score,
                materiality = (materiality * 100.0).roundToInt() / 100.0,
                confidence = if (post.positive + post.negative >= 5) 0.82 else 0.60,
                decayFactor = post.weight,
                catalystTag = if (post.important >= 2) "GUARD" else "CRYPTO",
                rationale = rationale,
                scoreSource = CatalystScoreSource.FINNHUB_MODEL,
                isFromFinnhub = false
            )
        }

    // ── Deterministic Offline Fallback (only used if Kraken / CryptoPanic unreachable) ────

    fun fallbackCryptoPosts(nowMs: Long = System.currentTimeMillis()): List<CryptoPanicPost> {
        val now = Instant.ofEpochMilli(nowMs)
        return listOf(
            CryptoPanicPost(
                title = "Bitcoin spot ETF net inflows stay positive across US trading desks",
                url = "https://cryptopanic.com",
                source = "CryptoPanic",
                publishedIso = now.minus(2, ChronoUnit.HOURS).toString(),
                ageHours = 2.0,
                currencies = listOf("BTC"),
                positive = 14,
                negative = 3,
                important = 2,
                score = cryptoPanicVoteScore(14, 3, 2),
                weight = cryptoPanicDecayWeight(2.0)
            ),
            CryptoPanicPost(
                title = "Ethereum validator staking queue climbs as Layer-2 settlement volumes expand",
                url = "https://cryptopanic.com",
                source = "CryptoPanic",
                publishedIso = now.minus(4, ChronoUnit.HOURS).toString(),
                ageHours = 4.0,
                currencies = listOf("ETH"),
                positive = 9,
                negative = 2,
                important = 1,
                score = cryptoPanicVoteScore(9, 2, 1),
                weight = cryptoPanicDecayWeight(4.0)
            ),
            CryptoPanicPost(
                title = "Solana decentralized exchange volume hits weekly high amid rising perpetual open interest",
                url = "https://cryptopanic.com",
                source = "CryptoPanic",
                publishedIso = now.minus(5, ChronoUnit.HOURS).toString(),
                ageHours = 5.0,
                currencies = listOf("SOL"),
                positive = 11,
                negative = 4,
                important = 1,
                score = cryptoPanicVoteScore(11, 4, 1),
                weight = cryptoPanicDecayWeight(5.0)
            )
        )
    }

    private fun firstNum(raw: Any?): Double = nthNum(raw, 0)

    private fun nthNum(raw: Any?, index: Int): Double {
        val list = raw as? List<*> ?: return 0.0
        return toFiniteDouble(list.getOrNull(index))
    }

    private fun toFiniteDouble(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble().takeIf { it.isFinite() } ?: 0.0
        is String -> raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        else -> 0.0
    }
}
