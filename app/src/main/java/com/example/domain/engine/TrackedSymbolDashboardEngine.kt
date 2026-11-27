package com.example.domain.engine

import com.example.domain.model.MarketEnvironment
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.VixSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DashboardSymbolFilter(val label: String) {
    TRACKED("Tracked"),
    ALL("All"),
    AI_SCORED("AI Scored"),
    VETOED("Vetoed")
}

enum class RegimeAlignmentStatus(val label: String) {
    ALIGNED("ALIGNED"),
    PANIC_GUARD("PANIC 50%"),
    GATED_MR("MR GATED"),
    DEFENSIVE("DEFENSIVE"),
    NEUTRAL("NEUTRAL")
}

enum class AiVerdictTone(val label: String) {
    VETOED("VETO"),
    BULLISH("BULLISH"),
    BEARISH("BEARISH"),
    MIXED("MIXED"),
    CLEAR("CLEAR"),
    UNSCORED("PENDING")
}

data class TrackedSymbolDashboardRow(
    val signal: SignalRow,
    val isWatchlisted: Boolean,
    val isHeld: Boolean,
    val marketEnvironment: MarketEnvironment,
    val regimeLabel: RegimeLabel,
    val regimeStatusText: String,
    val regimeAlignment: RegimeAlignmentStatus,
    val aiVerdictTone: AiVerdictTone,
    val aiNewsScore: Int, // -100 to +100 signed sentiment * materiality score
    val aiMaterialityPct: Int, // 0 to 100
    val advisorVerdict: AdvisorVetoVerdict?,
    val advisorTag: String, // "veto", "clear", "off"
    val recentHeadline: String,
    val verdictSummary: String
)

data class TrackedDashboardSummary(
    val totalTracked: Int,
    val bullishAiCount: Int,
    val bearishAiCount: Int,
    val vetoedCount: Int,
    val scoredCount: Int,
    val environment: MarketEnvironment,
    val regimeLabel: RegimeLabel,
    val panicGuardActive: Boolean
)

/**
 * Pure domain mapper that synthesizes tracked symbols, live market regime status,
 * quantitative news catalyst scores, and Advisor LLM veto verdicts into dashboard rows.
 */
object TrackedSymbolDashboardEngine {

    fun buildRows(
        rows: List<SignalRow>,
        regime: MarketRegime,
        watchlist: List<String>,
        heldSymbols: Set<String> = emptySet(),
        advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
        advisorEnabled: Boolean = false,
        vixSnapshot: VixSnapshot? = null
    ): List<TrackedSymbolDashboardRow> {
        if (rows.isEmpty()) return emptyList()
        val classification = MarketEnvironmentClassifier.classify(
            regime = regime,
            rows = rows,
            vixSnapshot = vixSnapshot
        )
        val env = classification.environment
        val watchSet = watchlist.map { it.trim().uppercase() }.toSet()
        val heldSet = heldSymbols.map { it.trim().uppercase() }.toSet()

        return rows.map { row ->
            val symUpper = row.symbol.uppercase()
            val isWatch = watchSet.contains(symUpper)
            val isHeld = heldSet.contains(symUpper)
            val verdict = advisorVerdicts[symUpper] ?: advisorVerdicts[row.symbol]

            val alignment = computeRegimeAlignment(row, regime)
            val regimeStatusText = buildRegimeStatusText(regime, env, alignment)

            val rawSigned = (row.newsDirection.coerceIn(-1.0, 1.0) * row.newsMateriality.coerceIn(0.0, 1.0))
            val aiScore = (rawSigned * 100.0).roundToInt().coerceIn(-100, 100)
            val matPct = (row.newsMateriality.coerceIn(0.0, 1.0) * 100.0).roundToInt()

            val advisorTag = when {
                verdict?.isVeto == true -> "veto"
                verdict != null -> verdict.llmTag.ifBlank { if (advisorEnabled) "clear" else "off" }
                advisorEnabled -> "clear"
                else -> "off"
            }

            val hasScoredNews = row.newsItems.isNotEmpty() || row.newsMateriality > 0.05 || abs(row.newsDirection) > 0.05
            val tone = when {
                verdict?.isVeto == true -> AiVerdictTone.VETOED
                hasScoredNews && aiScore >= 15 -> AiVerdictTone.BULLISH
                hasScoredNews && aiScore <= -15 -> AiVerdictTone.BEARISH
                hasScoredNews -> AiVerdictTone.MIXED
                verdict != null && !verdict.isVeto -> AiVerdictTone.CLEAR
                else -> AiVerdictTone.UNSCORED
            }

            val topNewsItem = row.newsItems.firstOrNull()
            val headline = when {
                verdict?.isVeto == true && verdict.headline.isNotBlank() -> verdict.headline
                row.keyCatalyst.isNotBlank() -> row.keyCatalyst
                topNewsItem != null && topNewsItem.title.isNotBlank() -> topNewsItem.title
                else -> ""
            }

            val summary = when {
                verdict?.isVeto == true -> {
                    val cat = verdict.category.ifBlank { "Adverse event" }
                    val why = verdict.why.ifBlank { "Material downside risk flagged" }
                    "$cat (${(verdict.weight * 100).roundToInt()}%) · $why"
                }
                topNewsItem != null && topNewsItem.rationale.isNotBlank() -> topNewsItem.rationale
                hasScoredNews -> {
                    val dirLabel = if (row.newsDirection >= 0) "Positive" else "Negative"
                    "$dirLabel news catalyst · materiality $matPct%"
                }
                verdict != null -> "18h headline veto screen passed (llm=$advisorTag)"
                else -> row.reasons.firstOrNull().orEmpty().ifBlank { "Awaiting AI news catalyst" }
            }

            TrackedSymbolDashboardRow(
                signal = row,
                isWatchlisted = isWatch,
                isHeld = isHeld,
                marketEnvironment = env,
                regimeLabel = regime.label,
                regimeStatusText = regimeStatusText,
                regimeAlignment = alignment,
                aiVerdictTone = tone,
                aiNewsScore = aiScore,
                aiMaterialityPct = matPct,
                advisorVerdict = verdict,
                advisorTag = advisorTag,
                recentHeadline = headline,
                verdictSummary = summary
            )
        }
    }

    fun selectForFilter(
        allRows: List<TrackedSymbolDashboardRow>,
        filter: DashboardSymbolFilter,
        maxTrackedFallback: Int = 6
    ): List<TrackedSymbolDashboardRow> {
        return when (filter) {
            DashboardSymbolFilter.TRACKED -> {
                val explicit = allRows.filter { it.isHeld || it.isWatchlisted }
                if (explicit.isNotEmpty()) {
                    explicit.sortedWith(
                        compareByDescending<TrackedSymbolDashboardRow> { it.isHeld }
                            .thenByDescending { it.aiVerdictTone == AiVerdictTone.VETOED }
                            .thenByDescending { it.signal.conviction }
                    )
                } else {
                    allRows
                        .sortedWith(
                            compareByDescending<TrackedSymbolDashboardRow> { it.aiVerdictTone == AiVerdictTone.VETOED }
                                .thenByDescending { it.signal.conviction }
                        )
                        .take(maxTrackedFallback)
                }
            }
            DashboardSymbolFilter.ALL -> allRows.sortedByDescending { it.signal.conviction }
            DashboardSymbolFilter.AI_SCORED -> allRows
                .filter { it.aiVerdictTone != AiVerdictTone.UNSCORED }
                .sortedWith(
                    compareByDescending<TrackedSymbolDashboardRow> { it.aiVerdictTone == AiVerdictTone.VETOED }
                        .thenByDescending { abs(it.aiNewsScore) }
                )
            DashboardSymbolFilter.VETOED -> allRows
                .filter { it.aiVerdictTone == AiVerdictTone.VETOED || it.aiVerdictTone == AiVerdictTone.BEARISH }
                .sortedWith(
                    compareByDescending<TrackedSymbolDashboardRow> { it.aiVerdictTone == AiVerdictTone.VETOED }
                        .thenBy { it.aiNewsScore }
                )
        }
    }

    fun summarize(
        rows: List<TrackedSymbolDashboardRow>,
        regime: MarketRegime
    ): TrackedDashboardSummary {
        val env = rows.firstOrNull()?.marketEnvironment ?: MarketEnvironment.NEUTRAL
        return TrackedDashboardSummary(
            totalTracked = rows.size,
            bullishAiCount = rows.count { it.aiVerdictTone == AiVerdictTone.BULLISH },
            bearishAiCount = rows.count { it.aiVerdictTone == AiVerdictTone.BEARISH },
            vetoedCount = rows.count { it.aiVerdictTone == AiVerdictTone.VETOED },
            scoredCount = rows.count { it.aiVerdictTone != AiVerdictTone.UNSCORED },
            environment = env,
            regimeLabel = regime.label,
            panicGuardActive = regime.panicGuardActive
        )
    }

    private fun computeRegimeAlignment(
        row: SignalRow,
        regime: MarketRegime
    ): RegimeAlignmentStatus {
        return when {
            regime.panicGuardActive -> RegimeAlignmentStatus.PANIC_GUARD
            row.strategyTag.equals("MEAN_REV", ignoreCase = true) -> RegimeAlignmentStatus.GATED_MR
            regime.label == RegimeLabel.RISK_ON && row.action == SignalAction.BUY -> RegimeAlignmentStatus.ALIGNED
            (regime.label == RegimeLabel.RISK_OFF || regime.label == RegimeLabel.CRISIS) && row.action == SignalAction.SELL ->
                RegimeAlignmentStatus.ALIGNED
            regime.label == RegimeLabel.RISK_OFF || regime.label == RegimeLabel.CRISIS ->
                RegimeAlignmentStatus.DEFENSIVE
            else -> RegimeAlignmentStatus.NEUTRAL
        }
    }

    private fun buildRegimeStatusText(
        regime: MarketRegime,
        env: MarketEnvironment,
        alignment: RegimeAlignmentStatus
    ): String {
        val base = when {
            regime.panicGuardActive -> "PANIC GUARD"
            regime.label == RegimeLabel.RISK_ON -> "RISK_ON · ${env.shortLabel}"
            regime.label == RegimeLabel.RISK_OFF -> "RISK_OFF · ${env.shortLabel}"
            regime.label == RegimeLabel.CRISIS -> "CRISIS"
            else -> "NEUTRAL · ${env.shortLabel}"
        }
        return when (alignment) {
            RegimeAlignmentStatus.GATED_MR -> "$base · MR"
            RegimeAlignmentStatus.PANIC_GUARD -> "PANIC 0.5×"
            else -> base
        }
    }
}
