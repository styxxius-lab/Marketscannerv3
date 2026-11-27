package com.example.domain.engine

import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import java.util.Locale
import kotlin.math.abs

enum class QuantScannerPreset(
    val label: String,
    val symbols: List<String>
) {
    MEGA_TECH("MEGA TECH", listOf("NVDA", "AAPL", "MSFT", "META", "AMZN", "GOOGL")),
    SEMIS("SEMIS", listOf("NVDA", "AMD", "AVGO", "QCOM", "MU", "AMAT")),
    FINANCE("FINANCE", listOf("JPM", "GS", "V", "MA", "BAC", "MS")),
    DEFENSIVE("ENERGY & DEF", listOf("XOM", "CVX", "UNH", "JNJ", "PG", "LMT"))
}

data class QuantScannerUiState(
    val tickerInput: String = "",
    val activeSymbols: List<String> = emptyList(),
    val liveStreamEnabled: Boolean = true,
    val isFetching: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val tickCount: Int = 0,
    val actionFilter: SignalAction? = null,
    val minConviction: Double = 0.0,
    val errorMessage: String? = null
)

data class QuantScannerSnapshot(
    val visibleSignals: List<SignalRow>,
    val scopedTotalCount: Int,
    val buyCount: Int,
    val sellCount: Int,
    val holdCount: Int,
    val avgNetRr: Double,
    val topConvictionSymbol: String?
)

object QuantSignalScannerEngine {

    const val MAX_CUSTOM_TICKERS = 15

    private val FACTOR_LABELS = mapOf(
        "trend" to "TRD",
        "momentum" to "MOM",
        "mom" to "MOM",
        "rs" to "RS",
        "rel_strength" to "RS",
        "volume" to "VOL",
        "vol" to "VOL",
        "volatility" to "ATR",
        "mean_rev" to "MR",
        "mr" to "MR",
        "quality" to "QUAL",
        "structure" to "STR",
        "news" to "NEWS"
    )

    fun parseTickerInput(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',', ' ', ';', '\n', '\t')
            .map { token ->
                token.trim()
                    .uppercase(Locale.US)
                    .filter { ch -> ch.isLetterOrDigit() || ch == '.' }
            }
            .filter { it.length in 1..8 && it.any { ch -> ch.isLetter() } }
            .distinct()
            .take(MAX_CUSTOM_TICKERS)
    }

    fun buildSnapshot(
        allRows: List<SignalRow>,
        activeSymbols: List<String>,
        actionFilter: SignalAction? = null,
        minConviction: Double = 0.0
    ): QuantScannerSnapshot {
        val scoped = if (activeSymbols.isNotEmpty()) {
            val wanted = activeSymbols.map { it.uppercase(Locale.US) }.toSet()
            allRows.filter { wanted.contains(it.symbol.uppercase(Locale.US)) }
        } else {
            allRows
        }

        val buys = scoped.count { it.action == SignalAction.BUY }
        val sells = scoped.count { it.action == SignalAction.SELL }
        val holds = scoped.size - buys - sells

        val filtered = scoped.filter { row ->
            val matchesAction = actionFilter == null || row.action == actionFilter
            val matchesConv = row.conviction >= minConviction
            matchesAction && matchesConv
        }.sortedWith(
            compareByDescending<SignalRow> {
                when (it.action) {
                    SignalAction.BUY -> 2
                    SignalAction.SELL -> 1
                    SignalAction.HOLD -> 0
                }
            }.thenByDescending { it.conviction }
                .thenByDescending { it.confidence }
        )

        val rrValues = filtered.mapNotNull { row ->
            (row.cost?.rrNet ?: row.rr).takeIf { it.isFinite() && it > 0.0 }
        }
        val avgRr = if (rrValues.isNotEmpty()) rrValues.average() else 0.0
        val topSym = filtered.firstOrNull()?.symbol

        return QuantScannerSnapshot(
            visibleSignals = filtered,
            scopedTotalCount = scoped.size,
            buyCount = buys,
            sellCount = sells,
            holdCount = holds,
            avgNetRr = avgRr,
            topConvictionSymbol = topSym
        )
    }

    fun topFactors(row: SignalRow, limit: Int = 4): List<Pair<String, Double>> {
        if (row.sub.isEmpty()) return emptyList()
        return row.sub.entries
            .filter { it.value.isFinite() }
            .sortedByDescending { abs(it.value) }
            .take(limit)
            .map { (key, value) ->
                val cleanKey = key.trim().lowercase(Locale.US)
                val shortLabel = FACTOR_LABELS[cleanKey]
                    ?: cleanKey.take(4).uppercase(Locale.US)
                shortLabel to value
            }
    }
}
