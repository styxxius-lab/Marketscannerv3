package com.example.domain.engine

import com.example.data.local.DailyPnlSnapshotEntity
import com.example.data.local.PaperHoldingEntity
import com.example.data.service.WatchlistQuoteSnippet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DailyPnlChartMode(val shortLabel: String) {
    COMBO("COMBO"),
    DAILY_BARS("DAILY"),
    CUMULATIVE("CUMUL")
}

data class DailyPnlChartAnalytics(
    val snapshots: List<DailyPnlSnapshotEntity> = emptyList(),
    val net30dPnl: Double = 0.0,
    val net30dPnlPct: Double = 0.0,
    val latestCumulativePnl: Double = 0.0,
    val latestPortfolioValue: Double = 0.0,
    val latestCostBasis: Double = 0.0,
    val bestDay: DailyPnlSnapshotEntity? = null,
    val worstDay: DailyPnlSnapshotEntity? = null,
    val avgDailyPnl: Double = 0.0,
    val greenDays: Int = 0,
    val redDays: Int = 0,
    val greenDayRatePct: Double = 0.0,
    val currentStreakDays: Int = 0
)

/**
 * Pure engine that computes a rolling 30-day daily simulated P&L series for Room-stored
 * [PaperHoldingEntity] positions using historical daily ticker closes and real-time quotes,
 * and summarizes 30-day portfolio performance KPIs.
 */
object PaperDailyPnlEngine {

    const val WINDOW_DAYS = 30
    private const val DAY_MS = 86_400_000L

    /**
     * Builds [windowDays] chronological daily P&L snapshots ending on [nowMs].
     *
     * For each holding, aligns the trailing [windowDays] + 1 daily closes so day `i`'s
     * `dailyPnl` reflects `portfolioValue[i] - portfolioValue[i - 1]` and `cumulativePnl`
     * reflects `portfolioValue[i] - totalCostBasis`.
     */
    fun build30DaySnapshots(
        holdings: List<PaperHoldingEntity>,
        closesBySymbol: Map<String, List<Double>>,
        liveQuotesBySymbol: Map<String, WatchlistQuoteSnippet>,
        windowDays: Int = WINDOW_DAYS,
        nowMs: Long = System.currentTimeMillis()
    ): List<DailyPnlSnapshotEntity> {
        if (holdings.isEmpty() || windowDays <= 0) return emptyList()

        val totalCostBasis = holdings.sumOf { it.shares * it.purchasePrice }
        val neededCloses = windowDays + 1

        // Pre-align each holding's trailing (windowDays + 1) prices ending at today's live price.
        val alignedPricesByHolding: List<Pair<PaperHoldingEntity, DoubleArray>> = holdings.map { holding ->
            val sym = holding.symbol.trim().uppercase(Locale.US)
            val liveQuote = liveQuotesBySymbol[sym]?.takeIf { it.currentPrice.isFinite() && it.currentPrice > 0.0 }
            val rawHistory = closesBySymbol[sym]
                .orEmpty()
                .filter { it.isFinite() && it > 0.0 }
                .toMutableList()

            // Anchor the final close to today's live quote when available.
            if (liveQuote != null) {
                if (rawHistory.isEmpty()) {
                    val prev = liveQuote.prevClose.takeIf { it.isFinite() && it > 0.0 } ?: holding.purchasePrice
                    rawHistory.add(prev)
                    rawHistory.add(liveQuote.currentPrice)
                } else {
                    rawHistory[rawHistory.lastIndex] = liveQuote.currentPrice
                    if (rawHistory.size >= 2 && liveQuote.prevClose.isFinite() && liveQuote.prevClose > 0.0) {
                        rawHistory[rawHistory.lastIndex - 1] = liveQuote.prevClose
                    }
                }
            } else if (rawHistory.isEmpty()) {
                rawHistory.add(holding.purchasePrice)
            }

            val aligned = DoubleArray(neededCloses)
            val tail = if (rawHistory.size >= neededCloses) {
                rawHistory.subList(rawHistory.size - neededCloses, rawHistory.size)
            } else {
                rawHistory
            }
            val padCount = neededCloses - tail.size
            val padValue = tail.firstOrNull() ?: holding.purchasePrice
            for (i in 0 until padCount) {
                aligned[i] = padValue
            }
            for (i in tail.indices) {
                aligned[padCount + i] = tail[i]
            }
            holding to aligned
        }

        // Compute UTC-midnight aligned day timestamps for the last [windowDays] days ending today.
        val todayMidnightMs = utcMidnightMs(nowMs)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val portfolioValues = DoubleArray(neededCloses) { dayIdx ->
            alignedPricesByHolding.sumOf { (holding, prices) -> holding.shares * prices[dayIdx] }
        }

        val snapshots = ArrayList<DailyPnlSnapshotEntity>(windowDays)
        for (i in 0 until windowDays) {
            val prevValue = portfolioValues[i]
            val currValue = portfolioValues[i + 1]
            val dailyPnl = currValue - prevValue
            val dailyPnlPct = if (prevValue > 0.0) (dailyPnl / prevValue) * 100.0 else 0.0
            val cumulativePnl = currValue - totalCostBasis
            val daysAgo = (windowDays - 1) - i
            val dayMs = todayMidnightMs - (daysAgo * DAY_MS)
            val dateKey = dateFormatter.format(Date(dayMs))

            snapshots.add(
                DailyPnlSnapshotEntity(
                    dateKey = dateKey,
                    epochDayMs = dayMs,
                    dailyPnl = dailyPnl,
                    dailyPnlPct = dailyPnlPct,
                    cumulativePnl = cumulativePnl,
                    portfolioValue = currValue,
                    costBasis = totalCostBasis,
                    holdingsCount = holdings.size,
                    updatedAtMs = nowMs
                )
            )
        }
        return snapshots
    }

    fun summarize(snapshots: List<DailyPnlSnapshotEntity>): DailyPnlChartAnalytics {
        if (snapshots.isEmpty()) return DailyPnlChartAnalytics()
        val ordered = snapshots.sortedBy { it.epochDayMs }
        val net30dPnl = ordered.sumOf { it.dailyPnl }
        val startPortfolioValue = (ordered.first().portfolioValue - ordered.first().dailyPnl)
            .takeIf { it > 0.0 }
            ?: ordered.first().costBasis.takeIf { it > 0.0 }
            ?: 1.0
        val net30dPnlPct = (net30dPnl / startPortfolioValue) * 100.0
        val latest = ordered.last()

        val bestDay = ordered.maxByOrNull { it.dailyPnl }
        val worstDay = ordered.minByOrNull { it.dailyPnl }
        val avgDailyPnl = net30dPnl / ordered.size
        val greenDays = ordered.count { it.dailyPnl > 0.005 }
        val redDays = ordered.count { it.dailyPnl < -0.005 }
        val activeDays = (greenDays + redDays).coerceAtLeast(1)
        val greenDayRatePct = (greenDays.toDouble() / activeDays) * 100.0

        var streak = 0
        for (i in ordered.lastIndex downTo 0) {
            val d = ordered[i].dailyPnl
            when {
                d > 0.005 -> {
                    if (streak < 0) break
                    streak++
                }
                d < -0.005 -> {
                    if (streak > 0) break
                    streak--
                }
                else -> break
            }
        }

        return DailyPnlChartAnalytics(
            snapshots = ordered,
            net30dPnl = net30dPnl,
            net30dPnlPct = net30dPnlPct,
            latestCumulativePnl = latest.cumulativePnl,
            latestPortfolioValue = latest.portfolioValue,
            latestCostBasis = latest.costBasis,
            bestDay = bestDay,
            worstDay = worstDay,
            avgDailyPnl = avgDailyPnl,
            greenDays = greenDays,
            redDays = redDays,
            greenDayRatePct = greenDayRatePct,
            currentStreakDays = streak
        )
    }

    private fun utcMidnightMs(timestampMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
