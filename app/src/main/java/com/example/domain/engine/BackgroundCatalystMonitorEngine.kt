package com.example.domain.engine

import java.util.Locale
import kotlin.math.abs

enum class BackgroundMonitorAlertKind(val badge: String) {
    CATALYST_PLUS_MOVE("CATALYST + MOVE"),
    HIGH_IMPACT_CATALYST("HIGH CATALYST"),
    SIGNIFICANT_MARKET_MOVE("MARKET MOVE")
}

data class BackgroundMonitorConfig(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 15,
    val minCatalystScoreAbs: Int = 50,
    val minImpactLevel: CatalystImpactLevel = CatalystImpactLevel.HIGH,
    val minPriceMovePct: Double = 3.0,
    val watchlistOnly: Boolean = false
)

data class TickerPriceMove(
    val symbol: String,
    val currentPrice: Double,
    val previousClose: Double,
    val changePct: Double
)

data class BackgroundMarketAlert(
    val dedupeKey: String,
    val symbol: String,
    val kind: BackgroundMonitorAlertKind,
    val title: String,
    val body: String,
    val catalystScore: Int? = null,
    val impactLevel: CatalystImpactLevel? = null,
    val priceChangePct: Double? = null,
    val currentPrice: Double? = null,
    val headline: String = "",
    val rationale: String = "",
    val detectedAtMs: Long
)

data class BackgroundMonitorRunStatus(
    val lastRunAtMs: Long = 0L,
    val tickersChecked: Int = 0,
    val alertsTriggered: Int = 0,
    val lastSummary: String = "",
    val scorerLabel: String = "",
    val succeeded: Boolean = true
)

/**
 * Pure domain evaluation engine used by [com.example.data.worker.CatalystMonitorWorker]
 * to detect high-impact catalyst scores and significant market price movements while
 * deduplicating notifications across consecutive periodic background sweeps.
 */
object BackgroundCatalystMonitorEngine {

    private const val MOVE_DEDUPE_BUCKET_MS = 6L * 3_600_000L

    fun evaluateAlerts(
        reports: List<TickerCatalystReport>,
        priceMovesBySymbol: Map<String, TickerPriceMove>,
        config: BackgroundMonitorConfig,
        watchlist: List<String>,
        alreadyNotifiedKeys: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<BackgroundMarketAlert> {
        if (!config.enabled) return emptyList()

        val watchSet = watchlist.map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
        val reportBySymbol = reports.associateBy { it.symbol.trim().uppercase(Locale.US) }
        val normalizedMoves = priceMovesBySymbol.entries.associate { (k, v) ->
            k.trim().uppercase(Locale.US) to v
        }

        val candidateSymbols = (reportBySymbol.keys + normalizedMoves.keys)
            .filter { sym ->
                !config.watchlistOnly || watchSet.isEmpty() || watchSet.contains(sym)
            }
            .sorted()

        val alerts = ArrayList<BackgroundMarketAlert>()
        val timeBucket = (nowMs / MOVE_DEDUPE_BUCKET_MS).coerceAtLeast(0L)

        for (symbol in candidateSymbols) {
            val report = reportBySymbol[symbol]
            val move = normalizedMoves[symbol]

            val hasHighCatalyst = report != null && report.headlineCount > 0 && (
                report.impactLevel.rank >= config.minImpactLevel.rank ||
                    abs(report.catalystScore) >= config.minCatalystScoreAbs
                )
            val hasBigMove = move != null && abs(move.changePct) >= config.minPriceMovePct

            if (!hasHighCatalyst && !hasBigMove) continue

            val kind = when {
                hasHighCatalyst && hasBigMove -> BackgroundMonitorAlertKind.CATALYST_PLUS_MOVE
                hasHighCatalyst -> BackgroundMonitorAlertKind.HIGH_IMPACT_CATALYST
                else -> BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE
            }

            val topHeadline = report?.headlines?.firstOrNull()?.headline.orEmpty()
            val dedupeKey = buildDedupeKey(
                symbol = symbol,
                kind = kind,
                topHeadline = topHeadline,
                moveDirectionSign = if ((move?.changePct ?: 0.0) >= 0.0) "UP" else "DN",
                timeBucket = timeBucket
            )
            if (alreadyNotifiedKeys.contains(dedupeKey)) continue

            val signedScore = report?.catalystScore?.let { if (it > 0) "+$it" else "$it" }
            val signedMove = move?.changePct?.let {
                val sign = if (it >= 0.0) "+" else ""
                "$sign${String.format(Locale.US, "%.2f", it)}%"
            }
            val priceText = move?.currentPrice?.let { "$${String.format(Locale.US, "%.2f", it)}" }

            val title = when (kind) {
                BackgroundMonitorAlertKind.CATALYST_PLUS_MOVE ->
                    "$symbol ${report!!.impactLevel.label} Catalyst ($signedScore) · Move $signedMove"
                BackgroundMonitorAlertKind.HIGH_IMPACT_CATALYST ->
                    "$symbol ${report!!.impactLevel.label} Catalyst Score $signedScore (${report.primaryTag})"
                BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE ->
                    "$symbol Significant Market Move $signedMove ($priceText)"
            }

            val body = when (kind) {
                BackgroundMonitorAlertKind.CATALYST_PLUS_MOVE -> buildString {
                    append("$symbol trading at $priceText ($signedMove). ")
                    if (topHeadline.isNotBlank()) append(topHeadline)
                    if (!report?.llmSummary.isNullOrBlank()) {
                        append("\nAI Catalyst: ").append(report!!.llmSummary)
                    }
                }
                BackgroundMonitorAlertKind.HIGH_IMPACT_CATALYST -> buildString {
                    if (topHeadline.isNotBlank()) append(topHeadline)
                    if (!report?.llmSummary.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("AI Catalyst (${report!!.scorerLabel}): ").append(report.llmSummary)
                    }
                }
                BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE -> buildString {
                    val dirWord = if ((move?.changePct ?: 0.0) >= 0.0) "rallied" else "dropped"
                    append("$symbol $dirWord $signedMove to $priceText vs prior close $${String.format(Locale.US, "%.2f", move!!.previousClose)}.")
                    if (topHeadline.isNotBlank()) {
                        append("\nLatest headline: ").append(topHeadline)
                    }
                }
            }

            alerts.add(
                BackgroundMarketAlert(
                    dedupeKey = dedupeKey,
                    symbol = symbol,
                    kind = kind,
                    title = title,
                    body = body,
                    catalystScore = report?.catalystScore,
                    impactLevel = report?.impactLevel,
                    priceChangePct = move?.changePct,
                    currentPrice = move?.currentPrice,
                    headline = topHeadline,
                    rationale = report?.llmSummary.orEmpty(),
                    detectedAtMs = nowMs
                )
            )
        }

        // Prioritize combined Catalyst+Move, then higher impact tier, then larger magnitude
        return alerts.sortedWith(
            compareBy<BackgroundMarketAlert> { it.kind.ordinal }
                .thenByDescending { it.impactLevel?.rank ?: 0 }
                .thenByDescending { abs(it.catalystScore ?: 0) }
                .thenByDescending { abs(it.priceChangePct ?: 0.0) }
        )
    }

    private fun buildDedupeKey(
        symbol: String,
        kind: BackgroundMonitorAlertKind,
        topHeadline: String,
        moveDirectionSign: String,
        timeBucket: Long
    ): String {
        val normHead = topHeadline.trim().lowercase(Locale.US).hashCode()
        return when (kind) {
            BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE ->
                "BG_MOVE_${symbol}_${moveDirectionSign}_$timeBucket"
            BackgroundMonitorAlertKind.HIGH_IMPACT_CATALYST ->
                "BG_CAT_${symbol}_$normHead"
            BackgroundMonitorAlertKind.CATALYST_PLUS_MOVE ->
                "BG_BOTH_${symbol}_${normHead}_$timeBucket"
        }
    }
}
