package com.example.domain.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class IndicatorMode(val shortLabel: String) {
    RSI_14("RSI (14)"),
    MA_CROSS("MA CROSS"),
    COMPOSITE("COMPOSITE")
}

enum class IndicatorPolarity(val weight: Int) {
    STRONG_BUY(2),
    BUY(1),
    NEUTRAL(0),
    SELL(-1),
    STRONG_SELL(-2)
}

enum class TickerDirectionFilter(val label: String) {
    ALL("ALL"),
    BULLISH("BULLISH"),
    BEARISH("BEARISH"),
    EXTREME("EXTREME")
}

enum class TickerSortField(val label: String) {
    SYMBOL("TICKER"),
    PRICE("PRICE"),
    CHANGE_PCT("CHG%"),
    RSI("RSI"),
    SIGNAL("SIGNAL")
}

data class TickerIndicatorQuote(
    val symbol: String,
    val sectorLabel: String,
    val price: Double,
    val previousClose: Double,
    val changePct: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val rsi14: Double,
    val ema9: Double,
    val ema21: Double,
    val prevEma9: Double,
    val prevEma21: Double,
    val sma20: Double,
    val sma50: Double,
    val maSpreadPct: Double,
    val freshGoldenCross: Boolean,
    val freshDeathCross: Boolean,
    val rsiLabel: String,
    val rsiPolarity: IndicatorPolarity,
    val maCrossLabel: String,
    val maCrossPolarity: IndicatorPolarity,
    val compositeLabel: String,
    val compositePolarity: IndicatorPolarity,
    val sparkline: List<Double>,
    val sourceLabel: String,
    val updatedAtMs: Long
)

data class TickerIndicatorSummary(
    val total: Int,
    val bullishCount: Int,
    val bearishCount: Int,
    val extremeCount: Int,
    val avgRsi: Double
)

object TickerIndicatorEngine {

    val DEFAULT_TICKERS: List<String> = listOf(
        "NVDA", "AAPL", "MSFT", "AMD", "TSLA", "META",
        "AMZN", "GOOGL", "SPY", "QQQ", "PLTR", "JPM"
    )

    private fun roundTo(value: Double, decimals: Int): Double {
        if (!value.isFinite()) return 0.0
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToInt() / factor
    }

    /**
     * Standard Wilder's Relative Strength Index over [period] bars (default 14).
     */
    fun computeRsi14(closes: List<Double>, period: Int = 14): Double {
        if (closes.size < 2) return 50.0
        val lookback = min(period, closes.size - 1)
        if (lookback <= 0) return 50.0

        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..lookback) {
            val delta = closes[i] - closes[i - 1]
            if (delta > 0.0) avgGain += delta else avgLoss += -delta
        }
        avgGain /= lookback.toDouble()
        avgLoss /= lookback.toDouble()

        for (i in (lookback + 1) until closes.size) {
            val delta = closes[i] - closes[i - 1]
            val gain = if (delta > 0.0) delta else 0.0
            val loss = if (delta < 0.0) -delta else 0.0
            avgGain = (avgGain * (lookback - 1) + gain) / lookback.toDouble()
            avgLoss = (avgLoss * (lookback - 1) + loss) / lookback.toDouble()
        }

        if (avgLoss == 0.0 && avgGain == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return roundTo(100.0 - (100.0 / (1.0 + rs)), 1).coerceIn(0.0, 100.0)
    }

    fun computeSma(closes: List<Double>, period: Int): Double {
        if (closes.isEmpty()) return 0.0
        val slice = closes.takeLast(min(period, closes.size))
        return roundTo(slice.average(), 2)
    }

    fun computeEmaSeries(closes: List<Double>, period: Int): List<Double> {
        if (closes.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        var current = closes.first()
        val out = ArrayList<Double>(closes.size)
        out.add(current)
        for (i in 1 until closes.size) {
            current = closes[i] * k + current * (1.0 - k)
            out.add(current)
        }
        return out
    }

    fun evaluateSymbol(
        symbol: String,
        historicalCloses: List<Double>,
        currentPrice: Double,
        previousClose: Double,
        dayHigh: Double = currentPrice,
        dayLow: Double = currentPrice,
        sourceLabel: String,
        updatedAtMs: Long = System.currentTimeMillis()
    ): TickerIndicatorQuote {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val livePrice = currentPrice.takeIf { it.isFinite() && it > 0.0 }
            ?: historicalCloses.lastOrNull()
            ?: 0.0

        val closes = buildList {
            addAll(historicalCloses.filter { it.isFinite() && it > 0.0 })
            if (isEmpty() && livePrice > 0.0) {
                add(livePrice)
            } else if (livePrice > 0.0) {
                // Keep the latest bar synchronized with the live quote so RSI & EMA respond in real time
                if (size >= 2 && abs(last() - livePrice) / max(livePrice, 1e-6) > 1e-6) {
                    set(lastIndex, livePrice)
                }
            }
        }

        val prevClose = previousClose.takeIf { it.isFinite() && it > 0.0 }
            ?: closes.getOrNull(closes.size - 2)
            ?: livePrice
        val changePct = if (prevClose > 0.0) roundTo((livePrice / prevClose - 1.0) * 100.0, 2) else 0.0

        val rsi14 = computeRsi14(closes, 14)
        val ema9Series = computeEmaSeries(closes, 9)
        val ema21Series = computeEmaSeries(closes, 21)
        val ema9 = roundTo(ema9Series.lastOrNull() ?: livePrice, 2)
        val ema21 = roundTo(ema21Series.lastOrNull() ?: livePrice, 2)
        val prevEma9 = roundTo(ema9Series.getOrNull(ema9Series.size - 2) ?: ema9, 2)
        val prevEma21 = roundTo(ema21Series.getOrNull(ema21Series.size - 2) ?: ema21, 2)

        val sma20 = computeSma(closes, 20)
        val sma50 = computeSma(closes, 50)
        val maSpreadPct = if (ema21 > 0.0) roundTo(((ema9 - ema21) / ema21) * 100.0, 2) else 0.0

        val freshGoldenCross = prevEma9 <= prevEma21 && ema9 > ema21
        val freshDeathCross = prevEma9 >= prevEma21 && ema9 < ema21

        val (rsiLabel, rsiPolarity) = classifyRsi(rsi14)
        val (maCrossLabel, maCrossPolarity) = classifyMaCross(
            maSpreadPct = maSpreadPct,
            freshGoldenCross = freshGoldenCross,
            freshDeathCross = freshDeathCross,
            sma20 = sma20,
            sma50 = sma50
        )
        val (compositeLabel, compositePolarity) = classifyComposite(
            rsi14 = rsi14,
            rsiPolarity = rsiPolarity,
            maCrossPolarity = maCrossPolarity,
            freshGoldenCross = freshGoldenCross,
            freshDeathCross = freshDeathCross
        )

        return TickerIndicatorQuote(
            symbol = cleanSym,
            sectorLabel = QuantEngine.classifySector(cleanSym).label,
            price = roundTo(livePrice, 2),
            previousClose = roundTo(prevClose, 2),
            changePct = changePct,
            dayHigh = roundTo(max(dayHigh, livePrice), 2),
            dayLow = roundTo(min(dayLow.takeIf { it > 0.0 } ?: livePrice, livePrice), 2),
            rsi14 = rsi14,
            ema9 = ema9,
            ema21 = ema21,
            prevEma9 = prevEma9,
            prevEma21 = prevEma21,
            sma20 = sma20,
            sma50 = sma50,
            maSpreadPct = maSpreadPct,
            freshGoldenCross = freshGoldenCross,
            freshDeathCross = freshDeathCross,
            rsiLabel = rsiLabel,
            rsiPolarity = rsiPolarity,
            maCrossLabel = maCrossLabel,
            maCrossPolarity = maCrossPolarity,
            compositeLabel = compositeLabel,
            compositePolarity = compositePolarity,
            sparkline = closes.takeLast(24),
            sourceLabel = sourceLabel,
            updatedAtMs = updatedAtMs
        )
    }

    private fun classifyRsi(rsi: Double): Pair<String, IndicatorPolarity> = when {
        rsi <= 30.0 -> "OVERSOLD" to IndicatorPolarity.STRONG_BUY
        rsi >= 70.0 -> "OVERBOUGHT" to IndicatorPolarity.STRONG_SELL
        rsi >= 56.0 -> "BULL MOM" to IndicatorPolarity.BUY
        rsi <= 44.0 -> "BEAR MOM" to IndicatorPolarity.SELL
        else -> "NEUTRAL" to IndicatorPolarity.NEUTRAL
    }

    private fun classifyMaCross(
        maSpreadPct: Double,
        freshGoldenCross: Boolean,
        freshDeathCross: Boolean,
        sma20: Double,
        sma50: Double
    ): Pair<String, IndicatorPolarity> = when {
        freshGoldenCross -> "GOLDEN X" to IndicatorPolarity.STRONG_BUY
        freshDeathCross -> "DEATH X" to IndicatorPolarity.STRONG_SELL
        maSpreadPct >= 1.0 && sma20 >= sma50 -> "BULL CROSS" to IndicatorPolarity.STRONG_BUY
        maSpreadPct >= 0.25 -> "BULL MA" to IndicatorPolarity.BUY
        maSpreadPct <= -1.0 && sma20 <= sma50 -> "BEAR CROSS" to IndicatorPolarity.STRONG_SELL
        maSpreadPct <= -0.25 -> "BEAR MA" to IndicatorPolarity.SELL
        else -> "FLAT MA" to IndicatorPolarity.NEUTRAL
    }

    private fun classifyComposite(
        rsi14: Double,
        rsiPolarity: IndicatorPolarity,
        maCrossPolarity: IndicatorPolarity,
        freshGoldenCross: Boolean,
        freshDeathCross: Boolean
    ): Pair<String, IndicatorPolarity> {
        val score = rsiPolarity.weight + maCrossPolarity.weight +
            (if (freshGoldenCross) 1 else 0) -
            (if (freshDeathCross) 1 else 0)
        return when {
            score >= 3 || (rsi14 <= 30.0 && maCrossPolarity.weight >= 0) -> "STRONG BUY" to IndicatorPolarity.STRONG_BUY
            score >= 1 -> "BUY" to IndicatorPolarity.BUY
            score <= -3 || (rsi14 >= 70.0 && maCrossPolarity.weight <= 0) -> "STRONG SELL" to IndicatorPolarity.STRONG_SELL
            score <= -1 -> "SELL" to IndicatorPolarity.SELL
            else -> "NEUTRAL" to IndicatorPolarity.NEUTRAL
        }
    }

    fun signalLabel(item: TickerIndicatorQuote, mode: IndicatorMode): String = when (mode) {
        IndicatorMode.RSI_14 -> item.rsiLabel
        IndicatorMode.MA_CROSS -> item.maCrossLabel
        IndicatorMode.COMPOSITE -> item.compositeLabel
    }

    fun signalPolarity(item: TickerIndicatorQuote, mode: IndicatorMode): IndicatorPolarity = when (mode) {
        IndicatorMode.RSI_14 -> item.rsiPolarity
        IndicatorMode.MA_CROSS -> item.maCrossPolarity
        IndicatorMode.COMPOSITE -> item.compositePolarity
    }

    fun isExtreme(item: TickerIndicatorQuote, mode: IndicatorMode): Boolean = when (mode) {
        IndicatorMode.RSI_14 -> item.rsi14 <= 30.0 || item.rsi14 >= 70.0
        IndicatorMode.MA_CROSS -> item.freshGoldenCross || item.freshDeathCross || abs(item.maSpreadPct) >= 1.5
        IndicatorMode.COMPOSITE -> item.compositePolarity == IndicatorPolarity.STRONG_BUY ||
            item.compositePolarity == IndicatorPolarity.STRONG_SELL
    }

    fun filterAndSort(
        items: List<TickerIndicatorQuote>,
        mode: IndicatorMode,
        filter: TickerDirectionFilter,
        sortField: TickerSortField,
        sortDescending: Boolean,
        searchQuery: String
    ): List<TickerIndicatorQuote> {
        val q = searchQuery.trim()
        val searched = if (q.isBlank()) {
            items
        } else {
            items.filter {
                it.symbol.contains(q, ignoreCase = true) ||
                    it.sectorLabel.contains(q, ignoreCase = true) ||
                    signalLabel(it, mode).contains(q, ignoreCase = true)
            }
        }

        val filtered = when (filter) {
            TickerDirectionFilter.ALL -> searched
            TickerDirectionFilter.BULLISH -> searched.filter { signalPolarity(it, mode).weight > 0 }
            TickerDirectionFilter.BEARISH -> searched.filter { signalPolarity(it, mode).weight < 0 }
            TickerDirectionFilter.EXTREME -> searched.filter { isExtreme(it, mode) }
        }

        val comparator: Comparator<TickerIndicatorQuote> = when (sortField) {
            TickerSortField.SYMBOL -> compareBy { it.symbol }
            TickerSortField.PRICE -> compareBy { it.price }
            TickerSortField.CHANGE_PCT -> compareBy { it.changePct }
            TickerSortField.RSI -> compareBy { it.rsi14 }
            TickerSortField.SIGNAL -> compareBy<TickerIndicatorQuote> {
                signalPolarity(it, mode).weight
            }.thenBy { abs(it.maSpreadPct) }
        }

        return if (sortDescending) {
            filtered.sortedWith(comparator.reversed())
        } else {
            filtered.sortedWith(comparator)
        }
    }

    fun summarize(items: List<TickerIndicatorQuote>, mode: IndicatorMode): TickerIndicatorSummary {
        if (items.isEmpty()) {
            return TickerIndicatorSummary(
                total = 0,
                bullishCount = 0,
                bearishCount = 0,
                extremeCount = 0,
                avgRsi = 50.0
            )
        }
        val bullish = items.count { signalPolarity(it, mode).weight > 0 }
        val bearish = items.count { signalPolarity(it, mode).weight < 0 }
        val extremes = items.count { isExtreme(it, mode) }
        val avgRsi = roundTo(items.map { it.rsi14 }.average(), 1)
        return TickerIndicatorSummary(
            total = items.size,
            bullishCount = bullish,
            bearishCount = bearish,
            extremeCount = extremes,
            avgRsi = avgRsi
        )
    }

    fun parseSymbols(raw: String): List<String> {
        return raw.split(",", " ", "\n", ";", "\t")
            .map { it.trim().uppercase(Locale.US) }
            .filter { token ->
                token.length in 1..8 && token.all { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '-' }
            }
            .distinct()
    }
}
