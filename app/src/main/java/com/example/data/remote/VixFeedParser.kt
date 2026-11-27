package com.example.data.remote

import com.example.domain.engine.VixAlertEngine
import com.example.domain.model.VixSnapshot
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.max
import kotlin.math.min

@JsonClass(generateAdapter = true)
data class CboeVixDataDto(
    val symbol: String? = null,
    val current_price: Double? = null,
    val prev_day_close: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null
)

@JsonClass(generateAdapter = true)
data class CboeVixResponseDto(
    val data: CboeVixDataDto? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartMetaDto(
    val regularMarketPrice: Double? = null,
    val chartPreviousClose: Double? = null,
    val previousClose: Double? = null,
    val regularMarketDayHigh: Double? = null,
    val regularMarketDayLow: Double? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartQuoteDto(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartIndicatorsDto(
    val quote: List<YahooChartQuoteDto>? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartResultItemDto(
    val meta: YahooChartMetaDto? = null,
    val indicators: YahooChartIndicatorsDto? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartContainerDto(
    val result: List<YahooChartResultItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class YahooVixResponseDto(
    val chart: YahooChartContainerDto? = null
)

object VixFeedParser {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val cboeAdapter = moshi.adapter(CboeVixResponseDto::class.java)
    private val yahooAdapter = moshi.adapter(YahooVixResponseDto::class.java)

    fun parseCboeJson(
        rawJson: String,
        previous: VixSnapshot?,
        nowMs: Long = System.currentTimeMillis()
    ): VixSnapshot? {
        val parsed = runCatching { cboeAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val data = parsed.data ?: return null
        val cur = data.current_price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val open = data.open?.takeIf { it.isFinite() && it > 0.0 } ?: previous?.open ?: cur
        val prevClose = data.prev_day_close?.takeIf { it.isFinite() && it > 0.0 } ?: previous?.prevClose ?: open
        val rawLow = data.low?.takeIf { it.isFinite() && it > 0.0 } ?: cur
        val rawHigh = data.high?.takeIf { it.isFinite() && it > 0.0 } ?: cur
        val low = min(rawLow, min(previous?.intradayLow ?: cur, cur))
        val high = max(rawHigh, max(previous?.intradayHigh ?: cur, cur))
        val series = ((previous?.intradaySeries ?: listOf(open)) + cur).takeLast(60)
        return VixAlertEngine.buildSnapshot(
            current = cur,
            open = open,
            prevClose = prevClose,
            intradayLow = low,
            intradayHigh = high,
            intradaySeries = series,
            source = "CBOE LIVE",
            nowMs = nowMs
        )
    }

    fun parseYahooJson(
        rawJson: String,
        previous: VixSnapshot?,
        nowMs: Long = System.currentTimeMillis()
    ): VixSnapshot? {
        val parsed = runCatching { yahooAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val item = parsed.chart?.result?.firstOrNull() ?: return null
        val meta = item.meta
        val quote = item.indicators?.quote?.firstOrNull()
        val closes = quote?.close.orEmpty().filterNotNull().filter { it.isFinite() && it > 0.0 }
        val lows = quote?.low.orEmpty().filterNotNull().filter { it.isFinite() && it > 0.0 }
        val highs = quote?.high.orEmpty().filterNotNull().filter { it.isFinite() && it > 0.0 }
        val opens = quote?.open.orEmpty().filterNotNull().filter { it.isFinite() && it > 0.0 }

        val cur = meta?.regularMarketPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: closes.lastOrNull()
            ?: return null
        val open = opens.firstOrNull() ?: previous?.open ?: cur
        val prevClose = (meta?.chartPreviousClose ?: meta?.previousClose)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: previous?.prevClose
            ?: open
        val minSeriesLow = (lows + closes).minOrNull() ?: cur
        val maxSeriesHigh = (highs + closes).maxOrNull() ?: cur
        val low = min(
            meta?.regularMarketDayLow?.takeIf { it.isFinite() && it > 0.0 } ?: minSeriesLow,
            min(minSeriesLow, min(previous?.intradayLow ?: cur, cur))
        )
        val high = max(
            meta?.regularMarketDayHigh?.takeIf { it.isFinite() && it > 0.0 } ?: maxSeriesHigh,
            max(maxSeriesHigh, max(previous?.intradayHigh ?: cur, cur))
        )
        val series = if (closes.isNotEmpty()) {
            (closes + cur).takeLast(60)
        } else {
            ((previous?.intradaySeries ?: listOf(open)) + cur).takeLast(60)
        }

        return VixAlertEngine.buildSnapshot(
            current = cur,
            open = open,
            prevClose = prevClose,
            intradayLow = low,
            intradayHigh = high,
            intradaySeries = series,
            source = "CBOE/YAHOO",
            nowMs = nowMs
        )
    }
}
