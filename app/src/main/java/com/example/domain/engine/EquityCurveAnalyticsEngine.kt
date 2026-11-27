package com.example.domain.engine

import com.example.domain.model.BotState
import com.example.domain.model.EquityPoint
import com.example.domain.model.TradeLogEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class EquityTimeframe(val label: String, val windowMs: Long?) {
    DAY_1("1D", 24L * 3_600_000L),
    WEEK_1("7D", 7L * 24L * 3_600_000L),
    MONTH_1("30D", 30L * 24L * 3_600_000L),
    ALL("ALL", null)
}

enum class EquityCurveChartMode(val label: String) {
    EQUITY("EQUITY $"),
    RETURN_PCT("RETURN %"),
    DRAWDOWN("DRAWDOWN")
}

data class EquityTradeMarker(
    val timestampMs: Long,
    val symbol: String,
    val type: String, // OPEN | CLOSE | TRIM
    val price: Double,
    val pnl: Double?
)

data class EquityCurveSample(
    val timestampMs: Long,
    val equity: Double,
    val returnPct: Double,
    val highWaterMark: Double,
    val drawdownPct: Double, // <= 0.0 (e.g. -2.4 means 2.4% below peak)
    val tradeMarker: EquityTradeMarker? = null,
    val isLivePoint: Boolean = false
)

data class EquityCurveAnalytics(
    val timeframe: EquityTimeframe,
    val samples: List<EquityCurveSample>,
    val baselineEquity: Double,
    val startEquity: Double,
    val currentEquity: Double,
    val periodReturnDollar: Double,
    val periodReturnPct: Double,
    val peakEquity: Double,
    val troughEquity: Double,
    val currentDrawdownPct: Double, // positive magnitude, e.g. 1.5 means -1.5% from peak
    val maxDrawdownPct: Double,     // positive magnitude
    val closedTradesCount: Int,
    val winRatePct: Double?,
    val profitFactor: Double?,
    val sharpeEstimate: Double?,
    val tradeMarkersCount: Int,
    val usesIntradayAxis: Boolean
)

/**
 * Pure analytics engine transforming [BotState.equityHistory], live mark-to-market equity,
 * and [BotState.log] trade executions into a smooth time-series equity curve with
 * high-water mark envelope, underwater drawdown series, trade markers, and KPIs.
 */
object EquityCurveAnalyticsEngine {

    private const val MAX_RENDER_POINTS = 120

    fun buildAnalytics(
        botState: BotState,
        timeframe: EquityTimeframe,
        nowMs: Long = System.currentTimeMillis()
    ): EquityCurveAnalytics {
        val baseEquity = if (botState.initialEquity > 0.0) botState.initialEquity else 10.0
        val liveEquity = PaperBotEngine.liveEquity(botState).takeIf { it.isFinite() && it > 0.0 } ?: baseEquity

        // 1. Gather raw chronological equity ticks + current live mark-to-market point
        val rawHistory = botState.equityHistory
            .filter { it.equity.isFinite() && it.equity > 0.0 && it.timestampMs > 0L }
            .sortedBy { it.timestampMs }
            .toMutableList()

        // Ensure there is always a starting baseline anchor before the current live point
        if (rawHistory.isEmpty()) {
            val anchorOffset = when (timeframe) {
                EquityTimeframe.DAY_1 -> 6L * 3_600_000L
                EquityTimeframe.WEEK_1 -> 3L * 24L * 3_600_000L
                EquityTimeframe.MONTH_1 -> 7L * 24L * 3_600_000L
                EquityTimeframe.ALL -> 6L * 3_600_000L
            }
            rawHistory.add(EquityPoint(timestampMs = nowMs - anchorOffset, equity = baseEquity))
        }

        val lastRecorded = rawHistory.last()
        val effectiveNow = max(nowMs, lastRecorded.timestampMs)
        if (effectiveNow - lastRecorded.timestampMs >= 1_000L || abs(liveEquity - lastRecorded.equity) > 1e-6) {
            rawHistory.add(EquityPoint(timestampMs = effectiveNow, equity = liveEquity))
        }

        // 2. Apply timeframe window while preserving a boundary anchor just before the window
        val windowMs = timeframe.windowMs
        val cutoffMs = if (windowMs != null) effectiveNow - windowMs else Long.MIN_VALUE
        val windowed = if (windowMs == null) {
            rawHistory
        } else {
            val inWindow = rawHistory.filter { it.timestampMs >= cutoffMs }
            val priorAnchor = rawHistory.lastOrNull { it.timestampMs < cutoffMs }
            buildList {
                if (priorAnchor != null) {
                    add(EquityPoint(timestampMs = cutoffMs, equity = priorAnchor.equity))
                } else if (inWindow.size <= 1) {
                    val anchorTs = min(cutoffMs, (inWindow.firstOrNull()?.timestampMs ?: effectiveNow) - 3_600_000L)
                    add(EquityPoint(timestampMs = anchorTs, equity = baseEquity))
                }
                addAll(inWindow)
            }.ifEmpty {
                listOf(
                    EquityPoint(timestampMs = cutoffMs, equity = baseEquity),
                    EquityPoint(timestampMs = effectiveNow, equity = liveEquity)
                )
            }
        }

        // Downsample deterministically if history grows very large, preserving first, last, peak, and trough
        val condensed = downsamplePreservingExtrema(windowed, MAX_RENDER_POINTS)

        // 3. Extract trade executions within the timeframe window to annotate the curve
        val tradeEvents = botState.log
            .filter { entry ->
                entry.timestampMs >= cutoffMs &&
                    (entry.type.equals("OPEN", ignoreCase = true) ||
                        entry.type.equals("CLOSE", ignoreCase = true) ||
                        entry.type.equals("TRIM", ignoreCase = true))
            }
            .map { entry ->
                EquityTradeMarker(
                    timestampMs = entry.timestampMs,
                    symbol = entry.symbol,
                    type = entry.type.uppercase(),
                    price = entry.price,
                    pnl = entry.pnl
                )
            }
            .sortedBy { it.timestampMs }

        // Map each trade marker to its nearest curve sample index
        val markerByIndex = HashMap<Int, EquityTradeMarker>()
        if (condensed.isNotEmpty()) {
            tradeEvents.forEach { marker ->
                var bestIdx = 0
                var bestDist = Long.MAX_VALUE
                for (i in condensed.indices) {
                    val d = abs(condensed[i].timestampMs - marker.timestampMs)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = i
                    }
                }
                // Prefer CLOSE markers over OPEN if two collide on the exact same tick
                val existing = markerByIndex[bestIdx]
                if (existing == null || marker.type == "CLOSE") {
                    markerByIndex[bestIdx] = marker
                }
            }
        }

        // 4. Compute high-water mark, underwater drawdown, and return % for every point
        val startEquity = condensed.first().equity.takeIf { it > 0.0 } ?: baseEquity
        var runningPeak = startEquity
        var peakEquity = startEquity
        var troughEquity = startEquity
        var maxDrawdownPct = 0.0

        val samples = condensed.mapIndexed { idx, pt ->
            if (pt.equity > runningPeak) runningPeak = pt.equity
            if (pt.equity > peakEquity) peakEquity = pt.equity
            if (pt.equity < troughEquity) troughEquity = pt.equity
            val ddMagnitude = if (runningPeak > 0.0) {
                ((runningPeak - pt.equity) / runningPeak * 100.0).coerceAtLeast(0.0)
            } else 0.0
            if (ddMagnitude > maxDrawdownPct) maxDrawdownPct = ddMagnitude

            val retPct = if (startEquity > 0.0) ((pt.equity - startEquity) / startEquity) * 100.0 else 0.0
            EquityCurveSample(
                timestampMs = pt.timestampMs,
                equity = pt.equity,
                returnPct = retPct,
                highWaterMark = runningPeak,
                drawdownPct = -ddMagnitude,
                tradeMarker = markerByIndex[idx],
                isLivePoint = idx == condensed.lastIndex
            )
        }

        val currentEq = samples.last().equity
        val periodReturnDollar = currentEq - startEquity
        val periodReturnPct = if (startEquity > 0.0) (periodReturnDollar / startEquity) * 100.0 else 0.0
        val currentDd = abs(samples.last().drawdownPct)

        // 5. Compute trade-level KPIs (win rate & profit factor) from closed trades in window
        val closedTrades: List<TradeLogEntry> = botState.log.filter { entry ->
            entry.timestampMs >= cutoffMs &&
                entry.type.equals("CLOSE", ignoreCase = true) &&
                entry.pnl != null
        }
        val winRatePct = if (closedTrades.isNotEmpty()) {
            (closedTrades.count { (it.pnl ?: 0.0) > 0.0 }.toDouble() / closedTrades.size.toDouble()) * 100.0
        } else null

        val grossProfit = closedTrades.mapNotNull { it.pnl }.filter { it > 0.0 }.sum()
        val grossLoss = closedTrades.mapNotNull { it.pnl }.filter { it < 0.0 }.sumOf { abs(it) }
        val profitFactor = when {
            closedTrades.isEmpty() -> null
            grossLoss > 1e-9 -> (grossProfit / grossLoss).coerceAtMost(99.9)
            grossProfit > 0.0 -> 99.9
            else -> 0.0
        }

        // 6. Step-return Sharpe estimate when enough distinct movement samples exist
        val sharpeEstimate = computeSharpeEstimate(samples)

        val spanMs = (samples.last().timestampMs - samples.first().timestampMs).coerceAtLeast(0L)
        val usesIntradayAxis = timeframe == EquityTimeframe.DAY_1 || spanMs <= 36L * 3_600_000L

        return EquityCurveAnalytics(
            timeframe = timeframe,
            samples = samples,
            baselineEquity = baseEquity,
            startEquity = startEquity,
            currentEquity = currentEq,
            periodReturnDollar = periodReturnDollar,
            periodReturnPct = periodReturnPct,
            peakEquity = peakEquity,
            troughEquity = troughEquity,
            currentDrawdownPct = currentDd,
            maxDrawdownPct = maxDrawdownPct,
            closedTradesCount = closedTrades.size,
            winRatePct = winRatePct,
            profitFactor = profitFactor,
            sharpeEstimate = sharpeEstimate,
            tradeMarkersCount = markerByIndex.size,
            usesIntradayAxis = usesIntradayAxis
        )
    }

    private fun computeSharpeEstimate(samples: List<EquityCurveSample>): Double? {
        if (samples.size < 4) return null
        val stepReturns = ArrayList<Double>(samples.size - 1)
        for (i in 1 until samples.size) {
            val prev = samples[i - 1].equity
            val curr = samples[i].equity
            if (prev > 0.0) {
                val r = (curr - prev) / prev
                if (abs(r) > 1e-9) stepReturns.add(r)
            }
        }
        if (stepReturns.size < 3) return null
        val mean = stepReturns.average()
        val variance = stepReturns.sumOf { (it - mean) * (it - mean) } / (stepReturns.size - 1)
        val std = sqrt(variance)
        if (std < 1e-9) return null
        // Scale by sqrt(N) capped to reasonable reporting range
        val raw = (mean / std) * sqrt(stepReturns.size.toDouble())
        return raw.coerceIn(-9.9, 9.9)
    }

    private fun downsamplePreservingExtrema(
        points: List<EquityPoint>,
        maxCount: Int
    ): List<EquityPoint> {
        if (points.size <= maxCount) return points
        val keepIndices = LinkedHashSet<Int>()
        keepIndices.add(0)
        keepIndices.add(points.lastIndex)

        val peakIdx = points.indices.maxByOrNull { points[it].equity } ?: 0
        val troughIdx = points.indices.minByOrNull { points[it].equity } ?: 0
        keepIndices.add(peakIdx)
        keepIndices.add(troughIdx)

        val step = (points.size - 1).toDouble() / (maxCount - 1).toDouble()
        var cursor = 0.0
        while (keepIndices.size < maxCount && cursor < points.size) {
            keepIndices.add(cursor.toInt().coerceIn(0, points.lastIndex))
            cursor += step
        }
        return keepIndices.sorted().map { points[it] }
    }
}
