package com.example.domain.engine

import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalRow
import com.example.domain.model.ThresholdAlertEvent
import com.example.domain.model.ThresholdAlertKind
import com.example.domain.model.ThresholdAlertSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ThresholdAlertMonitorState(
    val lastRegime: RegimeLabel? = null,
    val lastPanicGuardActive: Boolean = false,
    val lastAiScoresBySymbol: Map<String, Int> = emptyMap(),
    val alertedVetoKeys: Set<String> = emptySet(),
    val lastExtremeAlertBySymbol: Map<String, Int> = emptyMap()
)

data class ThresholdAlertEvalResult(
    val events: List<ThresholdAlertEvent>,
    val nextState: ThresholdAlertMonitorState
)

/**
 * Pure domain alert evaluator for user-configured Market Regime thresholds and
 * AI News Verdict score-change thresholds.
 */
object ThresholdAlertEngine {

    const val MIN_DELTA_THRESHOLD = 10
    const val MAX_DELTA_THRESHOLD = 80
    const val MIN_EXTREME_THRESHOLD = 20
    const val MAX_EXTREME_THRESHOLD = 95

    fun computeAiNewsScore(row: SignalRow): Int {
        val raw = row.newsDirection.coerceIn(-1.0, 1.0) * row.newsMateriality.coerceIn(0.0, 1.0)
        return (raw * 100.0).roundToInt().coerceIn(-100, 100)
    }

    fun evaluate(
        settings: ThresholdAlertSettings,
        regime: MarketRegime,
        rows: List<SignalRow>,
        watchlist: List<String>,
        advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
        monitorState: ThresholdAlertMonitorState = ThresholdAlertMonitorState(),
        nowMs: Long = System.currentTimeMillis()
    ): ThresholdAlertEvalResult {
        val events = ArrayList<ThresholdAlertEvent>()

        // 1. Market Regime Transition Threshold Alerts
        val prevRegime = monitorState.lastRegime
        if (settings.regimeAlertsEnabled &&
            settings.watchedRegimes.contains(regime.label) &&
            prevRegime != regime.label
        ) {
            val fromPart = prevRegime?.let { "from ${it.name} " }.orEmpty()
            events.add(
                ThresholdAlertEvent(
                    id = "REGIME_${regime.label.name}_$nowMs",
                    kind = ThresholdAlertKind.REGIME_SHIFT,
                    title = "Market Regime Alert: ${regime.label.name}",
                    detail = "Regime shifted ${fromPart}to ${regime.label.name} · breadth ${(regime.breadth * 100).roundToInt()}% · long ${"%.2f".format(Locale.US, regime.longMult)}×",
                    regimeLabel = regime.label,
                    timestampMs = nowMs
                )
            )
        }

        // 2. Daniel & Moskowitz Panic Guard Alert
        if (settings.regimeAlertsEnabled &&
            settings.alertOnPanicGuard &&
            regime.panicGuardActive &&
            !monitorState.lastPanicGuardActive
        ) {
            events.add(
                ThresholdAlertEvent(
                    id = "PANIC_GUARD_$nowMs",
                    kind = ThresholdAlertKind.PANIC_GUARD,
                    title = "Panic Guard Triggered (50% Exposure Cut)",
                    detail = "Index drawdown −${"%.1f".format(Locale.US, regime.panicDrawdownPct)}% with elevated volatility — momentum crash protection active.",
                    regimeLabel = regime.label,
                    timestampMs = nowMs
                )
            )
        }

        // 3. AI News Verdict Score Change & Extreme Threshold Alerts
        val watchSet = watchlist.map { it.trim().uppercase(Locale.US) }.toSet()
        val scopeWatchlist = settings.aiNewsWatchlistOnly && watchSet.isNotEmpty()
        val deltaThresh = settings.aiNewsScoreDeltaThreshold.coerceIn(MIN_DELTA_THRESHOLD, MAX_DELTA_THRESHOLD)
        val extremeThresh = settings.aiNewsMinScoreAbsThreshold.coerceIn(MIN_EXTREME_THRESHOLD, MAX_EXTREME_THRESHOLD)

        val nextScores = monitorState.lastAiScoresBySymbol.toMutableMap()
        val nextExtremeAlerted = monitorState.lastExtremeAlertBySymbol.toMutableMap()

        for (row in rows) {
            val sym = row.symbol.uppercase(Locale.US)
            val currentScore = computeAiNewsScore(row)
            val prevScore = monitorState.lastAiScoresBySymbol[sym]
            nextScores[sym] = currentScore

            if (!settings.aiNewsAlertsEnabled) continue
            if (scopeWatchlist && !watchSet.contains(sym)) continue

            val hasScoredNews = row.newsItems.isNotEmpty() || row.newsMateriality > 0.05 || abs(row.newsDirection) > 0.05
            if (prevScore != null) {
                val delta = currentScore - prevScore
                if (abs(delta) >= deltaThresh) {
                    val dirWord = if (delta > 0) "surged" else "dropped"
                    val signDelta = if (delta > 0) "+$delta" else "$delta"
                    val signScore = if (currentScore > 0) "+$currentScore" else "$currentScore"
                    val headlineSuffix = row.keyCatalyst.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    events.add(
                        ThresholdAlertEvent(
                            id = "AI_DELTA_${sym}_$nowMs",
                            kind = ThresholdAlertKind.AI_SCORE_DELTA,
                            title = "$sym AI News Score $dirWord ($signDelta pts)",
                            detail = "$sym AI verdict moved $prevScore → $signScore (Δ $signDelta ≥ ±$deltaThresh)$headlineSuffix",
                            symbol = sym,
                            scoreValue = currentScore,
                            scoreDelta = delta,
                            timestampMs = nowMs
                        )
                    )
                    nextExtremeAlerted[sym] = currentScore
                    continue
                }
            }

            if (hasScoredNews && abs(currentScore) >= extremeThresh) {
                val lastAlertedScore = monitorState.lastExtremeAlertBySymbol[sym]
                val crossedFreshly = lastAlertedScore == null ||
                    (lastAlertedScore * currentScore <= 0) ||
                    abs(currentScore - lastAlertedScore) >= 15
                if (crossedFreshly) {
                    val tone = if (currentScore >= 0) "Bullish" else "Bearish"
                    val signScore = if (currentScore > 0) "+$currentScore" else "$currentScore"
                    val headlineSuffix = row.keyCatalyst.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    events.add(
                        ThresholdAlertEvent(
                            id = "AI_EXTREME_${sym}_$nowMs",
                            kind = ThresholdAlertKind.AI_SCORE_EXTREME,
                            title = "$sym $tone AI Score Threshold ($signScore)",
                            detail = "$sym crossed ±$extremeThresh AI verdict threshold at $signScore$headlineSuffix",
                            symbol = sym,
                            scoreValue = currentScore,
                            timestampMs = nowMs
                        )
                    )
                    nextExtremeAlerted[sym] = currentScore
                }
            }
        }

        // 4. Advisor LLM Veto Threshold Alerts
        val nextVetoKeys = monitorState.alertedVetoKeys.toMutableSet()
        if (settings.aiNewsAlertsEnabled && settings.alertOnAdvisorVeto) {
            advisorVerdicts.forEach { (rawSym, verdict) ->
                if (!verdict.isVeto) return@forEach
                val sym = rawSym.uppercase(Locale.US)
                if (scopeWatchlist && !watchSet.contains(sym)) return@forEach
                val key = "$sym:${verdict.checkedAtMs}:${verdict.category}"
                if (nextVetoKeys.add(key)) {
                    val confPct = (verdict.weight * 100.0).roundToInt()
                    val cat = verdict.category.ifBlank { "adverse catalyst" }
                    val why = verdict.why.ifBlank { verdict.headline.ifBlank { "Material downside event flagged" } }
                    events.add(
                        ThresholdAlertEvent(
                            id = "ADVISOR_VETO_${sym}_$nowMs",
                            kind = ThresholdAlertKind.ADVISOR_VETO,
                            title = "AI Advisor VETO: $sym ($confPct%)",
                            detail = "$cat · $why",
                            symbol = sym,
                            scoreValue = -confPct,
                            timestampMs = nowMs
                        )
                    )
                }
            }
        }

        val nextState = ThresholdAlertMonitorState(
            lastRegime = regime.label,
            lastPanicGuardActive = regime.panicGuardActive,
            lastAiScoresBySymbol = nextScores,
            alertedVetoKeys = nextVetoKeys.toList().takeLast(60).toSet(),
            lastExtremeAlertBySymbol = nextExtremeAlerted
        )
        return ThresholdAlertEvalResult(events = events, nextState = nextState)
    }

    fun buildTestAlert(
        settings: ThresholdAlertSettings,
        regime: MarketRegime,
        nowMs: Long = System.currentTimeMillis()
    ): ThresholdAlertEvent {
        return if (settings.regimeAlertsEnabled) {
            val targetLabel = settings.watchedRegimes.firstOrNull() ?: regime.label
            ThresholdAlertEvent(
                id = "TEST_REGIME_$nowMs",
                kind = ThresholdAlertKind.REGIME_SHIFT,
                title = "Test Regime Alert: ${targetLabel.name}",
                detail = "Configured to alert on ${settings.watchedRegimes.joinToString(", ") { it.name }.ifBlank { "none" }} · AI Δ ≥ ±${settings.aiNewsScoreDeltaThreshold} pts",
                regimeLabel = targetLabel,
                timestampMs = nowMs
            )
        } else {
            ThresholdAlertEvent(
                id = "TEST_AI_$nowMs",
                kind = ThresholdAlertKind.AI_SCORE_DELTA,
                title = "Test AI News Score Alert (±${settings.aiNewsScoreDeltaThreshold} pts)",
                detail = "Alerts fire on AI score swing ≥ ±${settings.aiNewsScoreDeltaThreshold} or |Score| ≥ ${settings.aiNewsMinScoreAbsThreshold}",
                scoreDelta = settings.aiNewsScoreDeltaThreshold,
                timestampMs = nowMs
            )
        }
    }
}
