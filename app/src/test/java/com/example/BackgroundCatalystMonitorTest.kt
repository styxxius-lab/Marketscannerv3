package com.example

import com.example.domain.engine.BackgroundCatalystMonitorEngine
import com.example.domain.engine.BackgroundMonitorAlertKind
import com.example.domain.engine.BackgroundMonitorConfig
import com.example.domain.engine.CatalystImpactLevel
import com.example.domain.engine.ScoredCatalystHeadline
import com.example.domain.engine.TickerCatalystReport
import com.example.domain.engine.TickerPriceMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCatalystMonitorTest {

    private fun sampleReport(
        symbol: String,
        score: Int,
        level: CatalystImpactLevel,
        headline: String = "$symbol announces major guidance revision"
    ): TickerCatalystReport = TickerCatalystReport(
        symbol = symbol,
        catalystScore = score,
        impactScore = kotlin.math.abs(score),
        impactLevel = level,
        direction = if (score >= 0) 0.8 else -0.8,
        materiality = if (level.rank >= 3) 0.85 else 0.30,
        confidence = 0.90,
        primaryTag = "GUIDANCE",
        llmSummary = "Significant earnings revision.",
        scorerLabel = "GEMINI · gemini-2.5-flash",
        headlineSource = "FINNHUB",
        usedLlm = true,
        headlineCount = 1,
        headlines = listOf(
            ScoredCatalystHeadline(
                id = "H_$symbol",
                symbol = symbol,
                headline = headline,
                summary = "",
                source = "Reuters",
                url = "https://example.com",
                publishedAtMs = 1_795_608_000_000L,
                catalystScore = score,
                impactScore = kotlin.math.abs(score),
                impactLevel = level,
                direction = if (score >= 0) 0.8 else -0.8,
                materiality = if (level.rank >= 3) 0.85 else 0.30,
                confidence = 0.90,
                decayFactor = 1.0,
                catalystTag = "GUIDANCE",
                rationale = "Significant earnings revision.",
                usedLlm = true
            )
        ),
        analyzedAtMs = 1_795_608_000_000L
    )

    @Test
    fun evaluateAlerts_detectsCombinedCatalystPlusMoveAndOrdersByPriority() {
        val now = 1_795_608_000_000L
        val reports = listOf(
            sampleReport("NVDA", score = 82, level = CatalystImpactLevel.CRITICAL),
            sampleReport("AAPL", score = 58, level = CatalystImpactLevel.HIGH),
            sampleReport("MSFT", score = 12, level = CatalystImpactLevel.LOW)
        )
        val moves = mapOf(
            "NVDA" to TickerPriceMove("NVDA", 148.50, 140.00, 6.07),
            "AAPL" to TickerPriceMove("AAPL", 225.00, 224.00, 0.45),
            "TSLA" to TickerPriceMove("TSLA", 242.00, 255.00, -5.10)
        )
        val config = BackgroundMonitorConfig(
            enabled = true,
            minCatalystScoreAbs = 50,
            minImpactLevel = CatalystImpactLevel.HIGH,
            minPriceMovePct = 3.0
        )

        val alerts = BackgroundCatalystMonitorEngine.evaluateAlerts(
            reports = reports,
            priceMovesBySymbol = moves,
            config = config,
            watchlist = emptyList(),
            alreadyNotifiedKeys = emptySet(),
            nowMs = now
        )

        assertEquals(3, alerts.size)
        // Priority 1: combined high-impact catalyst + significant price move
        assertEquals("NVDA", alerts[0].symbol)
        assertEquals(BackgroundMonitorAlertKind.CATALYST_PLUS_MOVE, alerts[0].kind)
        // Priority 2: high-impact catalyst alone
        assertEquals("AAPL", alerts[1].symbol)
        assertEquals(BackgroundMonitorAlertKind.HIGH_IMPACT_CATALYST, alerts[1].kind)
        // Priority 3: significant price move alone
        assertEquals("TSLA", alerts[2].symbol)
        assertEquals(BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE, alerts[2].kind)
    }

    @Test
    fun evaluateAlerts_deduplicatesPreviouslyNotifiedKeys() {
        val now = 1_795_608_000_000L
        val reports = listOf(sampleReport("NVDA", score = 78, level = CatalystImpactLevel.CRITICAL))
        val config = BackgroundMonitorConfig(enabled = true)

        val firstPass = BackgroundCatalystMonitorEngine.evaluateAlerts(
            reports = reports,
            priceMovesBySymbol = emptyMap(),
            config = config,
            watchlist = emptyList(),
            alreadyNotifiedKeys = emptySet(),
            nowMs = now
        )
        assertEquals(1, firstPass.size)

        val secondPass = BackgroundCatalystMonitorEngine.evaluateAlerts(
            reports = reports,
            priceMovesBySymbol = emptyMap(),
            config = config,
            watchlist = emptyList(),
            alreadyNotifiedKeys = firstPass.map { it.dedupeKey }.toSet(),
            nowMs = now + 60_000L
        )
        assertTrue("Duplicate alert should be suppressed", secondPass.isEmpty())
    }

    @Test
    fun evaluateAlerts_respectsWatchlistOnlyAndDisabledConfig() {
        val now = 1_795_608_000_000L
        val reports = listOf(
            sampleReport("NVDA", score = 80, level = CatalystImpactLevel.CRITICAL),
            sampleReport("AMD", score = -72, level = CatalystImpactLevel.CRITICAL)
        )

        val watchlistAlerts = BackgroundCatalystMonitorEngine.evaluateAlerts(
            reports = reports,
            priceMovesBySymbol = emptyMap(),
            config = BackgroundMonitorConfig(enabled = true, watchlistOnly = true),
            watchlist = listOf("AMD"),
            alreadyNotifiedKeys = emptySet(),
            nowMs = now
        )
        assertEquals(1, watchlistAlerts.size)
        assertEquals("AMD", watchlistAlerts.first().symbol)

        val disabledAlerts = BackgroundCatalystMonitorEngine.evaluateAlerts(
            reports = reports,
            priceMovesBySymbol = emptyMap(),
            config = BackgroundMonitorConfig(enabled = false),
            watchlist = listOf("AMD"),
            alreadyNotifiedKeys = emptySet(),
            nowMs = now
        )
        assertTrue(disabledAlerts.isEmpty())
    }
}
