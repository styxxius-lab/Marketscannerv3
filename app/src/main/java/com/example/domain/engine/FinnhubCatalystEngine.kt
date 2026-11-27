package com.example.domain.engine

import com.example.domain.model.AnnotatedNewsArticle
import com.example.domain.model.CatalystScoreSource
import com.example.domain.model.NewsArticleFilter
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalRow
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class RawFinnhubArticle(
    val id: String,
    val symbol: String,
    val headline: String,
    val summary: String,
    val source: String,
    val category: String,
    val url: String,
    val publishedAtMs: Long
)

data class AiCatalystAssessment(
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val catalystTag: String = "",
    val rationale: String = ""
)

/**
 * Pure domain engine for computing AI-generated catalyst scores (-100..+100),
 * 18-hour exponential recency decay, event taxonomy tags, and filtering on
 * Finnhub financial news articles.
 */
object FinnhubCatalystEngine {

    const val HALF_LIFE_HOURS = 18.0
    const val HIGH_IMPACT_SCORE_ABS = 45

    private val POSITIVE_TERMS = listOf(
        "beats", "beat", "raises guidance", "raised guidance", "upgrade", "upgraded",
        "record revenue", "strong demand", "surge", "surges", "soars", "approval",
        "fda approves", "contract win", "partnership", "buyback", "dividend hike",
        "outperform", "accelerates", "expansion", "breakthrough", "rally", "bullish",
        "exceeds", "stronger than expected", "profit jumps", "revenue rises"
    )

    private val NEGATIVE_TERMS = listOf(
        "misses", "miss", "cuts guidance", "lowered guidance", "downgrade", "downgraded",
        "sec probe", "investigation", "lawsuit", "recall", "layoffs", "bankruptcy",
        "restatement", "accounting", "delay", "delayed", "slump", "plunges", "tumbles",
        "warning", "warns", "tariff", "sanction", "fine", "penalty", "breach", "hack",
        "underperform", "weak demand", "bearish", "fraud", "resigns", "resignation"
    )

    private val STRUCTURAL_TERMS = listOf(
        "guidance", "earnings", "quarter", "q1", "q2", "q3", "q4", "revenue",
        "sec", "doj", "ftc", "fda", "acquisition", "merger", "buyout", "restatement",
        "fed", "fomc", "cpi", "inflation", "tariff", "export control", "antitrust"
    )

    fun computeDecay(publishedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Double {
        if (publishedAtMs <= 0L) return 0.5
        val ageHours = max((nowMs - publishedAtMs) / 3_600_000.0, 0.0)
        return 0.5.pow(ageHours / HALF_LIFE_HOURS).coerceIn(0.10, 1.0)
    }

    /**
     * Converts direction (-1..+1), materiality (0..1), confidence (0..1), and
     * 18h recency decay into a signed catalyst score on [-100, +100].
     */
    fun computeCatalystScore(
        direction: Double,
        materiality: Double,
        confidence: Double,
        decayFactor: Double
    ): Int {
        val dir = direction.coerceIn(-1.0, 1.0)
        val mat = materiality.coerceIn(0.0, 1.0)
        val conf = confidence.coerceIn(0.25, 1.0)
        val decayWeight = (0.55 + 0.45 * decayFactor.coerceIn(0.0, 1.0))
        val raw = dir * mat * conf * decayWeight * 100.0
        return raw.roundToInt().coerceIn(-100, 100)
    }

    fun classifyCatalystTag(headline: String, summary: String, category: String): String {
        val text = "$headline $summary $category".lowercase(Locale.US)
        return when {
            text.contains("guidance") || text.contains("outlook") || text.contains("forecast") -> "GUIDANCE"
            text.contains("earnings") || text.contains("eps") || text.contains("quarter") || text.contains("revenue") -> "EARNINGS"
            text.contains("sec ") || text.contains("doj") || text.contains("antitrust") || text.contains("tariff") || text.contains("fda") || text.contains("regulator") -> "REGULATORY"
            text.contains("merger") || text.contains("acquir") || text.contains("buyout") || text.contains("takeover") -> "M&A"
            text.contains("upgrade") || text.contains("downgrade") || text.contains("price target") || text.contains("analyst") -> "ANALYST"
            text.contains("fed") || text.contains("fomc") || text.contains("cpi") || text.contains("inflation") || text.contains("payroll") || text.contains("treasury") -> "MACRO"
            text.contains("ai ") || text.contains("chip") || text.contains("launch") || text.contains("product") || text.contains("contract") -> "PRODUCT"
            else -> "MARKET"
        }
    }

    fun annotateWithAiScores(
        rawArticles: List<RawFinnhubArticle>,
        aiAssessmentsByIndex: Map<Int, AiCatalystAssessment>,
        scoreSource: CatalystScoreSource,
        nowMs: Long = System.currentTimeMillis()
    ): List<AnnotatedNewsArticle> {
        return rawArticles.mapIndexed { idx, raw ->
            val ai = aiAssessmentsByIndex[idx]
            if (ai != null) {
                val decay = computeDecay(raw.publishedAtMs, nowMs)
                val dir = ai.direction.coerceIn(-1.0, 1.0)
                val mat = ai.materiality.coerceIn(0.0, 1.0)
                val conf = ai.confidence.coerceIn(0.25, 1.0)
                val score = computeCatalystScore(dir, mat, conf, decay)
                val tag = ai.catalystTag.trim().uppercase(Locale.US)
                    .takeIf { it.isNotBlank() && it.length <= 14 }
                    ?: classifyCatalystTag(raw.headline, raw.summary, raw.category)
                AnnotatedNewsArticle(
                    id = raw.id,
                    symbol = raw.symbol.ifBlank { "MACRO" }.uppercase(Locale.US),
                    headline = raw.headline,
                    summary = raw.summary,
                    source = raw.source.ifBlank { "Finnhub" },
                    category = raw.category.ifBlank { "general" },
                    url = raw.url,
                    publishedAtMs = raw.publishedAtMs,
                    catalystScore = score,
                    direction = dir,
                    materiality = mat,
                    confidence = conf,
                    decayFactor = (decay * 100.0).roundToInt() / 100.0,
                    catalystTag = tag,
                    rationale = ai.rationale.trim().ifBlank { buildFallbackRationale(score, tag) },
                    scoreSource = scoreSource,
                    isFromFinnhub = true
                )
            } else {
                scoreDeterministic(raw, nowMs)
            }
        }.sortedByDescending { it.publishedAtMs }
    }

    fun scoreDeterministic(
        raw: RawFinnhubArticle,
        nowMs: Long = System.currentTimeMillis()
    ): AnnotatedNewsArticle {
        val text = "${raw.headline} ${raw.summary}".lowercase(Locale.US)
        val posHits = POSITIVE_TERMS.count { text.contains(it) }
        val negHits = NEGATIVE_TERMS.count { text.contains(it) }
        val structuralHits = STRUCTURAL_TERMS.count { text.contains(it) }

        val totalPolarity = posHits + negHits
        val direction = when {
            totalPolarity == 0 -> 0.0
            else -> ((posHits - negHits).toDouble() / max(totalPolarity, 2).toDouble()).coerceIn(-1.0, 1.0)
        }
        val baseMat = 0.22 + (structuralHits * 0.16) + (totalPolarity * 0.12)
        val materiality = baseMat.coerceIn(0.18, 0.92)
        val confidence = if (totalPolarity > 0) 0.78 else 0.52
        val decay = computeDecay(raw.publishedAtMs, nowMs)
        val score = computeCatalystScore(direction, materiality, confidence, decay)
        val tag = classifyCatalystTag(raw.headline, raw.summary, raw.category)

        return AnnotatedNewsArticle(
            id = raw.id,
            symbol = raw.symbol.ifBlank { "MACRO" }.uppercase(Locale.US),
            headline = raw.headline,
            summary = raw.summary,
            source = raw.source.ifBlank { "Finnhub" },
            category = raw.category.ifBlank { "general" },
            url = raw.url,
            publishedAtMs = raw.publishedAtMs,
            catalystScore = score,
            direction = direction,
            materiality = materiality,
            confidence = confidence,
            decayFactor = (decay * 100.0).roundToInt() / 100.0,
            catalystTag = tag,
            rationale = buildFallbackRationale(score, tag),
            scoreSource = CatalystScoreSource.FINNHUB_MODEL,
            isFromFinnhub = true
        )
    }

    fun buildFromScannedHeadlines(
        rows: List<SignalRow>,
        headlines: List<NewsHeadline>,
        nowMs: Long = System.currentTimeMillis()
    ): List<AnnotatedNewsArticle> {
        val articles = ArrayList<AnnotatedNewsArticle>()
        val seenTitles = HashSet<String>()

        rows.forEach { row ->
            row.newsItems.forEachIndexed { idx, item ->
                val normTitle = item.title.trim().lowercase(Locale.US)
                if (normTitle.isBlank() || !seenTitles.add("${row.symbol}:$normTitle")) return@forEachIndexed
                val matchedHeadline = headlines.firstOrNull {
                    it.sym.equals(row.symbol, ignoreCase = true) &&
                        it.title.equals(item.title, ignoreCase = true)
                }
                val pubMs = parseIsoOrDefault(matchedHeadline?.publishedIso, nowMs - idx * 1_800_000L)
                val decay = item.decay.coerceIn(0.1, 1.0)
                val score = computeCatalystScore(item.direction, item.materiality, 0.85, decay)
                val tag = classifyCatalystTag(item.title, item.rationale, "")
                articles.add(
                    AnnotatedNewsArticle(
                        id = "SCAN_${row.symbol}_${idx}_${normTitle.hashCode()}",
                        symbol = row.symbol.uppercase(Locale.US),
                        headline = item.title,
                        summary = matchedHeadline?.summary.orEmpty(),
                        source = matchedHeadline?.source?.ifBlank { "Market Scan" } ?: "Market Scan",
                        category = "equity",
                        url = "",
                        publishedAtMs = pubMs,
                        catalystScore = score,
                        direction = item.direction,
                        materiality = item.materiality,
                        confidence = 0.85,
                        decayFactor = decay,
                        catalystTag = tag,
                        rationale = item.rationale.takeIf { it.isNotBlank() && it != "demo" }
                            ?: buildFallbackRationale(score, tag),
                        scoreSource = if (item.rationale == "demo") CatalystScoreSource.FINNHUB_MODEL else CatalystScoreSource.AI_GEMINI,
                        isFromFinnhub = false
                    )
                )
            }
        }
        return articles.sortedByDescending { abs(it.catalystScore) }
    }

    fun filterArticles(
        articles: List<AnnotatedNewsArticle>,
        filter: NewsArticleFilter,
        watchlist: List<String>,
        searchQuery: String = ""
    ): List<AnnotatedNewsArticle> {
        val watchSet = watchlist.map { it.trim().uppercase(Locale.US) }.toSet()
        val cleanQuery = searchQuery.trim().uppercase(Locale.US)

        return articles.filter { art ->
            val matchesFilter = when (filter) {
                NewsArticleFilter.ALL -> true
                NewsArticleFilter.BULLISH -> art.catalystScore >= 10
                NewsArticleFilter.BEARISH -> art.catalystScore <= -10
                NewsArticleFilter.HIGH_IMPACT -> abs(art.catalystScore) >= HIGH_IMPACT_SCORE_ABS || art.materiality >= 0.65
                NewsArticleFilter.WATCHLIST -> watchSet.contains(art.symbol.uppercase(Locale.US))
            }
            if (!matchesFilter) return@filter false
            if (cleanQuery.isEmpty()) return@filter true
            art.symbol.uppercase(Locale.US).contains(cleanQuery) ||
                art.headline.uppercase(Locale.US).contains(cleanQuery) ||
                art.source.uppercase(Locale.US).contains(cleanQuery) ||
                art.catalystTag.uppercase(Locale.US).contains(cleanQuery)
        }
    }

    private fun parseIsoOrDefault(iso: String?, fallbackMs: Long): Long {
        if (iso.isNullOrBlank()) return fallbackMs
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            fallbackMs
        }
    }

    private fun buildFallbackRationale(score: Int, tag: String): String = when {
        score >= 45 -> "High-impact bullish $tag catalyst detected from Finnhub headline."
        score >= 10 -> "Moderately constructive $tag development with positive tone."
        score <= -45 -> "High-impact adverse $tag catalyst flagging downside pressure."
        score <= -10 -> "Negative $tag sentiment weighing on near-term momentum."
        else -> "Neutral $tag coverage with limited directional catalyst impact."
    }
}
