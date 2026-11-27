package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MarketScanDatabase
import com.example.data.local.PaperHoldingEntity
import com.example.data.repository.PaperHoldingsRepository
import com.example.data.service.WatchlistQuoteSnippet
import com.example.domain.engine.PaperHoldingsPnlEngine
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaperHoldingsModuleTest {

    private lateinit var database: MarketScanDatabase
    private lateinit var repository: PaperHoldingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarketScanDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = PaperHoldingsRepository(
            dao = database.paperHoldingDao(),
            dailyPnlDao = database.dailyPnlSnapshotDao(),
            clockMs = { 1_795_608_000_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomRepository_persistsMergesUpdatesAndDeletesHoldings() = runBlocking {
        // Insert first holding
        val firstRes = repository.addOrMergeHolding(" nvda ", shares = 10.0, purchasePrice = 120.0, notes = "Lot 1")
        assertTrue(firstRes.isSuccess)

        var list = repository.holdingsFlow.first()
        assertEquals(1, list.size)
        assertEquals("NVDA", list.first().symbol)
        assertEquals(10.0, list.first().shares, 1e-6)
        assertEquals(120.0, list.first().purchasePrice, 1e-6)

        // Adding same symbol merges via weighted-average purchase price: (10*120 + 10*140)/20 = 130
        val mergeRes = repository.addOrMergeHolding("\$NVDA", shares = 10.0, purchasePrice = 140.0, notes = "Added dip")
        assertTrue(mergeRes.isSuccess)
        list = repository.holdingsFlow.first()
        assertEquals(1, list.size)
        assertEquals(20.0, list.first().shares, 1e-6)
        assertEquals(130.0, list.first().purchasePrice, 1e-6)

        // Update holding dummy buy price directly
        val id = list.first().id
        val updateRes = repository.updateHolding(id = id, shares = 15.0, purchasePrice = 125.0, notes = "Edited")
        assertTrue(updateRes.isSuccess)
        list = repository.holdingsFlow.first()
        assertEquals(15.0, list.first().shares, 1e-6)
        assertEquals(125.0, list.first().purchasePrice, 1e-6)

        // Delete removes from Room
        repository.deleteHolding(id)
        list = repository.holdingsFlow.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun dailyPnl30DayEngine_persistsToRoomAndComputesSummaryKpis() = runBlocking {
        repository.addOrMergeHolding("NVDA", shares = 10.0, purchasePrice = 100.0)
        val holdings = repository.holdingsFlow.first()

        // 31 daily closes: starts at 100.0, alternates +2 and -1, ending at 115.0 (+150 total unrealized)
        val closes = MutableList(31) { 100.0 }
        for (i in 1..30) {
            closes[i] = closes[i - 1] + if (i % 2 == 1) 2.0 else -1.0
        }

        val snapshots = com.example.domain.engine.PaperDailyPnlEngine.build30DaySnapshots(
            holdings = holdings,
            closesBySymbol = mapOf("NVDA" to closes),
            liveQuotesBySymbol = emptyMap(),
            windowDays = 30,
            nowMs = 1_795_608_000_000L
        )
        assertEquals(30, snapshots.size)

        // Persist into Room daily_pnl_snapshots and read back via reactive Flow
        repository.replaceDailyPnlWindow(snapshots)
        val stored = repository.recentDailyPnlFlow.first()
        assertEquals(30, stored.size)
        // Verify strictly chronological ascending order
        for (i in 1 until stored.size) {
            assertTrue(stored[i].epochDayMs > stored[i - 1].epochDayMs)
        }

        val analytics = com.example.domain.engine.PaperDailyPnlEngine.summarize(stored)
        // 15 up days (+$20 each = +$300) and 15 down days (-$10 each = -$150) => net +$150
        assertEquals(150.0, analytics.net30dPnl, 1e-6)
        assertEquals(150.0, analytics.latestCumulativePnl, 1e-6)
        assertEquals(20.0, analytics.bestDay!!.dailyPnl, 1e-6)
        assertEquals(-10.0, analytics.worstDay!!.dailyPnl, 1e-6)
        assertEquals(15, analytics.greenDays)
        assertEquals(15, analytics.redDays)
        assertEquals(50.0, analytics.greenDayRatePct, 1e-6)
    }

    @Test
    fun pnlEngine_marksHoldingsAgainstLiveQuotesAndScannerFallback() {
        val holdings = listOf(
            PaperHoldingEntity(id = 1, symbol = "NVDA", shares = 10.0, purchasePrice = 120.0),
            PaperHoldingEntity(id = 2, symbol = "AAPL", shares = 5.0, purchasePrice = 230.0),
            PaperHoldingEntity(id = 3, symbol = "MSFT", shares = 2.0, purchasePrice = 400.0)
        )

        val liveQuotes = mapOf(
            "NVDA" to WatchlistQuoteSnippet(
                symbol = "NVDA",
                currentPrice = 140.0,
                changeDollar = 4.0,
                changePct = 2.94,
                dayHigh = 142.0,
                dayLow = 136.0,
                dayOpen = 137.0,
                prevClose = 136.0,
                updatedAtMs = 1_795_608_000_000L,
                sourceLabel = "FINNHUB LIVE",
                isFinnhubLive = true
            )
        )

        val scannerRows = listOf(
            SignalRow(
                symbol = "AAPL",
                action = SignalAction.HOLD,
                score = 0.0,
                confidence = 0.5,
                price = 220.0,
                entry = 220.0,
                stop = 210.0,
                target = 240.0,
                rr = 2.0,
                atrPct = 1.5,
                relVolume = 1.0,
                size = 0.0,
                reasons = emptyList(),
                sub = emptyMap(),
                series = listOf(222.0, 220.0)
            )
        )

        val summary = PaperHoldingsPnlEngine.evaluate(
            holdings = holdings,
            quotesBySymbol = liveQuotes,
            scannerRows = scannerRows
        )

        // NVDA: cost 1200, live 1400 => +200 (+16.6667%), dayPnl = +40
        // AAPL (scanner fallback): cost 1150, live 1100 => -50 (-4.3478%)
        // MSFT (pending): cost 800, effective 800 => 0
        assertEquals(3150.0, summary.totalCostBasis, 1e-6)
        assertEquals(3300.0, summary.totalMarketValue, 1e-6)
        assertEquals(150.0, summary.totalUnrealizedPnl, 1e-6)
        assertEquals((150.0 / 3150.0) * 100.0, summary.totalUnrealizedPnlPct, 1e-4)
        assertEquals(1, summary.winnersCount)
        assertEquals(1, summary.losersCount)
        assertEquals(2, summary.livePricedCount)

        val nvda = summary.valuations.first { it.holding.symbol == "NVDA" }
        assertTrue(nvda.isLivePriced)
        assertEquals("FINNHUB LIVE", nvda.priceSource)
        assertEquals(200.0, nvda.unrealizedPnl, 1e-6)
        assertEquals(40.0, nvda.dayPnl, 1e-6)

        val aapl = summary.valuations.first { it.holding.symbol == "AAPL" }
        assertTrue(aapl.isLivePriced)
        assertEquals("SCANNER LIVE", aapl.priceSource)
        assertEquals(-50.0, aapl.unrealizedPnl, 1e-6)

        val msft = summary.valuations.first { it.holding.symbol == "MSFT" }
        assertFalse(msft.isLivePriced)
        assertEquals("PENDING", msft.priceSource)
    }
}
