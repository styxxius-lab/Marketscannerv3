package com.example

import com.example.domain.engine.CouncilConfig
import com.example.domain.engine.CouncilEngine
import com.example.domain.engine.CouncilFeatures
import com.example.domain.engine.CouncilVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CouncilEngineTest {

    private fun sampleFeatures(position: String = "flat", price: Double = 150.0) = CouncilFeatures(
        symbol = "AAPL",
        asof = "2026-11-20",
        price = price,
        atrPct = 2.0,
        mom20d = 6.5,
        mom60d = 12.0,
        rsi14 = 58.0,
        adx14 = 28.0,
        rvol = 1.4,
        aboveEma20 = true,
        aboveEma50 = true,
        aboveEma200 = true,
        distEma20Pct = 2.1,
        regime = "RISK_ON",
        breadth = 0.72,
        volPctile = 0.35,
        sector = "Tech",
        position = position,
        entry = if (position == "long") 140.0 else 0.0
    )

    @Test
    fun familyDetectionAndQuorumDiversityHardGatesEnforced() {
        assertEquals("glm", CouncilEngine.family("glm-5.3"))
        assertEquals("qwen", CouncilEngine.family("qwen/qwen3-235b-a22b:free"))
        assertEquals("deepseek", CouncilEngine.family("deepseek-chat"))
        assertEquals("llama", CouncilEngine.family("llama-3.3-70b-versatile"))
        assertEquals("gemini", CouncilEngine.family("gemini-2.5-flash"))

        val f = sampleFeatures("flat")

        // Rule #2a: Fewer than min_members valid replies -> degraded HOLD, sizeMult = 0.0
        val lowQuorum = CouncilEngine.aggregate(
            votes = listOf(
                CouncilVote("gemini:gemini-2.5-flash", "gemini", "BUY", 0.90),
                CouncilVote("deepseek:deepseek-chat", "deepseek", error = "HTTP 429")
            ),
            features = f
        )
        assertTrue(lowQuorum.degraded)
        assertEquals("HOLD", lowQuorum.action)
        assertEquals(0.0, lowQuorum.sizeMult, 1e-6)
        assertEquals(1, lowQuorum.quorum)

        // Rule #2b: Same model family (correlated) -> degraded HOLD, sizeMult = 0.0
        val sameFamily = CouncilEngine.aggregate(
            votes = listOf(
                CouncilVote("openrouter:meta-llama/llama-3.3-70b:free", "llama", "BUY", 0.90),
                CouncilVote("groq:llama-3.3-70b-versatile", "llama", "BUY", 0.85)
            ),
            features = f
        )
        assertTrue(sameFamily.degraded)
        assertEquals("HOLD", sameFamily.action)
        assertEquals(0.0, sameFamily.sizeMult, 1e-6)
        assertEquals(1, sameFamily.families)
    }

    @Test
    fun ruleOneSizeMultNeverExceedsOneAndHardSellVetoWorks() {
        val fFlat = sampleFeatures("flat")
        // Unanimous high-confidence BUY across 3 distinct families -> BUY with sizeMult <= 1.0
        val buyDecision = CouncilEngine.aggregate(
            votes = listOf(
                CouncilVote("gemini:gemini-2.5-flash", "gemini", "BUY", 0.95),
                CouncilVote("deepseek:deepseek-chat", "deepseek", "BUY", 0.92),
                CouncilVote("openrouter:qwen/qwen3-235b-a22b:free", "qwen", "BUY", 0.88)
            ),
            features = fFlat
        )
        assertFalse(buyDecision.degraded)
        assertEquals("BUY", buyDecision.action)
        assertTrue("sizeMult must stay <= 1.0", buyDecision.sizeMult in 0.4..1.0)
        assertEquals(3, buyDecision.families)

        // Hard sell veto on flat position blocks BUY even if others say BUY
        val blockedFlat = CouncilEngine.aggregate(
            votes = listOf(
                CouncilVote("gemini:gemini-2.5-flash", "gemini", "BUY", 0.90),
                CouncilVote("deepseek:deepseek-chat", "deepseek", "BUY", 0.85),
                CouncilVote("groq:llama-3.3-70b-versatile", "llama", "SELL", 0.75)
            ),
            features = fFlat
        )
        assertEquals("HOLD", blockedFlat.action)
        assertEquals(0.0, blockedFlat.sizeMult, 1e-6)
        assertTrue(blockedFlat.reason.contains("blocked by"))

        // Hard sell veto on open long triggers SELL with sizeMult = 1.0
        val fLong = sampleFeatures("long")
        val vetoLong = CouncilEngine.aggregate(
            votes = listOf(
                CouncilVote("gemini:gemini-2.5-flash", "gemini", "HOLD", 0.60),
                CouncilVote("groq:llama-3.3-70b-versatile", "llama", "SELL", 0.78)
            ),
            features = fLong,
            config = CouncilConfig()
        )
        assertEquals("SELL", vetoLong.action)
        assertEquals(1.0, vetoLong.sizeMult, 1e-6)
        assertTrue(vetoLong.reason.contains("veto by"))
    }

    @Test
    fun ledgerResolutionScoringAndBoundedWeightsUpdateCorrectly() {
        assertEquals(true, CouncilEngine.hitOf("BUY", 0.03, 2.0))
        assertEquals(false, CouncilEngine.hitOf("BUY", -0.02, 2.0))
        assertEquals(true, CouncilEngine.hitOf("SELL", -0.04, 2.0))
        assertEquals(true, CouncilEngine.hitOf("HOLD", 0.004, 2.0))   // within 0.5 * 2% = 1% band
        assertEquals(false, CouncilEngine.hitOf("HOLD", 0.04, 2.0))   // moved 4% > 1% band

        val f = sampleFeatures("flat", price = 100.0)
        // Build 12 historical BUY decisions that all win (+5%) for member A and lose for member B (SELL)
        val rawLedger = (1..12).map { idx ->
            CouncilEngine.aggregate(
                votes = listOf(
                    CouncilVote("gemini:gemini-2.5-flash", "gemini", "BUY", 0.80),
                    CouncilVote("deepseek:deepseek-chat", "deepseek", "BUY", 0.75)
                ),
                features = f.copy(symbol = "AAPL", asof = "2026-11-${idx.toString().padStart(2, '0')}")
            )
        }
        val (resolved, count) = CouncilEngine.resolveLedger(
            ledger = rawLedger,
            latestPriceBySymbol = mapOf("AAPL" to 105.0)
        )
        assertEquals(12, count)
        assertTrue(resolved.all { it.outcome?.hit == true })

        val weights = CouncilEngine.updateWeights(resolved)
        val wGemini = weights["gemini:gemini-2.5-flash"]
        assertNotNull(wGemini)
        assertEquals(12, wGemini!!.n)
        assertTrue("Bounded weight in [0.5, 2.0], got ${wGemini.weight}", wGemini.weight in 0.5..2.0)

        val stats = CouncilEngine.computeStats(resolved, weights)
        assertEquals(12, stats.totalDecisions)
        assertEquals(12, stats.resolvedCount)
        assertEquals(1.0, stats.buySellAccuracy!!, 1e-6)
        assertNotNull(stats.sampleWarning)
    }
}
