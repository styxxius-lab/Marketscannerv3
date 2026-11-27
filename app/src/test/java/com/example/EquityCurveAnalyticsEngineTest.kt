package com.example

import com.example.domain.engine.EquityCurveAnalyticsEngine
import com.example.domain.engine.EquityTimeframe
import com.example.domain.model.BotState
import com.example.domain.model.EquityPoint
import com.example.domain.model.TradeLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquityCurveAnalyticsEngineTest {

    @Test
    fun buildAnalytics_computesPeakDrawdownWinRateAndProfitFactor() {
        val now = 1_795_608_000_000L
        val hour = 3_600_000L
        val history = listOf(
            EquityPoint(now - 5 * hour, 100.0),
            EquityPoint(now - 4 * hour, 110.0), // peak
            EquityPoint(now - 3 * hour, 99.0),  // 10% drawdown from 110
            EquityPoint(now - 2 * hour, 105.0),
            EquityPoint(now - 1 * hour, 114.0)  // new peak
        )
        val log = listOf(
            TradeLogEntry(now - 4 * hour, "OPEN", "NVDA", 1.0, 120.0),
            TradeLogEntry(now - 3 * hour, "CLOSE", "NVDA", 1.0, 114.0, pnl = -6.0),
            TradeLogEntry(now - 2 * hour, "OPEN", "AAPL", 1.0, 200.0),
            TradeLogEntry(now - 1 * hour, "CLOSE", "AAPL", 1.0, 220.0, pnl = 20.0)
        )
        val bot = BotState(
            equity = 114.0,
            cash = 114.0,
            initialEquity = 100.0,
            equityHistory = history,
            log = log
        )

        val analytics = EquityCurveAnalyticsEngine.buildAnalytics(bot, EquityTimeframe.ALL, nowMs = now)

        assertEquals(114.0, analytics.currentEquity, 1e-6)
        assertEquals(114.0, analytics.peakEquity, 1e-6)
        assertEquals(99.0, analytics.troughEquity, 1e-6)
        assertEquals(10.0, analytics.maxDrawdownPct, 0.01)
        assertEquals(0.0, analytics.currentDrawdownPct, 0.01)
        assertEquals(14.0, analytics.periodReturnPct, 0.01)
        assertEquals(2, analytics.closedTradesCount)
        assertEquals(50.0, analytics.winRatePct!!, 0.01)
        assertEquals(20.0 / 6.0, analytics.profitFactor!!, 0.01)
        assertTrue(analytics.tradeMarkersCount >= 2)
        assertNotNull(analytics.sharpeEstimate)
    }

    @Test
    fun buildAnalytics_filtersTimeframeWithBoundaryAnchor() {
        val now = 1_795_608_000_000L
        val day = 24L * 3_600_000L
        val history = listOf(
            EquityPoint(now - 20 * day, 100.0),
            EquityPoint(now - 10 * day, 108.0),
            EquityPoint(now - 2 * day, 112.0),
            EquityPoint(now - 6 * 3_600_000L, 115.0)
        )
        val bot = BotState(
            equity = 118.0,
            cash = 118.0,
            initialEquity = 100.0,
            equityHistory = history
        )

        val day1 = EquityCurveAnalyticsEngine.buildAnalytics(bot, EquityTimeframe.DAY_1, nowMs = now)
        val week1 = EquityCurveAnalyticsEngine.buildAnalytics(bot, EquityTimeframe.WEEK_1, nowMs = now)
        val all = EquityCurveAnalyticsEngine.buildAnalytics(bot, EquityTimeframe.ALL, nowMs = now)

        // 1D starts from boundary anchor (112.0 prior to 24h cutoff) -> 118.0
        assertEquals(112.0, day1.startEquity, 1e-6)
        assertTrue(day1.usesIntradayAxis)
        assertEquals(((118.0 - 112.0) / 112.0) * 100.0, day1.periodReturnPct, 0.01)

        // 7D starts from boundary anchor (108.0) -> 118.0
        assertEquals(108.0, week1.startEquity, 1e-6)

        // ALL starts from 100.0 -> 118.0 (+18%)
        assertEquals(100.0, all.startEquity, 1e-6)
        assertEquals(18.0, all.periodReturnPct, 0.01)
    }

    @Test
    fun buildAnalytics_handlesEmptyHistoryGracefully() {
        val now = 1_795_608_000_000L
        val bot = BotState(
            equity = 10.0,
            cash = 10.0,
            initialEquity = 10.0,
            equityHistory = emptyList()
        )

        val analytics = EquityCurveAnalyticsEngine.buildAnalytics(bot, EquityTimeframe.MONTH_1, nowMs = now)
        assertTrue(analytics.samples.size >= 2)
        assertEquals(10.0, analytics.currentEquity, 1e-6)
        assertEquals(0.0, analytics.maxDrawdownPct, 1e-6)
        assertEquals(0.0, analytics.periodReturnPct, 1e-6)
    }
}
