package com.example

import com.example.data.local.ThreatHistoryMapper
import com.example.data.remote.VixFeedParser
import com.example.data.repository.InMemoryThreatHistoryStore
import com.example.domain.engine.AdvisorBudget
import com.example.domain.engine.AdvisorConfig
import com.example.domain.engine.AdvisorEngine
import com.example.domain.engine.AdvisorHttpResponse
import com.example.domain.engine.AdvisorHttpTransport
import com.example.domain.engine.AdvisorVetoAction
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.engine.AiCatalystAssessment
import com.example.domain.engine.AiVerdictTone
import com.example.domain.engine.DashboardSymbolFilter
import com.example.domain.engine.FinnhubCatalystEngine
import com.example.domain.engine.MarketEnvironmentClassifier
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.engine.NewsCatalystAlertEngine
import com.example.domain.engine.PaperBotEngine
import com.example.domain.engine.PositionSizingEngine
import com.example.domain.engine.PriceAlertEngine
import com.example.domain.engine.QuantEngine
import com.example.domain.engine.QuantScannerPreset
import com.example.domain.engine.QuantSignalScannerEngine
import com.example.domain.engine.RawFinnhubArticle
import com.example.domain.engine.RegimeAlignmentStatus
import com.example.domain.engine.ThreatAssessmentEngine
import com.example.domain.engine.ThresholdAlertEngine
import com.example.domain.engine.ThresholdAlertMonitorState
import com.example.domain.engine.TrackedSymbolDashboardEngine
import com.example.domain.engine.VixAlertEngine
import com.example.domain.engine.VixMonitorState
import com.example.domain.model.MarketEnvironment
import com.example.engine.ThreatConfig
import com.example.engine.ThreatContext
import com.example.engine.ThreatEngine
import com.example.engine.ThreatNarrativeDiagnostic
import com.example.ui.SimulatorViewModel
import com.example.ui.ThreatScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.example.domain.model.AiProviderId
import com.example.domain.model.AlertCondition
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.BotState
import com.example.domain.model.CatalystScoreSource
import com.example.domain.model.CostEstimate
import com.example.domain.model.EquityPoint
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSector
import com.example.domain.model.ModelTier
import com.example.domain.model.ModelTierBasis
import com.example.domain.model.ModelTierFilter
import com.example.domain.model.NewsArticleFilter
import com.example.domain.model.NewsHeadline
import com.example.domain.model.RegimeLabel
import com.example.domain.model.RegimePoint
import com.example.domain.model.ScanRegimeSnapshot
import com.example.domain.model.ScoredNewsItem
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatCategory
import com.example.domain.model.ThreatLevel
import com.example.domain.model.ThreatNarrativeResult
import com.example.domain.model.ThreatPositionContext
import com.example.domain.model.ThresholdAlertKind
import com.example.domain.model.ThresholdAlertSettings
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradePosition
import com.example.domain.model.TrendBucket
import com.example.domain.model.VixSnapshot
import com.example.domain.model.VolatilityBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class QuantEngineTest {

    @Test
    fun demoUniverseProducesSortedSignalsWithCostAndSeries() {
        val cfg = AppConfig(demo = true, account = 10.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()

        val output = QuantEngine.runEngine(bars, snap, cfg)
        assertEquals(QuantEngine.UNIVERSE.size, output.rows.size)

        // Verify descending conviction sort and rank numbering
        for (i in 0 until output.rows.size - 1) {
            assertTrue(output.rows[i].conviction >= output.rows[i + 1].conviction)
            assertEquals(i + 1, output.rows[i].rank)
        }

        // Every signal should have 6 sub-factors, cost estimate, and sparkline series
        output.rows.forEach { row ->
            assertEquals(6, row.sub.size)
            assertNotNull(row.cost)
            assertTrue(row.series.size == 90)
        }
    }

    @Test
    fun newsVetoDowngradesBuyWithNegativeCatalyst() {
        val cfg = AppConfig(demo = true)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)
        val news = QuantEngine.demoNews(output.rows)
        val newsMap = QuantEngine.demoNewsMap(news)
        val applied = QuantEngine.applyNews(output.rows, newsMap)

        applied.filter { it.newsItems.isNotEmpty() }.forEach { row ->
            assertTrue(row.keyCatalyst.isNotBlank())
        }
    }

    @Test
    fun paperBotOpensAndClosesAtTarget() {
        val cfg = AppConfig(demo = true, account = 10.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)

        val buyCandidate = output.rows.firstOrNull {
            it.action == SignalAction.BUY && it.size > 0.0 && (it.cost == null || it.cost.viable)
        }
        assertNotNull("Expected at least one viable BUY candidate in demo universe", buyCandidate)
        val cand = buyCandidate!!

        val initial = BotState(
            equity = 10.0,
            cash = 10.0,
            initialEquity = 10.0,
            running = true
        )
        val opened = PaperBotEngine.tick(initial, listOf(cand), nowMs = 1_000_000L)
        assertNotNull(opened.pos)
        assertEquals(cand.symbol, opened.pos!!.symbol)
        assertEquals(1, opened.tradesToday)
        assertEquals("OPEN", opened.log.first().type)

        // Simulate price hitting target
        val hitTargetRow = cand.copy(price = cand.target + 1.0)
        val closed = PaperBotEngine.tick(opened, listOf(hitTargetRow), nowMs = 1_005_000L)
        assertNull(closed.pos)
        assertTrue(closed.equity > 10.0)
        assertEquals("CLOSE", closed.log.first().type)
        assertEquals("target", closed.log.first().note)
    }

    @Test
    fun paperBotUpdatesUnrealizedPnlAndClosesManually() {
        val cfg = AppConfig(demo = true, account = 10.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)

        val cand = output.rows.first {
            it.action == SignalAction.BUY && it.size > 0.0 && (it.cost == null || it.cost.viable)
        }
        val initial = BotState(
            equity = 10.0,
            cash = 10.0,
            initialEquity = 10.0,
            running = true
        )
        val opened = PaperBotEngine.tick(initial, listOf(cand), nowMs = 1_000_000L)
        val midPrice = (cand.entry + cand.target) / 2.0
        val ticked = PaperBotEngine.tick(opened, listOf(cand.copy(price = midPrice)), nowMs = 1_002_500L)

        val pos = ticked.pos!!
        assertEquals(midPrice, pos.last, 0.0001)
        assertTrue(pos.upnl > 0.0)

        val manuallyClosed = PaperBotEngine.closeManual(ticked, nowMs = 1_003_000L)
        assertNull(manuallyClosed.pos)
        assertTrue(manuallyClosed.equity > 10.0)
        assertEquals("manual", manuallyClosed.log.first().note)
        assertTrue(manuallyClosed.equityHistory.size >= 2)
    }

    @Test
    fun paperBotOpensManualPositionAndTracksEquityHistory() {
        val cfg = AppConfig(demo = true, account = 10.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)
        val anySignal = output.rows.first()

        val initial = PaperBotEngine.reset(10.0, nowMs = 1_000_000L)
        val opened = PaperBotEngine.openManual(initial, anySignal, nowMs = 1_001_000L)

        val openedPos = opened.pos
        assertNotNull(openedPos)
        assertEquals(anySignal.symbol, openedPos?.symbol)
        assertEquals(1, opened.tradesToday)

        val ticked = PaperBotEngine.tick(
            opened,
            listOf(anySignal.copy(price = anySignal.price * 1.02)),
            nowMs = 1_002_000L
        )
        assertTrue(ticked.equityHistory.size >= 2)
    }

    @Test
    fun portfolioTracksMultiplePositionsMarketValueAndUnrealizedPnl() {
        val cfg = AppConfig(demo = true, account = 100.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)
        val s1 = output.rows[0]
        val s2 = output.rows[1]

        val initial = PaperBotEngine.reset(100.0, nowMs = 1_000_000L)
        val withFirst = PaperBotEngine.openManual(initial, s1, nowMs = 1_001_000L)
        val withBoth = PaperBotEngine.openManual(withFirst, s2, nowMs = 1_002_000L)

        assertEquals(2, withBoth.positions.size)
        assertTrue(PaperBotEngine.totalMarketValue(withBoth) > 0.0)

        val up1 = s1.copy(price = s1.price * 1.03)
        val up2 = s2.copy(price = s2.price * 1.01)
        val ticked = PaperBotEngine.tick(withBoth, listOf(up1, up2), nowMs = 1_003_000L)

        assertEquals(2, ticked.positions.size)
        assertTrue(PaperBotEngine.totalUnrealizedPnl(ticked) > 0.0)
        assertTrue(PaperBotEngine.liveEquity(ticked) > 100.0)

        val afterCloseFirst = PaperBotEngine.closeManual(ticked, s1.symbol, nowMs = 1_004_000L)
        assertEquals(1, afterCloseFirst.positions.size)
        assertEquals(s2.symbol, afterCloseFirst.positions.first().symbol)
    }

    @Test
    fun detectRegimePopulatesTimelineAndTelemetry() {
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val regime = QuantEngine.detectRegime(bars)

        assertEquals(45, regime.timeline.size)
        val last = regime.timeline.last()
        assertEquals(0, last.dayOffset)
        assertEquals(regime.label, last.label)
        assertEquals(regime.breadth, last.breadth, 0.0001)
        assertTrue(regime.volPct in 0.0..1.0)
        assertTrue(regime.trendScore in -1.0..1.0)
    }

    @Test
    fun priceAlertsTriggerOnceOnThresholdCrossingAndSupportRearm() {
        val above = PriceAlertEngine.createAlert("NVDA", 145.0, AlertCondition.ABOVE, nowMs = 1_000L)
        val below = PriceAlertEngine.createAlert("TSLA", 220.0, AlertCondition.BELOW, nowMs = 1_000L)
        val initial = listOf(above, below)

        // Neither threshold crossed yet
        val tick1 = PriceAlertEngine.evaluate(initial, mapOf("NVDA" to 142.5, "TSLA" to 224.0), nowMs = 2_000L)
        assertTrue(tick1.newlyTriggered.isEmpty())
        assertTrue(tick1.updatedAlerts.all { it.active })

        // NVDA crosses above 145.0 while TSLA stays above 220.0
        val tick2 = PriceAlertEngine.evaluate(tick1.updatedAlerts, mapOf("NVDA" to 145.75, "TSLA" to 221.0), nowMs = 3_000L)
        assertEquals(1, tick2.newlyTriggered.size)
        assertEquals("NVDA", tick2.newlyTriggered.first().symbol)
        assertEquals(145.75, tick2.newlyTriggered.first().triggeredPrice!!, 0.0001)
        assertFalse(tick2.updatedAlerts.first { it.id == above.id }.active)

        // Subsequent tick above 145.0 must not double-fire NVDA; TSLA drops below 220.0
        val tick3 = PriceAlertEngine.evaluate(tick2.updatedAlerts, mapOf("NVDA" to 148.0, "TSLA" to 219.5), nowMs = 4_000L)
        assertEquals(1, tick3.newlyTriggered.size)
        assertEquals("TSLA", tick3.newlyTriggered.first().symbol)

        // Re-arm NVDA and clear triggered TSLA
        val rearmed = PriceAlertEngine.rearm(tick3.updatedAlerts, above.id)
        assertTrue(rearmed.first { it.id == above.id }.active)
        val cleared = PriceAlertEngine.clearTriggered(rearmed)
        assertEquals(1, cleared.size)
        assertEquals(above.id, cleared.first().id)
    }

    @Test
    fun thirtyDayEquitySeriesFiltersOldPointsAndComputesDrawdown() {
        val nowMs = 4_000_000_000L
        val thirtyFiveDaysAgo = nowMs - 35L * 24L * 60L * 60L * 1000L
        val tenDaysAgo = nowMs - 10L * 24L * 60L * 60L * 1000L
        val twoDaysAgo = nowMs - 2L * 24L * 60L * 60L * 1000L

        val state = BotState(
            equity = 11.4,
            cash = 11.4,
            initialEquity = 10.0,
            equityHistory = listOf(
                EquityPoint(thirtyFiveDaysAgo, 8.0), // outside 30-day window
                EquityPoint(tenDaysAgo, 12.0),       // 30d peak
                EquityPoint(twoDaysAgo, 10.8)        // 10% drawdown from 12.0
            )
        )

        val summary = PaperBotEngine.buildThirtyDayEquitySeries(state, nowMs = nowMs)
        assertEquals(3, summary.points.size) // two within 30d + current live point
        assertEquals(12.0, summary.high, 0.001)
        assertEquals(10.8, summary.low, 0.001)
        assertEquals(10.0, summary.maxDrawdownPct, 0.01)
        assertEquals(11.4, summary.points.last().equity, 0.001)
    }

    @Test
    fun detectNewLogEntriesCapturesOpenAndCloseTransitions() {
        val cfg = AppConfig(demo = true, account = 10.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)
        val cand = output.rows.first {
            it.action == SignalAction.BUY && it.size > 0.0 && (it.cost == null || it.cost.viable)
        }

        val initial = BotState(equity = 10.0, cash = 10.0, initialEquity = 10.0, running = true)
        val opened = PaperBotEngine.tick(initial, listOf(cand), nowMs = 1_000_000L)
        val openEvents = PaperBotEngine.detectNewLogEntries(initial, opened)
        assertEquals(1, openEvents.size)
        assertEquals("OPEN", openEvents.first().type)
        assertEquals(cand.symbol, openEvents.first().symbol)

        // Quiet tick without exit should produce zero events
        val flatTick = PaperBotEngine.tick(opened, listOf(cand), nowMs = 1_002_000L)
        assertTrue(PaperBotEngine.detectNewLogEntries(opened, flatTick).isEmpty())

        // Target hit close produces a single CLOSE event
        val closed = PaperBotEngine.tick(flatTick, listOf(cand.copy(price = cand.target + 1.0)), nowMs = 1_005_000L)
        val closeEvents = PaperBotEngine.detectNewLogEntries(flatTick, closed)
        assertEquals(1, closeEvents.size)
        assertEquals("CLOSE", closeEvents.first().type)
        assertEquals("target", closeEvents.first().note)
    }

    @Test
    fun appendScanRegimeCapsAtFiveMostRecentScans() {
        val sequence = listOf(
            RegimeLabel.CRISIS to 0.15,
            RegimeLabel.RISK_OFF to 0.35,
            RegimeLabel.NEUTRAL to 0.50,
            RegimeLabel.RISK_ON to 0.68,
            RegimeLabel.NEUTRAL to 0.52,
            RegimeLabel.RISK_ON to 0.74
        )
        var history: List<ScanRegimeSnapshot> = emptyList()
        sequence.forEachIndexed { idx, (label, breadth) ->
            history = QuantEngine.appendScanRegime(
                history = history,
                regime = MarketRegime(
                    label = label,
                    breadth = breadth,
                    longMult = 1.0,
                    shortMult = 1.0,
                    note = ""
                ),
                source = "demo",
                nowMs = (idx + 1) * 1000L
            )
        }

        assertEquals(5, history.size)
        assertEquals(RegimeLabel.RISK_OFF, history.first().label)
        assertEquals(RegimeLabel.RISK_ON, history.last().label)
        assertEquals(0.74, history.last().breadth, 0.0001)
        assertEquals(6000L, history.last().timestampMs)
    }

    @Test
    fun runEngineCategorizesUniverseBySectorWithOtherFallback() {
        val cfg = AppConfig(demo = true, watchlist = listOf("ZZZZ"))
        val syms = QuantEngine.UNIVERSE + "ZZZZ"
        val bars = syms.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap(syms)

        val output = QuantEngine.runEngine(bars, snap, cfg)
        val bySymbol = output.rows.associateBy { it.symbol }

        assertEquals(MarketSector.TECHNOLOGY, bySymbol["NVDA"]?.sector)
        assertEquals(MarketSector.CONSUMER, bySymbol["TSLA"]?.sector)
        assertEquals(MarketSector.FINANCIALS, bySymbol["JPM"]?.sector)
        assertEquals(MarketSector.ENERGY, bySymbol["XOM"]?.sector)
        assertEquals(MarketSector.HEALTHCARE, bySymbol["JNJ"]?.sector)
        assertEquals(MarketSector.INDUSTRIALS, bySymbol["BA"]?.sector)
        assertEquals(MarketSector.INDEX_ETF, bySymbol["SPY"]?.sector)
        assertEquals(MarketSector.OTHER, bySymbol["ZZZZ"]?.sector)
        assertTrue(QuantEngine.UNIVERSE.all { bySymbol[it]?.sector != MarketSector.OTHER })
    }

    @Test
    fun dailyThirtyDayPerformanceBuildsThirtyDailyBucketsWithActiveTracking() {
        val nowMs = 1_763_836_800_000L // fixed UTC timestamp
        val dayMs = 24L * 60L * 60L * 1000L
        val fiveDaysAgo = nowMs - 5L * dayMs
        val twoDaysAgo = nowMs - 2L * dayMs

        val state = BotState(
            equity = 11.5,
            cash = 11.5,
            initialEquity = 10.0,
            equityHistory = listOf(
                EquityPoint(nowMs - 40L * dayMs, 9.5), // prior to 30-day window
                EquityPoint(fiveDaysAgo, 12.0),
                EquityPoint(twoDaysAgo, 10.8)
            )
        )

        val summary = PaperBotEngine.buildDailyThirtyDayPerformance(state, nowMs = nowMs)
        assertEquals(30, summary.points.size)
        assertTrue(summary.points.all { it.hasActivity })
        assertEquals(30, summary.activeDaysCount)
        assertEquals(9.5, summary.points.first().equity, 0.01)
        assertEquals(11.5, summary.points.last().equity, 0.01)
        assertEquals(15.0, summary.periodReturnPct, 0.01)
        assertEquals(1.5, summary.periodPnl, 0.01)
        assertEquals(12.0, summary.high, 0.01)
        assertEquals(10.8, summary.low, 0.01)
        assertEquals(10.0, summary.maxDrawdownPct, 0.01)
    }

    @Test
    fun newsCatalystAlertEngineDetectsHighImpactWatchlistEventsOnce() {
        fun stubSignal(symbol: String, items: List<ScoredNewsItem>) = SignalRow(
            symbol = symbol,
            action = SignalAction.BUY,
            score = 65.0,
            confidence = 0.8,
            price = 100.0,
            entry = 100.0,
            stop = 95.0,
            target = 110.0,
            rr = 2.0,
            atrPct = 2.0,
            relVolume = 1.4,
            size = 0.25,
            reasons = emptyList(),
            sub = emptyMap(),
            newsItems = items
        )

        val rows = listOf(
            stubSignal(
                "NVDA",
                listOf(
                    ScoredNewsItem("Minor analyst tweak", 0.3, 0.28, "small note", 0.9),
                    ScoredNewsItem("Data-center backlog surge", 0.9, 0.82, "thesis-changing backlog", 0.95)
                )
            ),
            stubSignal(
                "AMD",
                listOf(ScoredNewsItem("Routine conference talk", 0.1, 0.30, "low impact", 0.9))
            ),
            stubSignal(
                "TSLA",
                listOf(ScoredNewsItem("Recall probe opened", -0.85, 0.78, "regulatory risk", 0.9))
            )
        )

        // Only NVDA and AMD are on watchlist; AMD is below 0.65 materiality; TSLA is not on watchlist.
        val firstPass = NewsCatalystAlertEngine.detectHighImpactWatchlistEvents(
            rows = rows,
            watchlist = listOf("nvda", "AMD"),
            alreadyNotifiedKeys = emptySet()
        )
        assertEquals(1, firstPass.size)
        assertEquals("NVDA", firstPass.first().symbol)
        assertEquals(0.82, firstPass.first().materiality, 0.001)

        // Second pass with already-notified keys must suppress duplicates
        val secondPass = NewsCatalystAlertEngine.detectHighImpactWatchlistEvents(
            rows = rows,
            watchlist = listOf("NVDA", "AMD"),
            alreadyNotifiedKeys = firstPass.map { it.eventKey }.toSet()
        )
        assertTrue(secondPass.isEmpty())
    }

    @Test
    fun paperBotEngineExportsTradeLogAsCsvAndTsvWithCumulativePnl() {
        val state = BotState(
            log = listOf(
                // Newest first, as stored in BotState.log
                TradeLogEntry(
                    timestampMs = 1_763_836_920_000L,
                    type = "CLOSE",
                    symbol = "NVDA",
                    qty = 0.05,
                    price = 148.0,
                    pnl = 0.40,
                    note = "target, automated"
                ),
                TradeLogEntry(
                    timestampMs = 1_763_836_800_000L,
                    type = "OPEN",
                    symbol = "NVDA",
                    qty = 0.05,
                    price = 140.0,
                    pnl = null,
                    note = "conviction 1.25"
                )
            )
        )

        val csv = PaperBotEngine.exportTradeLog(
            state = state,
            format = PaperBotEngine.LogExportFormat.CSV,
            timeZone = TimeZone.getTimeZone("UTC")
        ).trim().lines()

        assertEquals(3, csv.size)
        assertEquals("Timestamp,Action,Symbol,Qty,Price,Notional,Realized_PnL,Cumulative_PnL,Note", csv[0])
        assertTrue(csv[1].contains(",OPEN,NVDA,0.0500,140.00,7.00,,0.0000,conviction 1.25"))
        assertTrue(csv[2].contains(",CLOSE,NVDA,0.0500,148.00,7.40,0.4000,0.4000,\"target, automated\""))

        val tsv = PaperBotEngine.exportTradeLog(
            state = state,
            format = PaperBotEngine.LogExportFormat.TSV,
            timeZone = TimeZone.getTimeZone("UTC")
        ).trim().lines()
        assertEquals(3, tsv.size)
        assertTrue(tsv[0].contains("\t"))
        assertTrue(tsv[2].endsWith("\ttarget, automated"))
    }

    @Test
    fun buildRegimeHeatmapMapsVolatilityVsTrendAndCountsHistory() {
        val regime = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.72,
            longMult = 1.15,
            shortMult = 0.75,
            note = "breadth 72% above 50d",
            trendScore = 1.0,
            volPct = 0.30,
            timeline = listOf(
                RegimePoint(-2, RegimeLabel.NEUTRAL, 0.50, 0.0, 0.62),
                RegimePoint(-1, RegimeLabel.RISK_ON, 0.68, 1.0, 0.35),
                RegimePoint(0, RegimeLabel.RISK_ON, 0.72, 1.0, 0.30)
            )
        )

        val heatmap = QuantEngine.buildRegimeHeatmap(regime)
        assertEquals(12, heatmap.cells.size)
        assertEquals(VolatilityBucket.CALM.ordinal, heatmap.activeRow)
        assertEquals(TrendBucket.BULL.ordinal, heatmap.activeCol)
        assertTrue(heatmap.overallHealthScore >= 75)
        assertEquals("Healthy", heatmap.healthVerdict)

        val activeCell = heatmap.cells.first { it.isCurrent }
        assertEquals(VolatilityBucket.CALM, activeCell.volBucket)
        assertEquals(TrendBucket.BULL, activeCell.trendBucket)
        assertEquals(2, activeCell.historyCount)

        val neutralNormalCell = heatmap.cells.first {
            it.volBucket == VolatilityBucket.NORMAL && it.trendBucket == TrendBucket.MIXED
        }
        assertEquals(1, neutralNormalCell.historyCount)
        assertFalse(neutralNormalCell.isCurrent)

        // Verify Crisis extreme mapping (Bearish trend + Extreme vol > 90p)
        val crisisHeatmap = QuantEngine.buildRegimeHeatmap(
            MarketRegime(
                label = RegimeLabel.CRISIS,
                breadth = 0.20,
                longMult = 0.6,
                shortMult = 1.0,
                note = "stress",
                trendScore = -1.0,
                volPct = 0.96
            )
        )
        val crisisActive = crisisHeatmap.cells.first { it.isCurrent }
        assertEquals(RegimeLabel.CRISIS, crisisActive.regimeLabel)
        assertTrue(crisisHeatmap.overallHealthScore < 25)
        assertEquals("Stressed", crisisHeatmap.healthVerdict)
    }

    @Test
    fun positionSizingEngineBalancesRegimeNewsVolatilityKellyAndProjections() {
        val bullishSignal = SignalRow(
            symbol = "NVDA",
            action = SignalAction.BUY,
            score = 78.0,
            confidence = 0.86,
            price = 100.0,
            entry = 100.0,
            stop = 95.0,
            target = 112.0,
            rr = 2.4,
            atrPct = 2.2,
            relVolume = 1.6,
            size = 0.25,
            reasons = listOf("trend alignment", "strong AI demand"),
            sub = mapOf("Trend" to 0.8),
            spreadBps = 4.0,
            cost = CostEstimate(
                spreadBps = 4.0,
                impactBps = 4.0,
                roundTripBps = 8.0,
                rrNet = 2.38,
                viable = true,
                reason = "Low friction"
            ),
            newsDirection = 0.85,
            newsMateriality = 0.80,
            newsItems = listOf(
                ScoredNewsItem("Hyperscaler GPU orders accelerate", 0.9, 0.82, "Expands backlog visibility", 0.95)
            )
        )
        val riskOnRegime = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.76,
            longMult = 1.15,
            shortMult = 0.75,
            note = "breadth 76% above 50d",
            trendScore = 1.0,
            volPct = 0.35
        )
        val tape = listOf(
            NewsHeadline(
                sym = "NVDA",
                title = "NVDA suppliers raise Q4 wafer guidance",
                summary = "Supply chain confirmation",
                publishedIso = "2026-11-23T12:00:00Z",
                source = "MarketTape"
            )
        )

        val highConviction = PositionSizingEngine.calculate(
            signal = bullishSignal,
            regime = riskOnRegime,
            accountSize = 10_000.0,
            riskPct = 1.0,
            marketTapeHeadlines = tape
        )

        assertEquals("HIGH CONVICTION", highConviction.verdictLabel)
        assertEquals(100.0, highConviction.dollarRiskBudget, 0.01)
        assertTrue(highConviction.recommendedShares > 20.0)
        assertTrue(highConviction.breakdown.regimeMultiplier > 1.0)
        assertTrue(highConviction.breakdown.newsMultiplier > 1.0)
        assertTrue(highConviction.breakdown.quarterKellyRiskPct > 0.0)
        assertTrue(highConviction.projections.expectedValueDollars > 0.0)
        assertTrue(highConviction.projections.expectedAccountAfter10Trades > 10_000.0)
        assertEquals(2, highConviction.readableBriefs.size)

        // Conflict/news veto should slash recommended shares sharply
        val vetoedSignal = bullishSignal.copy(
            reasons = listOf("news veto: regulatory export probe"),
            newsDirection = -0.9,
            newsMateriality = 0.85
        )
        val vetoedResult = PositionSizingEngine.calculate(
            signal = vetoedSignal,
            regime = riskOnRegime,
            accountSize = 10_000.0,
            riskPct = 1.0,
            marketTapeHeadlines = tape
        )
        assertEquals("NEWS VETO", vetoedResult.verdictLabel)
        assertTrue(vetoedResult.recommendedShares < highConviction.recommendedShares * 0.5)
    }

    @Test
    fun paperBotOpenManualSupportsCustomSizedQty() {
        val signal = SignalRow(
            symbol = "AAPL",
            action = SignalAction.BUY,
            score = 62.0,
            confidence = 0.75,
            price = 200.0,
            entry = 200.0,
            stop = 190.0,
            target = 220.0,
            rr = 2.0,
            atrPct = 1.8,
            relVolume = 1.2,
            size = 0.01,
            reasons = emptyList(),
            sub = emptyMap()
        )
        val initial = PaperBotEngine.reset(1_000.0, nowMs = 1_000_000L)
        val opened = PaperBotEngine.openManual(initial, signal, nowMs = 1_001_000L, customQty = 1.25)

        assertEquals(1, opened.positions.size)
        assertEquals(1.25, opened.positions.first().qty, 0.0001)
        assertEquals(750.0, opened.cash, 0.01)
        assertTrue(opened.log.first().note.contains("sized manual"))
    }

    @Test
    fun vixAlertEngineDetectsIntradaySpikeAppliesHysteresisAndRearms() {
        val base = VixAlertEngine.buildSnapshot(
            current = 15.6,
            open = 15.0,
            prevClose = 14.8,
            intradayLow = 14.9,
            intradayHigh = 15.6,
            intradaySeries = listOf(15.0, 14.9, 15.6),
            source = "DEMO",
            nowMs = 1_000_000L
        )
        // +4.7% intraday spike < 10% threshold -> should stay armed with no event
        val calmEval = VixAlertEngine.evaluate(
            snapshot = base,
            thresholdPct = 10.0,
            enabled = true,
            monitorState = VixMonitorState(),
            nowMs = 1_000_000L
        )
        assertNull(calmEval.triggeredEvent)
        assertTrue(calmEval.monitorState.armed)

        // Sudden spike to 16.95 (+13.76% vs intraday low 14.90) -> must fire
        val spiked = VixAlertEngine.buildSnapshot(
            current = 16.95,
            open = 15.0,
            prevClose = 14.8,
            intradayLow = 14.9,
            intradayHigh = 16.95,
            intradaySeries = listOf(15.0, 14.9, 15.6, 16.95),
            source = "CBOE LIVE",
            nowMs = 1_010_000L
        )
        val spikeEval = VixAlertEngine.evaluate(
            snapshot = spiked,
            thresholdPct = 10.0,
            enabled = true,
            monitorState = calmEval.monitorState,
            nowMs = 1_010_000L
        )
        assertNotNull(spikeEval.triggeredEvent)
        assertEquals(16.95, spikeEval.triggeredEvent!!.vixLevel, 0.01)
        assertTrue(spikeEval.triggeredEvent!!.spikePct >= 13.0)
        assertFalse(spikeEval.monitorState.armed)

        // Sustained elevated reading within cooldown window must NOT spam
        val duplicateTick = VixAlertEngine.evaluate(
            snapshot = spiked.copy(current = 17.05, intradaySpikePct = 14.43),
            thresholdPct = 10.0,
            enabled = true,
            monitorState = spikeEval.monitorState,
            nowMs = 1_020_000L
        )
        assertNull(duplicateTick.triggeredEvent)

        // Cooling below 0.75 * threshold (7.5%) automatically re-arms
        val cooled = VixAlertEngine.buildSnapshot(
            current = 15.4,
            open = 15.0,
            prevClose = 14.8,
            intradayLow = 14.9,
            intradayHigh = 17.05,
            intradaySeries = listOf(15.0, 16.95, 15.4),
            source = "CBOE LIVE",
            nowMs = 1_050_000L
        )
        val cooledEval = VixAlertEngine.evaluate(
            snapshot = cooled,
            thresholdPct = 10.0,
            enabled = true,
            monitorState = duplicateTick.monitorState,
            nowMs = 1_050_000L
        )
        assertNull(cooledEval.triggeredEvent)
        assertTrue(cooledEval.monitorState.armed)
    }

    @Test
    fun vixFeedParserParsesCboeAndYahooIntradayFeeds() {
        val cboeJson = """
            {
              "data": {
                "symbol": "_VIX",
                "current_price": 18.45,
                "prev_day_close": 16.10,
                "open": 16.25,
                "high": 18.60,
                "low": 16.05
              }
            }
        """.trimIndent()
        val cboeSnap = VixFeedParser.parseCboeJson(cboeJson, previous = null, nowMs = 2_000_000L)
        assertNotNull(cboeSnap)
        assertEquals("CBOE LIVE", cboeSnap!!.source)
        assertEquals(18.45, cboeSnap.current, 0.01)
        assertTrue(cboeSnap.intradaySpikePct > 14.0)

        val yahooJson = """
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "regularMarketPrice": 19.20,
                      "chartPreviousClose": 16.50,
                      "regularMarketDayHigh": 19.40,
                      "regularMarketDayLow": 16.40
                    },
                    "indicators": {
                      "quote": [
                        {
                          "open": [16.55, 17.00, 18.10],
                          "high": [17.10, 18.30, 19.40],
                          "low": [16.40, 16.95, 18.00],
                          "close": [17.00, 18.10, 19.20]
                        }
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val yahooSnap = VixFeedParser.parseYahooJson(yahooJson, previous = null, nowMs = 3_000_000L)
        assertNotNull(yahooSnap)
        assertEquals("CBOE/YAHOO", yahooSnap!!.source)
        assertEquals(19.20, yahooSnap.current, 0.01)
        assertEquals(16.40, yahooSnap.intradayLow, 0.01)
        assertTrue(yahooSnap.intradaySeries.size >= 3)
        assertTrue(yahooSnap.intradaySpikePct > 16.0)
    }

    private fun buildSteadyThenShockBars(
        finalGapPct: Double,
        finalDropPct: Double,
        finalVolMult: Double,
        priorTrendPctPerBar: Double = 0.001
    ): BarSeries {
        val n = 75
        val o = ArrayList<Double>(n)
        val h = ArrayList<Double>(n)
        val l = ArrayList<Double>(n)
        val c = ArrayList<Double>(n)
        val v = ArrayList<Double>(n)
        var px = 100.0
        for (i in 0 until n - 1) {
            val open = px
            val close = px * (1.0 + priorTrendPctPerBar)
            o.add(open)
            h.add(maxOf(open, close) * 1.006)
            l.add(minOf(open, close) * 0.994)
            c.add(close)
            v.add(1_000_000.0 + (i % 5) * 20_000.0)
            px = close
        }
        val prevClose = c.last()
        val gapOpen = prevClose * (1.0 + finalGapPct / 100.0)
        val finalClose = prevClose * (1.0 + finalDropPct / 100.0)
        o.add(gapOpen)
        h.add(maxOf(gapOpen, finalClose) * 1.004)
        l.add(finalClose * 0.999)
        c.add(finalClose)
        v.add(1_000_000.0 * finalVolMult)
        return BarSeries(o, h, l, c, v)
    }

    @Test
    fun threatEngineDetectsQuantShockContagionAndStopBreach() {
        val shockBars = buildSteadyThenShockBars(
            finalGapPct = -6.0,
            finalDropPct = -8.0,
            finalVolMult = 4.2
        )
        val flatBench = buildSteadyThenShockBars(
            finalGapPct = 0.0,
            finalDropPct = 0.3,
            finalVolMult = 1.0
        )

        val q = ThreatAssessmentEngine.quantThreat(shockBars, benchBars = flatBench)
        assertNotNull(q)
        assertTrue("Expected high quant shock score, got ${q!!.score}", q.score >= 55.0)
        assertTrue(q.drivers.any { it.name == "gap" })
        assertTrue(q.drivers.any { it.name == "volume" })
        assertTrue(q.drivers.any { it.name == "structure" })

        val peerDown1 = buildSteadyThenShockBars(-1.5, -2.2, 1.4)
        val peerDown2 = buildSteadyThenShockBars(-1.0, -1.8, 1.2)
        val peerFlat = buildSteadyThenShockBars(0.1, 0.2, 1.0)
        val contagion = ThreatAssessmentEngine.contagion(listOf(peerDown1, peerDown2, peerFlat))
        assertNotNull(contagion)
        assertTrue(contagion!!.sectorWide)
        assertEquals(2, contagion.peersFalling)

        // Structural fraud headline + quant shock -> CRITICAL & EXIT_NOW
        val headlines = listOf(
            NewsHeadline(
                sym = "SMCI",
                title = "Company discloses auditor resignation and delayed 10-K filing",
                summary = "Independent auditor resigns over internal controls",
                publishedIso = "",
                source = "demo"
            )
        )
        val narrative = ThreatAssessmentEngine.classifyDemoHeadlines("SMCI", headlines)
        assertEquals(ThreatCategory.ACCOUNTING_FRAUD, narrative.category)
        assertTrue(narrative.structural)

        val criticalCard = ThreatAssessmentEngine.assess(
            symbol = "SMCI",
            bars = shockBars,
            headlines = headlines,
            narrative = narrative,
            benchBars = flatBench
        )
        assertNotNull(criticalCard)
        assertTrue(criticalCard!!.threatScore >= 65)
        assertTrue(
            criticalCard.action == ThreatAction.EXIT_NOW || criticalCard.action == ThreatAction.SELL
        )
        assertNotNull(criticalCard.suggestedStop)

        // Stop breach rule: price within 0.5% of stop forces EXIT_NOW even on low threat
        val calmBars = buildSteadyThenShockBars(0.0, 0.1, 1.0)
        val lowNarr = ThreatAssessmentEngine.narrativeDegraded()
        val pxNow = calmBars.c.last()
        val nearStopPos = ThreatPositionContext(
            qty = 2.0,
            entry = pxNow * 1.05,
            initialStop = pxNow * 0.96,
            stop = pxNow * 0.998, // price is within 0.2% of stop (< 0.5% buffer)
            upnl = -10.0
        )
        val stoppedCard = ThreatAssessmentEngine.assess(
            symbol = "AAPL",
            bars = calmBars,
            headlines = emptyList(),
            narrative = lowNarr,
            position = nearStopPos
        )
        assertNotNull(stoppedCard)
        assertEquals(ThreatAction.EXIT_NOW, stoppedCard!!.action)
    }

    @Test
    fun threatEngineTrimsWinnersAboveTwoRInsteadOfFullPanicExit() {
        val shockBars = buildSteadyThenShockBars(
            finalGapPct = -4.5,
            finalDropPct = -6.0,
            finalVolMult = 3.5
        )
        val px = shockBars.c.last()
        // Entry far below at +3.5R profit cushion with non-structural earnings miss
        val winnerPos = ThreatPositionContext(
            qty = 4.0,
            entry = px * 0.70,
            initialStop = px * 0.65,
            stop = px * 0.85,
            upnl = 120.0
        )
        val nonStructuralMiss = ThreatNarrativeResult(
            category = ThreatCategory.EARNINGS_MISS,
            materiality = 0.70,
            structural = false,
            companySpecific = true,
            persistenceDays = 8,
            confidence = 0.85,
            summary = "Minor quarterly EPS miss",
            severityPrior = ThreatCategory.EARNINGS_MISS.severity,
            degraded = false
        )
        val card = ThreatAssessmentEngine.assess(
            symbol = "NVDA",
            bars = shockBars,
            headlines = listOf(NewsHeadline("NVDA", "Quarterly miss on temporary mix", "", "", "demo")),
            narrative = nonStructuralMiss,
            position = winnerPos
        )
        assertNotNull(card)
        assertTrue(card!!.level >= ThreatLevel.ELEVATED)
        assertEquals(ThreatAction.TRIM, card.action)
    }

    @Test
    fun paperBotTrimManualAndUpdatePositionStopPreserveAccounting() {
        val cfg = AppConfig(demo = true, account = 1000.0, riskPct = 1.0)
        val bars = QuantEngine.UNIVERSE.associateWith { QuantEngine.genSeries(it) }
        val snap = QuantEngine.demoSnap()
        val output = QuantEngine.runEngine(bars, snap, cfg)
        val sig = output.rows.first().copy(price = 100.0, entry = 100.0, stop = 94.0, target = 112.0)

        val initial = PaperBotEngine.reset(1000.0, nowMs = 1_000_000L)
        val opened = PaperBotEngine.openManual(initial, sig, nowMs = 1_001_000L, customQty = 4.0)
        assertEquals(4.0, opened.positions.first().qty, 0.0001)
        assertEquals(600.0, opened.cash, 0.01)

        // Price rises to $110 -> trim 50% (2.0 shares) -> realizes +$20 PnL
        val ticked = PaperBotEngine.tick(opened, listOf(sig.copy(price = 110.0)), nowMs = 1_002_000L)
        val trimmed = PaperBotEngine.trimManual(ticked, sig.symbol, fraction = 0.5, nowMs = 1_003_000L)
        assertEquals(2.0, trimmed.positions.first().qty, 0.0001)
        assertEquals(820.0, trimmed.cash, 0.01)
        assertEquals(1020.0, trimmed.equity, 0.01)
        assertEquals("TRIM", trimmed.log.first().type)
        assertEquals(20.0, trimmed.log.first().pnl!!, 0.01)

        // Tighten stop from $94 to $105 (below current $110 price)
        val tightened = PaperBotEngine.updatePositionStop(trimmed, sig.symbol, 105.0)
        assertEquals(105.0, tightened.positions.first().stop, 0.01)
    }

    @Test
    fun threatEngineClassSupportsCustomConfigNarrativeAndContagion() = runBlocking {
        // Non-exhausted structural breakdown (gap < 2x ATR, heavy intraday fade + breakdown) -> SELL
        val breakdownBars = buildSteadyThenShockBars(
            finalGapPct = -1.6,
            finalDropPct = -6.5,
            finalVolMult = 3.8,
            priorTrendPctPerBar = -0.002
        )
        val engineWithLiveClassifier = ThreatEngine(
            config = ThreatConfig(),
            clockMs = { 10_000_000L },
            narrativeClassifier = ThreatEngine.NarrativeClassifier { sym, _ ->
                ThreatNarrativeDiagnostic(
                    categoryKey = "guidance_cut",
                    categoryLabel = "Guidance cut",
                    materiality = 0.88,
                    structural = true,
                    companySpecific = true,
                    persistenceDays = 20,
                    confidence = 0.90,
                    summary = "$sym lowers full-year revenue guidance",
                    severityPrior = ThreatCategory.GUIDANCE_CUT.severity,
                    degraded = false,
                    isLiveClassified = true
                )
            }
        )

        assertEquals(14, engineWithLiveClassifier.taxonomy.size)
        val sellCard = engineWithLiveClassifier.assess(
            symbol = "NVDA",
            bars = breakdownBars,
            headlines = listOf(
                NewsHeadline(
                    sym = "NVDA",
                    title = "Guidance cut weighs on outlook",
                    summary = "",
                    publishedIso = "",
                    source = "reuters"
                )
            ),
            ctx = ThreatContext(
                peerBars = listOf(
                    buildSteadyThenShockBars(-1.2, -1.8, 1.3),
                    buildSteadyThenShockBars(-0.9, -1.5, 1.2)
                )
            )
        )
        assertNotNull(sellCard)
        assertTrue(sellCard!!.narrative.isLiveClassified)
        assertEquals("guidance_cut", sellCard.narrative.categoryKey)
        assertTrue(sellCard.contagion!!.sectorWide)
        assertTrue(sellCard.quant.drivers.isNotEmpty())
        assertTrue(sellCard.threatScore >= 65)
        assertTrue(sellCard.action == ThreatAction.SELL || sellCard.action == ThreatAction.EXIT_NOW)

        // Capitulation gap (>2x ATR) + oversold RSI (<25) -> exhaustion guard advises TRIM rather than panic dump
        val capitulationBars = buildSteadyThenShockBars(
            finalGapPct = -5.5,
            finalDropPct = -7.0,
            finalVolMult = 3.8
        )
        val trimCard = engineWithLiveClassifier.assess(
            symbol = "NVDA",
            bars = capitulationBars,
            headlines = listOf(
                NewsHeadline("NVDA", "Guidance cut weighs on outlook", "", "", "reuters")
            )
        )
        assertNotNull(trimCard)
        assertEquals(ThreatAction.TRIM, trimCard!!.action)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun simulatorViewModelScansSymbolAndReactsInRealTime() {
        val mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        try {
            val vm = SimulatorViewModel(ioDispatcher = mainDispatcher)
            val cfg = AppConfig(demo = true, account = 1000.0, riskPct = 1.0)
            val spyBars = buildSteadyThenShockBars(0.0, 0.2, 1.0)
            val nvdaBars = buildSteadyThenShockBars(0.1, 0.3, 1.1)
            val output = QuantEngine.runEngine(
                mapOf("NVDA" to QuantEngine.genSeries("NVDA"), "SPY" to QuantEngine.genSeries("SPY")),
                QuantEngine.demoSnap(listOf("NVDA", "SPY")),
                cfg
            )
            val nvdaRow = output.rows.first { it.symbol == "NVDA" }
                .copy(price = nvdaBars.c.last())

            vm.syncMarketSnapshot(
                barsBySymbol = mapOf("NVDA" to nvdaBars, "SPY" to spyBars),
                rows = listOf(nvdaRow),
                headlines = listOf(
                    NewsHeadline("NVDA", "Data center demand remains steady", "", "", "demo")
                ),
                botState = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0),
                config = cfg
            )

            // Initial default auto-scan produces a valid Success card
            val initialState = vm.uiState.value.scanState
            assertTrue(initialState is ThreatScanState.Success)
            val initialCard = (initialState as ThreatScanState.Success).card
            assertEquals("NVDA", initialCard.symbol)
            assertEquals(ThreatAction.HOLD, initialCard.action)

            // Real-time update: open NVDA position near its stop -> immediately escalates recommendation to EXIT_NOW
            val livePx = nvdaBars.c.last()
            val atStopPos = TradePosition(
                symbol = "NVDA",
                qty = 2.0,
                entry = livePx * 1.05,
                stop = livePx * 0.999,
                target = livePx * 1.12,
                openedAtMs = 1_000L,
                upnl = -10.0,
                last = livePx
            )
            vm.syncMarketSnapshot(
                barsBySymbol = mapOf("NVDA" to nvdaBars, "SPY" to spyBars),
                rows = listOf(nvdaRow),
                headlines = emptyList(),
                botState = BotState(
                    equity = 1000.0,
                    cash = 800.0,
                    initialEquity = 1000.0,
                    positions = listOf(atStopPos)
                ),
                config = cfg
            )

            val updatedSuccess = vm.uiState.value.scanState as ThreatScanState.Success
            assertEquals(ThreatAction.EXIT_NOW, updatedSuccess.card.action)

            // Scanning blank symbol sets clear Error state
            vm.scanSymbol("   ")
            assertTrue(vm.uiState.value.scanState is ThreatScanState.Error)

            // Explicit symbol scan recovers to Success and records scan history
            vm.updateSymbolInput("aapl$")
            assertEquals("AAPL", vm.uiState.value.symbolInput)
            vm.scanSymbol("AAPL")
            assertTrue(vm.uiState.value.scanState is ThreatScanState.Success)
            assertEquals("AAPL", (vm.uiState.value.scanState as ThreatScanState.Success).card.symbol)
            assertTrue(vm.uiState.value.threatHistory.any { it.symbol == "AAPL" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun threatHistoryMapperRoundTripsThreatCardAndDrivers() = runBlocking {
        val shockBars = buildSteadyThenShockBars(
            finalGapPct = -2.2,
            finalDropPct = -6.0,
            finalVolMult = 3.6,
            priorTrendPctPerBar = -0.002
        )
        val engine = ThreatEngine(clockMs = { 5_555_000L })
        val card = engine.assess(
            symbol = "SMCI",
            bars = shockBars,
            headlines = listOf(
                NewsHeadline("SMCI", "SEC probe into delayed filing disclosed", "", "", "reuters")
            )
        )
        assertNotNull(card)
        val entity = ThreatHistoryMapper.toEntity(card!!)
        val restored = ThreatHistoryMapper.toEntry(entity.copy(id = 42L))

        assertEquals(42L, restored.id)
        assertEquals("SMCI", restored.symbol)
        assertEquals(card.timestampMs, restored.timestampMs)
        assertEquals(card.threatScore, restored.threatScore)
        assertEquals(card.level, restored.level)
        assertEquals(card.action, restored.action)
        assertEquals(card.verdict, restored.verdict)
        assertEquals(card.quant.drivers.size, restored.drivers.size)
        assertEquals(card.quant.drivers.firstOrNull()?.name, restored.drivers.firstOrNull()?.name)
        assertEquals(card.invalidation, restored.invalidation)
        assertEquals(card.suggestedStop, restored.suggestedStop)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun simulatorViewModelLogsFiltersAndClearsThreatHistory() {
        val mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        try {
            val store = InMemoryThreatHistoryStore()
            val vm = SimulatorViewModel(
                historyStore = store,
                ioDispatcher = mainDispatcher
            )
            val cfg = AppConfig(demo = true, account = 1000.0, riskPct = 1.0)
            val spyBars = buildSteadyThenShockBars(0.0, 0.2, 1.0)
            val nvdaBars = buildSteadyThenShockBars(0.1, 0.2, 1.0)
            val aaplRow = QuantEngine.runEngine(
                mapOf("NVDA" to QuantEngine.genSeries("NVDA"), "SPY" to QuantEngine.genSeries("SPY")),
                QuantEngine.demoSnap(listOf("NVDA", "SPY")),
                cfg
            ).rows.first { it.symbol == "NVDA" }.copy(price = nvdaBars.c.last())

            vm.syncMarketSnapshot(
                barsBySymbol = mapOf("NVDA" to nvdaBars, "SPY" to spyBars),
                rows = listOf(aaplRow),
                headlines = emptyList(),
                botState = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0),
                config = cfg
            )

            // Initial auto-scan for NVDA logged
            assertEquals(1, vm.uiState.value.threatHistory.size)
            assertEquals("NVDA", vm.uiState.value.threatHistory.first().symbol)

            // Unchanged real-time snapshot does not spam duplicate history entries
            vm.syncMarketSnapshot(
                barsBySymbol = mapOf("NVDA" to nvdaBars, "SPY" to spyBars),
                rows = listOf(aaplRow),
                headlines = emptyList(),
                botState = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0),
                config = cfg
            )
            assertEquals(1, vm.uiState.value.threatHistory.size)

            // Explicit user scan for AAPL logs a new entry
            vm.scanSymbol("AAPL")
            assertEquals(2, vm.uiState.value.threatHistory.size)
            assertTrue(vm.uiState.value.threatHistory.any { it.symbol == "AAPL" })

            // Symbol filter + scoped clear removes only AAPL entries and resets stale filter
            vm.setHistorySymbolFilter("AAPL")
            assertEquals("AAPL", vm.uiState.value.historySymbolFilter)
            vm.clearThreatHistory()
            assertEquals(1, vm.uiState.value.threatHistory.size)
            assertEquals("NVDA", vm.uiState.value.threatHistory.first().symbol)
            assertEquals(null, vm.uiState.value.historySymbolFilter)

            // Single-entry delete removes remaining NVDA log
            val nvdaId = vm.uiState.value.threatHistory.first().id
            vm.deleteHistoryEntry(nvdaId)
            assertTrue(vm.uiState.value.threatHistory.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun marketEnvironmentClassifierCategorizesBullBearVolatileCorrectly() {
        // 1. Bull Environment: Strong trend (+0.8), high breadth (68%), calm VIX (15.5)
        val bullRegime = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.68,
            longMult = 1.15,
            shortMult = 0.75,
            note = "breadth 68%",
            trendScore = 0.80,
            volPct = 0.35
        )
        val bullVix = VixSnapshot(
            current = 15.5,
            open = 16.0,
            prevClose = 16.2,
            intradayLow = 15.2,
            intradayHigh = 16.1,
            changeFromClosePct = -4.3,
            intradaySpikePct = 0.0
        )
        val bullClassification = MarketEnvironmentClassifier.classify(
            regime = bullRegime,
            vixSnapshot = bullVix
        )
        assertEquals(MarketEnvironment.BULL, bullClassification.environment)
        assertTrue("Expected barometer >= 65, got ${bullClassification.compositeBarometer}", bullClassification.compositeBarometer >= 65)
        assertTrue("Expected confidence >= 75%, got ${bullClassification.confidencePct}", bullClassification.confidencePct >= 75)
        assertTrue(bullClassification.recommendedLongMult >= 1.15)
        assertTrue(bullClassification.factors.isNotEmpty())

        // 2. Bear Environment: Downtrend (-0.8), low breadth (28%), neutral/elevated vol (26.0)
        val bearRegime = MarketRegime(
            label = RegimeLabel.RISK_OFF,
            breadth = 0.28,
            longMult = 0.6,
            shortMult = 1.25,
            note = "breadth 28%",
            trendScore = -0.80,
            volPct = 0.65
        )
        val bearVix = VixSnapshot(
            current = 26.0,
            open = 24.5,
            prevClose = 24.0,
            intradayLow = 24.0,
            intradayHigh = 27.0,
            changeFromClosePct = 8.3,
            intradaySpikePct = 4.0
        )
        val bearClassification = MarketEnvironmentClassifier.classify(
            regime = bearRegime,
            vixSnapshot = bearVix
        )
        assertEquals(MarketEnvironment.BEAR, bearClassification.environment)
        assertTrue("Expected barometer <= 40, got ${bearClassification.compositeBarometer}", bearClassification.compositeBarometer <= 40)
        assertTrue(bearClassification.recommendedShortMult >= 1.10)

        // 3. Volatile Environment: Sudden VIX spike (+18% intraday, VIX 28.5) despite mixed trend
        val mixedRegime = MarketRegime(
            label = RegimeLabel.NEUTRAL,
            breadth = 0.48,
            longMult = 1.0,
            shortMult = 1.0,
            note = "mixed",
            trendScore = 0.10,
            volPct = 0.70
        )
        val spikingVix = VixSnapshot(
            current = 28.5,
            open = 22.0,
            prevClose = 22.5,
            intradayLow = 21.8,
            intradayHigh = 29.0,
            changeFromClosePct = 26.6,
            intradaySpikePct = 18.2
        )
        val volatileClassification = MarketEnvironmentClassifier.classify(
            regime = mixedRegime,
            vixSnapshot = spikingVix
        )
        assertEquals(MarketEnvironment.VOLATILE, volatileClassification.environment)
        assertTrue(volatileClassification.vixSpikeActive)
        assertTrue(volatileClassification.stopStrategyText.contains("Wide volatility buffer"))
    }

    @Test
    fun alphaTraderPanicGuardHalvesPositionSizingOnCrashRebound() {
        val panicRegime = MarketRegime(
            label = RegimeLabel.NEUTRAL,
            breadth = 0.52,
            longMult = 1.0,
            shortMult = 1.0,
            note = "Panic Guard Active",
            trendScore = 0.40,
            volPct = 0.88,
            panicGuardActive = true,
            panicDrawdownPct = 8.5,
            panicCooldownRemaining = 3
        )

        val classification = MarketEnvironmentClassifier.classify(regime = panicRegime)
        assertEquals(MarketEnvironment.VOLATILE, classification.environment)
        assertTrue(classification.panicGuardActive)
        assertEquals(8.5, classification.panicDrawdownPct, 0.01)
        assertTrue(classification.factors.any { it.id == "panic_guard" })
        assertTrue(classification.tacticalPosture.contains("Panic Guard"))

        val signal = SignalRow(
            symbol = "NVDA",
            action = SignalAction.BUY,
            score = 65.0,
            confidence = 0.8,
            price = 100.0,
            entry = 100.0,
            stop = 95.0,
            target = 115.0,
            rr = 3.0,
            atrPct = 2.0,
            relVolume = 1.3,
            size = 0.25,
            reasons = emptyList(),
            sub = emptyMap()
        )

        val calmRegime = panicRegime.copy(panicGuardActive = false, longMult = 1.0)
        val calmResult = PositionSizingEngine.calculate(signal, calmRegime, accountSize = 10_000.0, riskPct = 1.0)
        val panicResult = PositionSizingEngine.calculate(signal, panicRegime, accountSize = 10_000.0, riskPct = 1.0)

        assertTrue(panicResult.panicGuardActive)
        assertEquals("PANIC GUARD (50% CUT)", panicResult.verdictLabel)
        assertEquals(0.50, panicResult.breakdown.panicMultiplier, 0.001)
        assertEquals(calmResult.recommendedShares * 0.50, panicResult.recommendedShares, 0.01)
    }

    @Test
    fun alphaTraderGatesMeanReversionBehindRiskOffAndUsesTwoDaySkipMomentum() {
        // Build a downtrending 240-bar series that ends deeply oversold (RSI <= 32, price < EMA200)
        fun buildDowntrendBars(startPx: Double, endDropStart: Int): BarSeries {
            val n = 240
            val o = ArrayList<Double>(n)
            val h = ArrayList<Double>(n)
            val l = ArrayList<Double>(n)
            val c = ArrayList<Double>(n)
            val v = ArrayList<Double>(n)
            var px = startPx
            for (i in 0 until n) {
                val ret = if (i >= endDropStart) -0.018 else -0.0015
                val open = px
                val close = (px * (1.0 + ret)).coerceAtLeast(5.0)
                o.add(open)
                h.add(maxOf(open, close) * 1.005)
                l.add(minOf(open, close) * 0.995)
                c.add(close)
                v.add(2_000_000.0)
                px = close
            }
            return BarSeries(o, h, l, c, v)
        }

        val spyDown = buildDowntrendBars(500.0, 225)
        val stockA = buildDowntrendBars(150.0, 220)
        val stockB = buildDowntrendBars(120.0, 220)
        val bars = mapOf("SPY" to spyDown, "AAPL" to stockA, "MSFT" to stockB)
        val snap = QuantEngine.demoSnap(bars.keys.toList())
        val cfg = AppConfig(demo = true, account = 1000.0, riskPct = 1.0)

        val output = QuantEngine.runEngine(bars, snap, cfg)
        assertTrue(
            "Expected RISK_OFF or CRISIS regime, got ${output.regime.label}",
            output.regime.label == RegimeLabel.RISK_OFF || output.regime.label == RegimeLabel.CRISIS
        )

        val aapl = output.rows.first { it.symbol == "AAPL" }
        assertEquals("MEAN_REV", aapl.strategyTag)
        assertEquals(SignalAction.BUY, aapl.action)
        assertTrue(aapl.reasons.any { it.contains("Mean reversion gated") })
        assertTrue(aapl.reasons.any { it.contains("20d(−2d) ROC") })
    }

    @Test
    fun alphaTraderHalfKellyAndWholeShareBrokerBracketEligibility() {
        val signal = SignalRow(
            symbol = "MSFT",
            action = SignalAction.BUY,
            score = 70.0,
            confidence = 0.85,
            price = 100.0,
            entry = 100.0,
            stop = 95.0,
            target = 110.0,
            rr = 2.0,
            atrPct = 2.0,
            relVolume = 1.4,
            size = 0.2,
            reasons = emptyList(),
            sub = emptyMap()
        )
        val regime = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.70,
            longMult = 1.15,
            shortMult = 0.75,
            note = ""
        )

        // Large account: 10,000 equity -> ~20+ shares -> whole shares retain >= 60% notional -> BRACKET SAFE
        val largeResult = PositionSizingEngine.calculate(signal, regime, accountSize = 10_000.0, riskPct = 1.0)
        assertTrue(largeResult.breakdown.halfKellyRiskPct > 0.0)
        assertEquals(largeResult.breakdown.quarterKellyRiskPct * 2.0, largeResult.breakdown.halfKellyRiskPct, 0.05)
        assertTrue(largeResult.wholeShares >= 10)
        assertTrue(largeResult.bracketEligibleWholeShares)
        assertEquals("BROKER BRACKET (Whole Shares)", largeResult.executionMode)

        // Very small account: 10.0 equity -> 0.02 shares -> 0 whole shares -> FRACTIONAL
        val smallResult = PositionSizingEngine.calculate(signal, regime, accountSize = 10.0, riskPct = 1.0)
        assertEquals(0, smallResult.wholeShares)
        assertFalse(smallResult.bracketEligibleWholeShares)
        assertEquals("FRACTIONAL (Client Managed)", smallResult.executionMode)
    }

    @Test
    fun alphaTraderTradeLifecyclePromotesTrailingStopAndTakesProfitAtOneR() {
        val cand = SignalRow(
            symbol = "AAPL",
            action = SignalAction.BUY,
            score = 65.0,
            confidence = 0.8,
            price = 100.0,
            entry = 100.0,
            stop = 95.0, // Risk is $5.00
            target = 115.0, // +3.0R
            rr = 3.0,
            atrPct = 2.5, // ATR is $2.50
            relVolume = 1.2,
            size = 0.02,
            reasons = emptyList(),
            sub = emptyMap()
        )

        val initial = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0, running = true)
        val opened = PaperBotEngine.openManual(initial, cand, nowMs = 1_000_000L, customQty = 4.0)
        val pos = opened.positions.first()
        assertEquals(4.0, pos.qty, 0.0001)
        assertEquals(100.0, pos.entry, 0.01)
        assertEquals(95.0, pos.stop, 0.01)

        // Price rises to 105.50 (+1.1R > 1.0R): Automated bot trims 50%, moves stop to breakeven (100.0), and begins trailing
        val tickedAt1R = PaperBotEngine.tick(
            opened,
            listOf(cand.copy(price = 105.50)),
            nowMs = 1_002_000L
        )
        val postTrimPos = tickedAt1R.positions.first()
        assertEquals(2.0, postTrimPos.qty, 0.0001)
        assertTrue(postTrimPos.partialTaken)
        assertTrue(postTrimPos.isTrailing)
        assertTrue("Stop should move to at least breakeven (100.0), got ${postTrimPos.stop}", postTrimPos.stop >= 100.0)
        assertTrue(tickedAt1R.log.any { it.type == "TRIM" && it.note.contains("+1.0R reached") })

        // Price climbs higher to $112.00 (highest price $112.00). ATR is $2.80, so 2x ATR trail stop is $106.40
        val climbed = PaperBotEngine.tick(
            tickedAt1R,
            listOf(cand.copy(price = 112.00, atrPct = 2.5)),
            nowMs = 1_003_000L
        )
        val trailingPos = climbed.positions.first()
        assertTrue("Trailing stop should ratchet higher, got ${trailingPos.stop}", trailingPos.stop > postTrimPos.stop)

        // Price pulls back and hits the ratcheted trailing stop
        val hitStop = PaperBotEngine.tick(
            climbed,
            listOf(cand.copy(price = trailingPos.stop - 0.5)),
            nowMs = 1_004_000L
        )
        assertTrue(hitStop.positions.isEmpty())
        assertEquals("CLOSE", hitStop.log.first().type)
        assertEquals("trailing stop", hitStop.log.first().note)
    }

    @Test
    fun alphaTraderTimeStopClosesStagnantPositionAfterFifteenSessions() {
        val cand = SignalRow(
            symbol = "XOM",
            action = SignalAction.BUY,
            score = 60.0,
            confidence = 0.75,
            price = 100.0,
            entry = 100.0,
            stop = 95.0,
            target = 110.0,
            rr = 2.0,
            atrPct = 2.0,
            relVolume = 1.1,
            size = 0.01,
            reasons = emptyList(),
            sub = emptyMap()
        )

        val initial = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0, running = true)
        var state = PaperBotEngine.openManual(initial, cand, nowMs = 1_000_000L, customQty = 2.0)

        // Price stays essentially flat ($100.20, rMultiple = +0.04 < 0.3R) for 15 sessions
        for (i in 1..15) {
            state = PaperBotEngine.tick(
                state,
                listOf(cand.copy(price = 100.20)),
                nowMs = 1_000_000L + i * 60_000L
            )
        }

        assertTrue("Stagnant trade should be closed by time stop", state.positions.isEmpty())
        assertEquals("CLOSE", state.log.first().type)
        assertTrue(state.log.first().note.contains("time stop"))
    }

    @Test
    fun advisorConfigSanitizesPlaceholdersAndBudgetEnforcesRateLimits() = runBlocking {
        val placeholderCfg = AdvisorConfig(
            baseUrl = "https://aihubmix.com/v1/",
            apiKey = "YOUR_LLM_API_KEY",
            model = "YOUR_LLM_MODEL",
            finnhubKey = "YOUR_FINNHUB_KEY"
        )
        assertFalse(placeholderCfg.enabled)
        assertFalse(placeholderCfg.vetoReady)
        assertEquals("https://aihubmix.com/v1", placeholderCfg.cleanBaseUrl)

        val journalOnlyCfg = AdvisorConfig(
            baseUrl = "https://aihubmix.com/v1",
            apiKey = "sk-test-1234",
            model = "gpt-4o-mini",
            finnhubKey = ""
        )
        assertTrue(journalOnlyCfg.enabled)
        assertFalse(journalOnlyCfg.vetoReady)

        // Token bucket: 2 calls/hr, 1.2s min gap
        var now = 1_000_000L
        val sleeps = mutableListOf<Long>()
        val budget = AdvisorBudget(
            perHour = 2,
            minGapSec = 1.2,
            clockMs = { now },
            sleepFn = { ms ->
                sleeps.add(ms)
                now += ms
            }
        )
        assertTrue(budget.allow())
        now += 200L // 1000ms short of 1200ms min gap
        assertTrue(budget.allow())
        assertEquals(listOf(1000L), sleeps)
        // Third call within same hour must be denied
        assertFalse(budget.allow())
        assertEquals(2, budget.callsUsedLastHour())
        // Advance past 1-hour window -> budget resets
        now += 3_600_001L
        assertTrue(budget.allow())
    }

    @Test
    fun advisorHeadlinesFilters18hWindowAndFailsSoftWithoutKey() = runBlocking {
        val nowMs = 1_763_900_000_000L
        val nowSec = nowMs / 1000L
        val fakeTransport = object : AdvisorHttpTransport {
            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse = AdvisorHttpResponse(200, "{}")

            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse {
                val body = """
                    [
                      {"datetime": ${nowSec - 7200}, "headline": "Fresh 2h guidance cut", "source": "Reuters", "url": "https://ex/1"},
                      {"datetime": ${nowSec - 3600}, "headline": "Newest 1h SEC probe", "source": "Bloomberg", "url": "https://ex/2"},
                      {"datetime": ${nowSec - 90000}, "headline": "Stale 25h old recap", "source": "Blog", "url": "https://ex/3"},
                      {"datetime": ${nowSec - 10800}, "headline": "Third 3h filing delay", "source": "WSJ", "url": "https://ex/4"},
                      {"datetime": ${nowSec - 14400}, "headline": "Fourth 4h trading halt", "source": "CNBC", "url": "https://ex/5"},
                      {"datetime": ${nowSec - 18000}, "headline": "Fifth 5h credit downgrade", "source": "FT", "url": "https://ex/6"}
                    ]
                """.trimIndent()
                return AdvisorHttpResponse(200, body)
            }
        }

        // Without FINNHUB_KEY -> returns empty list (no invented news)
        val noKeyAdvisor = AdvisorEngine(
            config = AdvisorConfig(baseUrl = "https://aihubmix.com/v1", apiKey = "sk-x", model = "m", finnhubKey = ""),
            transport = fakeTransport,
            clockMs = { nowMs },
            sleepFn = {}
        )
        assertTrue(noKeyAdvisor.headlines("NVDA").isEmpty())

        // With FINNHUB_KEY -> drops >18h stale item, sorts freshest first, caps at 4
        val advisor = AdvisorEngine(
            config = AdvisorConfig(baseUrl = "https://aihubmix.com/v1", apiKey = "sk-x", model = "m", finnhubKey = "fh-key"),
            transport = fakeTransport,
            clockMs = { nowMs },
            sleepFn = {}
        )
        val items = advisor.headlines("NVDA")
        assertEquals(4, items.size)
        assertEquals("Newest 1h SEC probe", items[0].headline)
        assertEquals("Fresh 2h guidance cut", items[1].headline)
        assertFalse(items.any { it.headline.contains("Stale 25h") })
    }

    @Test
    fun advisorNewsVetoEnforcesFailSafeConfidenceThresholdAndRetryFallback() = runBlocking {
        val nowMs = 1_763_900_000_000L
        val nowSec = nowMs / 1000L
        val postPayloads = mutableListOf<String>()
        var postCallIndex = 0

        val transport = object : AdvisorHttpTransport {
            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse {
                return AdvisorHttpResponse(
                    200,
                    """[{"datetime": ${nowSec - 1800}, "headline": "Company discloses SEC accounting restatement", "source": "Reuters", "url": ""}]"""
                )
            }

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse {
                postPayloads.add(jsonBody)
                return when (postCallIndex++) {
                    // Attempt 0 for SMCI: aggregator rejects response_format with HTTP 400
                    0 -> AdvisorHttpResponse(400, """{"error":"response_format not supported"}""")
                    // Attempt 1 for SMCI: succeeds with wrapped prose around JSON -> salvageJsonObject() must recover
                    1 -> AdvisorHttpResponse(
                        200,
                        """{"choices":[{"message":{"content":"Verdict: {\"action\":\"veto\",\"confidence\":0.88,\"category\":\"accounting\",\"why\":\"SEC restatement disclosed\"}"}}]}"""
                    )
                    // Low confidence (0.55 < 0.70) for AAPL -> must fail safe to NONE
                    2 -> AdvisorHttpResponse(
                        200,
                        """{"choices":[{"message":{"content":"{\"action\":\"veto\",\"confidence\":0.55,\"category\":\"rumor\",\"why\":\"unconfirmed\"}"}}]}"""
                    )
                    // Timeout / exception for MSFT -> must fail safe to NONE
                    else -> throw RuntimeException("socket timeout")
                }
            }
        }

        val advisor = AdvisorEngine(
            config = AdvisorConfig(
                baseUrl = "https://aihubmix.com/v1",
                apiKey = "sk-test",
                model = "gpt-4o-mini",
                finnhubKey = "fh-test",
                minGapSec = 0.0
            ),
            transport = transport,
            clockMs = { nowMs },
            sleepFn = {}
        )

        // 1. SMCI: retries without response_format, salvages JSON, returns VETO
        val veto = advisor.newsVeto("SMCI")
        assertTrue(veto.isVeto)
        assertEquals(AdvisorVetoAction.VETO, veto.action)
        assertEquals(0.88, veto.weight, 0.001)
        assertEquals("accounting", veto.category)
        assertEquals("veto", veto.llmTag)
        assertTrue(postPayloads[0].contains("response_format"))
        assertFalse(postPayloads[1].contains("response_format"))
        assertTrue(advisor.recentEvents().any { it.event == "LLM_VETO" && it.details["symbol"] == "SMCI" })

        // Cached within 30 minutes -> no extra HTTP call
        val cached = advisor.newsVeto("SMCI")
        assertEquals(veto, cached)
        assertEquals(2, postPayloads.size)

        // 2. AAPL: confidence 0.55 < 0.70 -> NONE (clear)
        val lowConf = advisor.newsVeto("AAPL")
        assertFalse(lowConf.isVeto)
        assertEquals(AdvisorVetoAction.NONE, lowConf.action)
        assertEquals("clear", lowConf.llmTag)

        // 3. MSFT: transport throws -> fail-safe NONE (never turns silence into sell/veto)
        val failed = advisor.newsVeto("MSFT")
        assertFalse(failed.isVeto)
        assertEquals(AdvisorVetoAction.NONE, failed.action)
        assertTrue(advisor.recentEvents().any { it.event == "LLM_FAIL" })
    }

    @Test
    fun advisorJournalAndPopulationAuditCompareClearVsOffVsVeto() = runBlocking {
        val nowMs = 1_763_950_000_000L
        val transport = object : AdvisorHttpTransport {
            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse =
                AdvisorHttpResponse(200, "[]")

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse {
                return AdvisorHttpResponse(
                    200,
                    """{"choices":[{"message":{"content":"{\"headline\":\"Momentum wins led by NVDA (+2.1R)\",\"observations\":[\"NVDA clear trades averaged +1.8R\"],\"concerns\":[\"Two off-regime stops clipped -0.9R\"]}"}}]}"""
                )
            }
        }
        val advisor = AdvisorEngine(
            config = AdvisorConfig(baseUrl = "https://aihubmix.com/v1", apiKey = "sk-test", model = "m", minGapSec = 0.0),
            transport = transport,
            clockMs = { nowMs },
            sleepFn = {}
        )

        val entries = listOf(
            TradeLogEntry(1_000L, "CLOSE", "NVDA", 1.0, 110.0, pnl = 10.0, note = "target", llmVerdict = "clear", regime = "RISK_ON", rMultiple = 2.0),
            TradeLogEntry(2_000L, "CLOSE", "MSFT", 1.0, 98.0, pnl = -2.0, note = "stop", llmVerdict = "clear", regime = "RISK_ON", rMultiple = -0.5),
            TradeLogEntry(3_000L, "CLOSE", "XOM", 1.0, 95.0, pnl = -5.0, note = "stop", llmVerdict = "off", regime = "RISK_OFF", rMultiple = -1.0),
            TradeLogEntry(4_000L, "VETO", "SMCI", 0.0, 42.0, pnl = null, note = "veto: accounting", llmVerdict = "veto", regime = "RISK_ON")
        )

        val report = advisor.journal(entries)
        assertNotNull(report)
        assertEquals(3, report!!.sampleSize)
        assertTrue(report.headline.contains("NVDA"))
        assertEquals(1, report.observations.size)
        assertEquals(1, report.concerns.size)
        assertTrue(report.formatText().contains("· NVDA"))
        assertTrue(report.formatText().contains("! Two off-regime"))

        val pop = AdvisorEngine.buildPopulationReport(entries)
        assertEquals(3, pop.totalClosedTrades)
        assertEquals(1, pop.vetoEventsLogged)
        val clearBucket = pop.buckets.first { it.llmTag == "clear" }
        assertEquals(2, clearBucket.tradeCount)
        assertEquals(50.0, clearBucket.winRatePct, 0.01)
        assertEquals(8.0, clearBucket.totalPnl, 0.01)
        assertEquals(0.75, clearBucket.avgRMultiple!!, 0.01)
        val offBucket = pop.buckets.first { it.llmTag == "off" }
        assertEquals(1, offBucket.tradeCount)
        assertEquals(0.0, offBucket.winRatePct, 0.01)
        assertEquals(-5.0, offBucket.totalPnl, 0.01)
    }

    @Test
    fun paperBotEngineEnforcesAdvisorVetoRuleTwoAndLogsVerdictRuleThree() {
        val vetoedBuy = SignalRow(
            symbol = "SMCI",
            action = SignalAction.BUY,
            score = 80.0,
            confidence = 0.90,
            price = 50.0,
            entry = 50.0,
            stop = 47.0,
            target = 58.0,
            rr = 2.67,
            atrPct = 3.0,
            relVolume = 2.0,
            size = 0.05,
            reasons = emptyList(),
            sub = emptyMap()
        )
        val clearBuy = vetoedBuy.copy(symbol = "NVDA", price = 100.0, entry = 100.0, stop = 95.0, target = 110.0)
        val verdicts = mapOf(
            "SMCI" to AdvisorVetoVerdict(
                symbol = "SMCI",
                action = AdvisorVetoAction.VETO,
                weight = 0.91,
                category = "accounting",
                why = "Auditor resignation",
                llmTag = "veto"
            ),
            "NVDA" to AdvisorVetoVerdict.none("NVDA", advisorEnabled = true)
        )

        val start = BotState(equity = 1000.0, cash = 1000.0, initialEquity = 1000.0, running = true)
        val afterOpen = PaperBotEngine.tick(
            state = start,
            rows = listOf(vetoedBuy, clearBuy),
            nowMs = 1_763_900_000_000L,
            advisorVerdicts = verdicts,
            advisorEnabled = true,
            regimeLabel = "RISK_ON"
        )

        // Rule #2: SMCI must be skipped, NVDA opened instead
        assertEquals(1, afterOpen.positions.size)
        assertEquals("NVDA", afterOpen.positions.first().symbol)
        assertEquals("clear", afterOpen.positions.first().llmVerdict)
        assertEquals("RISK_ON", afterOpen.positions.first().entryRegime)

        // Rule #3: Both VETO skip and OPEN entry are logged with llm tags
        assertTrue(afterOpen.log.any { it.type == "VETO" && it.symbol == "SMCI" && it.llmVerdict == "veto" })
        val openLog = afterOpen.log.first { it.type == "OPEN" && it.symbol == "NVDA" }
        assertEquals("clear", openLog.llmVerdict)
        assertEquals("RISK_ON", openLog.regime)

        // Target hit carries llmVerdict, regime, and rMultiple onto CLOSE entry
        val afterClose = PaperBotEngine.tick(
            state = afterOpen,
            rows = listOf(clearBuy.copy(price = 110.5)),
            nowMs = 1_763_900_060_000L,
            advisorVerdicts = verdicts,
            advisorEnabled = true,
            regimeLabel = "RISK_ON"
        )
        assertTrue(afterClose.positions.isEmpty())
        val closeLog = afterClose.log.first { it.type == "CLOSE" }
        assertEquals("clear", closeLog.llmVerdict)
        assertEquals("RISK_ON", closeLog.regime)
        assertEquals(2.0, closeLog.rMultiple!!, 0.01)
    }

    @Test
    fun trackedSymbolDashboardEngineMapsRegimeStatusAiNewsVerdictsAndFilters() {
        val baseRow = SignalRow(
            symbol = "NVDA",
            action = SignalAction.BUY,
            score = 78.0,
            confidence = 0.88,
            price = 140.0,
            entry = 140.0,
            stop = 132.0,
            target = 160.0,
            rr = 2.5,
            atrPct = 2.6,
            relVolume = 1.7,
            size = 0.05,
            reasons = listOf("20d momentum breakout"),
            sub = emptyMap(),
            conviction = 1.65,
            keyCatalyst = "Hyperscaler capex raises GPU demand",
            newsDirection = 0.85,
            newsMateriality = 0.80,
            newsItems = listOf(
                ScoredNewsItem("Hyperscaler capex raises GPU demand", 0.85, 0.80, "Direct revenue catalyst", 0.92)
            )
        )
        val vetoedRow = baseRow.copy(
            symbol = "SMCI",
            conviction = 1.40,
            keyCatalyst = "Auditor resignation",
            newsDirection = -0.90,
            newsMateriality = 0.85,
            newsItems = emptyList()
        )
        val unscoredRow = baseRow.copy(
            symbol = "MSFT",
            conviction = 0.90,
            keyCatalyst = "",
            newsDirection = 0.0,
            newsMateriality = 0.0,
            newsItems = emptyList()
        )

        val riskOnRegime = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.72,
            longMult = 1.15,
            shortMult = 0.75,
            note = "breadth 72%",
            trendScore = 0.85,
            volPct = 0.30
        )

        val verdicts = mapOf(
            "SMCI" to AdvisorVetoVerdict(
                symbol = "SMCI",
                action = AdvisorVetoAction.VETO,
                weight = 0.92,
                category = "accounting",
                why = "SEC filing delayed",
                headline = "Company delays 10-K filing",
                llmTag = "veto"
            ),
            "NVDA" to AdvisorVetoVerdict.none("NVDA", advisorEnabled = true)
        )

        val mapped = TrackedSymbolDashboardEngine.buildRows(
            rows = listOf(baseRow, vetoedRow, unscoredRow),
            regime = riskOnRegime,
            watchlist = listOf("NVDA", "SMCI"),
            heldSymbols = setOf("NVDA"),
            advisorVerdicts = verdicts,
            advisorEnabled = true
        )

        val nvda = mapped.first { it.signal.symbol == "NVDA" }
        assertTrue(nvda.isHeld)
        assertTrue(nvda.isWatchlisted)
        assertEquals(RegimeAlignmentStatus.ALIGNED, nvda.regimeAlignment)
        assertEquals(AiVerdictTone.BULLISH, nvda.aiVerdictTone)
        assertEquals(68, nvda.aiNewsScore)
        assertEquals("clear", nvda.advisorTag)

        val smci = mapped.first { it.signal.symbol == "SMCI" }
        assertEquals(AiVerdictTone.VETOED, smci.aiVerdictTone)
        assertEquals("veto", smci.advisorTag)
        assertTrue(smci.verdictSummary.contains("accounting"))

        // Tracked filter includes only held/watchlisted (NVDA held first, then SMCI)
        val trackedOnly = TrackedSymbolDashboardEngine.selectForFilter(mapped, DashboardSymbolFilter.TRACKED)
        assertEquals(listOf("NVDA", "SMCI"), trackedOnly.map { it.signal.symbol })

        // Vetoed filter surfaces SMCI
        val vetoedOnly = TrackedSymbolDashboardEngine.selectForFilter(mapped, DashboardSymbolFilter.VETOED)
        assertEquals(listOf("SMCI"), vetoedOnly.map { it.signal.symbol })

        // Summary counts match
        val summary = TrackedSymbolDashboardEngine.summarize(trackedOnly, riskOnRegime)
        assertEquals(2, summary.totalTracked)
        assertEquals(1, summary.bullishAiCount)
        assertEquals(1, summary.vetoedCount)
        assertEquals(2, summary.scoredCount)
    }

    @Test
    fun thresholdAlertEngineEvaluatesRegimeShiftsPanicGuardAndAiScoreChanges() {
        val baseRow = SignalRow(
            symbol = "NVDA",
            action = SignalAction.BUY,
            score = 72.0,
            confidence = 0.85,
            price = 120.0,
            entry = 120.0,
            stop = 113.6,
            target = 132.8,
            rr = 2.0,
            atrPct = 2.6,
            relVolume = 1.4,
            size = 1.0,
            reasons = listOf("Momentum"),
            sub = emptyMap(),
            conviction = 1.2,
            keyCatalyst = "Baseline headline",
            newsDirection = 0.20,
            newsMateriality = 0.50,
            newsItems = listOf(ScoredNewsItem("Baseline headline", 0.20, 0.50, "Mild positive", 0.90))
        )
        val otherRow = baseRow.copy(
            symbol = "AAPL",
            keyCatalyst = "Supply chain update",
            newsDirection = 0.90,
            newsMateriality = 0.85,
            newsItems = listOf(ScoredNewsItem("Supply chain update", 0.90, 0.85, "Strong positive", 0.95))
        )

        val settings = ThresholdAlertSettings(
            regimeAlertsEnabled = true,
            watchedRegimes = setOf(RegimeLabel.RISK_OFF, RegimeLabel.CRISIS),
            alertOnPanicGuard = true,
            aiNewsAlertsEnabled = true,
            aiNewsScoreDeltaThreshold = 25,
            aiNewsMinScoreAbsThreshold = 60,
            alertOnAdvisorVeto = true,
            aiNewsWatchlistOnly = true
        )

        // 1. Unwatched RISK_ON regime does not fire regime shift; AAPL excluded by watchlistOnly = true
        val riskOn = MarketRegime(
            label = RegimeLabel.RISK_ON,
            breadth = 0.72,
            longMult = 1.1,
            shortMult = 0.8,
            note = "risk on"
        )
        val pass1 = ThresholdAlertEngine.evaluate(
            settings = settings,
            regime = riskOn,
            rows = listOf(baseRow, otherRow),
            watchlist = listOf("NVDA"),
            monitorState = ThresholdAlertMonitorState(),
            nowMs = 1_000L
        )
        assertTrue(pass1.events.isEmpty())
        assertEquals(10, pass1.nextState.lastAiScoresBySymbol["NVDA"])

        // 2. Shift to watched RISK_OFF + Panic Guard active + NVDA AI score surges from +10 to +72 (delta +62 >= 25)
        val riskOffPanic = MarketRegime(
            label = RegimeLabel.RISK_OFF,
            breadth = 0.28,
            longMult = 0.65,
            shortMult = 1.15,
            note = "risk off",
            panicGuardActive = true,
            panicDrawdownPct = 9.4
        )
        val surgedNvda = baseRow.copy(
            keyCatalyst = "Major AI datacenter guidance raise",
            newsDirection = 0.90,
            newsMateriality = 0.80
        )
        val vetoVerdict = AdvisorVetoVerdict(
            symbol = "NVDA",
            action = AdvisorVetoAction.VETO,
            weight = 0.88,
            category = "regulatory",
            why = "Export control investigation",
            headline = "Regulator opens export probe",
            checkedAtMs = 2_000L,
            llmTag = "veto"
        )
        val pass2 = ThresholdAlertEngine.evaluate(
            settings = settings,
            regime = riskOffPanic,
            rows = listOf(surgedNvda, otherRow),
            watchlist = listOf("NVDA"),
            advisorVerdicts = mapOf("NVDA" to vetoVerdict),
            monitorState = pass1.nextState,
            nowMs = 2_000L
        )
        val kinds2 = pass2.events.map { it.kind }.toSet()
        assertTrue(kinds2.contains(ThresholdAlertKind.REGIME_SHIFT))
        assertTrue(kinds2.contains(ThresholdAlertKind.PANIC_GUARD))
        assertTrue(kinds2.contains(ThresholdAlertKind.AI_SCORE_DELTA))
        assertTrue(kinds2.contains(ThresholdAlertKind.ADVISOR_VETO))
        assertTrue(pass2.events.none { it.symbol == "AAPL" })

        // 3. Re-evaluating identical state deduplicates regime, panic guard, AI delta, and advisor veto
        val pass3 = ThresholdAlertEngine.evaluate(
            settings = settings,
            regime = riskOffPanic,
            rows = listOf(surgedNvda, otherRow),
            watchlist = listOf("NVDA"),
            advisorVerdicts = mapOf("NVDA" to vetoVerdict),
            monitorState = pass2.nextState,
            nowMs = 3_000L
        )
        assertTrue(pass3.events.isEmpty())

        // 4. Disabling watchlistOnly lets AAPL trigger AI_SCORE_EXTREME (|77| >= 60)
        val pass4 = ThresholdAlertEngine.evaluate(
            settings = settings.copy(aiNewsWatchlistOnly = false),
            regime = riskOffPanic,
            rows = listOf(surgedNvda, otherRow),
            watchlist = listOf("NVDA"),
            advisorVerdicts = mapOf("NVDA" to vetoVerdict),
            monitorState = pass3.nextState,
            nowMs = 4_000L
        )
        assertEquals(1, pass4.events.size)
        assertEquals(ThresholdAlertKind.AI_SCORE_EXTREME, pass4.events.first().kind)
        assertEquals("AAPL", pass4.events.first().symbol)
    }

    @Test
    fun finnhubCatalystEngineScoresAnnotatesAndFiltersFinancialNews() {
        val nowMs = 1_764_000_000_000L
        val rawArticles = listOf(
            RawFinnhubArticle(
                id = "FH_1",
                symbol = "NVDA",
                headline = "NVIDIA raises full-year guidance on record AI datacenter demand",
                summary = "Quarterly revenue beats consensus with stronger than expected margins.",
                source = "Reuters",
                category = "company",
                url = "https://example.com/nvda",
                publishedAtMs = nowMs - 3_600_000L // 1h ago
            ),
            RawFinnhubArticle(
                id = "FH_2",
                symbol = "SMCI",
                headline = "SEC probe launched following delayed accounting filing",
                summary = "Regulators review internal controls after auditor resignation.",
                source = "Bloomberg",
                category = "company",
                url = "https://example.com/smci",
                publishedAtMs = nowMs - 7_200_000L // 2h ago
            ),
            RawFinnhubArticle(
                id = "FH_3",
                symbol = "MACRO",
                headline = "Treasury yields steady ahead of FOMC minutes release",
                summary = "Bond markets trade in a narrow range.",
                source = "CNBC",
                category = "general",
                url = "https://example.com/macro",
                publishedAtMs = nowMs - 18 * 3_600_000L // 18h ago (half-life)
            )
        )

        // Verify 18h decay half-life
        assertEquals(0.5, FinnhubCatalystEngine.computeDecay(nowMs - 18 * 3_600_000L, nowMs), 0.01)

        // Annotate first article with AI Gemini assessment, others fall back to deterministic model
        val annotated = FinnhubCatalystEngine.annotateWithAiScores(
            rawArticles = rawArticles,
            aiAssessmentsByIndex = mapOf(
                0 to AiCatalystAssessment(
                    direction = 0.90,
                    materiality = 0.85,
                    confidence = 0.92,
                    catalystTag = "GUIDANCE",
                    rationale = "Material revenue guidance raise driven by hyperscaler orders."
                )
            ),
            scoreSource = CatalystScoreSource.AI_GEMINI,
            nowMs = nowMs
        )

        assertEquals(3, annotated.size)
        val nvda = annotated.first { it.symbol == "NVDA" }
        val smci = annotated.first { it.symbol == "SMCI" }
        val macro = annotated.first { it.symbol == "MACRO" }

        assertEquals(CatalystScoreSource.AI_GEMINI, nvda.scoreSource)
        assertTrue(nvda.catalystScore >= 60)
        assertEquals("GUIDANCE", nvda.catalystTag)

        assertEquals(CatalystScoreSource.FINNHUB_MODEL, smci.scoreSource)
        assertTrue(smci.catalystScore <= -25)
        assertEquals("REGULATORY", smci.catalystTag)

        assertEquals("MACRO", macro.catalystTag)

        // Filtering checks
        val bullish = FinnhubCatalystEngine.filterArticles(annotated, NewsArticleFilter.BULLISH, listOf("NVDA"))
        assertEquals(listOf("NVDA"), bullish.map { it.symbol })

        val bearish = FinnhubCatalystEngine.filterArticles(annotated, NewsArticleFilter.BEARISH, listOf("NVDA"))
        assertEquals(listOf("SMCI"), bearish.map { it.symbol })

        val watched = FinnhubCatalystEngine.filterArticles(annotated, NewsArticleFilter.WATCHLIST, listOf("NVDA"))
        assertEquals(listOf("NVDA"), watched.map { it.symbol })

        val searched = FinnhubCatalystEngine.filterArticles(annotated, NewsArticleFilter.ALL, emptyList(), "reuters")
        assertEquals(listOf("NVDA"), searched.map { it.symbol })
    }

    @Test
    fun modelRadarEngineClassifiesRanksFiltersAndParsesProviders() {
        // 1. OpenRouter authoritative declared pricing (free + paid + non-chat embedding)
        val openRouterJson = """
            {
              "data": [
                {
                  "id": "z-ai/glm-4.5-air:free",
                  "name": "GLM 4.5 Air (free)",
                  "context_length": 131072,
                  "pricing": {"prompt": "0", "completion": "0"},
                  "created": 1750000000,
                  "architecture": {"modality": "text->text"}
                },
                {
                  "id": "anthropic/claude-sonnet-4",
                  "name": "Claude Sonnet 4",
                  "context_length": 200000,
                  "pricing": {"prompt": "0.000003", "completion": "0.000015"},
                  "created": 1750000000,
                  "architecture": {"modality": "text+image->text"}
                },
                {
                  "id": "openai/text-embedding-3-large",
                  "name": "Embedding Large",
                  "context_length": 8192,
                  "pricing": {"prompt": "0.00000013", "completion": "0"},
                  "architecture": {"modality": "text->embeddings"}
                }
              ]
            }
        """.trimIndent()
        val orModels = ModelRadarEngine.parseOpenRouter(openRouterJson)
        assertEquals(3, orModels.size)
        val freeGlm = orModels.first { it.id == "z-ai/glm-4.5-air:free" }
        assertEquals(ModelTier.FREE, freeGlm.tier)
        assertEquals(ModelTierBasis.DECLARED, freeGlm.basis)
        assertEquals(0.0, freeGlm.priceIn!!, 0.0001)
        assertEquals("$0", ModelRadarEngine.formatPrice(freeGlm.priceIn))

        val paidClaude = orModels.first { it.id == "anthropic/claude-sonnet-4" }
        assertEquals(ModelTier.PAID, paidClaude.tier)
        assertEquals(ModelTierBasis.DECLARED, paidClaude.basis)
        assertEquals(3.0, paidClaude.priceIn!!, 0.01)
        assertEquals(15.0, paidClaude.priceOut!!, 0.01)

        val embed = orModels.first { it.id == "openai/text-embedding-3-large" }
        assertFalse(ModelRadarEngine.isChat(embed))

        // 2. AIHubMix without pricing -> free inferred from marker, others stay unknown honestly
        val aihubJson = """
            {
              "data": [
                {"id": "glm-5.3-free", "context_window": 128000},
                {"id": "gpt-4o-mini"}
              ]
            }
        """.trimIndent()
        val ahModels = ModelRadarEngine.parseGeneric(AiProviderId.AIHUBMIX, aihubJson)
        val ahFree = ahModels.first { it.id == "glm-5.3-free" }
        assertEquals(ModelTier.FREE, ahFree.tier)
        assertEquals(ModelTierBasis.INFERRED, ahFree.basis)
        assertEquals(null, ahFree.priceIn)
        assertEquals("?", ModelRadarEngine.formatPrice(ahFree.priceIn))

        val ahUnknown = ahModels.first { it.id == "gpt-4o-mini" }
        assertEquals(ModelTier.UNKNOWN, ahUnknown.tier)
        assertEquals(ModelTierBasis.UNKNOWN, ahUnknown.basis)

        // 3. DeepSeek without pricing -> provider default PAID with honest null prices
        val deepseekJson = """{"data": [{"id": "deepseek-chat"}, {"id": "deepseek-reasoner"}]}"""
        val dsModels = ModelRadarEngine.parseGeneric(AiProviderId.DEEPSEEK, deepseekJson)
        assertEquals(2, dsModels.size)
        assertTrue(dsModels.all { it.tier == ModelTier.PAID && it.basis == ModelTierBasis.PROVIDER && it.priceIn == null })

        // 4. Combined filtering respects enabled provider ON/OFF toggles and tier filter
        val all = orModels + ahModels + dsModels + ModelRadarEngine.builtInGeminiCatalog()
        val onlyOrAndDs = ModelRadarEngine.filterCatalog(
            models = all,
            enabledProviders = setOf(AiProviderId.OPENROUTER, AiProviderId.DEEPSEEK),
            providerFilter = null,
            tierFilter = ModelTierFilter.PAID
        )
        assertTrue(onlyOrAndDs.none { it.provider == AiProviderId.AIHUBMIX || it.provider == AiProviderId.GEMINI })
        assertTrue(onlyOrAndDs.all { it.tier == ModelTier.PAID && ModelRadarEngine.isChat(it) })

        // 5. pickBest selects declared free chat model over inferred free, and respects provider filter
        val bestFree = ModelRadarEngine.pickBest(
            models = all,
            tier = ModelTierFilter.FREE,
            enabledProviders = setOf(AiProviderId.OPENROUTER, AiProviderId.AIHUBMIX)
        )
        assertEquals("z-ai/glm-4.5-air:free", bestFree?.id)

        val bestDeepSeekPaid = ModelRadarEngine.pickBest(
            models = all,
            tier = ModelTierFilter.PAID,
            provider = AiProviderId.DEEPSEEK,
            enabledProviders = setOf(AiProviderId.DEEPSEEK)
        )
        assertEquals(AiProviderId.DEEPSEEK, bestDeepSeekPaid?.provider)

        // 6. AdvisorEngine runtime routing switch turns AI ON and OFF cleanly
        val advisor = AdvisorEngine(config = AdvisorConfig())
        assertFalse(advisor.enabled)
        advisor.setRuntimeLlmRouting(
            baseUrl = AiProviderId.OPENROUTER.baseUrl,
            apiKey = "sk-or-test-key",
            model = "z-ai/glm-4.5-air:free",
            isOpenRouter = true,
            forceDisabled = false
        )
        assertTrue(advisor.enabled)
        assertEquals("z-ai/glm-4.5-air:free", advisor.effectiveModel)

        advisor.setRuntimeLlmRouting(
            baseUrl = AiProviderId.OPENROUTER.baseUrl,
            apiKey = "sk-or-test-key",
            model = "z-ai/glm-4.5-air:free",
            isOpenRouter = true,
            forceDisabled = true
        )
        assertFalse(advisor.enabled)
    }

    @Test
    fun quantSignalScannerEngineParsesFiltersAndComputesTelemetry() {
        val parsed = QuantSignalScannerEngine.parseTickerInput(" nvda, aapl; msft brk.b 1234 nvda ")
        assertEquals(listOf("NVDA", "AAPL", "MSFT", "BRK.B"), parsed)
        assertTrue(QuantScannerPreset.MEGA_TECH.symbols.contains("NVDA"))

        val buyRow = SignalRow(
            symbol = "NVDA",
            action = SignalAction.BUY,
            score = 74.0,
            confidence = 0.85,
            price = 135.0,
            entry = 135.0,
            stop = 128.0,
            target = 156.0,
            rr = 3.0,
            atrPct = 2.6,
            relVolume = 1.4,
            size = 0.15,
            reasons = listOf("Strong 20d momentum"),
            sub = mapOf("trend" to 0.85, "momentum" to 0.72, "rs" to 0.60, "volume" to 0.45, "quality" to 0.12),
            conviction = 1.45
        )
        val sellRow = buyRow.copy(
            symbol = "AMD",
            action = SignalAction.SELL,
            rr = 2.0,
            conviction = 0.85
        )
        val holdRow = buyRow.copy(
            symbol = "AAPL",
            action = SignalAction.HOLD,
            rr = 1.2,
            conviction = 0.30
        )

        val scopedSnap = QuantSignalScannerEngine.buildSnapshot(
            allRows = listOf(buyRow, sellRow, holdRow),
            activeSymbols = listOf("NVDA", "AMD"),
            actionFilter = null,
            minConviction = 0.60
        )
        assertEquals(2, scopedSnap.scopedTotalCount)
        assertEquals(1, scopedSnap.buyCount)
        assertEquals(1, scopedSnap.sellCount)
        assertEquals(0, scopedSnap.holdCount)
        assertEquals(2.5, scopedSnap.avgNetRr, 0.01)
        assertEquals("NVDA", scopedSnap.topConvictionSymbol)
        assertEquals(listOf("NVDA", "AMD"), scopedSnap.visibleSignals.map { it.symbol })

        val highConvBuyOnly = QuantSignalScannerEngine.buildSnapshot(
            allRows = listOf(buyRow, sellRow, holdRow),
            activeSymbols = emptyList(),
            actionFilter = SignalAction.BUY,
            minConviction = 1.20
        )
        assertEquals(listOf("NVDA"), highConvBuyOnly.visibleSignals.map { it.symbol })

        val factors = QuantSignalScannerEngine.topFactors(buyRow, limit = 3)
        assertEquals(listOf("TRD", "MOM", "RS"), factors.map { it.first })
        assertEquals(0.85, factors.first().second, 0.001)
    }
}




