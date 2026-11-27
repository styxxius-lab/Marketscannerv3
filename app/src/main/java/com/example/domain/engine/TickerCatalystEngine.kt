package com.example.domain.engine

import com.example.domain.model.AiProviderId
import com.example.domain.model.AppConfig
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Discrete catalyst impact tier surfaced for each individual ticker.
 */
enum class CatalystImpactLevel(
    val label: String,
    val shortLabel: String,
    val rank: Int
) {
    CRITICAL("CRITICAL", "CRIT", 4),
    HIGH("HIGH", "HIGH", 3),
    MODERATE("MODERATE", "MOD", 2),
    LOW("LOW", "LOW", 1);

    companion object {
        fun fromWire(raw: String?): CatalystImpactLevel? {
            val clean = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return when {
                clean.startsWith("CRIT") || clean == "EXTREME" || clean == "SEVERE" -> CRITICAL
                clean == "HIGH" || clean == "MAJOR" -> HIGH
                clean.startsWith("MOD") || clean == "MEDIUM" -> MODERATE
                clean == "LOW" || clean == "MINOR" || clean == "NONE" -> LOW
                else -> null
            }
        }
    }
}

enum class CatalystImpactFilter(val label: String) {
    ALL("ALL"),
    CRITICAL("CRITICAL"),
    HIGH("HIGH"),
    MODERATE("MODERATE"),
    LOW("LOW")
}

/**
 * Normalized headline fetched from Finnhub, Alpaca, Yahoo Finance RSS, or cached scans.
 */
data class RawTickerHeadline(
    val id: String,
    val symbol: String,
    val headline: String,
    val summary: String = "",
    val source: String = "",
    val url: String = "",
    val publishedAtMs: Long = 0L
)

/**
 * Structured LLM evaluation for a single headline.
 */
data class LlmHeadlineCatalyst(
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val impactLevelHint: CatalystImpactLevel? = null,
    val catalystTag: String = "",
    val rationale: String = ""
)

enum class AiProviderRunStatus(val shortLabel: String) {
    SCORED("SCORED"),
    READY("READY"),
    FAILED("ERROR"),
    DISABLED("OFF"),
    NO_KEY("NO KEY")
}

/**
 * Execution telemetry for one implemented AI provider during a News Catalyst scoring sweep.
 */
data class AiProviderCatalystRun(
    val provider: AiProviderId,
    val providerName: String = provider.displayName,
    val modelId: String = "",
    val status: AiProviderRunStatus = AiProviderRunStatus.NO_KEY,
    val scoredHeadlineCount: Int = 0,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Individual AI provider's stock-level catalyst score and impact rating for a specific ticker.
 */
data class AiProviderTickerVerdict(
    val provider: AiProviderId,
    val providerName: String = provider.displayName,
    val modelId: String = "",
    val catalystScore: Int,
    val impactScore: Int,
    val impactLevel: CatalystImpactLevel,
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val catalystTag: String,
    val rationale: String
)

/**
 * Individual headline annotated with its decayed Catalyst Score and Impact Level.
 */
data class ScoredCatalystHeadline(
    val id: String,
    val symbol: String,
    val headline: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAtMs: Long,
    val catalystScore: Int,
    val impactScore: Int,
    val impactLevel: CatalystImpactLevel,
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val decayFactor: Double,
    val catalystTag: String,
    val rationale: String,
    val usedLlm: Boolean,
    val aiModelsCount: Int = if (usedLlm) 1 else 0
)

/**
 * Aggregated per-ticker Catalyst Score (-100..+100), Impact Level, and multi-AI provider breakdown.
 */
data class TickerCatalystReport(
    val symbol: String,
    val catalystScore: Int,
    val impactScore: Int,
    val impactLevel: CatalystImpactLevel,
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val primaryTag: String,
    val llmSummary: String,
    val scorerLabel: String,
    val headlineSource: String,
    val usedLlm: Boolean,
    val headlineCount: Int,
    val headlines: List<ScoredCatalystHeadline>,
    val analyzedAtMs: Long,
    val aiVerdicts: List<AiProviderTickerVerdict> = emptyList(),
    val aiAgreementPct: Int = 100
) {
    val sentimentLabel: String
        get() = when {
            catalystScore >= 12 -> "BULLISH"
            catalystScore <= -12 -> "BEARISH"
            else -> "NEUTRAL"
        }

    val impactRatingLabel: String
        get() = "${impactLevel.label} $sentimentLabel"
}

data class TickerCatalystBatchResult(
    val reports: List<TickerCatalystReport>,
    val scorerLabel: String,
    val usedLlm: Boolean,
    val headlineSourceLabel: String,
    val fetchedAtMs: Long,
    val warning: String? = null,
    val providerRuns: List<AiProviderCatalystRun> = emptyList(),
    val activeAiCount: Int = 0,
    val totalImplementedAiCount: Int = AiProviderId.entries.size
)

data class TickerCatalystSummary(
    val totalTickers: Int = 0,
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val moderateCount: Int = 0,
    val lowCount: Int = 0,
    val bullishCount: Int = 0,
    val bearishCount: Int = 0
)

/**
 * Pure domain engine for ticker-level LLM Catalyst Score calculation, impact-level
 * classification, Yahoo Finance RSS headline parsing, and strict LLM JSON extraction.
 */
object TickerCatalystEngine {

    val DEFAULT_TICKERS = listOf("NVDA", "AAPL", "MSFT", "TSLA", "META", "AMD")

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()

    private val TICKER_REGEX = Regex("^[A-Z][A-Z0-9.\\-]{0,6}$")
    private val ITEM_REGEX = Regex("<item\\b[^>]*>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TAG_HTML_REGEX = Regex("<[^>]+>")

    /**
     * Parses a comma/space/newline-delimited user ticker string into clean uppercase symbols.
     */
    fun parseTickerList(raw: String, fallback: List<String> = DEFAULT_TICKERS, maxCount: Int = 8): List<String> {
        val parsed = raw
            .split(',', ' ', '\n', ';', '\t')
            .map { it.trim().uppercase(Locale.US).removePrefix("$") }
            .filter { it.isNotEmpty() && TICKER_REGEX.matches(it) }
            .distinct()
            .take(maxCount)
        return parsed.ifEmpty { fallback.take(maxCount) }
    }

    /**
     * Classifies a ticker or headline's catalyst intensity into CRITICAL / HIGH / MODERATE / LOW.
     */
    fun classifyImpactLevel(
        absCatalystScore: Int,
        materiality: Double,
        impactScore: Int,
        llmHint: CatalystImpactLevel? = null
    ): CatalystImpactLevel {
        val computed = when {
            impactScore >= 76 || absCatalystScore >= 68 || materiality >= 0.85 -> CatalystImpactLevel.CRITICAL
            impactScore >= 48 || absCatalystScore >= 40 || materiality >= 0.65 -> CatalystImpactLevel.HIGH
            impactScore >= 24 || absCatalystScore >= 18 || materiality >= 0.40 -> CatalystImpactLevel.MODERATE
            else -> CatalystImpactLevel.LOW
        }
        // Respect LLM impact hint if it is within one tier of the quantitative score
        if (llmHint != null && abs(llmHint.rank - computed.rank) <= 1) {
            return if (llmHint.rank > computed.rank && materiality >= 0.35) llmHint else computed
        }
        return computed
    }

    /**
     * Computes a 0..100 impact intensity score independent of bullish/bearish sign,
     * combining decayed magnitude, materiality, and confidence.
     */
    fun computeImpactScore(
        absCatalystScore: Int,
        materiality: Double,
        confidence: Double,
        decayFactor: Double
    ): Int {
        val mat = materiality.coerceIn(0.0, 1.0)
        val conf = confidence.coerceIn(0.25, 1.0)
        val decay = decayFactor.coerceIn(0.10, 1.0)
        val structural = mat * (0.60 + 0.40 * conf) * (0.55 + 0.45 * decay) * 100.0
        return max(absCatalystScore.toDouble(), structural).roundToInt().coerceIn(0, 100)
    }

    /**
     * Aggregates all headlines for a single ticker into a [TickerCatalystReport] with a
     * signed Catalyst Score (-100..+100), Impact Score (0..100), and Impact Level.
     */
    fun aggregateTicker(
        symbol: String,
        rawHeadlines: List<RawTickerHeadline>,
        llmScoresByHeadlineId: Map<String, LlmHeadlineCatalyst>,
        scorerLabel: String,
        usedLlm: Boolean,
        headlineSource: String,
        nowMs: Long = System.currentTimeMillis()
    ): TickerCatalystReport {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (rawHeadlines.isEmpty()) {
            return TickerCatalystReport(
                symbol = cleanSym,
                catalystScore = 0,
                impactScore = 0,
                impactLevel = CatalystImpactLevel.LOW,
                direction = 0.0,
                materiality = 0.0,
                confidence = 0.0,
                primaryTag = "QUIET",
                llmSummary = "No recent market headlines found for $cleanSym.",
                scorerLabel = scorerLabel,
                headlineSource = headlineSource,
                usedLlm = false,
                headlineCount = 0,
                headlines = emptyList(),
                analyzedAtMs = nowMs
            )
        }

        val scored = rawHeadlines.map { raw ->
            val llm = llmScoresByHeadlineId[raw.id]
            val decay = FinnhubCatalystEngine.computeDecay(raw.publishedAtMs, nowMs)
            if (llm != null) {
                val dir = llm.direction.coerceIn(-1.0, 1.0)
                val mat = llm.materiality.coerceIn(0.0, 1.0)
                val conf = llm.confidence.coerceIn(0.25, 1.0)
                val score = FinnhubCatalystEngine.computeCatalystScore(dir, mat, conf, decay)
                val impactScore = computeImpactScore(abs(score), mat, conf, decay)
                val level = classifyImpactLevel(abs(score), mat, impactScore, llm.impactLevelHint)
                val tag = llm.catalystTag.trim().uppercase(Locale.US)
                    .takeIf { it.isNotBlank() && it.length <= 14 }
                    ?: FinnhubCatalystEngine.classifyCatalystTag(raw.headline, raw.summary, "")
                val rationale = llm.rationale.trim().ifBlank {
                    buildFallbackHeadlineRationale(cleanSym, score, level, tag)
                }
                ScoredCatalystHeadline(
                    id = raw.id,
                    symbol = cleanSym,
                    headline = raw.headline,
                    summary = raw.summary,
                    source = raw.source.ifBlank { headlineSource },
                    url = raw.url,
                    publishedAtMs = raw.publishedAtMs,
                    catalystScore = score,
                    impactScore = impactScore,
                    impactLevel = level,
                    direction = dir,
                    materiality = mat,
                    confidence = conf,
                    decayFactor = (decay * 100.0).roundToInt() / 100.0,
                    catalystTag = tag,
                    rationale = rationale,
                    usedLlm = true
                )
            } else {
                val det = FinnhubCatalystEngine.scoreDeterministic(
                    RawFinnhubArticle(
                        id = raw.id,
                        symbol = cleanSym,
                        headline = raw.headline,
                        summary = raw.summary,
                        source = raw.source.ifBlank { headlineSource },
                        category = "equity",
                        url = raw.url,
                        publishedAtMs = raw.publishedAtMs
                    ),
                    nowMs = nowMs
                )
                val impactScore = computeImpactScore(
                    absCatalystScore = abs(det.catalystScore),
                    materiality = det.materiality,
                    confidence = det.confidence,
                    decayFactor = det.decayFactor
                )
                val level = classifyImpactLevel(abs(det.catalystScore), det.materiality, impactScore)
                ScoredCatalystHeadline(
                    id = raw.id,
                    symbol = cleanSym,
                    headline = raw.headline,
                    summary = raw.summary,
                    source = det.source,
                    url = raw.url,
                    publishedAtMs = raw.publishedAtMs,
                    catalystScore = det.catalystScore,
                    impactScore = impactScore,
                    impactLevel = level,
                    direction = det.direction,
                    materiality = det.materiality,
                    confidence = det.confidence,
                    decayFactor = det.decayFactor,
                    catalystTag = det.catalystTag,
                    rationale = det.rationale,
                    usedLlm = false
                )
            }
        }.sortedWith(compareByDescending<ScoredCatalystHeadline> { it.impactScore }.thenByDescending { it.publishedAtMs })

        // Aggregate across the ticker's headlines weighted by recency decay, materiality, and confidence
        var weightSum = 0.0
        var dirWeighted = 0.0
        var scoreWeighted = 0.0
        var confWeighted = 0.0
        var peakMat = 0.0
        var peakImpact = 0
        var peakAbsScore = 0

        scored.forEach { item ->
            val w = max(0.15, item.decayFactor * (0.35 + item.materiality) * item.confidence)
            weightSum += w
            dirWeighted += item.direction * w
            scoreWeighted += item.catalystScore * w
            confWeighted += item.confidence * w
            if (item.materiality > peakMat) peakMat = item.materiality
            if (item.impactScore > peakImpact) peakImpact = item.impactScore
            if (abs(item.catalystScore) > peakAbsScore) peakAbsScore = abs(item.catalystScore)
        }

        val avgDir = if (weightSum > 0.0) (dirWeighted / weightSum).coerceIn(-1.0, 1.0) else 0.0
        val avgConf = if (weightSum > 0.0) (confWeighted / weightSum).coerceIn(0.25, 1.0) else 0.5
        val weightedScore = if (weightSum > 0.0) scoreWeighted / weightSum else 0.0

        // Blend weighted average with dominant catalyst so a critical breaking headline isn't diluted away
        val dominant = scored.first()
        val blendedScore = (0.55 * weightedScore + 0.45 * dominant.catalystScore)
            .roundToInt()
            .coerceIn(-100, 100)
        val aggregatedMat = ((0.65 * peakMat) + (0.35 * scored.map { it.materiality }.average()))
            .coerceIn(0.0, 1.0)
        val aggregatedImpact = max(
            abs(blendedScore),
            ((0.70 * peakImpact) + (0.30 * scored.map { it.impactScore }.average())).roundToInt()
        ).coerceIn(0, 100)

        val level = classifyImpactLevel(
            absCatalystScore = max(abs(blendedScore), peakAbsScore),
            materiality = aggregatedMat,
            impactScore = aggregatedImpact,
            llmHint = if (dominant.usedLlm) dominant.impactLevel else null
        )

        val anyLlm = usedLlm && scored.any { it.usedLlm }

        return TickerCatalystReport(
            symbol = cleanSym,
            catalystScore = blendedScore,
            impactScore = aggregatedImpact,
            impactLevel = level,
            direction = avgDir,
            materiality = (aggregatedMat * 100.0).roundToInt() / 100.0,
            confidence = (avgConf * 100.0).roundToInt() / 100.0,
            primaryTag = dominant.catalystTag,
            llmSummary = dominant.rationale,
            scorerLabel = if (anyLlm) scorerLabel else "QUANT MODEL",
            headlineSource = headlineSource,
            usedLlm = anyLlm,
            headlineCount = scored.size,
            headlines = scored,
            analyzedAtMs = nowMs
        )
    }

    /**
     * Aggregates per-headline evaluations from ALL responding AI providers into:
     * 1. Per-AI individual [AiProviderTickerVerdict] ratings for the ticker
     * 2. Confidence-weighted multi-AI consensus [TickerCatalystReport] with directional agreement %
     */
    fun aggregateTickerMultiAi(
        symbol: String,
        rawHeadlines: List<RawTickerHeadline>,
        scoresByProvider: Map<AiProviderId, Map<String, LlmHeadlineCatalyst>>,
        modelIdByProvider: Map<AiProviderId, String>,
        scorerLabel: String,
        headlineSource: String,
        nowMs: Long = System.currentTimeMillis()
    ): TickerCatalystReport {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (rawHeadlines.isEmpty()) {
            return aggregateTicker(
                symbol = cleanSym,
                rawHeadlines = emptyList(),
                llmScoresByHeadlineId = emptyMap(),
                scorerLabel = scorerLabel,
                usedLlm = false,
                headlineSource = headlineSource,
                nowMs = nowMs
            )
        }

        val headlineIds = rawHeadlines.map { it.id }.toSet()

        // 1. Compute individual stock-level verdict for each AI provider that scored at least 1 headline
        val aiVerdicts = ArrayList<AiProviderTickerVerdict>()
        scoresByProvider.forEach { (provider, providerMap) ->
            val relevant = providerMap.filterKeys { it in headlineIds }
            if (relevant.isNotEmpty()) {
                val modelId = modelIdByProvider[provider].orEmpty()
                val singleReport = aggregateTicker(
                    symbol = cleanSym,
                    rawHeadlines = rawHeadlines,
                    llmScoresByHeadlineId = relevant,
                    scorerLabel = provider.displayName,
                    usedLlm = true,
                    headlineSource = headlineSource,
                    nowMs = nowMs
                )
                aiVerdicts.add(
                    AiProviderTickerVerdict(
                        provider = provider,
                        providerName = provider.displayName,
                        modelId = modelId,
                        catalystScore = singleReport.catalystScore,
                        impactScore = singleReport.impactScore,
                        impactLevel = singleReport.impactLevel,
                        direction = singleReport.direction,
                        materiality = singleReport.materiality,
                        confidence = singleReport.confidence,
                        catalystTag = singleReport.primaryTag,
                        rationale = singleReport.llmSummary
                    )
                )
            }
        }

        // 2. Blend per-headline scores across all responding AI providers (confidence-weighted)
        val consensusPerHeadline = LinkedHashMap<String, LlmHeadlineCatalyst>()
        val modelCountByHeadline = LinkedHashMap<String, Int>()

        rawHeadlines.forEach { raw ->
            val votes = scoresByProvider.values.mapNotNull { it[raw.id] }
            if (votes.isNotEmpty()) {
                modelCountByHeadline[raw.id] = votes.size
                val totalWeight = votes.sumOf { it.confidence.coerceIn(0.25, 1.0) }.coerceAtLeast(0.25)
                val blendedDir = votes.sumOf { it.direction.coerceIn(-1.0, 1.0) * it.confidence.coerceIn(0.25, 1.0) } / totalWeight
                val blendedMat = votes.sumOf { it.materiality.coerceIn(0.0, 1.0) * it.confidence.coerceIn(0.25, 1.0) } / totalWeight
                val avgConf = (votes.map { it.confidence.coerceIn(0.25, 1.0) }.average()).coerceIn(0.25, 1.0)
                val avgHintRank = votes.mapNotNull { it.impactLevelHint?.rank }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                val consensusHint = when (avgHintRank) {
                    4 -> CatalystImpactLevel.CRITICAL
                    3 -> CatalystImpactLevel.HIGH
                    2 -> CatalystImpactLevel.MODERATE
                    1 -> CatalystImpactLevel.LOW
                    else -> null
                }
                val topTag = votes.map { it.catalystTag.trim().uppercase(Locale.US) }
                    .filter { it.isNotBlank() && it.length <= 14 }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    .orEmpty()
                val bestRationale = votes
                    .maxByOrNull { it.confidence * (0.4 + it.materiality) }
                    ?.rationale
                    .orEmpty()

                consensusPerHeadline[raw.id] = LlmHeadlineCatalyst(
                    direction = blendedDir.coerceIn(-1.0, 1.0),
                    materiality = blendedMat.coerceIn(0.0, 1.0),
                    confidence = avgConf,
                    impactLevelHint = consensusHint,
                    catalystTag = topTag,
                    rationale = bestRationale
                )
            }
        }

        val usedAnyLlm = consensusPerHeadline.isNotEmpty()
        val baseReport = aggregateTicker(
            symbol = cleanSym,
            rawHeadlines = rawHeadlines,
            llmScoresByHeadlineId = consensusPerHeadline,
            scorerLabel = scorerLabel,
            usedLlm = usedAnyLlm,
            headlineSource = headlineSource,
            nowMs = nowMs
        )

        // 3. Measure multi-AI consensus agreement (0..100%) across responding providers
        val agreementPct = computeAiAgreementPct(aiVerdicts)
        val enrichedHeadlines = baseReport.headlines.map { h ->
            h.copy(aiModelsCount = modelCountByHeadline[h.id] ?: if (h.usedLlm) 1 else 0)
        }

        return baseReport.copy(
            headlines = enrichedHeadlines,
            aiVerdicts = aiVerdicts.sortedByDescending { abs(it.catalystScore) },
            aiAgreementPct = agreementPct
        )
    }

    /**
     * Computes 0..100% agreement across responding AI providers based on sign alignment and
     * dispersion of their individual catalyst scores.
     */
    fun computeAiAgreementPct(verdicts: List<AiProviderTickerVerdict>): Int {
        if (verdicts.size <= 1) return 100
        val scores = verdicts.map { it.catalystScore.toDouble() }
        val mean = scores.average()
        val meanAbsDev = scores.map { abs(it - mean) }.average()
        val spreadAgreement = (1.0 - (meanAbsDev / 60.0)).coerceIn(0.0, 1.0)

        val bullish = verdicts.count { it.catalystScore >= 10 }
        val bearish = verdicts.count { it.catalystScore <= -10 }
        val neutral = verdicts.size - bullish - bearish
        val majorityCount = maxOf(bullish, bearish, neutral)
        val signAgreement = majorityCount.toDouble() / verdicts.size.toDouble()

        return ((0.60 * signAgreement + 0.40 * spreadAgreement) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    /**
     * Builds the live configuration status roster of all implemented AI providers prior to a run.
     */
    fun buildConfiguredProviderRoster(config: AppConfig): List<AiProviderCatalystRun> {
        val routing = config.aiRouting
        val fallbackGeminiKey = config.geminiKey.trim()
        return AiProviderId.entries.map { provider ->
            val settings = routing.settingsFor(provider)
            val key = ModelRadarEngine.resolveApiKey(provider, routing, fallbackGeminiKey)
            val modelId = settings.selectedModelId.trim().ifBlank { defaultModelIdFor(provider) }
            val status = when {
                !settings.enabled -> AiProviderRunStatus.DISABLED
                key.isBlank() -> AiProviderRunStatus.NO_KEY
                else -> AiProviderRunStatus.READY
            }
            AiProviderCatalystRun(
                provider = provider,
                providerName = provider.displayName,
                modelId = modelId,
                status = status
            )
        }
    }

    fun defaultModelIdFor(provider: AiProviderId): String = when (provider) {
        AiProviderId.GEMINI -> "gemini-2.5-flash"
        AiProviderId.OPENROUTER -> "google/gemini-2.0-flash-exp:free"
        AiProviderId.AIHUBMIX -> "gpt-4o-mini"
        AiProviderId.DEEPSEEK -> "deepseek-chat"
        AiProviderId.GROQ -> "llama-3.3-70b-versatile"
    }

    /**
     * Extracts per-headline catalyst assessments from strict or markdown-wrapped LLM JSON output.
     * Supports both `{"items":[...]}` and `{"catalysts":[...]}`.
     */
    fun parseLlmCatalystJson(rawText: String): Map<String, LlmHeadlineCatalyst> {
        val json = extractJsonObject(rawText) ?: return emptyMap()
        val payload = try {
            mapAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        val rawList = (payload["items"] as? List<*>)
            ?: (payload["catalysts"] as? List<*>)
            ?: (payload["scores"] as? List<*>)
            ?: return emptyMap()

        val out = LinkedHashMap<String, LlmHeadlineCatalyst>()
        for (entry in rawList) {
            val row = entry as? Map<*, *> ?: continue
            val id = row["id"]?.toString()?.trim().orEmpty()
            if (id.isEmpty()) continue
            val direction = (asDouble(row["direction"]) ?: 0.0).coerceIn(-1.0, 1.0)
            val materiality = (asDouble(row["materiality"]) ?: 0.35).coerceIn(0.0, 1.0)
            val confidence = (asDouble(row["confidence"]) ?: 0.72).coerceIn(0.25, 1.0)
            val impactHint = CatalystImpactLevel.fromWire(row["impact_level"]?.toString() ?: row["impactLevel"]?.toString())
            val tag = (row["catalyst_tag"] ?: row["catalystTag"] ?: row["tag"])?.toString()?.trim().orEmpty()
            val rationale = (row["rationale"] ?: row["reason"] ?: row["summary"])?.toString()?.trim().orEmpty()
            out[id] = LlmHeadlineCatalyst(
                direction = direction,
                materiality = materiality,
                confidence = confidence,
                impactLevelHint = impactHint,
                catalystTag = tag,
                rationale = rationale
            )
        }
        return out
    }

    /**
     * Parses Yahoo Finance ticker RSS XML (`https://feeds.finance.yahoo.com/rss/2.0/headline?s=SYM`)
     * into [RawTickerHeadline] entries without Android framework dependencies.
     */
    fun parseYahooRssXml(
        xml: String,
        symbol: String,
        nowMs: Long = System.currentTimeMillis(),
        maxItems: Int = 5
    ): List<RawTickerHeadline> {
        if (xml.isBlank()) return emptyList()
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val out = ArrayList<RawTickerHeadline>()
        val seenTitles = HashSet<String>()

        ITEM_REGEX.findAll(xml).forEachIndexed { idx, match ->
            if (out.size >= maxItems) return@forEachIndexed
            val block = match.groupValues[1]
            val title = cleanXmlText(extractTag(block, "title"))
            if (title.isBlank()) return@forEachIndexed
            val normKey = title.lowercase(Locale.US)
            if (!seenTitles.add(normKey)) return@forEachIndexed

            val desc = cleanXmlText(extractTag(block, "description"))
            val link = cleanXmlText(extractTag(block, "link"))
            val pubRaw = cleanXmlText(extractTag(block, "pubDate"))
            val sourceTag = cleanXmlText(extractTag(block, "source"))
            val pubMs = parseRfc822OrDefault(pubRaw, nowMs - idx * 3_600_000L)

            out.add(
                RawTickerHeadline(
                    id = "YRSS_${cleanSym}_${idx}_${normKey.hashCode()}",
                    symbol = cleanSym,
                    headline = title,
                    summary = desc.take(280),
                    source = sourceTag.ifBlank { "Yahoo Finance" },
                    url = link,
                    publishedAtMs = pubMs
                )
            )
        }
        return out
    }

    fun filterReports(
        reports: List<TickerCatalystReport>,
        impactFilter: CatalystImpactFilter,
        query: String = ""
    ): List<TickerCatalystReport> {
        val cleanQuery = query.trim().uppercase(Locale.US)
        return reports.filter { report ->
            val matchesImpact = when (impactFilter) {
                CatalystImpactFilter.ALL -> true
                CatalystImpactFilter.CRITICAL -> report.impactLevel == CatalystImpactLevel.CRITICAL
                CatalystImpactFilter.HIGH -> report.impactLevel == CatalystImpactLevel.HIGH
                CatalystImpactFilter.MODERATE -> report.impactLevel == CatalystImpactLevel.MODERATE
                CatalystImpactFilter.LOW -> report.impactLevel == CatalystImpactLevel.LOW
            }
            if (!matchesImpact) return@filter false
            if (cleanQuery.isEmpty()) return@filter true
            report.symbol.uppercase(Locale.US).contains(cleanQuery) ||
                report.primaryTag.uppercase(Locale.US).contains(cleanQuery) ||
                report.llmSummary.uppercase(Locale.US).contains(cleanQuery) ||
                report.headlines.any { it.headline.uppercase(Locale.US).contains(cleanQuery) }
        }
    }

    fun summarize(reports: List<TickerCatalystReport>): TickerCatalystSummary =
        TickerCatalystSummary(
            totalTickers = reports.size,
            criticalCount = reports.count { it.impactLevel == CatalystImpactLevel.CRITICAL },
            highCount = reports.count { it.impactLevel == CatalystImpactLevel.HIGH },
            moderateCount = reports.count { it.impactLevel == CatalystImpactLevel.MODERATE },
            lowCount = reports.count { it.impactLevel == CatalystImpactLevel.LOW },
            bullishCount = reports.count { it.catalystScore >= 15 },
            bearishCount = reports.count { it.catalystScore <= -15 }
        )

    private fun extractJsonObject(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1)
            }
            val lastFence = text.lastIndexOf("```")
            if (lastFence >= 0) {
                text = text.substring(0, lastFence)
            }
            text = text.trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun extractTag(block: String, tagName: String): String {
        val regex = Regex("<$tagName\\b[^>]*>(.*?)</$tagName>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(block)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun cleanXmlText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.trim()
        if (text.startsWith("<![CDATA[") && text.endsWith("]]>")) {
            text = text.removePrefix("<![CDATA[").removeSuffix("]]>")
        }
        text = text.replace(TAG_HTML_REGEX, " ")
        text = text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun parseRfc822OrDefault(raw: String, fallbackMs: Long): Long {
        if (raw.isBlank()) return fallbackMs
        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(raw).toEpochMilli()
            } catch (_: Exception) {
                fallbackMs
            }
        }
    }

    private fun asDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun buildFallbackHeadlineRationale(
        symbol: String,
        score: Int,
        level: CatalystImpactLevel,
        tag: String
    ): String = when {
        score >= 35 -> "${level.label} bullish $tag catalyst detected for $symbol."
        score <= -35 -> "${level.label} bearish $tag catalyst flags downside risk for $symbol."
        score >= 10 -> "Constructive $tag headline supporting $symbol near-term tone."
        score <= -10 -> "Adverse $tag coverage weighing on $symbol momentum."
        else -> "Routine $tag coverage for $symbol with limited catalyst impact."
    }
}
