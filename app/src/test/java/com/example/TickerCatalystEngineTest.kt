package com.example

import com.example.data.service.MarketCatalystService
import com.example.domain.engine.AdvisorHttpResponse
import com.example.domain.engine.AdvisorHttpTransport
import com.example.domain.engine.CatalystImpactFilter
import com.example.domain.engine.CatalystImpactLevel
import com.example.domain.engine.LlmHeadlineCatalyst
import com.example.domain.engine.RawTickerHeadline
import com.example.domain.engine.TickerCatalystEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiProviderSettings
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.AppConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerCatalystEngineTest {

    @Test
    fun parseYahooRssXml_extractsHeadlinesEntitiesAndDates() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title><![CDATA[NVIDIA Beats Q4 Estimates &amp; Raises Data Center Guidance]]></title>
                  <link>https://finance.yahoo.com/news/nvda-beats-123.html</link>
                  <description>Chipmaker &quot;surges&quot; on strong AI demand.</description>
                  <pubDate>Wed, 25 Nov 2026 12:00:00 +0000</pubDate>
                  <source>Reuters</source>
                </item>
                <item>
                  <title>NVIDIA Announces Next-Gen GPU Architecture</title>
                  <link>https://finance.yahoo.com/news/nvda-gpu-456.html</link>
                  <description>New silicon boosts inference throughput.</description>
                  <pubDate>Wed, 25 Nov 2026 10:00:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val items = TickerCatalystEngine.parseYahooRssXml(sampleXml, "nvda", nowMs = 1_795_608_000_000L)
        assertEquals(2, items.size)
        assertEquals("NVDA", items[0].symbol)
        assertEquals("NVIDIA Beats Q4 Estimates & Raises Data Center Guidance", items[0].headline)
        assertEquals("Chipmaker \"surges\" on strong AI demand.", items[0].summary)
        assertEquals("Reuters", items[0].source)
        assertEquals("https://finance.yahoo.com/news/nvda-beats-123.html", items[0].url)
        assertEquals("Yahoo Finance", items[1].source)
    }

    @Test
    fun parseLlmCatalystJson_handlesMarkdownFencesAndClamping() {
        val fenced = """
            ```json
            {
              "items": [
                {
                  "id": "H1",
                  "direction": 1.4,
                  "materiality": 0.92,
                  "confidence": 0.95,
                  "impact_level": "CRITICAL",
                  "catalyst_tag": "EARNINGS",
                  "rationale": "Blowout revenue beat and raised full-year guidance."
                },
                {
                  "id": "H2",
                  "direction": -0.65,
                  "materiality": 0.70,
                  "confidence": 0.80,
                  "impact_level": "HIGH",
                  "catalyst_tag": "REGULATORY",
                  "rationale": "Antitrust inquiry introduces material margin risk."
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = TickerCatalystEngine.parseLlmCatalystJson(fenced)
        assertEquals(2, parsed.size)
        val h1 = parsed["H1"]!!
        assertEquals(1.0, h1.direction, 1e-6)
        assertEquals(0.92, h1.materiality, 1e-6)
        assertEquals(CatalystImpactLevel.CRITICAL, h1.impactLevelHint)
        assertEquals("EARNINGS", h1.catalystTag)

        val h2 = parsed["H2"]!!
        assertEquals(-0.65, h2.direction, 1e-6)
        assertEquals(CatalystImpactLevel.HIGH, h2.impactLevelHint)
    }

    @Test
    fun aggregateTicker_surfacesCriticalAndLowImpactLevelsCorrectly() {
        val now = 1_795_608_000_000L
        val criticalHeadline = RawTickerHeadline(
            id = "NVDA_1",
            symbol = "NVDA",
            headline = "NVIDIA raises full-year revenue guidance by 18% after record quarter",
            source = "Reuters",
            publishedAtMs = now - 1_800_000L
        )
        val criticalReport = TickerCatalystEngine.aggregateTicker(
            symbol = "NVDA",
            rawHeadlines = listOf(criticalHeadline),
            llmScoresByHeadlineId = mapOf(
                "NVDA_1" to LlmHeadlineCatalyst(
                    direction = 0.95,
                    materiality = 0.92,
                    confidence = 0.94,
                    impactLevelHint = CatalystImpactLevel.CRITICAL,
                    catalystTag = "GUIDANCE",
                    rationale = "Major upward guidance revision signals accelerating enterprise demand."
                )
            ),
            scorerLabel = "GOOGLE GEMINI · gemini-2.5-flash",
            usedLlm = true,
            headlineSource = "FINNHUB",
            nowMs = now
        )

        assertEquals(CatalystImpactLevel.CRITICAL, criticalReport.impactLevel)
        assertTrue("Expected strong bullish catalyst score", criticalReport.catalystScore >= 70)
        assertTrue("Expected high impact intensity", criticalReport.impactScore >= 75)
        assertTrue(criticalReport.usedLlm)

        val quietHeadline = RawTickerHeadline(
            id = "AAPL_1",
            symbol = "AAPL",
            headline = "Apple shares drift in quiet midday trading alongside peers",
            source = "Yahoo Finance",
            publishedAtMs = now - 36L * 3_600_000L // 36h old -> decayed
        )
        val lowReport = TickerCatalystEngine.aggregateTicker(
            symbol = "AAPL",
            rawHeadlines = listOf(quietHeadline),
            llmScoresByHeadlineId = mapOf(
                "AAPL_1" to LlmHeadlineCatalyst(
                    direction = 0.05,
                    materiality = 0.15,
                    confidence = 0.60,
                    impactLevelHint = CatalystImpactLevel.LOW,
                    catalystTag = "MARKET",
                    rationale = "Routine intraday tape commentary with minimal catalyst impact."
                )
            ),
            scorerLabel = "GOOGLE GEMINI · gemini-2.5-flash",
            usedLlm = true,
            headlineSource = "YAHOO RSS",
            nowMs = now
        )

        assertEquals(CatalystImpactLevel.LOW, lowReport.impactLevel)
        assertTrue(lowReport.impactScore < 25)

        val filtered = TickerCatalystEngine.filterReports(
            reports = listOf(criticalReport, lowReport),
            impactFilter = CatalystImpactFilter.CRITICAL
        )
        assertEquals(1, filtered.size)
        assertEquals("NVDA", filtered.first().symbol)
    }

    @Test
    fun marketCatalystService_fetchesLiveHeadlinesAndScoresWithConfiguredLlm() = runBlocking {
        val now = 1_795_608_000_000L
        val fakeTransport = object : AdvisorHttpTransport {
            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse {
                return when {
                    url.contains("s=NVDA") -> AdvisorHttpResponse(
                        code = 200,
                        body = """
                            <rss><channel>
                              <item>
                                <title>NVIDIA Secures Multi-Billion Sovereign AI Contract</title>
                                <link>https://finance.yahoo.com/nvda-contract</link>
                                <description>New hyperscale cluster rollout accelerates backlog.</description>
                                <pubDate>Wed, 25 Nov 2026 12:00:00 +0000</pubDate>
                              </item>
                            </channel></rss>
                        """.trimIndent()
                    )
                    url.contains("s=TSLA") -> AdvisorHttpResponse(
                        code = 200,
                        body = """
                            <rss><channel>
                              <item>
                                <title>Tesla Faces NHTSA Safety Recall Inquiry Over Autopilot</title>
                                <link>https://finance.yahoo.com/tsla-recall</link>
                                <description>Regulators open formal investigation into driver assistance.</description>
                                <pubDate>Wed, 25 Nov 2026 11:30:00 +0000</pubDate>
                              </item>
                            </channel></rss>
                        """.trimIndent()
                    )
                    else -> AdvisorHttpResponse(404, "")
                }
            }

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse {
                // Extract the actual generated headline IDs from the prompt so the mock LLM returns exact matches
                val idMatches = Regex("id=([A-Za-z0-9_\\-]+)").findAll(jsonBody).map { it.groupValues[1] }.toList()
                val nvdaId = idMatches.first { it.contains("NVDA") }
                val tslaId = idMatches.first { it.contains("TSLA") }
                val llmJson = """
                    {"items":[
                      {"id":"$nvdaId","direction":0.90,"materiality":0.88,"confidence":0.92,"impact_level":"CRITICAL","catalyst_tag":"PRODUCT","rationale":"Major sovereign AI deal expands revenue visibility."},
                      {"id":"$tslaId","direction":-0.75,"materiality":0.78,"confidence":0.86,"impact_level":"HIGH","catalyst_tag":"REGULATORY","rationale":"Formal safety inquiry creates regulatory and recall overhang."}
                    ]}
                """.trimIndent().replace("\"", "\\\"").replace("\n", "")
                return AdvisorHttpResponse(
                    code = 200,
                    body = """{"choices":[{"message":{"content":"$llmJson"}}]}"""
                )
            }
        }

        val config = AppConfig(
            demo = true,
            aiRouting = AiRoutingConfig(
                activeProvider = AiProviderId.OPENROUTER,
                providers = mapOf(
                    AiProviderId.OPENROUTER to AiProviderSettings(
                        enabled = true,
                        apiKey = "test-openrouter-key",
                        selectedModelId = "qwen/qwen3-235b:free"
                    )
                )
            )
        )

        val service = MarketCatalystService(transport = fakeTransport, clockMs = { now })
        val result = service.analyzeTickers(listOf("NVDA", "TSLA"), config)

        assertTrue(result.usedLlm)
        assertEquals("YAHOO RSS", result.headlineSourceLabel)
        assertEquals(2, result.reports.size)

        val nvda = result.reports.first { it.symbol == "NVDA" }
        val tsla = result.reports.first { it.symbol == "TSLA" }

        assertEquals(CatalystImpactLevel.CRITICAL, nvda.impactLevel)
        assertTrue(nvda.catalystScore > 60)
        assertEquals("PRODUCT", nvda.primaryTag)

        assertEquals(CatalystImpactLevel.HIGH, tsla.impactLevel)
        assertTrue(tsla.catalystScore < -40)
        assertEquals("REGULATORY", tsla.primaryTag)
    }

    @Test
    fun marketCatalystService_fallsBackCleanlyWhenNoLlmKeyConfigured() = runBlocking {
        val now = 1_795_608_000_000L
        val fakeTransport = object : AdvisorHttpTransport {
            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse {
                return AdvisorHttpResponse(
                    code = 200,
                    body = """
                        <rss><channel>
                          <item>
                            <title>AMD Beats Earnings and Raises Full-Year Guidance</title>
                            <link>https://finance.yahoo.com/amd-beats</link>
                            <description>Record data center revenue drives upgrade.</description>
                            <pubDate>Wed, 25 Nov 2026 12:00:00 +0000</pubDate>
                          </item>
                        </channel></rss>
                    """.trimIndent()
                )
            }

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse = throw AssertionError("Should not invoke LLM without key")
        }

        val disabledProviders = AiProviderId.entries.associateWith {
            AiProviderSettings(enabled = false, apiKey = "")
        }
        val config = AppConfig(
            demo = true,
            geminiKey = "",
            aiRouting = AiRoutingConfig(
                activeProvider = AiProviderId.GEMINI,
                providers = disabledProviders
            )
        )

        val service = MarketCatalystService(transport = fakeTransport, clockMs = { now })
        val result = service.analyzeTickers(listOf("AMD"), config)

        assertFalse(result.usedLlm)
        assertEquals(1, result.reports.size)
        val amd = result.reports.first()
        assertEquals("AMD", amd.symbol)
        assertTrue(amd.catalystScore > 0)
        assertTrue(result.warning != null)
        assertEquals(AiProviderId.entries.size, result.providerRuns.size)
        assertEquals(0, result.activeAiCount)
    }

    @Test
    fun aggregateTickerMultiAi_computesConsensusScoreAgreementAndPerAiVerdicts() {
        val now = 1_795_608_000_000L
        val headline = RawTickerHeadline(
            id = "NVDA_MULTI_1",
            symbol = "NVDA",
            headline = "NVIDIA posts blowout quarterly data center revenue and lifts guidance",
            source = "Finnhub",
            publishedAtMs = now - 900_000L
        )

        val scoresByProvider = mapOf(
            AiProviderId.GEMINI to mapOf(
                "NVDA_MULTI_1" to LlmHeadlineCatalyst(
                    direction = 0.92,
                    materiality = 0.90,
                    confidence = 0.94,
                    impactLevelHint = CatalystImpactLevel.CRITICAL,
                    catalystTag = "EARNINGS",
                    rationale = "Gemini: Blowout data center revenue beat lifts multi-quarter outlook."
                )
            ),
            AiProviderId.DEEPSEEK to mapOf(
                "NVDA_MULTI_1" to LlmHeadlineCatalyst(
                    direction = 0.85,
                    materiality = 0.86,
                    confidence = 0.88,
                    impactLevelHint = CatalystImpactLevel.CRITICAL,
                    catalystTag = "EARNINGS",
                    rationale = "DeepSeek: Upward guidance revision confirms accelerating AI demand."
                )
            ),
            AiProviderId.GROQ to mapOf(
                "NVDA_MULTI_1" to LlmHeadlineCatalyst(
                    direction = 0.78,
                    materiality = 0.80,
                    confidence = 0.82,
                    impactLevelHint = CatalystImpactLevel.HIGH,
                    catalystTag = "GUIDANCE",
                    rationale = "Groq: Strong beat-and-raise supports bullish momentum."
                )
            )
        )

        val report = TickerCatalystEngine.aggregateTickerMultiAi(
            symbol = "NVDA",
            rawHeadlines = listOf(headline),
            scoresByProvider = scoresByProvider,
            modelIdByProvider = mapOf(
                AiProviderId.GEMINI to "gemini-2.5-flash",
                AiProviderId.DEEPSEEK to "deepseek-chat",
                AiProviderId.GROQ to "llama-3.3-70b-versatile"
            ),
            scorerLabel = "3/5 AIs CONSENSUS",
            headlineSource = "FINNHUB",
            nowMs = now
        )

        assertEquals(3, report.aiVerdicts.size)
        assertEquals(3, report.headlines.first().aiModelsCount)
        assertEquals(CatalystImpactLevel.CRITICAL, report.impactLevel)
        assertEquals("CRITICAL BULLISH", report.impactRatingLabel)
        assertTrue("Expected high consensus agreement (>= 80%)", report.aiAgreementPct >= 80)
        assertTrue("Expected strong bullish consensus score", report.catalystScore >= 60)

        // Verify disagreement lowers aiAgreementPct
        val splitScores = mapOf(
            AiProviderId.GEMINI to mapOf(
                "NVDA_MULTI_1" to LlmHeadlineCatalyst(
                    direction = 0.90,
                    materiality = 0.85,
                    confidence = 0.90,
                    impactLevelHint = CatalystImpactLevel.CRITICAL,
                    catalystTag = "EARNINGS",
                    rationale = "Bullish view"
                )
            ),
            AiProviderId.DEEPSEEK to mapOf(
                "NVDA_MULTI_1" to LlmHeadlineCatalyst(
                    direction = -0.85,
                    materiality = 0.85,
                    confidence = 0.90,
                    impactLevelHint = CatalystImpactLevel.CRITICAL,
                    catalystTag = "EARNINGS",
                    rationale = "Bearish view"
                )
            )
        )
        val splitReport = TickerCatalystEngine.aggregateTickerMultiAi(
            symbol = "NVDA",
            rawHeadlines = listOf(headline),
            scoresByProvider = splitScores,
            modelIdByProvider = mapOf(
                AiProviderId.GEMINI to "gemini-2.5-flash",
                AiProviderId.DEEPSEEK to "deepseek-chat"
            ),
            scorerLabel = "2/5 AIs CONSENSUS",
            headlineSource = "FINNHUB",
            nowMs = now
        )
        assertTrue("Opposing AI verdicts should produce lower agreement (< 50%)", splitReport.aiAgreementPct < 50)
    }

    @Test
    fun marketCatalystService_dispatchesAllConfiguredAisInParallelAndIsolatesFailures() = runBlocking {
        val now = 1_795_608_000_000L
        val invokedUrls = java.util.Collections.synchronizedList(mutableListOf<String>())

        val fakeTransport = object : AdvisorHttpTransport {
            override suspend fun get(url: String, timeoutSec: Double): AdvisorHttpResponse {
                return AdvisorHttpResponse(
                    code = 200,
                    body = """
                        <rss><channel>
                          <item>
                            <title>Apple Unveils Major AI Hardware Upgrade Cycle</title>
                            <link>https://finance.yahoo.com/aapl-ai</link>
                            <description>Analysts raise shipment forecasts after keynote.</description>
                            <pubDate>Wed, 25 Nov 2026 12:00:00 +0000</pubDate>
                          </item>
                        </channel></rss>
                    """.trimIndent()
                )
            }

            override suspend fun postJson(
                url: String,
                headers: Map<String, String>,
                jsonBody: String,
                timeoutSec: Double
            ): AdvisorHttpResponse {
                invokedUrls.add(url)
                val idMatches = Regex("id=([A-Za-z0-9_\\-]+)").findAll(jsonBody).map { it.groupValues[1] }.toList()
                val aaplId = idMatches.first { it.contains("AAPL") }

                return when {
                    url.contains("generativelanguage.googleapis.com") -> {
                        val inner = """{"items":[{"id":"$aaplId","direction":0.85,"materiality":0.82,"confidence":0.90,"impact_level":"CRITICAL","catalyst_tag":"PRODUCT","rationale":"Gemini sees flagship upgrade cycle lifting revenue."}]}"""
                            .replace("\"", "\\\"")
                        AdvisorHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"$inner"}]}}]}""")
                    }
                    url.contains("openrouter.ai") -> {
                        val inner = """{"items":[{"id":"$aaplId","direction":0.76,"materiality":0.78,"confidence":0.86,"impact_level":"HIGH","catalyst_tag":"PRODUCT","rationale":"OpenRouter flags strong analyst shipment revisions."}]}"""
                            .replace("\"", "\\\"")
                        AdvisorHttpResponse(200, """{"choices":[{"message":{"content":"$inner"}}]}""")
                    }
                    url.contains("api.deepseek.com") -> {
                        val inner = """{"items":[{"id":"$aaplId","direction":0.80,"materiality":0.80,"confidence":0.88,"impact_level":"HIGH","catalyst_tag":"PRODUCT","rationale":"DeepSeek notes bullish hardware replacement catalyst."}]}"""
                            .replace("\"", "\\\"")
                        AdvisorHttpResponse(200, """{"choices":[{"message":{"content":"$inner"}}]}""")
                    }
                    url.contains("api.groq.com") -> {
                        // Simulate a provider outage — must be isolated without breaking the consensus
                        AdvisorHttpResponse(503, """{"error":"service unavailable"}""")
                    }
                    else -> AdvisorHttpResponse(404, "")
                }
            }
        }

        val config = AppConfig(
            demo = true,
            geminiKey = "test-gemini-key",
            aiRouting = AiRoutingConfig(
                activeProvider = AiProviderId.GEMINI,
                providers = mapOf(
                    AiProviderId.GEMINI to AiProviderSettings(enabled = true, apiKey = "test-gemini-key", selectedModelId = "gemini-2.5-flash"),
                    AiProviderId.OPENROUTER to AiProviderSettings(enabled = true, apiKey = "test-or-key", selectedModelId = "qwen/qwen3-235b:free"),
                    AiProviderId.DEEPSEEK to AiProviderSettings(enabled = true, apiKey = "test-ds-key", selectedModelId = "deepseek-chat"),
                    AiProviderId.GROQ to AiProviderSettings(enabled = true, apiKey = "test-groq-key", selectedModelId = "llama-3.3-70b-versatile"),
                    AiProviderId.AIHUBMIX to AiProviderSettings(enabled = false, apiKey = "unused-key", selectedModelId = "gpt-4o-mini")
                )
            )
        )

        val service = MarketCatalystService(transport = fakeTransport, clockMs = { now })
        val result = service.analyzeTickers(listOf("AAPL"), config)

        assertTrue(result.usedLlm)
        assertEquals(3, result.activeAiCount)
        assertEquals(AiProviderId.entries.size, result.totalImplementedAiCount)
        assertEquals(AiProviderId.entries.size, result.providerRuns.size)
        assertEquals("3/5 AIs CONSENSUS", result.scorerLabel)
        assertEquals(4, invokedUrls.size) // Gemini, OpenRouter, DeepSeek, Groq invoked; AIHubMix skipped (disabled)

        val runsByProvider = result.providerRuns.associateBy { it.provider }
        assertEquals(com.example.domain.engine.AiProviderRunStatus.SCORED, runsByProvider[AiProviderId.GEMINI]?.status)
        assertEquals(com.example.domain.engine.AiProviderRunStatus.SCORED, runsByProvider[AiProviderId.OPENROUTER]?.status)
        assertEquals(com.example.domain.engine.AiProviderRunStatus.SCORED, runsByProvider[AiProviderId.DEEPSEEK]?.status)
        assertEquals(com.example.domain.engine.AiProviderRunStatus.FAILED, runsByProvider[AiProviderId.GROQ]?.status)
        assertEquals(com.example.domain.engine.AiProviderRunStatus.DISABLED, runsByProvider[AiProviderId.AIHUBMIX]?.status)

        val aapl = result.reports.first()
        assertEquals("AAPL", aapl.symbol)
        assertEquals(3, aapl.aiVerdicts.size)
        assertTrue(aapl.aiAgreementPct >= 85)
        assertTrue(aapl.catalystScore >= 55)
        assertTrue(aapl.impactRatingLabel.endsWith("BULLISH"))
        assertTrue("Partial failure warning should be surfaced", result.warning?.contains("Groq") == true)
    }
}
