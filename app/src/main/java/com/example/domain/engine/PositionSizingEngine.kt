package com.example.domain.engine

import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ScoredNewsItem
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SizingDriverBreakdown(
    val baseRiskShares: Double,
    val regimeMultiplier: Double,
    val newsMultiplier: Double,
    val volatilityMultiplier: Double,
    val quarterKellyRiskPct: Double,
    val halfKellyRiskPct: Double = quarterKellyRiskPct * 2.0,
    val estimatedWinProb: Double,
    val netRewardRiskRatio: Double,
    val roundTripCostDollars: Double,
    val panicMultiplier: Double = 1.0,
    val wholeSharesRetainedPct: Double = 0.0
)

data class TradeProjectionSummary(
    val stopPnlDollars: Double,
    val expectedValueDollars: Double,
    val targetPnlDollars: Double,
    val stopAccountAfter: Double,
    val expectedAccountAfter10Trades: Double,
    val targetAccountAfter: Double,
    val dailyHaltTradesCount: Int
)

data class ReadableMarketBrief(
    val headline: String,
    val rationale: String,
    val direction: Double,
    val materiality: Double,
    val sourceLabel: String
)

data class PositionSizingResult(
    val symbol: String,
    val action: SignalAction,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val stopDistancePct: Double,
    val accountSize: Double,
    val riskPct: Double,
    val dollarRiskBudget: Double,
    val recommendedShares: Double,
    val wholeShares: Int,
    val recommendedNotional: Double,
    val portfolioWeightPct: Double,
    val actualRiskDollars: Double,
    val capApplied: Boolean,
    val verdictLabel: String,
    val verdictNote: String,
    val breakdown: SizingDriverBreakdown,
    val projections: TradeProjectionSummary,
    val readableBriefs: List<ReadableMarketBrief>,
    val bracketEligibleWholeShares: Boolean = false,
    val executionMode: String = "FRACTIONAL (Client Managed)",
    val panicGuardActive: Boolean = false
)

object PositionSizingEngine {

    private fun round4(x: Double): Double = (x * 10000.0).roundToInt() / 10000.0
    private fun round2(x: Double): Double = (x * 100.0).roundToInt() / 100.0

    fun calculate(
        signal: SignalRow,
        regime: MarketRegime,
        accountSize: Double,
        riskPct: Double,
        marketTapeHeadlines: List<NewsHeadline> = emptyList()
    ): PositionSizingResult {
        val safeAccount = accountSize.coerceAtLeast(1.0)
        val safeRiskPct = riskPct.coerceIn(0.1, 25.0)
        val entry = signal.price.coerceAtLeast(0.01)
        val isShort = signal.action == SignalAction.SELL

        val rawStop = signal.stop
        val stopPrice = when {
            isShort && rawStop > entry -> rawStop
            !isShort && rawStop < entry && rawStop > 0.0 -> rawStop
            isShort -> round2(entry * (1.0 + max(signal.atrPct, 1.0) * 1.5 / 100.0))
            else -> round2(entry * (1.0 - max(signal.atrPct, 1.0) * 1.5 / 100.0))
        }
        val rawTarget = signal.target
        val targetPrice = when {
            isShort && rawTarget < entry && rawTarget > 0.0 -> rawTarget
            !isShort && rawTarget > entry -> rawTarget
            isShort -> round2(entry * (1.0 - max(signal.atrPct, 1.0) * 3.0 / 100.0))
            else -> round2(entry * (1.0 + max(signal.atrPct, 1.0) * 3.0 / 100.0))
        }

        val stopDistance = abs(entry - stopPrice).coerceAtLeast(entry * 0.005)
        val targetDistance = abs(targetPrice - entry).coerceAtLeast(stopDistance * 0.5)
        val stopDistancePct = round2((stopDistance / entry) * 100.0)
        val dollarRiskBudget = round2(safeAccount * (safeRiskPct / 100.0))
        val baseRiskShares = dollarRiskBudget / stopDistance

        // 1. Market Regime Multiplier
        val regimeMult = when {
            signal.action == SignalAction.SELL -> regime.shortMult
            else -> regime.longMult
        }.coerceIn(0.4, 1.25)

        // 2. AI News Catalyst Alignment Multiplier
        val directionalSign = if (isShort) -1.0 else 1.0
        val alignedNewsScore = directionalSign * signal.newsDirection * signal.newsMateriality
        val hasNewsVeto = signal.reasons.any { it.startsWith("news veto", ignoreCase = true) }
        val newsMult = when {
            signal.event?.blocked == true -> 0.0
            hasNewsVeto -> 0.35
            alignedNewsScore >= 0.35 -> 1.15
            alignedNewsScore >= 0.15 -> 1.06
            alignedNewsScore <= -0.35 -> 0.45
            alignedNewsScore <= -0.15 -> 0.70
            else -> 1.0
        }

        // 3. Volatility Regime Multiplier (throttle sizing if ATR > 5% or Vol > 85th pct)
        val volMult = when {
            regime.label == RegimeLabel.CRISIS || regime.volPct > 0.90 -> 0.65
            signal.atrPct > 5.5 -> 0.75
            signal.atrPct in 0.8..3.2 && regime.volPct < 0.65 -> 1.05
            else -> 1.0
        }

        // 4. Expectancy, Net R:R & Quarter-Kelly Calibration
        val roundTripBps = signal.cost?.roundTripBps ?: (signal.spreadBps + 4.0)
        val frictionPerShare = entry * (roundTripBps / 10_000.0)
        val grossRR = targetDistance / stopDistance
        val frictionR = frictionPerShare / stopDistance
        val netRR = (grossRR - frictionR).coerceAtLeast(0.1)

        val confComponent = (signal.confidence.coerceIn(0.0, 1.0) - 0.5) * 0.22
        val convComponent = abs(signal.conviction).coerceIn(0.0, 2.0) * 0.04
        val newsProbBoost = (alignedNewsScore * 0.07).coerceIn(-0.10, 0.10)
        val regimeProbBoost = ((regime.breadth - 0.5) * 0.06).coerceIn(-0.05, 0.05)
        val estWinProb = (0.44 + confComponent + convComponent + newsProbBoost + regimeProbBoost)
            .coerceIn(0.28, 0.72)

        val rawKelly = if (netRR > 0.0) {
            (estWinProb * netRR - (1.0 - estWinProb)) / netRR
        } else 0.0
        val halfKellyPct = round2((rawKelly.coerceIn(0.0, 0.25) * 0.50) * 100.0)
        val quarterKellyPct = round2((rawKelly.coerceIn(0.0, 0.25) * 0.25) * 100.0)

        // 5. Panic Guard & Combined Sizing & Account Concentration Cap
        val panicMult = if (regime.panicGuardActive) 0.50 else 1.0
        val adjustedRiskShares = baseRiskShares * regimeMult * newsMult * volMult * panicMult
        val maxConcentrationFrac = if (safeAccount < 50.0) 0.75 else 0.35
        val maxCapShares = (safeAccount * maxConcentrationFrac) / entry
        val costBlocked = signal.cost != null && !signal.cost.viable && signal.action != SignalAction.HOLD

        val rawRecommended = if (signal.event?.blocked == true) {
            0.0
        } else {
            min(adjustedRiskShares, maxCapShares)
        }
        val recommendedShares = round4(rawRecommended.coerceAtLeast(0.0))
        val capApplied = adjustedRiskShares > maxCapShares && recommendedShares > 0.0
        val recommendedNotional = round2(recommendedShares * entry)
        val portfolioWeightPct = round2((recommendedNotional / safeAccount) * 100.0)
        val actualRiskDollars = round2(recommendedShares * stopDistance)

        // Whole Share preference for broker-side bracket protection (AlphaTrader 3.0)
        val wholeShares = recommendedShares.toInt()
        val wholeNotional = wholeShares * entry
        val wholeSharesRetainedPct = if (recommendedNotional > 0.0) {
            round2((wholeNotional / recommendedNotional) * 100.0)
        } else 0.0
        val bracketEligible = wholeShares >= 1 && (wholeNotional >= 0.60 * recommendedNotional)
        val executionMode = if (bracketEligible) "BROKER BRACKET (Whole Shares)" else "FRACTIONAL (Client Managed)"

        val roundTripCostDollars = round2(recommendedShares * frictionPerShare)
        val stopLossDollars = round2(-(actualRiskDollars + roundTripCostDollars))
        val targetProfitDollars = round2((recommendedShares * targetDistance) - roundTripCostDollars)
        val expectedValueDollars = round2(
            estWinProb * targetProfitDollars + (1.0 - estWinProb) * stopLossDollars
        )

        val dailyHaltLimitDollars = safeAccount * PaperBotEngine.DAILY_LOSS_LIMIT
        val haltCount = if (abs(stopLossDollars) > 0.0001) {
            max(1, (dailyHaltLimitDollars / abs(stopLossDollars)).toInt())
        } else {
            99
        }

        val verdictLabel: String
        val verdictNote: String
        when {
            signal.event?.blocked == true -> {
                verdictLabel = "EVENT BLOCKED"
                verdictNote = "Upcoming earnings within 2 sessions — zero size recommended."
            }
            regime.panicGuardActive -> {
                verdictLabel = "PANIC GUARD (50% CUT)"
                verdictNote = "Daniel & Moskowitz momentum crash risk active — exposure halved to prevent bounce trap."
            }
            hasNewsVeto -> {
                verdictLabel = "NEWS VETO"
                verdictNote = "High-impact news conflicts with technical setup — size slashed."
            }
            costBlocked -> {
                verdictLabel = "COST WARNING"
                verdictNote = signal.cost?.reason?.ifBlank { "Spread drag narrows net R:R." }
                    ?: "Spread drag narrows net R:R."
            }
            capApplied -> {
                verdictLabel = "CAPPED ${(maxConcentrationFrac * 100).roundToInt()}%"
                verdictNote = "Position capped at ${(maxConcentrationFrac * 100).roundToInt()}% equity to limit concentration."
            }
            alignedNewsScore >= 0.25 && regime.label == RegimeLabel.RISK_ON -> {
                verdictLabel = "HIGH CONVICTION"
                verdictNote = "Regime & AI news catalyst align — full risk-adjusted sizing."
            }
            else -> {
                verdictLabel = "OPTIMAL SIZE"
                verdictNote = "Sized for ${"%.1f".format(safeRiskPct)}% risk adjusted by regime & volatility."
            }
        }

        val briefs = buildReadableBriefs(signal, marketTapeHeadlines, regime)

        return PositionSizingResult(
            symbol = signal.symbol,
            action = signal.action,
            entryPrice = round2(entry),
            stopPrice = round2(stopPrice),
            targetPrice = round2(targetPrice),
            stopDistancePct = stopDistancePct,
            accountSize = round2(safeAccount),
            riskPct = round2(safeRiskPct),
            dollarRiskBudget = dollarRiskBudget,
            recommendedShares = recommendedShares,
            wholeShares = wholeShares,
            recommendedNotional = recommendedNotional,
            portfolioWeightPct = portfolioWeightPct,
            actualRiskDollars = actualRiskDollars,
            capApplied = capApplied,
            verdictLabel = verdictLabel,
            verdictNote = verdictNote,
            breakdown = SizingDriverBreakdown(
                baseRiskShares = round4(baseRiskShares),
                regimeMultiplier = round2(regimeMult),
                newsMultiplier = round2(newsMult),
                volatilityMultiplier = round2(volMult),
                quarterKellyRiskPct = quarterKellyPct,
                halfKellyRiskPct = halfKellyPct,
                estimatedWinProb = round2(estWinProb),
                netRewardRiskRatio = round2(netRR),
                roundTripCostDollars = roundTripCostDollars,
                panicMultiplier = panicMult,
                wholeSharesRetainedPct = wholeSharesRetainedPct
            ),
            projections = TradeProjectionSummary(
                stopPnlDollars = stopLossDollars,
                expectedValueDollars = expectedValueDollars,
                targetPnlDollars = targetProfitDollars,
                stopAccountAfter = round2((safeAccount + stopLossDollars).coerceAtLeast(0.0)),
                expectedAccountAfter10Trades = round2((safeAccount + expectedValueDollars * 10.0).coerceAtLeast(0.0)),
                targetAccountAfter = round2(safeAccount + targetProfitDollars),
                dailyHaltTradesCount = haltCount
            ),
            readableBriefs = briefs,
            bracketEligibleWholeShares = bracketEligible,
            executionMode = executionMode,
            panicGuardActive = regime.panicGuardActive
        )
    }

    private fun buildReadableBriefs(
        signal: SignalRow,
        marketTapeHeadlines: List<NewsHeadline>,
        regime: MarketRegime
    ): List<ReadableMarketBrief> {
        val out = ArrayList<ReadableMarketBrief>()
        signal.newsItems.forEach { item: ScoredNewsItem ->
            val note = if (item.rationale.isNotBlank() && item.rationale != "demo") {
                item.rationale
            } else {
                val dirWord = if (item.direction >= 0) "Positive" else "Negative"
                "$dirWord catalyst · ${(item.materiality * 100).roundToInt()}% materiality"
            }
            out.add(
                ReadableMarketBrief(
                    headline = item.title,
                    rationale = note,
                    direction = item.direction,
                    materiality = item.materiality,
                    sourceLabel = "AI SCORED"
                )
            )
        }
        val existingTitles = out.map { it.headline }.toSet()
        marketTapeHeadlines
            .filter { it.sym.equals(signal.symbol, ignoreCase = true) && !existingTitles.contains(it.title) }
            .take(2)
            .forEach { h ->
                out.add(
                    ReadableMarketBrief(
                        headline = h.title,
                        rationale = h.summary.ifBlank { "Live market tape · ${h.source.ifBlank { "feed" }}" },
                        direction = signal.newsDirection,
                        materiality = signal.newsMateriality,
                        sourceLabel = "MARKET TAPE"
                    )
                )
            }
        if (out.isEmpty()) {
            val driverSummary = signal.reasons.take(3).joinToString(" · ").ifBlank {
                "6-factor score ${"%.0f".format(signal.score)} · ${regime.note}"
            }
            out.add(
                ReadableMarketBrief(
                    headline = "${signal.symbol} (${signal.sector.label}) · ${signal.action.name} setup",
                    rationale = driverSummary,
                    direction = if (signal.conviction >= 0) 1.0 else -1.0,
                    materiality = signal.confidence,
                    sourceLabel = "QUANT SCAN"
                )
            )
        }
        return out.take(4)
    }
}
