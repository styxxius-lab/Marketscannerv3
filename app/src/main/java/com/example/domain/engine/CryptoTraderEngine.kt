package com.example.domain.engine

import com.example.domain.model.BarSeries
import com.example.domain.model.CryptoBacktestSummary
import com.example.domain.model.CryptoBtcRegime
import com.example.domain.model.CryptoDecayReport
import com.example.domain.model.CryptoEventCategory
import com.example.domain.model.CryptoEventClassification
import com.example.domain.model.CryptoFeedFreshness
import com.example.domain.model.CryptoHeatRow
import com.example.domain.model.CryptoPortfolioHeat
import com.example.domain.model.CryptoTsmomSignal
import com.example.domain.model.DecayVerdict
import com.example.domain.model.StablecoinPegRow
import com.example.domain.model.StablecoinPegStatus
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradePosition
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin port of `cryptotrader.py 2.0` + `crypto_events.py`:
 * - Categorical crypto news risk gate (`HACK`, `HALT`, `DELIST`, `DEPEG`, `REGULATORY`, `UNLOCK`, `OUTAGE`, `NONE`)
 * - Multi-horizon risk-adjusted TSMOM signal (`lookbacks = [7, 30, 90]`, 365-day annualised vol, EMA 20/50/200 stack)
 * - BTC macro regime scaler (`RISK_ON`, `ELEVATED`, `RISK_OFF`, `CRISIS`)
 * - Portfolio heat cap (`max_heat_pct = 6.0%`), half-Kelly cap, pairwise 60d correlation cap (`0.90`)
 * - Stablecoin peg monitor (`USDT/USD`, `USDC/USD`, `1.5%` threshold), edge decay t-stat guard, feed freshness
 * - 9-variant grid backtester with fees + slippage both sides, Net Sharpe (365), Deflated Sharpe Ratio (DSR), and PBO
 *
 * Strictly paper-simulation & analytics only (no real-money exchange execution).
 */
object CryptoTraderEngine {

    val LOOKBACKS = listOf(7, 30, 90)
    const val ENTRY_SCORE = 0.60
    const val EXIT_SCORE = -0.20
    const val HOLDING_HORIZON_DAYS = 21
    const val MIN_EDGE_COST_RATIO = 3.0
    const val HARD_STOP_ATR = 2.5
    const val MAX_HEAT_PCT = 6.0
    const val MAX_CORR = 0.90
    const val PEG_BREACH_THRESHOLD = 0.015
    const val FEED_MAX_AGE_HOURS = 48.0
    const val DEFAULT_FEE_BPS = 12.0
    const val DEFAULT_SLIP_BPS = 8.0

    /** Exact risk-keyword pre-filter from `cryptotrader.py 2.0` before spending LLM tokens. */
    val RISK_WORDS = listOf(
        "hack", "exploit", "breach", "stolen", "drained", "halt",
        "suspend", "delist", "depeg", "insolven", "bankrupt", "frozen",
        "lawsuit", "sues", "sec charges", "ban", "outage", "paused",
        "withdrawal", "shutdown"
    )

    /** Exact system prompt from `crypto_events.py` / `cryptotrader.py 2.0`. */
    const val EVENT_PROMPT = """Classify ONE crypto headline into exactly one category.

Categories: HACK, HALT, DELIST, DEPEG, REGULATORY, UNLOCK, OUTAGE, NONE.

Rules:
- Output JSON only: {"category":str,"confidence":0.0-1.0,"span":str}
- Use NONE for price predictions, analyst calls, ETF flows, partnerships,
  listings, upgrades that completed, and routine market commentary.
- Use only what is written. You have no reliable knowledge of current events,
  token supplies, unlock schedules or team changes. Do not infer them.
- confidence above 0.8 requires the category to be stated plainly in the text."""

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()

    // ── Categorical Event Gate (`crypto_events.py`) ──────────────────────────

    fun hasRiskKeyword(headline: String): Boolean {
        val lower = headline.lowercase(Locale.US)
        return RISK_WORDS.any { lower.contains(it) }
    }

    /**
     * Parses raw LLM JSON output strictly following `crypto_events.py`:
     * - Unknown categories or malformed JSON -> NONE (fail-open; never breaks trading).
     * - Confidence < 0.65 for non-NONE -> NONE.
     */
    fun parseEventClassificationJson(
        rawJson: String?,
        headline: String = "",
        viaLlm: Boolean = true
    ): CryptoEventClassification {
        if (rawJson.isNullOrBlank()) return CryptoEventClassification.none(headline)
        val parsed = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: run {
            val a = rawJson.indexOf('{')
            val b = rawJson.lastIndexOf('}')
            if (a < 0 || b <= a) return CryptoEventClassification.none(headline)
            runCatching { mapAdapter.fromJson(rawJson.substring(a, b + 1)) }.getOrNull()
        } ?: return CryptoEventClassification.none(headline)

        val cat = CryptoEventCategory.fromWire(parsed["category"]?.toString())
        val conf = toFiniteDouble(parsed["confidence"]).coerceIn(0.0, 1.0)
        if (cat != CryptoEventCategory.NONE && conf < 0.65) {
            return CryptoEventClassification.none(headline)
        }
        val span = parsed["span"]?.toString()?.trim().orEmpty().take(80)
        return CryptoEventClassification(
            category = cat,
            mult = cat.mult,
            block = cat.block,
            confidence = round2(conf),
            span = span,
            headline = headline.take(220),
            viaLlm = viaLlm
        )
    }

    /**
     * Deterministic fallback classifier used only when no LLM endpoint is configured,
     * requiring explicit unambiguous risk terms so routine market noise stays NONE.
     */
    fun classifyHeadlineDeterministic(headline: String): CryptoEventClassification {
        if (!hasRiskKeyword(headline)) return CryptoEventClassification.none(headline)
        val lower = headline.lowercase(Locale.US)
        val match: Pair<CryptoEventCategory, String>? = when {
            listOf("hack", "exploit", "drained", "breach", "stolen").any { lower.contains(it) } ->
                CryptoEventCategory.HACK to (listOf("hack", "exploit", "drained", "breach", "stolen").first { lower.contains(it) })
            listOf("delist", "trading suspension").any { lower.contains(it) } ->
                CryptoEventCategory.DELIST to "delist"
            listOf("depeg", "lost peg", "loses peg").any { lower.contains(it) } ->
                CryptoEventCategory.DEPEG to "depeg"
            listOf("halts withdrawal", "halt withdrawals", "withdrawal halt", "frozen", "paused withdrawals", "bankrupt", "insolven").any { lower.contains(it) } ->
                CryptoEventCategory.HALT to "halt"
            listOf("sec charges", "sues", "lawsuit", "enforcement", "ban").any { lower.contains(it) } ->
                CryptoEventCategory.REGULATORY to "regulatory"
            listOf("token unlock", "vesting cliff", "unlock").any { lower.contains(it) } ->
                CryptoEventCategory.UNLOCK to "unlock"
            listOf("outage", "chain halt", "network halt", "shutdown").any { lower.contains(it) } ->
                CryptoEventCategory.OUTAGE to "outage"
            else -> null
        }
        if (match == null) return CryptoEventClassification.none(headline)
        val (cat, span) = match
        return CryptoEventClassification(
            category = cat,
            mult = cat.mult,
            block = cat.block,
            confidence = 0.72,
            span = span,
            headline = headline.take(220),
            viaLlm = false
        )
    }

    // ── Indicators & Multi-Horizon TSMOM Signal ──────────────────────────────

    fun emaSeries(values: List<Double>, span: Int): DoubleArray {
        val out = DoubleArray(values.size)
        if (values.isEmpty()) return out
        val alpha = 2.0 / (span + 1.0)
        out[0] = values[0]
        for (i in 1 until values.size) {
            out[i] = alpha * values[i] + (1.0 - alpha) * out[i - 1]
        }
        return out
    }

    fun atrSeries(bars: BarSeries, period: Int = 14): DoubleArray {
        val n = bars.c.size
        val out = DoubleArray(n)
        if (n == 0) return out
        val alpha = 1.0 / period.toDouble()
        for (i in 0 until n) {
            val hl = bars.h[i] - bars.l[i]
            val tr = if (i == 0) {
                hl
            } else {
                val prevC = bars.c[i - 1]
                max(hl, max(abs(bars.h[i] - prevC), abs(bars.l[i] - prevC)))
            }
            out[i] = if (i == 0) tr else alpha * tr + (1.0 - alpha) * out[i - 1]
        }
        return out
    }

    /** Crypto trades 365 days/year, so annualize rolling 30d returns std by sqrt(365). */
    fun annVolSeries(closes: List<Double>, window: Int = 30): DoubleArray {
        val n = closes.size
        val out = DoubleArray(n) { Double.NaN }
        if (n <= window) return out
        val rets = DoubleArray(n) { Double.NaN }
        for (i in 1 until n) {
            if (closes[i - 1] > 0.0) {
                rets[i] = closes[i] / closes[i - 1] - 1.0
            }
        }
        val scale = sqrt(365.0)
        for (i in window until n) {
            var sum = 0.0
            var count = 0
            for (j in (i - window + 1)..i) {
                val r = rets[j]
                if (r.isFinite()) {
                    sum += r
                    count++
                }
            }
            if (count < 2) continue
            val mean = sum / count
            var sq = 0.0
            for (j in (i - window + 1)..i) {
                val r = rets[j]
                if (r.isFinite()) {
                    val d = r - mean
                    sq += d * d
                }
            }
            val std = sqrt(sq / (count - 1).toDouble())
            out[i] = std * scale
        }
        return out
    }

    fun tsmomScoreAt(
        closes: List<Double>,
        annVol: DoubleArray,
        index: Int,
        lookbacks: List<Int> = LOOKBACKS
    ): Double {
        if (index !in closes.indices) return 0.0
        val v = annVol.getOrElse(index) { Double.NaN }
        if (!v.isFinite() || v <= 0.0) return 0.0
        var sum = 0.0
        var count = 0
        for (lb in lookbacks) {
            val prevIdx = index - lb
            if (prevIdx < 0 || closes[prevIdx] <= 0.0) continue
            val ret = closes[index] / closes[prevIdx] - 1.0
            val denom = v * sqrt(lb.toDouble() / 365.0)
            if (denom > 0.0) {
                sum += ret / denom
                count++
            }
        }
        return if (count > 0) sum / count else 0.0
    }

    fun evaluateSignals(
        barsBySymbol: Map<String, BarSeries>,
        takerBps: Double = DEFAULT_FEE_BPS,
        slipBps: Double = DEFAULT_SLIP_BPS,
        minHistoryBars: Int = 95
    ): List<CryptoTsmomSignal> {
        val roundTripBps = 2.0 * (takerBps + slipBps)
        val out = ArrayList<CryptoTsmomSignal>()
        for ((sym, bars) in barsBySymbol) {
            val n = bars.c.size
            if (n < minHistoryBars) continue
            val vol = annVolSeries(bars.c, 30)
            val lastIdx = n - 1
            val v = vol[lastIdx]
            if (!v.isFinite() || v <= 0.0) continue
            val px = bars.c[lastIdx]
            val e20 = emaSeries(bars.c, 20)[lastIdx]
            val e50 = emaSeries(bars.c, 50)[lastIdx]
            val e200 = emaSeries(bars.c, min(200, n))[lastIdx]
            val atrVal = atrSeries(bars, 14)[lastIdx]
            val score = tsmomScoreAt(bars.c, vol, lastIdx)
            val above50 = px > e50
            val stack = px > e20 && e20 > e50 && e50 > e200
            val expectedEdge = round1(v * sqrt(HOLDING_HORIZON_DAYS.toDouble() / 365.0) * 5000.0)
            val costBps = round1(roundTripBps)
            val costViable = expectedEdge >= MIN_EDGE_COST_RATIO * costBps
            val qualified = score >= ENTRY_SCORE && above50 && stack && costViable
            out.add(
                CryptoTsmomSignal(
                    symbol = sym,
                    price = px,
                    score = round2(score),
                    annVol = v,
                    atr = atrVal,
                    aboveEma50 = above50,
                    trendStack = stack,
                    expectedEdgeBps = expectedEdge,
                    costBps = costBps,
                    costViable = costViable,
                    qualified = qualified
                )
            )
        }
        return out.sortedByDescending { it.score }
    }

    // ── BTC Macro Regime (`_btc_regime`) ─────────────────────────────────────

    fun btcRegime(barsBySymbol: Map<String, BarSeries>): CryptoBtcRegime {
        val btc = barsBySymbol["BTC/USD"] ?: return CryptoBtcRegime()
        val n = btc.c.size
        if (n < 60) return CryptoBtcRegime()
        val lastIdx = n - 1
        val px = btc.c[lastIdx]
        val e200 = emaSeries(btc.c, min(200, n))[lastIdx]
        val vol = annVolSeries(btc.c, 30)
        val validVols = vol.filter { it.isFinite() }
        val latestVol = vol[lastIdx]
        val pct = if (validVols.size > 30 && latestVol.isFinite()) {
            validVols.count { it <= latestVol }.toDouble() / validVols.size.toDouble()
        } else {
            0.5
        }
        val (mult, label) = when {
            pct > 0.95 && px < e200 -> 0.25 to "CRISIS"
            px < e200 -> 0.55 to "RISK_OFF"
            pct > 0.85 -> 0.75 to "ELEVATED"
            else -> 1.0 to "RISK_ON"
        }
        return CryptoBtcRegime(
            label = label,
            mult = mult,
            btcPrice = px,
            btcEma200 = e200,
            volPercentile = round2(pct)
        )
    }

    // ── Portfolio Heat, Half-Kelly & Correlation Guards ──────────────────────

    fun computeHeat(
        positions: List<TradePosition>,
        equity: Double,
        livePrices: Map<String, Double> = emptyMap(),
        capPct: Double = MAX_HEAT_PCT
    ): CryptoPortfolioHeat {
        var total = 0.0
        val rows = positions.map { p ->
            val px = livePrices[p.symbol]?.takeIf { it > 0.0 } ?: p.last.takeIf { it > 0.0 } ?: p.entry
            val risk = max(0.0, (px - p.stop) * p.qty)
            total += risk
            CryptoHeatRow(symbol = p.symbol, riskUsd = round2(risk))
        }
        val pct = if (equity > 0.0) (total / equity) * 100.0 else 0.0
        val headroom = if (equity > 0.0) max(0.0, equity * (capPct - pct) / 100.0) else 0.0
        return CryptoPortfolioHeat(
            totalRiskUsd = round2(total),
            pctOfEquity = round2(pct),
            capPct = capPct,
            headroomUsd = round2(headroom),
            breach = pct > capPct,
            rows = rows
        )
    }

    fun fitToHeat(notional: Double, price: Double, stop: Double, headroomUsd: Double): Double {
        if (price <= 0.0 || headroomUsd <= 0.0) return 0.0
        val perUnit = max(price - stop, 1e-9)
        return round2(max(0.0, min(notional, headroomUsd * price / perUnit)))
    }

    fun kellyCap(winRate: Double, payoff: Double, n: Int, frac: Double = 0.5): Double {
        if (n < 30 || payoff <= 0.0) return 0.0
        val f = winRate - (1.0 - winRate) / payoff
        return f.coerceIn(0.0, 1.0) * frac
    }

    fun isCorrelationOk(
        candidateSymbol: String,
        heldSymbols: Set<String>,
        barsBySymbol: Map<String, BarSeries>,
        maxCorr: Double = MAX_CORR
    ): Boolean {
        val b1 = barsBySymbol[candidateSymbol] ?: return true
        val r1 = tailReturns(b1.c, 60)
        if (r1.size < 30) return true
        for (held in heldSymbols) {
            if (held == candidateSymbol) continue
            val b2 = barsBySymbol[held] ?: continue
            val r2 = tailReturns(b2.c, 60)
            val m = min(r1.size, r2.size)
            if (m < 30) continue
            val corr = pearsonCorrelation(
                r1.subList(r1.size - m, r1.size),
                r2.subList(r2.size - m, r2.size)
            )
            if (corr.isFinite() && corr > maxCorr) return false
        }
        return true
    }

    // ── Stablecoin Peg Check (`peg_check`) & Edge Decay (`decay_check`) ──────

    fun parsePegCheckJson(
        rawJson: String,
        threshold: Double = PEG_BREACH_THRESHOLD,
        nowMs: Long = System.currentTimeMillis()
    ): StablecoinPegStatus {
        val quotes = CryptoFeedsEngine.parseKrakenTickersJson(
            rawJson = rawJson,
            requestedSymbols = listOf("USDT/USD", "USDC/USD")
        )
        val rows = listOf("USDT/USD", "USDC/USD").map { sym ->
            val q = quotes[sym]
            if (q != null && q.last > 0.0) {
                val devBps = round1((q.last - 1.0) * 10_000.0)
                val breach = abs(q.last - 1.0) > threshold
                StablecoinPegRow(symbol = sym, price = q.last, devBps = devBps, breach = breach)
            } else {
                StablecoinPegRow(symbol = sym, error = "unavailable")
            }
        }
        return StablecoinPegStatus(
            rows = rows,
            anyBreach = rows.any { it.breach },
            checkedAtMs = nowMs
        )
    }

    fun evaluateDecay(
        logEntries: List<TradeLogEntry>,
        window: Int = 20,
        minTrades: Int = 20,
        tStatCut: Double = -1.0
    ): CryptoDecayReport {
        val closed = logEntries
            .filter { it.pnl != null && it.price > 0.0 && it.qty > 0.0 }
            .sortedBy { it.timestampMs }
        val rets = closed.mapNotNull { entry ->
            val pnl = entry.pnl ?: return@mapNotNull null
            val notional = entry.price * entry.qty
            if (notional > 0.0) (pnl / notional) else null
        }
        if (rets.size < minTrades) {
            return CryptoDecayReport(
                sampleCount = rets.size,
                totalClosed = rets.size,
                verdict = DecayVerdict.INSUFFICIENT,
                note = "need $minTrades closed trades (${rets.size}/$minTrades)"
            )
        }
        val recent = rets.takeLast(window)
        val n = recent.size
        val mean = recent.average()
        val sd = sqrt(recent.sumOf { (it - mean).pow(2) } / max(n - 1, 1).toDouble())
        val tStat = if (sd > 0.0) mean / (sd / sqrt(n.toDouble())) else 0.0
        val prior = rets.dropLast(window)
        val priorMeanPct = if (prior.isNotEmpty()) round3(prior.average() * 100.0) else null
        val verdict = when {
            tStat <= tStatCut -> DecayVerdict.DECAYED
            tStat < 0.0 -> DecayVerdict.WEAK
            else -> DecayVerdict.OK
        }
        val note = when (verdict) {
            DecayVerdict.DECAYED -> "edge decayed (t=${"%.2f".format(Locale.US, tStat)}) — halt entries"
            DecayVerdict.WEAK -> "recent edge soft (t=${"%.2f".format(Locale.US, tStat)})"
            DecayVerdict.OK -> "edge intact over last $n closed trades"
            DecayVerdict.INSUFFICIENT -> "need $minTrades closed trades"
        }
        return CryptoDecayReport(
            sampleCount = n,
            totalClosed = rets.size,
            meanPct = round3(mean * 100.0),
            sdPct = round3(sd * 100.0),
            tStat = round2(tStat),
            priorMeanPct = priorMeanPct,
            verdict = verdict,
            note = note
        )
    }

    fun evaluateFeedFreshness(
        lastBarEpochSecBySymbol: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeHours: Double = FEED_MAX_AGE_HOURS
    ): List<CryptoFeedFreshness> {
        val nowSec = nowMs / 1000.0
        return lastBarEpochSecBySymbol.entries
            .map { (sym, tsSec) ->
                val ageH = if (tsSec > 0L) round1(max(0.0, (nowSec - tsSec) / 3600.0)) else null
                CryptoFeedFreshness(
                    symbol = sym,
                    ageHours = ageH,
                    stale = ageH == null || ageH > maxAgeHours
                )
            }
            .sortedBy { it.ageHours ?: Double.MAX_VALUE }
    }

    // ── 9-Variant Backtest + Deflated Sharpe Ratio (DSR) + PBO ───────────────

    private data class SimPos(
        val qty: Double,
        val entry: Double,
        val stop: Double
    )

    /**
     * Runs the exact 9-variant `(max_pos ∈ {3, 5, 7}) × (target_vol ∈ {0.25, 0.35, 0.50})`
     * grid backtest from `cryptotrader.py 2.0`, returning Net Sharpe (365), CAGR,
     * Max Drawdown, Deflated Sharpe Ratio (DSR > 0.95), and 10-block CSCV PBO (< 0.50).
     */
    fun runGridBacktest(
        barsBySymbol: Map<String, BarSeries>,
        feeBps: Double = DEFAULT_FEE_BPS,
        slipBps: Double = DEFAULT_SLIP_BPS,
        nowMs: Long = System.currentTimeMillis()
    ): CryptoBacktestSummary? {
        val eligible = barsBySymbol.filterValues { it.c.size >= 140 }
        if (eligible.isEmpty()) return null

        val syms = eligible.keys.sorted()
        val n = eligible.values.minOf { it.c.size }
        val start = min(200, max(95, n / 4))
        val sessionCount = n - start
        if (sessionCount < 35) return null

        // Align all bars to their trailing `n` daily candles
        val aligned = syms.associateWith { sym ->
            val b = eligible.getValue(sym)
            val off = b.c.size - n
            BarSeries(
                o = b.o.subList(off, b.o.size),
                h = b.h.subList(off, b.h.size),
                l = b.l.subList(off, b.l.size),
                c = b.c.subList(off, b.c.size),
                v = b.v.subList(off, b.v.size)
            )
        }

        // Precompute per-symbol arrays once for fast 9-variant evaluation
        val volMap = HashMap<String, DoubleArray>(syms.size)
        val atrMap = HashMap<String, DoubleArray>(syms.size)
        val trendMap = HashMap<String, BooleanArray>(syms.size)
        val stackMap = HashMap<String, BooleanArray>(syms.size)
        val scoreMap = HashMap<String, DoubleArray>(syms.size)

        for (s in syms) {
            val b = aligned.getValue(s)
            val vol = annVolSeries(b.c, 30)
            val e20 = emaSeries(b.c, 20)
            val e50 = emaSeries(b.c, 50)
            val e200 = emaSeries(b.c, min(200, n))
            val atr = atrSeries(b, 14)
            val trend = BooleanArray(n) { i -> b.c[i] > e50[i] }
            val stack = BooleanArray(n) { i -> b.c[i] > e20[i] && e20[i] > e50[i] && e50[i] > e200[i] }
            val scores = DoubleArray(n) { i -> tsmomScoreAt(b.c, vol, i) }
            volMap[s] = vol
            atrMap[s] = atr
            trendMap[s] = trend
            stackMap[s] = stack
            scoreMap[s] = scores
        }

        // BTC regime array
        val regime = DoubleArray(n) { 1.0 }
        aligned["BTC/USD"]?.let { btc ->
            val e200 = emaSeries(btc.c, min(200, n))
            val vol = annVolSeries(btc.c, 30)
            for (t in start until n) {
                val above = btc.c[t] > e200[t]
                val wStart = max(0, t - 179)
                var valid = 0
                var le = 0
                val curV = vol[t]
                if (curV.isFinite()) {
                    for (k in wStart..t) {
                        val vk = vol[k]
                        if (vk.isFinite()) {
                            valid++
                            if (vk <= curV) le++
                        }
                    }
                }
                val vp = if (valid > 30) le.toDouble() / valid.toDouble() else 0.5
                regime[t] = when {
                    vp > 0.95 && !above -> 0.25
                    !above -> 0.55
                    else -> 1.0
                }
            }
        }

        val grid = listOf(3, 5, 7).flatMap { mp ->
            listOf(0.25, 0.35, 0.50).map { tv -> mp to tv }
        }
        val rt = 2.0 * (feeBps + slipBps) / 10_000.0
        val costBps = 2.0 * (feeBps + slipBps)

        val runs = ArrayList<DoubleArray>(grid.size)
        val sharpes = DoubleArray(grid.size)

        grid.forEachIndexed { idx, (maxPos, targetVol) ->
            var cash = 10_000.0
            val pos = LinkedHashMap<String, SimPos>()
            val curve = DoubleArray(sessionCount)

            for (t in start until n) {
                val iter = pos.entries.iterator()
                while (iter.hasNext()) {
                    val (s, p) = iter.next()
                    val c = aligned.getValue(s).c[t]
                    if (c <= p.stop || !trendMap.getValue(s)[t] || scoreMap.getValue(s)[t] <= EXIT_SCORE) {
                        cash += p.qty * c * (1.0 - rt / 2.0)
                        iter.remove()
                    }
                }

                val openSlots = maxPos - pos.size
                if (openSlots > 0) {
                    val ranked = ArrayList<Triple<Double, String, Double>>()
                    for (s in syms) {
                        if (pos.containsKey(s) || !stackMap.getValue(s)[t]) continue
                        val sc = scoreMap.getValue(s)[t]
                        if (!sc.isFinite() || sc < ENTRY_SCORE) continue
                        val v = volMap.getValue(s)[t]
                        if (!v.isFinite() || v <= 0.0) continue
                        val edge = v * sqrt(HOLDING_HORIZON_DAYS.toDouble() / 365.0) * 5000.0
                        if (edge < MIN_EDGE_COST_RATIO * costBps) continue
                        ranked.add(Triple(sc, s, v))
                    }
                    ranked.sortByDescending { it.first }

                    val eq = cash + pos.entries.sumOf { (s, p) -> p.qty * aligned.getValue(s).c[t] }
                    for ((_, s, v) in ranked.take(openSlots)) {
                        val notional = min(
                            eq * (targetVol / sqrt(maxPos.toDouble())) / max(v, 0.10) * regime[t],
                            min(eq * 0.30, cash * 0.95)
                        )
                        if (notional < 10.0) continue
                        val b = aligned.getValue(s)
                        val px = if (t + 1 < n) b.o[t + 1] else b.c[t]
                        if (!px.isFinite() || px <= 0.0) continue
                        val qty = notional / px
                        cash -= qty * px * (1.0 + rt / 2.0)
                        val a = atrMap.getValue(s)[t]
                        pos[s] = SimPos(qty = qty, entry = px, stop = px - HARD_STOP_ATR * a)
                    }
                }

                val mtm = pos.entries.sumOf { (s, p) -> p.qty * aligned.getValue(s).c[t] }
                curve[t - start] = cash + mtm
            }

            val rets = DoubleArray(sessionCount)
            for (i in 1 until sessionCount) {
                rets[i] = if (curve[i - 1] > 0.0) (curve[i] / curve[i - 1] - 1.0) else 0.0
            }
            runs.add(rets)
            sharpes[idx] = sharpe365(rets)
        }

        var bestIdx = 0
        for (i in 1 until sharpes.size) {
            if (sharpes[i] > sharpes[bestIdx]) bestIdx = i
        }
        val bestRets = runs[bestIdx]
        val bestSharpe = sharpes[bestIdx]
        val dsr = deflatedSharpe(bestSharpe, bestRets.size, grid.size)
        val overfitPbo = computePbo(runs, blocks = min(10, max(4, (bestRets.size / 6) * 2)))

        var equityMult = 1.0
        var peak = 1.0
        var maxDd = 0.0
        var bestDay = 0.0
        var worstDay = 0.0
        for (r in bestRets) {
            equityMult *= (1.0 + r)
            if (equityMult > peak) peak = equityMult
            val dd = (equityMult / peak) - 1.0
            if (dd < maxDd) maxDd = dd
            if (r > bestDay) bestDay = r
            if (r < worstDay) worstDay = r
        }

        val years = bestRets.size / 365.0
        val cagr = if (years > 0.0 && equityMult > 0.0) equityMult.pow(1.0 / years) - 1.0 else 0.0
        val passed = dsr > 0.95 && overfitPbo < 0.50 && bestSharpe > 1.0

        return CryptoBacktestSummary(
            bestMaxPos = grid[bestIdx].first,
            bestTargetVol = grid[bestIdx].second,
            sessions = bestRets.size,
            years = round2(years),
            cagrPct = round2(cagr * 100.0),
            netSharpe = round2(bestSharpe),
            maxDrawdownPct = round2(maxDd * 100.0),
            bestDayPct = round2(bestDay * 100.0),
            worstDayPct = round2(worstDay * 100.0),
            variantsTried = grid.size,
            deflatedSharpe = round3(dsr),
            pbo = round3(overfitPbo),
            passed = passed,
            pairCount = syms.size,
            feeBps = feeBps,
            slipBps = slipBps,
            ranAtMs = nowMs
        )
    }

    fun sharpe365(rets: DoubleArray): Double {
        if (rets.size < 30) return 0.0
        val mean = rets.average()
        val variance = rets.sumOf { (it - mean).pow(2) } / (rets.size - 1).toDouble()
        val sd = sqrt(variance)
        return if (sd > 0.0) (mean / sd) * sqrt(365.0) else 0.0
    }

    fun expectedMaxSharpe(nTrials: Int): Double {
        if (nTrials < 2) return 0.0
        val a = sqrt(2.0 * ln(nTrials.toDouble()))
        val b = (ln(ln(nTrials.toDouble())) + ln(4.0 * PI)) / (2.0 * a)
        return a - b
    }

    fun deflatedSharpe(sr: Double, nObs: Int, nTrials: Int): Double {
        if (nObs < 30) return 0.0
        val variance = (1.0 + 0.25 * sr * sr) / (nObs - 1).toDouble()
        val sd = sqrt(variance)
        if (sd <= 0.0) return 0.0
        val z = (sr - expectedMaxSharpe(nTrials) * sd) / sd
        return 0.5 * (1.0 + erf(z / sqrt(2.0)))
    }

    /**
     * Exact Combinatorially Symmetric Cross-Validation (CSCV) Probability of Backtest Overfitting
     * matching `pbo(mat, blocks=10)` in `cryptotrader.py 2.0`.
     */
    fun computePbo(runs: List<DoubleArray>, blocks: Int = 10): Double {
        val nVar = runs.size
        val tCount = runs.firstOrNull()?.size ?: 0
        val evenBlocks = if (blocks % 2 == 0) blocks else blocks - 1
        if (nVar < 2 || evenBlocks < 4 || tCount < evenBlocks * 4) return 1.0

        val edges = IntArray(evenBlocks + 1) { i -> ((i.toLong() * tCount) / evenBlocks).toInt() }
        val half = evenBlocks / 2
        val combos = combinations(evenBlocks, half)
        var below = 0
        var total = 0

        for (combo in combos) {
            val inside = combo.toHashSet()
            val sa = DoubleArray(nVar)
            val sb = DoubleArray(nVar)
            var bestInSample = 0

            for (v in 0 until nVar) {
                val series = runs[v]
                var sumA = 0.0
                var cntA = 0
                var sumB = 0.0
                var cntB = 0
                for (b in 0 until evenBlocks) {
                    val start = edges[b]
                    val end = edges[b + 1]
                    if (b in inside) {
                        for (t in start until end) {
                            sumA += series[t]
                            cntA++
                        }
                    } else {
                        for (t in start until end) {
                            sumB += series[t]
                            cntB++
                        }
                    }
                }
                if (cntA < 2 || cntB < 2) continue
                val meanA = sumA / cntA
                val meanB = sumB / cntB
                var sqA = 0.0
                var sqB = 0.0
                for (b in 0 until evenBlocks) {
                    val start = edges[b]
                    val end = edges[b + 1]
                    if (b in inside) {
                        for (t in start until end) sqA += (series[t] - meanA).pow(2)
                    } else {
                        for (t in start until end) sqB += (series[t] - meanB).pow(2)
                    }
                }
                sa[v] = meanA / (sqrt(sqA / cntA) + 1e-12)
                sb[v] = meanB / (sqrt(sqB / cntB) + 1e-12)
                if (sa[v] > sa[bestInSample]) bestInSample = v
            }

            val oosScore = sb[bestInSample]
            val rankFrac = sb.count { it < oosScore }.toDouble() / nVar.toDouble()
            if (rankFrac < 0.5) below++
            total++
        }
        return if (total > 0) below.toDouble() / total.toDouble() else 1.0
    }

    // ── Math Helpers ─────────────────────────────────────────────────────────

    private fun combinations(n: Int, k: Int): List<IntArray> {
        val result = ArrayList<IntArray>()
        val cur = IntArray(k)
        fun backtrack(start: Int, depth: Int) {
            if (depth == k) {
                result.add(cur.copyOf())
                return
            }
            for (i in start..(n - k + depth)) {
                cur[depth] = i
                backtrack(i + 1, depth + 1)
            }
        }
        backtrack(0, 0)
        return result
    }

    /** Abramowitz & Stegun 7.1.26 error function approximation (max error < 1.5e-7). */
    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }

    private fun tailReturns(closes: List<Double>, count: Int): List<Double> {
        if (closes.size < 2) return emptyList()
        val start = max(1, closes.size - count)
        val out = ArrayList<Double>(closes.size - start)
        for (i in start until closes.size) {
            if (closes[i - 1] > 0.0) {
                out.add(closes[i] / closes[i - 1] - 1.0)
            }
        }
        return out
    }

    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        val n = min(x.size, y.size)
        if (n < 2) return 0.0
        val mx = x.take(n).average()
        val my = y.take(n).average()
        var num = 0.0
        var dx = 0.0
        var dy = 0.0
        for (i in 0 until n) {
            val a = x[i] - mx
            val b = y[i] - my
            num += a * b
            dx += a * a
            dy += b * b
        }
        val denom = sqrt(dx * dy)
        return if (denom > 0.0) num / denom else 0.0
    }

    private fun toFiniteDouble(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble().takeIf { it.isFinite() } ?: 0.0
        is String -> raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        else -> 0.0
    }

    private fun round1(x: Double): Double = (x * 10.0).roundToInt() / 10.0
    private fun round2(x: Double): Double = (x * 100.0).roundToInt() / 100.0
    private fun round3(x: Double): Double = (x * 1000.0).roundToInt() / 1000.0
}
