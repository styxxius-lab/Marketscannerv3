package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class YahooChartSeries(
    val symbol: String,
    val currentPrice: Double,
    val previousClose: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val closes: List<Double>,
    val updatedAtMs: Long
)

object YahooChartParser {

    /**
     * Parses a Yahoo Finance v8 chart JSON payload into a real-time price and historical close list.
     * Returns null when the response has no usable quote or close series.
     */
    fun parseChartJson(rawJson: String, fallbackSymbol: String): YahooChartSeries? {
        return try {
            val root = JSONObject(rawJson)
            val chart = root.optJSONObject("chart") ?: return null
            val results = chart.optJSONArray("result") ?: return null
            if (results.length() == 0) return null
            val first = results.optJSONObject(0) ?: return null
            val meta = first.optJSONObject("meta") ?: return null

            val symbol = meta.optString("symbol", fallbackSymbol)
                .trim()
                .uppercase(Locale.US)
                .ifBlank { fallbackSymbol.trim().uppercase(Locale.US) }

            val indicators = first.optJSONObject("indicators")
            val quoteArr = indicators?.optJSONArray("quote")
            val quoteObj = if (quoteArr != null && quoteArr.length() > 0) quoteArr.optJSONObject(0) else null
            val closeArr = quoteObj?.optJSONArray("close")

            val closes = ArrayList<Double>()
            if (closeArr != null) {
                for (i in 0 until closeArr.length()) {
                    if (!closeArr.isNull(i)) {
                        val v = closeArr.optDouble(i, Double.NaN)
                        if (v.isFinite() && v > 0.0) {
                            closes.add(v)
                        }
                    }
                }
            }

            val metaPrice = meta.optDouble("regularMarketPrice", Double.NaN)
            val currentPrice = when {
                metaPrice.isFinite() && metaPrice > 0.0 -> metaPrice
                closes.isNotEmpty() -> closes.last()
                else -> return null
            }

            val metaPrevClose = meta.optDouble("chartPreviousClose", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?: meta.optDouble("previousClose", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            val previousClose = when {
                metaPrevClose != null -> metaPrevClose
                closes.size >= 2 -> closes[closes.size - 2]
                else -> currentPrice
            }

            val dayHigh = meta.optDouble("regularMarketDayHigh", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?: currentPrice
            val dayLow = meta.optDouble("regularMarketDayLow", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?: currentPrice

            val marketTimeSec = meta.optLong("regularMarketTime", 0L)
            val updatedAtMs = if (marketTimeSec > 0L) marketTimeSec * 1000L else System.currentTimeMillis()

            YahooChartSeries(
                symbol = symbol,
                currentPrice = currentPrice,
                previousClose = previousClose,
                dayHigh = maxOf(dayHigh, currentPrice),
                dayLow = minOf(dayLow, currentPrice),
                closes = closes,
                updatedAtMs = updatedAtMs
            )
        } catch (_: Exception) {
            null
        }
    }
}

class YahooChartClient(
    private val okHttp: OkHttpClient
) {
    fun fetchChart(symbol: String, range: String = "6mo", interval: String = "1d"): YahooChartSeries? {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isEmpty()) return null
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=$interval&range=$range"
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) MarketScan/1.0")
                .header("Accept", "application/json")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                YahooChartParser.parseChartJson(body, clean)
            }
        } catch (_: Exception) {
            null
        }
    }
}
