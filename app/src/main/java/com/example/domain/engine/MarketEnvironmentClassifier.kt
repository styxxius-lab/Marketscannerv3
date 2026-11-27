package com.example.domain.engine

import com.example.domain.model.MarketEnvironment
import com.example.domain.model.MarketEnvironmentClassification
import com.example.domain.model.MarketEnvironmentFactor
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSector
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.VixSnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Synthesizes market regime telemetry, universe signal rows, and VIX volatility readings
 * to classify the current market environment into Bull, Bear, Volatile, Neutral, or Crisis regimes.
 */
object MarketEnvironmentClassifier {

    fun classify(
        regime: MarketRegime,
        rows: List<SignalRow> = emptyList(),
        vixSnapshot: VixSnapshot? = null
    ): MarketEnvironmentClassification {
        val trend = regime.trendScore.coerceIn(-1.0, 1.0)
        val breadth = regime.breadth.coerceIn(0.0, 1.0)
        val volPct = regime.volPct.coerceIn(0.0, 1.0)

        val buys = rows.count { it.action == SignalAction.BUY }
        val sells = rows.count { it.action == SignalAction.SELL }
        val holds = rows.count { it.action == SignalAction.HOLD }
        val totalSignals = rows.size.coerceAtLeast(1)
        val buyRatio = buys.toDouble() / totalSignals.toDouble()
        val sellRatio = sells.toDouble() / totalSignals.toDouble()

        val vixLevel = vixSnapshot?.current
        val vixChg = vixSnapshot?.changeFromClosePct
        val vixSpike = vixSnapshot?.intradaySpikePct?.let { it >= 10.0 } ?: false ||
            (vixChg != null && vixChg >= 12.0)

        // Find leading sector
        val sectorGroups = rows.groupBy { it.sector }
        val leadingSectorEntry = sectorGroups.maxByOrNull { (_, sectorRows) ->
            if (sectorRows.isEmpty()) 0.0 else sectorRows.map { it.rs20 }.average()
        }
        val leadingSector = leadingSectorEntry?.key
        val leadingSectorGain = leadingSectorEntry?.value?.let { sectorRows ->
            if (sectorRows.isEmpty()) 0.0 else sectorRows.map { it.rs20 }.average()
        }

        // 1. Classification Decision Matrix
        val environment: MarketEnvironment = when {
            // Crisis: Extreme volatility, panic capitulation, or breakdown
            regime.label == RegimeLabel.CRISIS ||
                (vixLevel != null && vixLevel >= 34.0) ||
                (volPct >= 0.90 && trend <= -0.75) -> {
                MarketEnvironment.CRISIS
            }

            // Sudden Acute Volatility Spike or D&M Panic Guard: Sudden shock or momentum crash trap
            vixSpike || regime.panicGuardActive -> {
                MarketEnvironment.VOLATILE
            }

            // Bear: Negative trend structure and deteriorating breadth
            trend <= -0.50 && breadth <= 0.42 -> {
                MarketEnvironment.BEAR
            }

            // Bull: Positive trend structure, robust breadth, low/moderate volatility
            trend >= 0.50 && breadth >= 0.52 && (vixLevel == null || vixLevel < 23.0) && volPct < 0.75 -> {
                MarketEnvironment.BULL
            }

            // High Volatility: Elevated VIX or high historical volatility with mixed/choppy trend
            (vixLevel != null && vixLevel >= 22.0) ||
                volPct >= 0.74 ||
                (abs(trend) < 0.55 && volPct >= 0.65) -> {
                MarketEnvironment.VOLATILE
            }

            // Range-bound / Neutral
            else -> {
                MarketEnvironment.NEUTRAL
            }
        }

        // 2. Composite Barometer (0 = Severe Panic, 50 = Neutral, 100 = Euphoric Expansion)
        val trendNorm = ((trend + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val breadthNorm = breadth.coerceIn(0.0, 1.0)
        val volInverted = (1.0 - volPct).coerceIn(0.0, 1.0)
        val signalNorm = (buyRatio / (buyRatio + sellRatio + 0.05)).coerceIn(0.0, 1.0)

        var rawBarometer = (trendNorm * 35.0 + breadthNorm * 35.0 + volInverted * 20.0 + signalNorm * 10.0)
        if (vixSpike) {
            rawBarometer = max(5.0, rawBarometer - 15.0)
        }
        val compositeBarometer = rawBarometer.roundToInt().coerceIn(0, 100)

        // 3. Classification Confidence (converging indicators yield higher certainty)
        val indicatorAgreement = when (environment) {
            MarketEnvironment.BULL -> (if (trend >= 0.75) 0.35 else 0.20) +
                (if (breadth >= 0.60) 0.35 else 0.20) +
                (if (vixLevel != null && vixLevel < 18.0) 0.30 else 0.20)

            MarketEnvironment.BEAR -> (if (trend <= -0.75) 0.35 else 0.20) +
                (if (breadth <= 0.35) 0.35 else 0.20) +
                (if (volPct >= 0.60) 0.30 else 0.20)

            MarketEnvironment.VOLATILE -> (if (vixSpike) 0.40 else 0.25) +
                (if (volPct >= 0.75) 0.35 else 0.25) +
                (if (vixLevel != null && vixLevel >= 23.0) 0.25 else 0.15)

            MarketEnvironment.CRISIS -> (if (vixLevel != null && vixLevel >= 30.0) 0.45 else 0.30) +
                (if (breadth <= 0.25) 0.35 else 0.25) + 0.20

            MarketEnvironment.NEUTRAL -> (if (abs(trend) < 0.4) 0.35 else 0.20) +
                (if (breadth in 0.40..0.60) 0.35 else 0.20) + 0.20
        }
        val confidencePct = (indicatorAgreement * 100.0).roundToInt().coerceIn(60, 96)

        // 4. Tactical Sizing & Risk Rules
        val (longMult, shortMult, cashAlloc, stopStrategy) = when (environment) {
            MarketEnvironment.BULL -> Tuple4(
                1.20,
                0.60,
                10,
                "Trailing stop (1.5× ATR) · Let winners run"
            )
            MarketEnvironment.BEAR -> Tuple4(
                0.55,
                1.25,
                45,
                "Tight capital stops (0.8× ATR) · Fast profit taking"
            )
            MarketEnvironment.VOLATILE -> Tuple4(
                0.65,
                0.85,
                35,
                "Wide volatility buffer (2.2× ATR) · Halve share sizing"
            )
            MarketEnvironment.NEUTRAL -> Tuple4(
                1.00,
                1.00,
                25,
                "Standard structure stop (1.2× ATR) · Range mean reversion"
            )
            MarketEnvironment.CRISIS -> Tuple4(
                0.30,
                1.30,
                75,
                "Emergency stops on all positions · Defensive sidelines"
            )
        }

        val posture = when (environment) {
            MarketEnvironment.BULL -> "Aggressive Long / Trend Following"
            MarketEnvironment.BEAR -> "Defensive Stance / Selective Shorting"
            MarketEnvironment.VOLATILE -> "Volatility Buffering / Position Sizing Cut 35%"
            MarketEnvironment.NEUTRAL -> "Selective Range Trading / Mean Reversion"
            MarketEnvironment.CRISIS -> "Maximum Capital Defense / High Cash Reserve"
        }

        // 5. Environmental Factors Breakdown
        val factors = buildList {
            // Trend Factor
            val trendText = when {
                trend >= 0.75 -> "+${"%.1f".format(Locale.US, trend)} (Strong Bull)"
                trend >= 0.25 -> "+${"%.1f".format(Locale.US, trend)} (Moderate Up)"
                trend <= -0.75 -> "${"%.1f".format(Locale.US, trend)} (Severe Bear)"
                trend <= -0.25 -> "${"%.1f".format(Locale.US, trend)} (Moderate Down)"
                else -> "${"%.1f".format(Locale.US, trend)} (Flat/Mixed)"
            }
            add(
                MarketEnvironmentFactor(
                    id = "trend",
                    name = "SPY 50/200 EMA Trend",
                    valueDisplay = trendText,
                    score = (trendNorm * 100).roundToInt(),
                    verdict = if (trend >= 0.25) "Bullish Trend" else if (trend <= -0.25) "Bearish Trend" else "Neutral Oscillation",
                    isPositive = trend >= 0.0
                )
            )

            // Breadth Factor
            val bPct = (breadth * 100.0).roundToInt()
            add(
                MarketEnvironmentFactor(
                    id = "breadth",
                    name = "Universe Breadth (>50d)",
                    valueDisplay = "$bPct%",
                    score = bPct,
                    verdict = if (bPct >= 55) "Broad Participation" else if (bPct <= 40) "Deteriorating Breadth" else "Balanced Breadth",
                    isPositive = bPct >= 50
                )
            )

            // VIX / Volatility Factor
            val vixDisplay = if (vixLevel != null) {
                "${"%.1f".format(Locale.US, vixLevel)} (${if (vixChg != null && vixChg >= 0) "+" else ""}${"%.1f".format(Locale.US, vixChg ?: 0.0)}%)"
            } else {
                "${(volPct * 100).roundToInt()}th percentile"
            }
            val volScore = if (vixLevel != null) {
                ((vixLevel / 40.0) * 100.0).roundToInt().coerceIn(10, 100)
            } else {
                (volPct * 100.0).roundToInt()
            }
            add(
                MarketEnvironmentFactor(
                    id = "vix",
                    name = "Volatility Index (VIX)",
                    valueDisplay = vixDisplay,
                    score = volScore,
                    verdict = if (vixSpike) "Intraday Spike Active" else if (volScore >= 65) "Elevated Risk" else "Calm / Low Risk",
                    isPositive = !vixSpike && volScore < 55
                )
            )

            // Signal Ratio Factor
            val signalDisplay = "$buys BUY · $sells SELL · $holds HOLD"
            add(
                MarketEnvironmentFactor(
                    id = "signal_bias",
                    name = "Scanner Signal Ratio",
                    valueDisplay = signalDisplay,
                    score = (buyRatio * 100).roundToInt(),
                    verdict = if (buys > sells * 2) "Net Long Bias" else if (sells > buys * 2) "Net Short Bias" else "Balanced Signals",
                    isPositive = buys >= sells
                )
            )

            // Sector Leadership Factor
            if (leadingSector != null && leadingSectorGain != null) {
                add(
                    MarketEnvironmentFactor(
                        id = "sector_lead",
                        name = "Sector Leadership",
                        valueDisplay = "${leadingSector.label} (${if (leadingSectorGain >= 0) "+" else ""}${"%.1f".format(Locale.US, leadingSectorGain)}%)",
                        score = ((leadingSectorGain + 5.0) / 10.0 * 100.0).roundToInt().coerceIn(0, 100),
                        verdict = if (leadingSectorGain >= 1.5) "Strong Outperformance" else "Modest Leadership",
                        isPositive = leadingSectorGain >= 0.0
                    )
                )
            }

            // Daniel & Moskowitz Panic Guard Factor
            if (regime.panicGuardActive) {
                add(
                    MarketEnvironmentFactor(
                        id = "panic_guard",
                        name = "D&M Panic Guard",
                        valueDisplay = "ACTIVE (-${"%.1f".format(Locale.US, regime.panicDrawdownPct)}% DD)",
                        score = 25,
                        verdict = "Crash Protection Active · Sizing Cut 50%",
                        isPositive = false
                    )
                )
            }
        }

        val headlineVerdict = when (environment) {
            MarketEnvironment.BULL -> "Tape is supportive. High market breadth and positive momentum favor trend breakouts."
            MarketEnvironment.BEAR -> "Tape is defensive. Deteriorating breadth and negative slope dictate smaller size and tight stops."
            MarketEnvironment.VOLATILE -> if (regime.panicGuardActive) {
                "Panic Guard active. Deep index drawdown and high volatility detected — naive momentum rebound suppressed."
            } else {
                "Tape is erratic. Wide swings and elevated VIX trigger mean-reversion rules and size reduction."
            }
            MarketEnvironment.NEUTRAL -> "Tape is balanced. No clear directional catalyst; focus on high-conviction catalyst setups."
            MarketEnvironment.CRISIS -> "Tape is dislocated. Severe selling pressure and extreme volatility warrant defensive sidelines."
        }

        return MarketEnvironmentClassification(
            environment = environment,
            confidencePct = confidencePct,
            compositeBarometer = compositeBarometer,
            trendScore = trend,
            breadthPct = breadth * 100.0,
            volatilityPercentile = volPct,
            vixLevel = vixLevel,
            vixChangePct = vixChg,
            vixSpikeActive = vixSpike,
            buySignalCount = buys,
            sellSignalCount = sells,
            holdSignalCount = holds,
            leadingSector = leadingSector,
            leadingSectorGainPct = leadingSectorGain,
            tacticalPosture = if (regime.panicGuardActive) "Panic Guard · Exposure Cut 50% · Momentum Cooldown" else posture,
            recommendedLongMult = longMult,
            recommendedShortMult = shortMult,
            recommendedCashAllocationPct = cashAlloc,
            stopStrategyText = stopStrategy,
            factors = factors,
            headlineVerdict = headlineVerdict,
            panicGuardActive = regime.panicGuardActive,
            panicDrawdownPct = regime.panicDrawdownPct
        )
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
