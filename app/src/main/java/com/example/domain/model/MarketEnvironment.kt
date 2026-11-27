package com.example.domain.model

import androidx.compose.ui.graphics.Color

/**
 * High-level market environment classifications synthesized from tape trend,
 * universe breadth, and volatility telemetry.
 */
enum class MarketEnvironment(
    val code: String,
    val title: String,
    val shortLabel: String,
    val summary: String,
    val defaultPosture: String
) {
    BULL(
        code = "BULL",
        title = "Bull Market Expansion",
        shortLabel = "BULL",
        summary = "Broad participation and positive index momentum with suppressed volatility. Favorable risk/reward for long equity exposure.",
        defaultPosture = "Risk-On / Aggressive Long"
    ),
    BEAR(
        code = "BEAR",
        title = "Bear Market Downtrend",
        shortLabel = "BEAR",
        summary = "Negative index structure with deteriorating universe breadth and elevated drawdown risk. Capital preservation recommended.",
        defaultPosture = "Defensive / Selective Shorts"
    ),
    VOLATILE(
        code = "VOLATILE",
        title = "High Volatility Regime",
        shortLabel = "VOLATILE",
        summary = "Heightened intraday variance, rapid sector rotations, or surging VIX. Mean reversion active; reduce position sizing.",
        defaultPosture = "Tactical Sizing / Wide Stops"
    ),
    NEUTRAL(
        code = "NEUTRAL",
        title = "Range-Bound Consolidation",
        shortLabel = "NEUTRAL",
        summary = "Balanced market breadth and oscillating index momentum without clear multi-week directional bias.",
        defaultPosture = "Selective / Range Trading"
    ),
    CRISIS(
        code = "CRISIS",
        title = "Crisis / Capitulation Shock",
        shortLabel = "CRISIS",
        summary = "Severe market dislocation or liquidity panic. Cascading stops and extreme volatility; prioritize maximum capital defense.",
        defaultPosture = "Capital Defense / Cash"
    )
}

/**
 * Individual quantitative factor contributing to the environment classification.
 */
data class MarketEnvironmentFactor(
    val id: String,
    val name: String,
    val valueDisplay: String,
    val score: Int, // 0 to 100
    val verdict: String,
    val isPositive: Boolean
)

/**
 * Full output of the [com.example.domain.engine.MarketEnvironmentClassifier],
 * providing actionable classification, confidence, sub-metrics, and tactical rules.
 */
data class MarketEnvironmentClassification(
    val environment: MarketEnvironment,
    val confidencePct: Int,
    val compositeBarometer: Int, // 0 (Severe Crisis) -> 50 (Neutral) -> 100 (Strong Bull)
    val trendScore: Double, // -1.0 (Strong Bear) to +1.0 (Strong Bull)
    val breadthPct: Double, // % of universe above 50d SMA
    val volatilityPercentile: Double, // 0.0 to 1.0
    val vixLevel: Double?,
    val vixChangePct: Double?,
    val vixSpikeActive: Boolean,
    val buySignalCount: Int,
    val sellSignalCount: Int,
    val holdSignalCount: Int,
    val leadingSector: MarketSector?,
    val leadingSectorGainPct: Double?,
    val tacticalPosture: String,
    val recommendedLongMult: Double,
    val recommendedShortMult: Double,
    val recommendedCashAllocationPct: Int,
    val stopStrategyText: String,
    val factors: List<MarketEnvironmentFactor>,
    val headlineVerdict: String,
    val panicGuardActive: Boolean = false,
    val panicDrawdownPct: Double = 0.0
)
