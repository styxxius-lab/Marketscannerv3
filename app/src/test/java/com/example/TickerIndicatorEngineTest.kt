package com.example

import com.example.domain.engine.IndicatorMode
import com.example.domain.engine.IndicatorPolarity
import com.example.domain.engine.TickerDirectionFilter
import com.example.domain.engine.TickerIndicatorEngine
import com.example.domain.engine.TickerSortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerIndicatorEngineTest {

    @Test
    fun rsi14IdentifiesOverboughtAndOversoldExtremes() {
        val risingCloses = List(40) { i -> 100.0 + i * 2.0 }
        val overbought = TickerIndicatorEngine.evaluateSymbol(
            symbol = "NVDA",
            historicalCloses = risingCloses,
            currentPrice = 182.0,
            previousClose = 178.0,
            sourceLabel = "YAHOO LIVE"
        )
        assertTrue("Expected RSI >= 70 on steady rally, got ${overbought.rsi14}", overbought.rsi14 >= 70.0)
        assertEquals("OVERBOUGHT", overbought.rsiLabel)
        assertEquals(IndicatorPolarity.STRONG_SELL, overbought.rsiPolarity)
        assertTrue(TickerIndicatorEngine.isExtreme(overbought, IndicatorMode.RSI_14))

        val fallingCloses = List(40) { i -> 200.0 - i * 2.5 }
        val oversold = TickerIndicatorEngine.evaluateSymbol(
            symbol = "AMD",
            historicalCloses = fallingCloses,
            currentPrice = 98.0,
            previousClose = 102.5,
            sourceLabel = "FINNHUB LIVE"
        )
        assertTrue("Expected RSI <= 30 on steady selloff, got ${oversold.rsi14}", oversold.rsi14 <= 30.0)
        assertEquals("OVERSOLD", oversold.rsiLabel)
        assertEquals(IndicatorPolarity.STRONG_BUY, oversold.rsiPolarity)
        assertTrue(TickerIndicatorEngine.isExtreme(oversold, IndicatorMode.RSI_14))
    }

    @Test
    fun emaCrossoverDetectsBullishAndBearishTrends() {
        // Long flat base followed by a strong uptrend -> EMA9 > EMA21 and SMA20 > SMA50
        val uptrend = List(40) { 100.0 } + List(20) { i -> 100.0 + (i + 1) * 1.5 }
        val bullQuote = TickerIndicatorEngine.evaluateSymbol(
            symbol = "AAPL",
            historicalCloses = uptrend,
            currentPrice = 132.0,
            previousClose = 130.0,
            sourceLabel = "ALPACA LIVE"
        )
        assertTrue(bullQuote.ema9 > bullQuote.ema21)
        assertTrue(bullQuote.sma20 > bullQuote.sma50)
        assertTrue(bullQuote.maSpreadPct > 1.0)
        assertTrue(bullQuote.maCrossPolarity.weight > 0)

        // Long flat base followed by sharp downtrend -> EMA9 < EMA21 and SMA20 < SMA50
        val downtrend = List(40) { 150.0 } + List(20) { i -> 150.0 - (i + 1) * 2.0 }
        val bearQuote = TickerIndicatorEngine.evaluateSymbol(
            symbol = "TSLA",
            historicalCloses = downtrend,
            currentPrice = 108.0,
            previousClose = 110.0,
            sourceLabel = "YAHOO LIVE"
        )
        assertTrue(bearQuote.ema9 < bearQuote.ema21)
        assertTrue(bearQuote.sma20 < bearQuote.sma50)
        assertTrue(bearQuote.maSpreadPct < -1.0)
        assertTrue(bearQuote.maCrossPolarity.weight < 0)
    }

    @Test
    fun filterSortSummaryAndSymbolParsingWorkConsistently() {
        val parsed = TickerIndicatorEngine.parseSymbols("nvda, aapl ; msft  NVDA  bad$!")
        assertEquals(listOf("NVDA", "AAPL", "MSFT"), parsed)

        val bull = TickerIndicatorEngine.evaluateSymbol(
            symbol = "MSFT",
            historicalCloses = List(30) { 100.0 } + List(20) { i -> 100.0 + i * 1.2 },
            currentPrice = 126.0,
            previousClose = 123.0,
            sourceLabel = "YAHOO LIVE"
        )
        val bear = TickerIndicatorEngine.evaluateSymbol(
            symbol = "INTC",
            historicalCloses = List(30) { 80.0 } + List(20) { i -> 80.0 - i * 1.2 },
            currentPrice = 54.0,
            previousClose = 56.0,
            sourceLabel = "YAHOO LIVE"
        )
        val quotes = listOf(bull, bear)

        val bullOnly = TickerIndicatorEngine.filterAndSort(
            items = quotes,
            mode = IndicatorMode.MA_CROSS,
            filter = TickerDirectionFilter.BULLISH,
            sortField = TickerSortField.PRICE,
            sortDescending = true,
            searchQuery = ""
        )
        assertEquals(listOf("MSFT"), bullOnly.map { it.symbol })

        val sortedAscPrice = TickerIndicatorEngine.filterAndSort(
            items = quotes,
            mode = IndicatorMode.MA_CROSS,
            filter = TickerDirectionFilter.ALL,
            sortField = TickerSortField.PRICE,
            sortDescending = false,
            searchQuery = ""
        )
        assertEquals(listOf("INTC", "MSFT"), sortedAscPrice.map { it.symbol })

        val summary = TickerIndicatorEngine.summarize(quotes, IndicatorMode.MA_CROSS)
        assertEquals(2, summary.total)
        assertEquals(1, summary.bullishCount)
        assertEquals(1, summary.bearishCount)
        assertFalse(bull.sparkline.isEmpty())
    }
}
