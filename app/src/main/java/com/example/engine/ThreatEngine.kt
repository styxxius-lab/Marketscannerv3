package com.example.engine

import com.example.domain.engine.QuantEngine
import com.example.domain.model.BarSeries
import com.example.domain.model.ContagionResult
import com.example.domain.model.NewsHeadline
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatCategory
import com.example.domain.model.ThreatDriver
import com.example.domain.model.ThreatLevel
import com.example.domain.model.ThreatNarrativeResult
import com.example.domain.model.ThreatPositionContext
import com.example.domain.model.ThreatQuantResult
import com.example.domain.model.TradePosition
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Configuration parameters matching threat.js CFG.
 */
data class ThreatConfig(
    val wQuant: Double = 0.60,
    val wNarrative: Double = 0.40,
    val lvlElevated: Double = 40.0,
    val lvlHigh: Double = 65.0,
    val lvlCritical: Double = 85.0,
    val stopBufferPct: Double = 0.5,
    val profitRForTrimBias: Double = 2.0,
    val exhaustionRsi: Double = 25.0,
    val exhaustionGapAtr: Double = 2.0,
    val halfLifeHours: Double = 18.0
)

/**
 * Taxonomy entry classifying corporate/macro risk catalysts by severity, persistence, and thesis impact.
 */
data class ThreatTaxonomyEntry(
    val key: String,
    val severity: Double,
    val persistenceDays: Int,
    val structural: Boolean,
    val label: String
)

/**
 * Individual technical shock diagnostic driver.
 */
data class ThreatDiagnosticDriver(
    val name: String,
    val value: Double,
    val score: Int,
    val text: String
)

/**
 * Quantitative tape shock breakdown and derived technical indicators.
 */
data class QuantDiagnostics(
    val score: Double,
    val drivers: List<ThreatDiagnosticDriver>,
    val gapPct: Double,
    val chgPct: Double,
    val volZ: Double,
    val rangeRatio: Double,
    val clv: Double,
    val dd20Pct: Double,
    val atr: Double,
    val rsi: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val swingLow20: Double,
    val relWeakPct: Double,
    val volShift: Double
)

/**
 * Narrative catalyst assessment derived from LLM classification or local rule fallback.
 */
data class ThreatNarrativeDiagnostic(
    val categoryKey: String,
    val categoryLabel: String,
    val materiality: Double,
    val structural: Boolean,
    val companySpecific: Boolean,
    val persistenceDays: Int,
    val confidence: Double,
    val summary: String,
    val severityPrior: Double,
    val degraded: Boolean,
    val isLiveClassified: Boolean
)

/**
 * Peer contagion diagnostic measuring sector-wide sympathy drawdowns.
 */
data class ContagionDiagnostic(
    val sectorWide: Boolean,
    val peerDropAvgPct: Double,
    val peersFalling: Int,
    val peerCount: Int
)

/**
 * Position risk snapshot passed into ThreatEngine evaluation.
 */
data class ThreatPosition(
    val qty: Double,
    val entry: Double,
    val initialStop: Double,
    val stop: Double,
    val upnl: Double = 0.0
)

/**
 * Optional market context (benchmark series, sector peers, live price override) for assessment.
 */
data class ThreatContext(
    val benchBars: BarSeries? = null,
    val peerBars: List<BarSeries> = emptyList(),
    val priceOverride: Double? = null
)

/**
 * Output ThreatCard produced by [ThreatEngine.assess], encapsulating the fused decision,
 * quantitative diagnostics, narrative classification, sector contagion, and protective stop levels.
 */
data class ThreatCard(
    val symbol: String,
    val timestampMs: Long,
    val price: Double,
    val action: ThreatAction,
    val verdict: String,
    val trimFraction: Double,
    val threatScore: Int,
    val level: ThreatLevel,
    val confidence: Double,
    val reasoning: List<String>,
    val priceActionScore: Int,
    val narrativeScore: Int,
    val quant: QuantDiagnostics,
    val narrative: ThreatNarrativeDiagnostic,
    val contagion: ContagionDiagnostic?,
    val suggestedStop: Double?,
    val invalidation: String
)

/**
 * Kotlin ThreatAssessment engine ported from threat.js for Android.
 * Evaluates quantitative price/volume shocks, classifies narrative catalysts via a 14-item
 * taxonomy, measures sector peer contagion, and issues HOLD / TRIM / SELL / EXIT_NOW decisions.
 */
class ThreatEngine(
    val config: ThreatConfig = ThreatConfig(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val narrativeClassifier: NarrativeClassifier? = null
) {

    fun interface NarrativeClassifier {
        suspend fun classify(symbol: String, headlines: List<NewsHeadline>): ThreatNarrativeDiagnostic?
    }

    /* ─────────────── 2 · THREAT TAXONOMY ─────────────── */
    val taxonomy: Map<String, ThreatTaxonomyEntry> = TAXONOMY

    /* ─────────────── 3 · SMALL INDICATOR SET ─────────────── */
    private fun clamp(x: Double, a: Double, b: Double): Double = max(a, min(b, x))

    private fun band(x: Double, lo: Double, hi: Double): Double =
        if (hi <= lo) 0.0 else clamp((x - lo) / (hi - lo), 0.0, 1.0) * 100.0

    private fun round1(x: Double): Double = if (x.isFinite()) (x * 10.0).roundToInt() / 10.0 else 0.0
    private fun round2(x: Double): Double = if (x.isFinite()) (x * 100.0).roundToInt() / 100.0 else 0.0

    private fun mean(a: List<Double>): Double = if (a.isEmpty()) 0.0 else a.sum() / a.size

    private fun sd(a: List<Double>): Double {
        if (a.isEmpty()) return 0.0
        val m = mean(a)
        var acc = 0.0
        for (x in a) {
            val d = x - m
            acc += d * d
        }
        return sqrt(acc / a.size)
    }

    private fun emaArr(a: List<Double>, n: Int): List<Double> = QuantEngine.ema(a, n)

    private fun atrArr(h: List<Double>, l: List<Double>, c: List<Double>, n: Int = 14): List<Double> =
        QuantEngine.atr(h, l, c, n)

    private fun rsiLast(c: List<Double>, n: Int = 14): Double {
        if (c.size < 2) return 50.0
        return QuantEngine.rsi(c, n).lastOrNull() ?: 50.0
    }

    /* ─────────────── 4 · QUANT THREAT ─────────────── */
    fun quantThreat(
        bars: BarSeries,
        ctx: ThreatContext = ThreatContext()
    ): QuantDiagnostics? {
        val o = bars.o
        val h = bars.h
        val l = bars.l
        val c = bars.c
        val v = bars.v
        val n = c.size
        if (n < 60 || o.size < n || h.size < n || l.size < n || v.size < n) return null

        val lastClose = c[n - 1]
        val pxOverride = ctx.priceOverride
        val px = if (pxOverride != null && pxOverride.isFinite() && pxOverride > 0.0) {
            pxOverride
        } else {
            lastClose
        }
        val prevC = c[n - 2].takeIf { it > 0.0 } ?: return null
        val open = o[n - 1]
        val atrVal = atrArr(h, l, c, 14).lastOrNull()?.takeIf { it > 0.0 } ?: (px * 0.02)

        val closeForRsi = if (px != lastClose) c.dropLast(1) + px else c
        val rsi = rsiLast(closeForRsi, 14)

        val drivers = ArrayList<ThreatDiagnosticDriver>()
        fun add(name: String, value: Double, score: Double, text: String) {
            drivers.add(
                ThreatDiagnosticDriver(
                    name = name,
                    value = value,
                    score = score.roundToInt(),
                    text = text
                )
            )
        }

        // 1 · gap severity
        val gapPct = (open - prevC) / prevC * 100.0
        val gapScore = if (gapPct < 0.0) band(-gapPct, 0.5, 7.0) else 0.0
        if (gapScore > 5.0) {
            add("gap", round2(gapPct), gapScore, "Gapped ${"%.1f".format(Locale.US, gapPct)}%")
        }

        // 2 · intraday damage
        val chgPct = (px - prevC) / prevC * 100.0
        val chgScore = if (chgPct < 0.0) band(-chgPct, 1.0, 8.0) else 0.0
        if (chgScore > 5.0) {
            add("day_change", round2(chgPct), chgScore, "Down ${"%.1f".format(Locale.US, -chgPct)}% on the day")
        }

        // 3 · volume confirmation
        val vWin = v.subList(max(0, n - 21), n - 1)
        val vStd = sd(vWin)
        val vZ = if (vStd > 0.0) (v[n - 1] - mean(vWin)) / vStd else 0.0
        val volScore = band(vZ, 0.5, 4.5)
        if (volScore > 5.0) {
            add("volume", round2(vZ), volScore, "Volume ${"%.1f".format(Locale.US, vZ)}σ above normal")
        }

        // 4 · range expansion
        val barHi = max(h[n - 1], px)
        val barLo = min(l[n - 1], px)
        val range = max(0.0, barHi - barLo)
        val rangeRatio = if (atrVal > 0.0) range / atrVal else 1.0
        val rangeScore = band(rangeRatio, 1.0, 3.0)
        if (rangeScore > 5.0) {
            add("range", round2(rangeRatio), rangeScore, "Range ${"%.1f".format(Locale.US, rangeRatio)}× ATR")
        }

        // 5 · close location — did it close near the low?
        val clv = if (range > 0.0) ((px - barLo) - (barHi - px)) / range else 0.0
        val clvScore = if (clv < 0.0) band(-clv, 0.1, 0.9) else 0.0
        if (clvScore > 20.0) {
            add("close_location", round2(clv), clvScore, "Trading near session low — sellers in control")
        }

        // 6 · drawdown from 20d high
        val hi20 = h.subList(max(0, n - 20), n).maxOrNull() ?: px
        val dd20 = if (hi20 > 0.0) (px - hi20) / hi20 * 100.0 else 0.0
        val ddScore = if (dd20 < 0.0) band(-dd20, 3.0, 15.0) else 0.0
        if (ddScore > 5.0) {
            add("drawdown", round2(dd20), ddScore, "${"%.1f".format(Locale.US, -dd20)}% off the 20-day high")
        }

        // 7 · structure damage
        val e20 = emaArr(closeForRsi, 20).lastOrNull() ?: px
        val e50 = emaArr(closeForRsi, 50).lastOrNull() ?: px
        val e200 = emaArr(closeForRsi, 200).lastOrNull() ?: px
        var struct = 0.0
        val breaks = ArrayList<String>()
        if (px < e20) {
            struct += 22.0
            breaks.add("20 EMA")
        }
        if (px < e50) {
            struct += 22.0
            breaks.add("50 EMA")
        }
        if (n >= 120 && px < e200) {
            struct += 16.0
            breaks.add("200 EMA")
        }
        if (e20 < e50) {
            struct += 14.0
            breaks.add("20<50 EMA")
        }
        val swingLow = l.subList(max(0, n - 21), n - 1).minOrNull() ?: px
        if (px < swingLow) {
            struct += 18.0
            breaks.add("prior swing low")
        }
        struct = clamp(struct, 0.0, 100.0)
        if (struct > 5.0) {
            add("structure", struct, struct, if (breaks.isNotEmpty()) "Broke ${breaks.joinToString(", ")}" else "Structure intact")
        }

        // 8 · relative weakness vs benchmark
        var relScore = 0.0
        var relWeakPct = 0.0
        val benchBars = ctx.benchBars
        if (benchBars != null && benchBars.c.size >= 22) {
            val bc = benchBars.c
            val stockBase = c[n - 21]
            val benchBase = bc[bc.size - 21]
            if (stockBase > 0.0 && benchBase > 0.0) {
                val relRet = (px / stockBase - 1.0) - (bc.last() / benchBase - 1.0)
                relWeakPct = round2(relRet * 100.0)
                if (relRet < 0.0) {
                    relScore = band(-relRet * 100.0, 1.0, 10.0)
                    if (relScore > 5.0) {
                        add(
                            "relative",
                            relWeakPct,
                            relScore,
                            "Underperforming benchmark by ${"%.1f".format(Locale.US, -relWeakPct)}pp over 20d"
                        )
                    }
                }
            }
        }

        // 9 · volatility regime shift
        val recentReturns = ArrayList<Double>(10)
        for (idx in (n - 10) until n) {
            val prev = closeForRsi[idx - 1]
            if (prev > 0.0) recentReturns.add(closeForRsi[idx] / prev - 1.0)
        }
        val priorReturns = ArrayList<Double>(50)
        val priorStart = max(1, n - 60)
        val priorEnd = max(priorStart + 1, n - 10)
        for (idx in priorStart until priorEnd) {
            val prev = closeForRsi[idx - 1]
            if (prev > 0.0) priorReturns.add(closeForRsi[idx] / prev - 1.0)
        }
        val volNow = sd(recentReturns)
        val volPrior = sd(priorReturns)
        val volShift = if (volPrior > 0.0) volNow / volPrior else 1.0
        val volShiftScore = band(volShift, 1.2, 3.0)
        if (volShiftScore > 5.0) {
            add("vol_regime", round2(volShift), volShiftScore, "Realized vol ${"%.1f".format(Locale.US, volShift)}× baseline")
        }

        val weighted = (
            gapScore * 0.20 +
                chgScore * 0.16 +
                volScore * 0.13 +
                rangeScore * 0.10 +
                clvScore * 0.09 +
                ddScore * 0.08 +
                struct * 0.13 +
                relScore * 0.06 +
                volShiftScore * 0.05
            )
        val composite = if (chgPct <= -4.0 && vZ >= 2.0) {
            clamp(weighted * 1.15, 0.0, 100.0)
        } else {
            clamp(weighted, 0.0, 100.0)
        }

        return QuantDiagnostics(
            score = round1(composite),
            drivers = drivers.sortedByDescending { it.score },
            gapPct = round2(gapPct),
            chgPct = round2(chgPct),
            volZ = round2(vZ),
            rangeRatio = round2(rangeRatio),
            clv = round2(clv),
            dd20Pct = round2(dd20),
            atr = round2(atrVal),
            rsi = round1(rsi),
            ema20 = round2(e20),
            ema50 = round2(e50),
            ema200 = round2(e200),
            swingLow20 = round2(swingLow),
            relWeakPct = relWeakPct,
            volShift = round2(volShift)
        )
    }

    /* ─────────────── 5 · NARRATIVE ASSESSMENT ─────────────── */
    fun narrativeDegraded(
        reason: String = "No verified threat catalyst in recent headlines"
    ): ThreatNarrativeDiagnostic {
        val none = taxonomy.getValue("none")
        return ThreatNarrativeDiagnostic(
            categoryKey = none.key,
            categoryLabel = none.label,
            materiality = 0.0,
            structural = false,
            companySpecific = false,
            persistenceDays = 0,
            confidence = 0.35,
            summary = reason,
            severityPrior = 0.0,
            degraded = true,
            isLiveClassified = false
        )
    }

    suspend fun narrativeThreat(
        symbol: String,
        headlines: List<NewsHeadline>
    ): ThreatNarrativeDiagnostic {
        val symHeadlines = headlines.filter { it.sym.equals(symbol, ignoreCase = true) }
        if (symHeadlines.isEmpty()) {
            return narrativeDegraded("No recent headlines for $symbol — tape-only assessment")
        }
        if (narrativeClassifier != null) {
            val live = narrativeClassifier.classify(symbol, symHeadlines)
            if (live != null) {
                return live.copy(isLiveClassified = true)
            }
        }
        return classifyHeadlinesLocally(symbol, symHeadlines)
    }

    fun classifyHeadlinesLocally(
        symbol: String,
        headlines: List<NewsHeadline>
    ): ThreatNarrativeDiagnostic {
        val symHeadlines = headlines.filter { it.sym.equals(symbol, ignoreCase = true) }
        if (symHeadlines.isEmpty()) {
            return narrativeDegraded("No recent headlines for $symbol — tape-only assessment")
        }
        val combined = symHeadlines.joinToString(" | ") { "${it.title} ${it.summary}" }.lowercase(Locale.US)

        val key = when {
            Regex("auditor|restate|restatement|accounting|sec delay|10-k|10-q|fraud|subpoena").containsMatchIn(combined) ->
                "accounting_fraud"
            Regex("guidance|cuts outlook|lowers forecast|warns on|revenue outlook").containsMatchIn(combined) ->
                "guidance_cut"
            Regex("regulator|regulatory|antitrust|eu probe|doj|ftc|fine|penalty|ban|tariff").containsMatchIn(combined) ->
                "regulatory_action"
            Regex("miss|misses|disappoint|below est|weak deliver|deliveries miss|shortfall").containsMatchIn(combined) ->
                "earnings_miss"
            Regex("lost customer|major customer|contract cancel|termination").containsMatchIn(combined) ->
                "customer_loss"
            Regex("market share|competitor|pricing pressure|rival").containsMatchIn(combined) ->
                "competition"
            Regex("offering|dilution|convertible|secondary|share sale|capital raise").containsMatchIn(combined) ->
                "dilution"
            Regex("supply chain|shortage|halt|factory shut|recall|production delay").containsMatchIn(combined) ->
                "supply_chain"
            Regex("lawsuit|litigation|class action|sued|settlement").containsMatchIn(combined) ->
                "litigation"
            Regex("cfo|ceo steps down|resign|executive departure|ouster").containsMatchIn(combined) ->
                "executive_departure"
            Regex("fed|rates|cpi|macro|yields|recession").containsMatchIn(combined) ->
                "macro"
            Regex("rotation|sector selloff|sympathy").containsMatchIn(combined) ->
                "sector_rotation"
            Regex("downgrade|price target cut|underperform|sell rating").containsMatchIn(combined) ->
                "analyst_downgrade"
            else -> "none"
        }

        val entry = taxonomy[key] ?: taxonomy.getValue("none")
        if (entry.key == "none") {
            return ThreatNarrativeDiagnostic(
                categoryKey = entry.key,
                categoryLabel = entry.label,
                materiality = 0.08,
                structural = false,
                companySpecific = false,
                persistenceDays = 0,
                confidence = 0.72,
                summary = "Headlines show no adverse threat trigger: ${symHeadlines.first().title}",
                severityPrior = 0.0,
                degraded = false,
                isLiveClassified = false
            )
        }

        val materiality = clamp(entry.severity * 0.88, 0.25, 0.98)
        val companySpecific = entry.key != "macro" && entry.key != "sector_rotation"
        return ThreatNarrativeDiagnostic(
            categoryKey = entry.key,
            categoryLabel = entry.label,
            materiality = round2(materiality),
            structural = entry.structural,
            companySpecific = companySpecific,
            persistenceDays = entry.persistenceDays,
            confidence = 0.80,
            summary = "${entry.label}: ${symHeadlines.first().title}",
            severityPrior = entry.severity,
            degraded = false,
            isLiveClassified = false
        )
    }

    fun headlineDecayFactor(headlines: List<NewsHeadline>, nowMs: Long = clockMs()): Double {
        if (headlines.isEmpty()) return 0.75
        var maxDecay = 0.25
        for (h in headlines) {
            val ageHours = try {
                if (h.publishedIso.isNotBlank()) {
                    ((nowMs - Instant.parse(h.publishedIso).toEpochMilli()) / 3_600_000.0).coerceAtLeast(0.0)
                } else 6.0
            } catch (_: Exception) {
                6.0
            }
            val d = 0.5.pow(ageHours / config.halfLifeHours)
            if (d > maxDecay) maxDecay = d
        }
        return clamp(maxDecay, 0.25, 1.0)
    }

    fun narrativeScore(
        narrative: ThreatNarrativeDiagnostic,
        headlines: List<NewsHeadline>,
        nowMs: Long = clockMs()
    ): Double {
        if (narrative.degraded || narrative.categoryKey == "none") {
            return clamp(narrative.materiality * 100.0 * 0.35, 0.0, 20.0)
        }
        val decay = headlineDecayFactor(headlines, nowMs)
        val raw = (narrative.materiality * 0.65 + narrative.severityPrior * 0.35) * 100.0 * decay
        return clamp(raw, 0.0, 100.0)
    }

    /* ─────────────── 6 · PEER CONTAGION ─────────────── */
    fun contagion(peerBars: List<BarSeries>): ContagionDiagnostic? {
        if (peerBars.isEmpty()) return null
        val drops = ArrayList<Double>(peerBars.size)
        for (bars in peerBars) {
            val c = bars.c
            if (c.size >= 2 && c[c.size - 2] > 0.0) {
                drops.add((c[c.size - 1] / c[c.size - 2] - 1.0) * 100.0)
            }
        }
        if (drops.isEmpty()) return null
        val avgDrop = mean(drops)
        val peersFalling = drops.count { it <= -1.0 }
        val sectorWide = (peersFalling.toDouble() / drops.size) >= 0.60 || avgDrop <= -1.2
        return ContagionDiagnostic(
            sectorWide = sectorWide,
            peerDropAvgPct = round2(avgDrop),
            peersFalling = peersFalling,
            peerCount = drops.size
        )
    }

    /* ─────────────── 7 · ASSESS ENTRYPOINTS ─────────────── */
    suspend fun assess(
        symbol: String,
        bars: BarSeries,
        headlines: List<NewsHeadline> = emptyList(),
        position: ThreatPosition? = null,
        ctx: ThreatContext = ThreatContext(),
        nowMs: Long = clockMs()
    ): ThreatCard? {
        val symHeadlines = headlines.filter { it.sym.equals(symbol, ignoreCase = true) }
        val narrative = narrativeThreat(symbol, symHeadlines)
        return assessWithNarrative(
            symbol = symbol,
            bars = bars,
            headlines = symHeadlines,
            narrative = narrative,
            position = position,
            ctx = ctx,
            nowMs = nowMs
        )
    }

    fun assessWithNarrative(
        symbol: String,
        bars: BarSeries,
        headlines: List<NewsHeadline>,
        narrative: ThreatNarrativeDiagnostic,
        position: ThreatPosition? = null,
        ctx: ThreatContext = ThreatContext(),
        nowMs: Long = clockMs()
    ): ThreatCard? {
        val q = quantThreat(bars, ctx) ?: return null
        val px = ctx.priceOverride?.takeIf { it.isFinite() && it > 0.0 } ?: bars.c.last()
        val contagionRes = contagion(ctx.peerBars)
        val nScore = narrativeScore(narrative, headlines, nowMs)

        val effectiveQuantWeight = if (narrative.degraded) 0.85 else config.wQuant
        val effectiveNarrWeight = if (narrative.degraded) 0.15 else config.wNarrative
        var fusedScore = q.score * effectiveQuantWeight + nScore * effectiveNarrWeight

        if (contagionRes?.sectorWide == true && !narrative.structural && !narrative.companySpecific) {
            fusedScore *= 0.88
        }
        val finalScore = clamp(fusedScore, 0.0, 100.0).roundToInt()

        val level = when {
            finalScore >= config.lvlCritical -> ThreatLevel.CRITICAL
            finalScore >= config.lvlHigh -> ThreatLevel.HIGH
            finalScore >= config.lvlElevated -> ThreatLevel.ELEVATED
            else -> ThreatLevel.LOW
        }

        val reasoning = ArrayList<String>()
        val decision = decide(
            finalScore = finalScore.toDouble(),
            quant = q,
            narrative = narrative,
            position = position,
            price = px,
            contagion = contagionRes,
            reasoning = reasoning
        )

        val suggestedStop = suggestStop(px, q, level, position)
        val invalidation = buildInvalidation(px, q, narrative)
        val baseConf = if (narrative.degraded) 0.58 else narrative.confidence
        val fusedConf = round2(clamp(baseConf * 0.55 + (min(q.drivers.size, 4) / 4.0) * 0.45, 0.35, 0.96))

        return ThreatCard(
            symbol = symbol.uppercase(Locale.US),
            timestampMs = nowMs,
            price = round2(px),
            action = decision.action,
            verdict = decision.verdict,
            trimFraction = decision.trimFraction,
            threatScore = finalScore,
            level = level,
            confidence = fusedConf,
            reasoning = reasoning,
            priceActionScore = q.score.roundToInt(),
            narrativeScore = nScore.roundToInt(),
            quant = q,
            narrative = narrative,
            contagion = contagionRes,
            suggestedStop = suggestedStop,
            invalidation = invalidation
        )
    }

    /* ─────────────── 8 · DECISION ENGINE & STOP ADVISOR ─────────────── */
    private data class DecisionResult(
        val action: ThreatAction,
        val verdict: String,
        val trimFraction: Double
    )

    private fun decide(
        finalScore: Double,
        quant: QuantDiagnostics,
        narrative: ThreatNarrativeDiagnostic,
        position: ThreatPosition?,
        price: Double,
        contagion: ContagionDiagnostic?,
        reasoning: MutableList<String>
    ): DecisionResult {
        if (position != null && position.stop > 0.0) {
            val stopTrigger = position.stop * (1.0 + config.stopBufferPct / 100.0)
            if (price <= stopTrigger) {
                val distPct = ((price - position.stop) / position.stop) * 100.0
                reasoning.add(
                    "Price (${"$%.2f".format(Locale.US, price)}) is ${"%.2f".format(Locale.US, distPct)}% vs stop (${"$%.2f".format(Locale.US, position.stop)}) — inside ${config.stopBufferPct}% stop buffer"
                )
                return DecisionResult(
                    action = ThreatAction.EXIT_NOW,
                    verdict = "Mechanical stop breached or within ${config.stopBufferPct}% — exit immediately.",
                    trimFraction = 1.0
                )
            }
        }

        val initialRiskPerShare = if (position != null) {
            abs(position.entry - position.initialStop).takeIf { it > 0.0001 } ?: quant.atr
        } else 0.0
        val profitR = if (position != null && initialRiskPerShare > 0.0) {
            (price - position.entry) / initialRiskPerShare
        } else 0.0

        val gapAtrMultiple = if (quant.atr > 0.0 && quant.gapPct < 0.0) {
            abs((quant.gapPct / 100.0) * price) / quant.atr
        } else 0.0
        val exhausted = quant.rsi < config.exhaustionRsi && gapAtrMultiple >= config.exhaustionGapAtr

        if (narrative.categoryKey != "none") {
            val structuralTag = if (narrative.structural) "structural thesis damage" else "transient catalyst"
            reasoning.add(
                "${narrative.categoryLabel} ($structuralTag, ~${narrative.persistenceDays}d persistence)"
            )
        } else if (narrative.degraded) {
            reasoning.add("Tape-only evaluation (no adverse corporate catalyst classified)")
        }

        if (contagion?.sectorWide == true) {
            reasoning.add(
                "Sector-wide contagion: ${contagion.peersFalling}/${contagion.peerCount} peers falling (avg ${"%.1f".format(Locale.US, contagion.peerDropAvgPct)}%)"
            )
        }

        if (position != null) {
            reasoning.add("Open position P&L at ${"%.2f".format(Locale.US, profitR)}R vs initial risk")
        }

        val isFraud = narrative.categoryKey == "accounting_fraud"

        if (finalScore >= config.lvlCritical) {
            if (exhausted && !isFraud) {
                reasoning.add(
                    "Exhaustion guard active: RSI ${quant.rsi} (<${config.exhaustionRsi}) after ${"%.1f".format(Locale.US, gapAtrMultiple)}× ATR gap — avoid panic selling entire lot into capitulation"
                )
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "Critical shock near capitulation lows — trim 50% and tighten stop.",
                    trimFraction = 0.5
                )
            }
            reasoning.add("Critical threat fusion (${finalScore.roundToInt()}/100) — capital preservation priority")
            return DecisionResult(
                action = ThreatAction.EXIT_NOW,
                verdict = "Critical shock (${narrative.categoryLabel}) — exit full exposure now.",
                trimFraction = 1.0
            )
        }

        if (finalScore >= config.lvlHigh) {
            if (exhausted && !isFraud) {
                reasoning.add(
                    "Oversold exhaustion: RSI ${quant.rsi} & ${"%.1f".format(Locale.US, gapAtrMultiple)}× ATR gap — trim rather than full capitulation exit"
                )
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "High threat but oversold capitulation — trim 50% and trail stop.",
                    trimFraction = 0.5
                )
            }
            if (narrative.structural && narrative.materiality >= 0.55) {
                reasoning.add("Structural catalyst confirms technical breakdown — full exit warranted")
                return DecisionResult(
                    action = ThreatAction.SELL,
                    verdict = "High threat with structural damage — sell position.",
                    trimFraction = 1.0
                )
            }
            if (profitR >= config.profitRForTrimBias) {
                reasoning.add("Cushion > ${config.profitRForTrimBias}R allows locking in half while giving trend room")
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "High threat on >2R winner — trim 50% to protect gains.",
                    trimFraction = 0.5
                )
            }
            if (contagion?.sectorWide == true && !narrative.structural) {
                reasoning.add("Sympathy selloff across sector peers without company-specific thesis break")
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "Sector sympathy shock — trim 50% and monitor peer stabilization.",
                    trimFraction = 0.5
                )
            }
            return DecisionResult(
                action = ThreatAction.SELL,
                verdict = "High quantitative & narrative threat — exit position.",
                trimFraction = 1.0
            )
        }

        if (finalScore >= config.lvlElevated) {
            if (narrative.structural && narrative.materiality >= 0.60) {
                reasoning.add("Early structural catalyst emerging — reduce risk by trimming half")
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "Elevated risk with structural warning — trim 50% and raise stop.",
                    trimFraction = 0.5
                )
            }
            if (profitR >= config.profitRForTrimBias) {
                reasoning.add("Up > ${config.profitRForTrimBias}R — harvest partial profit into elevated volatility")
                return DecisionResult(
                    action = ThreatAction.TRIM,
                    verdict = "Elevated volatility on >2R profit — trim 50% and trail tight stop.",
                    trimFraction = 0.5
                )
            }
            reasoning.add("Elevated tape stress — maintain position while tightening protective stop")
            return DecisionResult(
                action = ThreatAction.HOLD,
                verdict = "Elevated tape warning — hold with tightened stop.",
                trimFraction = 0.0
            )
        }

        reasoning.add("Price action and headlines within normal volatility bounds")
        return DecisionResult(
            action = ThreatAction.HOLD,
            verdict = "Low threat — thesis intact, hold position.",
            trimFraction = 0.0
        )
    }

    private fun suggestStop(
        price: Double,
        quant: QuantDiagnostics,
        level: ThreatLevel,
        position: ThreatPosition?
    ): Double? {
        if (price <= 0.0 || quant.atr <= 0.0) return null
        val atrMult = when (level) {
            ThreatLevel.CRITICAL -> 0.5
            ThreatLevel.HIGH -> 0.85
            ThreatLevel.ELEVATED -> 1.25
            ThreatLevel.LOW -> 1.8
        }
        val volatilityStop = price - quant.atr * atrMult
        val swingSupport = quant.swingLow20 - quant.atr * 0.25
        var candidate = max(volatilityStop, min(swingSupport, price * 0.99))
        if (position != null && position.stop > 0.0) {
            candidate = max(position.stop, candidate)
        }
        candidate = min(candidate, price * 0.995)
        return if (candidate > 0.0) round2(candidate) else null
    }

    private fun buildInvalidation(
        price: Double,
        quant: QuantDiagnostics,
        narrative: ThreatNarrativeDiagnostic
    ): String {
        val reclaimLevel = round2(max(quant.ema20, price * 1.02))
        val supportFloor = round2(quant.swingLow20)
        return when {
            narrative.structural && narrative.categoryKey != "none" ->
                "Thesis invalidated until closing reclaim of 20 EMA (${"$%.2f".format(Locale.US, reclaimLevel)}) + management clarification"
            quant.dd20Pct <= -5.0 ->
                "Threat eases if price holds ${"$%.2f".format(Locale.US, supportFloor)} swing low and closes above ${"$%.2f".format(Locale.US, reclaimLevel)}"
            else ->
                "Holds constructive above ${"$%.2f".format(Locale.US, supportFloor)}; bull control confirmed > ${"$%.2f".format(Locale.US, reclaimLevel)}"
        }
    }

    companion object {
        val TAXONOMY: Map<String, ThreatTaxonomyEntry> = ThreatCategory.entries.associate { cat ->
            cat.key to ThreatTaxonomyEntry(
                key = cat.key,
                severity = cat.severity,
                persistenceDays = cat.persistenceDays,
                structural = cat.structural,
                label = cat.label
            )
        }

        fun posFromTrade(pos: TradePosition): ThreatPosition = ThreatPosition(
            qty = pos.qty,
            entry = pos.entry,
            initialStop = pos.stop,
            stop = pos.stop,
            upnl = pos.upnl
        )

        fun fromDomainPosition(pos: ThreatPositionContext?): ThreatPosition? = pos?.let {
            ThreatPosition(
                qty = it.qty,
                entry = it.entry,
                initialStop = it.initialStop,
                stop = it.stop,
                upnl = it.upnl
            )
        }

        fun fromDomainNarrative(
            n: ThreatNarrativeResult,
            isLiveClassified: Boolean = false
        ): ThreatNarrativeDiagnostic = ThreatNarrativeDiagnostic(
            categoryKey = n.category.key,
            categoryLabel = n.category.label,
            materiality = n.materiality,
            structural = n.structural,
            companySpecific = n.companySpecific,
            persistenceDays = n.persistenceDays,
            confidence = n.confidence,
            summary = n.summary,
            severityPrior = n.severityPrior,
            degraded = n.degraded,
            isLiveClassified = isLiveClassified
        )

        fun toDomainQuant(q: QuantDiagnostics): ThreatQuantResult = ThreatQuantResult(
            score = q.score,
            drivers = q.drivers.map { ThreatDriver(it.name, it.value, it.score, it.text) },
            gapPct = q.gapPct,
            chgPct = q.chgPct,
            volZ = q.volZ,
            rangeRatio = q.rangeRatio,
            clv = q.clv,
            dd20Pct = q.dd20Pct,
            atr = q.atr,
            rsi = q.rsi,
            ema20 = q.ema20,
            swingLow20 = q.swingLow20,
            relWeakPct = q.relWeakPct,
            volShift = q.volShift
        )

        fun toDomainNarrative(n: ThreatNarrativeDiagnostic): ThreatNarrativeResult = ThreatNarrativeResult(
            category = ThreatCategory.fromKey(n.categoryKey),
            materiality = n.materiality,
            structural = n.structural,
            companySpecific = n.companySpecific,
            persistenceDays = n.persistenceDays,
            confidence = n.confidence,
            summary = n.summary,
            severityPrior = n.severityPrior,
            degraded = n.degraded
        )

        fun toDomainContagion(c: ContagionDiagnostic?): ContagionResult? = c?.let {
            ContagionResult(
                sectorWide = it.sectorWide,
                peerDropAvgPct = it.peerDropAvgPct,
                peersFalling = it.peersFalling,
                peerCount = it.peerCount
            )
        }

        fun toDomainAssessment(card: ThreatCard): ThreatAssessment = ThreatAssessment(
            symbol = card.symbol,
            timestampMs = card.timestampMs,
            price = card.price,
            action = card.action,
            verdict = card.verdict,
            trimFraction = card.trimFraction,
            threatScore = card.threatScore,
            level = card.level,
            confidence = card.confidence,
            reasoning = card.reasoning,
            priceActionScore = card.priceActionScore,
            narrativeScore = card.narrativeScore,
            drivers = card.quant.drivers.map { ThreatDriver(it.name, it.value, it.score, it.text) },
            narrative = toDomainNarrative(card.narrative),
            contagion = toDomainContagion(card.contagion),
            suggestedStop = card.suggestedStop,
            invalidation = card.invalidation,
            isLiveClassified = card.narrative.isLiveClassified
        )
    }
}
