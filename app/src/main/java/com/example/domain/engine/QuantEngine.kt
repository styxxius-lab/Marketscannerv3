package com.example.domain.engine

import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.CostEstimate
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSector
import com.example.domain.model.NewsHeadline
import com.example.domain.model.RegimeHeatmapData
import com.example.domain.model.RegimeLabel
import com.example.domain.model.RegimeMatrixCell
import com.example.domain.model.ScanRegimeSnapshot
import com.example.domain.model.ScoredNewsItem
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.SnapshotQuote
import com.example.domain.model.SymbolNewsSummary
import com.example.domain.model.TrendBucket
import com.example.domain.model.VolatilityBucket
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

object QuantEngine {

    val UNIVERSE = listOf(
        "NVDA", "AAPL", "MSFT", "AMD", "TSLA", "META", "AMZN", "GOOGL", "SPY", "QQQ",
        "PLTR", "COIN", "SOFI", "MARA", "SMCI", "AVGO", "NFLX", "CRM", "SHOP", "UBER",
        "JPM", "BAC", "XOM", "CVX", "PFE", "JNJ", "WMT", "COST", "DIS", "BA"
    )

    private val SECTOR_MAP = mapOf(
        // Technology & Semiconductors
        "NVDA" to MarketSector.TECHNOLOGY,
        "AAPL" to MarketSector.TECHNOLOGY,
        "MSFT" to MarketSector.TECHNOLOGY,
        "AMD" to MarketSector.TECHNOLOGY,
        "META" to MarketSector.TECHNOLOGY,
        "GOOGL" to MarketSector.TECHNOLOGY,
        "GOOG" to MarketSector.TECHNOLOGY,
        "PLTR" to MarketSector.TECHNOLOGY,
        "SMCI" to MarketSector.TECHNOLOGY,
        "AVGO" to MarketSector.TECHNOLOGY,
        "CRM" to MarketSector.TECHNOLOGY,
        "ORCL" to MarketSector.TECHNOLOGY,
        "ADBE" to MarketSector.TECHNOLOGY,
        "INTC" to MarketSector.TECHNOLOGY,
        "QCOM" to MarketSector.TECHNOLOGY,
        "SNOW" to MarketSector.TECHNOLOGY,
        "PANW" to MarketSector.TECHNOLOGY,
        "MU" to MarketSector.TECHNOLOGY,
        "ARM" to MarketSector.TECHNOLOGY,
        "TSM" to MarketSector.TECHNOLOGY,
        "IBM" to MarketSector.TECHNOLOGY,
        // Consumer & Retail / Media
        "TSLA" to MarketSector.CONSUMER,
        "AMZN" to MarketSector.CONSUMER,
        "NFLX" to MarketSector.CONSUMER,
        "SHOP" to MarketSector.CONSUMER,
        "UBER" to MarketSector.CONSUMER,
        "WMT" to MarketSector.CONSUMER,
        "COST" to MarketSector.CONSUMER,
        "DIS" to MarketSector.CONSUMER,
        "NKE" to MarketSector.CONSUMER,
        "SBUX" to MarketSector.CONSUMER,
        "HD" to MarketSector.CONSUMER,
        "MCD" to MarketSector.CONSUMER,
        "TGT" to MarketSector.CONSUMER,
        // Financials & Fintech / Crypto
        "JPM" to MarketSector.FINANCIALS,
        "BAC" to MarketSector.FINANCIALS,
        "SOFI" to MarketSector.FINANCIALS,
        "COIN" to MarketSector.FINANCIALS,
        "MARA" to MarketSector.FINANCIALS,
        "V" to MarketSector.FINANCIALS,
        "MA" to MarketSector.FINANCIALS,
        "GS" to MarketSector.FINANCIALS,
        "MS" to MarketSector.FINANCIALS,
        "PYPL" to MarketSector.FINANCIALS,
        "BLK" to MarketSector.FINANCIALS,
        "C" to MarketSector.FINANCIALS,
        "WFC" to MarketSector.FINANCIALS,
        // Energy
        "XOM" to MarketSector.ENERGY,
        "CVX" to MarketSector.ENERGY,
        "COP" to MarketSector.ENERGY,
        "SLB" to MarketSector.ENERGY,
        "OXY" to MarketSector.ENERGY,
        "EOG" to MarketSector.ENERGY,
        // Healthcare
        "PFE" to MarketSector.HEALTHCARE,
        "JNJ" to MarketSector.HEALTHCARE,
        "UNH" to MarketSector.HEALTHCARE,
        "LLY" to MarketSector.HEALTHCARE,
        "MRK" to MarketSector.HEALTHCARE,
        "ABBV" to MarketSector.HEALTHCARE,
        "TMO" to MarketSector.HEALTHCARE,
        "MRNA" to MarketSector.HEALTHCARE,
        // Industrials & Aerospace
        "BA" to MarketSector.INDUSTRIALS,
        "CAT" to MarketSector.INDUSTRIALS,
        "GE" to MarketSector.INDUSTRIALS,
        "HON" to MarketSector.INDUSTRIALS,
        "LMT" to MarketSector.INDUSTRIALS,
        "RTX" to MarketSector.INDUSTRIALS,
        "UPS" to MarketSector.INDUSTRIALS,
        "DE" to MarketSector.INDUSTRIALS,
        // Index & Broad ETFs
        "SPY" to MarketSector.INDEX_ETF,
        "QQQ" to MarketSector.INDEX_ETF,
        "IWM" to MarketSector.INDEX_ETF,
        "DIA" to MarketSector.INDEX_ETF,
        "VTI" to MarketSector.INDEX_ETF,
        "VOO" to MarketSector.INDEX_ETF,
        "XLF" to MarketSector.INDEX_ETF,
        "XLK" to MarketSector.INDEX_ETF,
        "XLE" to MarketSector.INDEX_ETF,
        "ARKK" to MarketSector.INDEX_ETF
    )

    fun classifySector(symbol: String): MarketSector {
        val clean = symbol.trim().uppercase()
        if (clean.contains("/")) return CryptoFeedsEngine.classifyCryptoSector(clean)
        return SECTOR_MAP[clean] ?: MarketSector.OTHER
    }

    private val FACTOR_WEIGHTS = mapOf(
        "trend" to 0.28,
        "momentum" to 0.22,
        "breakout" to 0.16,
        "volume" to 0.14,
        "intraday" to 0.12,
        "meanrev" to 0.08
    )

    fun clamp(x: Double, a: Double, b: Double): Double = max(a, min(b, x))

    private fun roundTo(value: Double, decimals: Int): Double {
        if (!value.isFinite()) return 0.0
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToInt() / factor
    }

    fun ema(a: List<Double>, n: Int): List<Double> {
        if (a.isEmpty()) return emptyList()
        val k = 2.0 / (n + 1.0)
        var e = a[0]
        val o = ArrayList<Double>(a.size)
        o.add(e)
        for (i in 1 until a.size) {
            e = a[i] * k + e * (1.0 - k)
            o.add(e)
        }
        return o
    }

    fun sma(a: List<Double>, n: Int): List<Double?> {
        val out = ArrayList<Double?>(a.size)
        for (i in a.indices) {
            if (i < n - 1) {
                out.add(null)
            } else {
                var sum = 0.0
                for (j in (i - n + 1)..i) sum += a[j]
                out.add(sum / n)
            }
        }
        return out
    }

    fun std(a: List<Double>, n: Int): List<Double?> {
        val out = ArrayList<Double?>(a.size)
        for (i in a.indices) {
            if (i < n - 1) {
                out.add(null)
            } else {
                var sum = 0.0
                for (j in (i - n + 1)..i) sum += a[j]
                val mean = sum / n
                var sq = 0.0
                for (j in (i - n + 1)..i) {
                    val diff = a[j] - mean
                    sq += diff * diff
                }
                out.add(sqrt(sq / n))
            }
        }
        return out
    }

    fun rsi(c: List<Double>, n: Int = 14): List<Double> {
        val o = MutableList(c.size) { 50.0 }
        var up = 0.0
        var dn = 0.0
        for (i in 1 until c.size) {
            val d = c[i] - c[i - 1]
            up = (up * (n - 1) + max(d, 0.0)) / n
            dn = (dn * (n - 1) + max(-d, 0.0)) / n
            o[i] = if (dn == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + up / dn)
        }
        return o
    }

    fun atr(h: List<Double>, l: List<Double>, c: List<Double>, n: Int = 14): List<Double> {
        if (c.isEmpty()) return emptyList()
        val tr = ArrayList<Double>(c.size)
        tr.add(h[0] - l[0])
        for (i in 1 until c.size) {
            val v1 = h[i] - l[i]
            val v2 = abs(h[i] - c[i - 1])
            val v3 = abs(l[i] - c[i - 1])
            tr.add(max(v1, max(v2, v3)))
        }
        return ema(tr, n)
    }

    data class AdxResult(val adx: Double, val pdi: Double, val mdi: Double)

    fun adx(h: List<Double>, l: List<Double>, c: List<Double>, n: Int = 14): AdxResult {
        if (c.isEmpty()) return AdxResult(0.0, 0.0, 0.0)
        val p = ArrayList<Double>(c.size).apply { add(0.0) }
        val m = ArrayList<Double>(c.size).apply { add(0.0) }
        for (i in 1 until c.size) {
            val u = h[i] - h[i - 1]
            val d = l[i - 1] - l[i]
            p.add(if (u > d && u > 0) u else 0.0)
            m.add(if (d > u && d > 0) d else 0.0)
        }
        val a = atr(h, l, c, n)
        val ep = ema(p, n)
        val em = ema(m, n)
        var out = 0.0
        for (i in c.indices) {
            val denom = if (a[i] == 0.0) 1.0 else a[i]
            val pd = 100.0 * ep[i] / denom
            val md = 100.0 * em[i] / denom
            val dx = if (pd + md == 0.0) 0.0 else 100.0 * abs(pd - md) / (pd + md)
            out = (out * (n - 1) + dx) / n
        }
        val last = c.size - 1
        val lastDenom = if (a[last] == 0.0) 1.0 else a[last]
        return AdxResult(
            adx = out,
            pdi = 100.0 * ep[last] / lastDenom,
            mdi = 100.0 * em[last] / lastDenom
        )
    }

    fun macdH(c: List<Double>): List<Double> {
        val a = ema(c, 12)
        val b = ema(c, 26)
        val line = a.indices.map { i -> a[i] - b[i] }
        val sig = ema(line, 9)
        return line.indices.map { i -> line[i] - sig[i] }
    }

    fun obv(c: List<Double>, v: List<Double>): List<Double> {
        var o = 0.0
        return c.indices.map { i ->
            if (i > 0) o += sign(c[i] - c[i - 1]) * v[i]
            o
        }
    }

    fun rankZ(vals: List<Double>): List<Double> {
        val n = vals.size
        if (n < 3) return vals.map { 0.0 }
        val idx = vals.mapIndexed { i, v -> v to i }.sortedBy { it.first }
        val z = MutableList(n) { 0.0 }
        idx.forEachIndexed { r, (_, origIdx) ->
            val p = clamp((r + 1).toDouble() / (n + 1).toDouble(), 0.001, 0.999)
            z[origIdx] = ln(p / (1.0 - p)) / 1.7
        }
        return z
    }

    fun evaluate(sym: String, d: BarSeries, cfg: AppConfig): SignalRow? {
        val c = d.c
        val h = d.h
        val l = d.l
        val v = d.v
        if (c.size < 220) return null
        val px = c.last()
        if (!(px > 0.0)) return null

        val e9 = ema(c, 9).last()
        val e20 = ema(c, 20).last()
        val e50s = ema(c, 50)
        val e50 = e50s.last()
        val e200 = ema(c, 200).last()

        val rVal = rsi(c).last()
        val mVal = macdH(c).last() / px * 100.0
        val aVal = atr(h, l, c).last()
        val atrPct = aVal / px * 100.0
        val avgV = v.subList(max(0, v.size - 21), v.size - 1).average().let { if (it <= 0.0) 1.0 else it }
        val rv = v.last() / avgV
        val adxRes = adx(h, l, c)

        val sub = LinkedHashMap<String, Double>()
        val why = ArrayList<String>()

        // Trend
        var t = 0.0
        when {
            px > e20 && e20 > e50 && e50 > e200 -> {
                t += 55.0
                why.add("EMA 20>50>200 stacked up")
            }
            px > e20 && e20 > e50 -> {
                t += 30.0
                why.add("above rising 20/50 EMA")
            }
            px < e20 && e20 < e50 && e50 < e200 -> {
                t -= 55.0
                why.add("EMA 20<50<200 stacked down")
            }
            px < e20 && e20 < e50 -> {
                t -= 30.0
                why.add("below falling 20/50 EMA")
            }
            else -> why.add("no clean trend")
        }
        val prevE50 = e50s[max(0, e50s.size - 11)].let { if (it == 0.0) e50 else it }
        val slope = (e50 / prevE50 - 1.0) * 100.0
        t += clamp(slope * 12.0, -25.0, 25.0)
        t += (if (adxRes.pdi > adxRes.mdi) 1.0 else -1.0) * clamp((adxRes.adx - 18.0) * 1.6, 0.0, 20.0)
        why.add("ADX ${adxRes.adx.roundToInt()} ${if (adxRes.pdi > adxRes.mdi) "up" else "down"}")
        sub["trend"] = clamp(t, -100.0, 100.0)

        // Momentum
        var m = 0.0
        when {
            rVal in 52.0..68.0 -> {
                m += 18.0
                why.add("RSI ${rVal.roundToInt()} momentum zone")
            }
            rVal >= 45.0 && rVal <= 78.0 -> m += 10.0
            rVal > 78.0 -> {
                m -= 22.0
                why.add("RSI ${rVal.roundToInt()} overbought")
            }
            rVal < 35.0 -> {
                m -= 14.0
                why.add("RSI ${rVal.roundToInt()} weak")
            }
        }
        m += clamp(mVal * 8.0, -25.0, 25.0)
        // AlphaTrader 3.0: 20-day momentum lookback skipping the last 2 days to avoid short-term reversal bounce
        val roc = (c[c.size - 3] / c[c.size - 23] - 1.0) * 100.0
        m += clamp(roc * 4.0, -30.0, 30.0)
        val rocSign = if (roc >= 0) "+" else ""
        why.add("20d(−2d) ROC $rocSign${"%.1f".format(roc)}%")
        sub["momentum"] = clamp(m, -100.0, 100.0)

        // Breakout
        val hi20 = h.takeLast(20).max()
        val lo20 = l.takeLast(20).min()
        val hi55 = h.takeLast(55).max()
        val lo55 = l.takeLast(55).min()
        val hi252 = h.max()
        val lo252 = l.min()
        var b = 0.0
        if (px >= hi20) {
            b += 30.0
            why.add("20d breakout")
        } else if (px <= lo20) {
            b -= 30.0
            why.add("20d breakdown")
        }
        if (px >= hi55) {
            b += 25.0
            why.add("55d breakout")
        } else if (px <= lo55) {
            b -= 25.0
        }
        if (hi252 > lo252) {
            val pos = (px - lo252) / (hi252 - lo252)
            b += clamp((pos - 0.5) * 90.0, -30.0, 40.0)
            why.add("${(pos * 100).roundToInt()}% of 52w range")
        }
        sub["breakout"] = clamp(b, -100.0, 100.0)

        // Volume
        var vv = 0.0
        if (rv > 1.5) {
            vv += 25.0
            why.add("volume ${"%.1f".format(rv)}x avg")
        } else if (rv < 0.6) {
            vv -= 15.0
            why.add("thin volume")
        }
        val ob = obv(c, v)
        val obPrev = ob[max(0, ob.size - 21)]
        vv += clamp((ob.last() - obPrev) / max(abs(obPrev), 1.0) * 100.0, -35.0, 35.0)
        sub["volume"] = clamp(vv, -100.0, 100.0)

        // Intraday proxy
        var it = 0.0
        it += if (px > e9) 20.0 else -20.0
        it += when {
            px > e9 && e9 > e20 -> 25.0
            px < e9 && e9 < e20 -> -25.0
            else -> 0.0
        }
        it += when {
            px >= hi20 -> 30.0
            px <= lo20 -> -30.0
            else -> 0.0
        }
        sub["intraday"] = clamp(it, -100.0, 100.0)

        // Mean reversion
        val s20 = std(c, 20).last()
        val m20 = sma(c, 20).last()
        val z = if (s20 != null && s20 != 0.0 && m20 != null) (px - m20) / s20 else 0.0
        sub["meanrev"] = clamp(-z * 30.0, -40.0, 40.0)

        var score = 0.0
        for ((k, w) in FACTOR_WEIGHTS) score += w * (sub[k] ?: 0.0)
        val agree = FACTOR_WEIGHTS.entries.sumOf { (k, w) ->
            if ((sub[k] ?: 0.0) * score > 0) w else 0.0
        }
        val confidence = ((agree * min(c.size / 252.0, 1.0)) * 100.0).roundToInt() / 100.0

        val isCrypto = sym.contains("/")
        val maxAtr = if (isCrypto) 18.0 else 9.0
        var action = SignalAction.HOLD
        if (atrPct < 0.3 || atrPct > maxAtr) {
            why.add(0, "ATR ${"%.1f".format(atrPct)}% outside tradable band")
        } else if (score >= 45.0 && confidence >= 0.5) {
            action = SignalAction.BUY
        } else if (score <= -45.0 && confidence >= 0.5) {
            action = SignalAction.SELL
        }

        val stop: Double
        val target: Double
        when (action) {
            SignalAction.BUY -> {
                stop = min(px - 1.5 * aVal, l.takeLast(10).min() - 0.25 * aVal)
                target = px + 3.0 * aVal
            }
            SignalAction.SELL -> {
                stop = max(px + 1.5 * aVal, h.takeLast(10).max() + 0.25 * aVal)
                target = px - 3.0 * aVal
            }
            SignalAction.HOLD -> {
                stop = px - 1.5 * aVal
                target = px + 3.0 * aVal
            }
        }
        val risk = abs(px - stop)
        val size = if (risk > 0.0) min(cfg.account * cfg.riskPct / 100.0 / risk, cfg.account * 0.25 / px) else 0.0
        val priceDecimals = if (isCrypto && px < 5.0) 4 else 2

        return SignalRow(
            symbol = sym,
            action = action,
            score = roundTo(score, 1),
            confidence = confidence,
            price = roundTo(px, priceDecimals),
            entry = roundTo(px, priceDecimals),
            stop = roundTo(stop, priceDecimals),
            target = roundTo(target, priceDecimals),
            rr = if (risk > 0.0) roundTo(abs(target - px) / risk, 2) else 0.0,
            atrPct = roundTo(atrPct, 2),
            relVolume = roundTo(rv, 2),
            size = roundTo(size, if (isCrypto && size < 0.01) 6 else 4),
            reasons = why,
            sub = sub,
            series = c.takeLast(90),
            sector = classifySector(sym)
        )
    }

    fun detectRegime(bars: Map<String, BarSeries>): MarketRegime {
        val spy = bars["SPY"] ?: bars["BTC/USD"]
        val eligible = bars.values.filter { it.c.size >= 60 }
        val ema50List = eligible.map { it.c to ema(it.c, 50) }

        val spyEma50 = if (spy != null && spy.c.size >= 220) ema(spy.c, 50) else emptyList()
        val spyEma200 = if (spy != null && spy.c.size >= 220) ema(spy.c, 200) else emptyList()
        val spyRets = if (spy != null && spy.c.size >= 220) {
            (1 until spy.c.size).map { i -> spy.c[i] / spy.c[i - 1] - 1.0 }
        } else emptyList()
        val spyVolSeries = if (spyRets.isNotEmpty()) std(spyRets, 20) else emptyList()

        fun computeAt(backOffset: Int): com.example.domain.model.RegimePoint {
            var bSum = 0
            var bCount = 0
            for ((c, e50) in ema50List) {
                val idx = c.size - 1 - backOffset
                if (idx >= 50) {
                    if (c[idx] > e50[idx]) bSum++
                    bCount++
                }
            }
            val br = if (bCount > 0) bSum.toDouble() / bCount else 0.5

            var tr = 0.0
            var vp = 0.5
            if (spy != null && spyEma50.isNotEmpty()) {
                val idx = spy.c.size - 1 - backOffset
                if (idx >= 200) {
                    val p = spy.c[idx]
                    val e50 = spyEma50[idx]
                    val e200 = spyEma200[idx]
                    tr = (if (p > e50) 0.5 else -0.5) + (if (e50 > e200) 0.5 else -0.5)
                    val retIdx = idx - 1
                    val curVol = spyVolSeries.getOrNull(retIdx)
                    if (curVol != null) {
                        val startIdx = max(0, retIdx - 119)
                        val recent = (startIdx..retIdx).mapNotNull { spyVolSeries.getOrNull(it) }
                        vp = if (recent.isNotEmpty()) recent.count { it < curVol }.toDouble() / recent.size else 0.5
                    }
                }
            }
            val lbl = when {
                tr >= 1.0 && br > 0.55 && vp < 0.8 -> RegimeLabel.RISK_ON
                tr <= -1.0 && br < 0.4 -> if (vp > 0.9) RegimeLabel.CRISIS else RegimeLabel.RISK_OFF
                else -> RegimeLabel.NEUTRAL
            }
            return com.example.domain.model.RegimePoint(
                dayOffset = -backOffset,
                label = lbl,
                breadth = br,
                trend = tr,
                volPct = vp
            )
        }

        val maxHistoryDays = min(
            45,
            listOfNotNull(
                eligible.minOfOrNull { it.c.size - 51 }?.coerceAtLeast(0),
                if (spy != null && spy.c.size >= 220) spy.c.size - 201 else null
            ).minOrNull() ?: 0
        ).coerceAtLeast(1)

        val timeline = (maxHistoryDays - 1 downTo 0).map { offset -> computeAt(offset) }
        val current = timeline.last()

        // Daniel & Moskowitz Panic Guard (AlphaTrader 3.0):
        // Detects momentum crash trap: DD >= 7% with high volatility (>= 0.85) and rebound
        val spy60Max = if (spy != null && spy.c.size >= 60) spy.c.takeLast(60).max() else 1.0
        val spyLastPx = spy?.c?.lastOrNull() ?: 1.0
        val spyDrawdown = if (spy60Max > 0.0) (spy60Max - spyLastPx) / spy60Max else 0.0
        val spy5dRebound = if (spy != null && spy.c.size >= 6) spy.c.last() > spy.c[spy.c.size - 6] else false
        val panicTriggered = spyDrawdown >= 0.07 && current.volPct >= 0.85 && (current.trend > 0.0 || spy5dRebound)

        val longMult = when (current.label) {
            RegimeLabel.RISK_ON -> 1.15
            RegimeLabel.RISK_OFF, RegimeLabel.CRISIS -> 0.6
            RegimeLabel.NEUTRAL -> 1.0
        }

        return MarketRegime(
            label = current.label,
            breadth = current.breadth,
            longMult = longMult,
            shortMult = when (current.label) {
                RegimeLabel.RISK_ON -> 0.75
                RegimeLabel.RISK_OFF -> 1.25
                else -> 1.0
            },
            note = if (panicTriggered) {
                "breadth ${(current.breadth * 100).roundToInt()}% · PANIC GUARD ACTIVE (-${"%.1f".format(spyDrawdown * 100)}% DD)"
            } else {
                "breadth ${(current.breadth * 100).roundToInt()}% above 50d"
            },
            trendScore = current.trend,
            volPct = current.volPct,
            timeline = timeline,
            panicGuardActive = panicTriggered,
            panicDrawdownPct = roundTo(spyDrawdown * 100.0, 1),
            panicCooldownRemaining = if (panicTriggered) 3 else 0
        )
    }

    fun estimateCost(r: SignalRow): CostEstimate {
        val spread = max(r.spreadBps, 0.0)
        val notional = r.price * max(r.size, 0.0)
        val adv = max(r.adv, 1.0)
        val participation = min(notional / adv, 0.02)
        val sigma = abs(r.atrPct) * 100.0
        val spreadCost = spread / 2.0
        val impact = 0.6 * sigma * sqrt(participation)
        val roundTrip = (spreadCost + impact) * 2.0
        val stopBps = if (r.price > 0.0) abs(r.price - r.stop) / r.price * 10000.0 else 0.0
        val gainBps = if (r.price > 0.0) abs(r.target - r.price) / r.price * 10000.0 else 0.0
        val rrNet = if (stopBps + roundTrip > 0.0) (gainBps - roundTrip) / (stopBps + roundTrip) else 0.0
        val viable = stopBps >= 3.0 * roundTrip && rrNet >= 1.5
        val reason = when {
            viable -> ""
            stopBps < 3.0 * roundTrip -> "stop ${stopBps.roundToInt()}bps < 3x cost"
            else -> "net R:R ${"%.2f".format(rrNet)} < 1.5"
        }
        return CostEstimate(
            spreadBps = roundTo(spreadCost, 1),
            impactBps = roundTo(impact, 1),
            roundTripBps = roundTo(roundTrip, 1),
            rrNet = roundTo(rrNet, 2),
            viable = viable,
            reason = reason
        )
    }

    data class EngineOutput(val rows: List<SignalRow>, val regime: MarketRegime)

    fun runEngine(bars: Map<String, BarSeries>, snap: Map<String, SnapshotQuote>, cfg: AppConfig): EngineOutput {
        val reg = detectRegime(bars)
        val rawRows = bars.keys.mapNotNull { sym -> evaluate(sym, bars[sym]!!, cfg) }
        if (rawRows.isEmpty()) return EngineOutput(emptyList(), reg)

        val zTech = rankZ(rawRows.map { it.score })
        val zRR = rankZ(rawRows.map { it.rr })
        val zLiq = rankZ(rawRows.map { log10(max(snap[it.symbol]?.dollarVol ?: 1.0, 1.0)) })
        val zChg = rankZ(rawRows.map { snap[it.symbol]?.changePct ?: 0.0 })

        val panicScale = if (reg.panicGuardActive) 0.50 else 1.0
        val meanRevRegime = reg.label == RegimeLabel.RISK_OFF || reg.label == RegimeLabel.CRISIS

        val fused = rawRows.mapIndexed { i, r ->
            val comp = 0.5 * zTech[i] + 0.2 * zRR[i] + 0.15 * zLiq[i] + 0.15 * zChg[i]
            val effectiveLongMult = reg.longMult * panicScale
            var conviction = roundTo(comp * (if (comp > 0) effectiveLongMult else reg.shortMult), 3)
            var index = clamp(50.0 + 22.0 * conviction, 0.0, 100.0)
            var action = when {
                conviction >= 0.85 && reg.label != RegimeLabel.CRISIS -> SignalAction.BUY
                conviction <= -0.85 -> SignalAction.SELL
                else -> SignalAction.HOLD
            }
            var stop = r.stop
            var target = r.target
            var rr = r.rr
            var strategyTag = "MOMENTUM"
            var reasons = r.reasons

            // AlphaTrader 3.0: Mean reversion gated strictly behind RISK_OFF / CRISIS regimes (RSI <= 32, px < EMA200)
            val symBars = bars[r.symbol]
            val mrMaxAtr = if (r.symbol.contains("/")) 18.0 else 9.0
            if (meanRevRegime && symBars != null && symBars.c.size >= 200 && r.atrPct in 0.3..mrMaxAtr) {
                val rVal = rsi(symBars.c).last()
                val e200 = ema(symBars.c, 200).last()
                val px = symBars.c.last()
                val pxDec = if (r.symbol.contains("/") && px < 5.0) 4 else 2
                if (rVal <= 32.0 && px < e200) {
                    val aVal = atr(symBars.h, symBars.l, symBars.c).last().coerceAtLeast(px * 0.005)
                    stop = roundTo(px - 2.0 * aVal, pxDec)
                    target = roundTo(px + 2.0 * aVal, pxDec)
                    val mrRisk = abs(px - stop)
                    rr = if (mrRisk > 0.0) roundTo(abs(target - px) / mrRisk, 2) else 1.0
                    strategyTag = "MEAN_REV"
                    action = SignalAction.BUY
                    conviction = roundTo(max(conviction, 0.90) * panicScale, 3)
                    index = clamp(50.0 + 22.0 * conviction, 0.0, 100.0)
                    reasons = listOf("Mean reversion gated (${reg.label.name} · RSI ${rVal.roundToInt()} ≤ 32)") + r.reasons
                }
            }

            val s = snap[r.symbol]
            val adv = s?.dollarVol ?: 5e7
            val sized = roundTo(r.size * panicScale, 4)
            val updated = r.copy(
                conviction = conviction,
                index = index,
                action = action,
                stop = stop,
                target = target,
                rr = rr,
                size = sized,
                reasons = reasons,
                spreadBps = s?.spreadBps ?: 10.0,
                rs20 = s?.changePct ?: 0.0,
                adv = adv,
                strategyTag = strategyTag
            )
            updated.copy(cost = estimateCost(updated))
        }
            .sortedByDescending { it.conviction }
            .mapIndexed { idx, row -> row.copy(rank = idx + 1) }

        return EngineOutput(fused, reg)
    }

    // ── Deterministic Demo Data (Mulberry32) ──

    private fun mulberry(initialSeed: Int): () -> Double {
        var seed = initialSeed
        return {
            seed += 0x6D2B79F5
            var t = (seed xor (seed ushr 15)) * (1 or seed)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            ((t xor (t ushr 14)).toUInt().toDouble()) / 4294967296.0
        }
    }

    fun genSeries(sym: String, days: Int = 300): BarSeries {
        val seed = sym.sumOf { it.code } + 7
        val r = mulberry(seed)
        val drift = (r() - 0.42) * 0.0022
        val vol = 0.012 + r() * 0.022
        var px = 20.0 + r() * 380.0
        val o = ArrayList<Double>(days)
        val h = ArrayList<Double>(days)
        val l = ArrayList<Double>(days)
        val c = ArrayList<Double>(days)
        val v = ArrayList<Double>(days)
        for (i in 0 until days) {
            val shock = (r() + r() + r() - 1.5) * vol
            val op = px
            px = max(0.8, px * (1.0 + drift + shock))
            o.add(op)
            c.add(px)
            h.add(max(op, px) * (1.0 + r() * vol * 0.5))
            l.add(min(op, px) * (1.0 - r() * vol * 0.5))
            v.add(((0.4 + r() * 2.2) * 1e6).roundToInt().toDouble())
        }
        return BarSeries(o, h, l, c, v)
    }

    fun demoSnap(symbols: List<String> = UNIVERSE): Map<String, SnapshotQuote> {
        val out = LinkedHashMap<String, SnapshotQuote>()
        symbols.forEach { sym ->
            val r = mulberry(sym.sumOf { it.code } + 99)()
            out[sym] = SnapshotQuote(
                price = 0.0,
                changePct = (r - 0.45) * 6.0,
                spreadBps = 3.0 + r * 12.0,
                dollarVol = (r + 0.4) * 8e8
            )
        }
        return out
    }

    private val DEMO_HEADLINES = mapOf(
        "NVDA" to listOf(
            "Data-center backlog extends into 2027, supplier checks show",
            "Hyperscaler capex raised across three majors"
        ),
        "AMD" to listOf(
            "New accelerator ships to two cloud customers",
            "Analyst raises target on inference share gains"
        ),
        "TSLA" to listOf(
            "Deliveries miss consensus by 4%",
            "Price cuts expanded to two more European markets"
        ),
        "META" to listOf(
            "Ad load rises on Reels, impressions up double digits",
            "EU opens probe into ad-measurement practices"
        ),
        "COIN" to listOf(
            "Spot volumes jump 38% month over month",
            "Regulatory clarity bill advances out of committee"
        ),
        "SMCI" to listOf(
            "Auditor flags internal controls, filing delayed",
            "Index committee reviews continued inclusion"
        ),
        "SPY" to listOf(
            "Fed minutes show split on timing of next cut",
            "Breadth narrows as megacaps carry the tape"
        )
    )

    private val DEMO_BAD_REGEX = Regex("miss|cut|probe|flags|delay|narrow|downgrade|investigat", RegexOption.IGNORE_CASE)
    private val DEMO_HIGH_IMPACT_REGEX = Regex("backlog|jump 38%|auditor|probe|miss consensus", RegexOption.IGNORE_CASE)

    fun demoNews(rows: List<SignalRow>): List<NewsHeadline> {
        val out = ArrayList<NewsHeadline>()
        val now = Instant.now()
        rows.forEachIndexed { idx, r ->
            (DEMO_HEADLINES[r.symbol] ?: emptyList()).forEachIndexed { j, title ->
                val published = now.minus((idx * 2L + j * 3L + 1L), ChronoUnit.HOURS).toString()
                out.add(
                    NewsHeadline(
                        sym = r.symbol,
                        title = title,
                        summary = "",
                        publishedIso = published,
                        source = "demo"
                    )
                )
            }
        }
        return out
    }

    fun demoNewsMap(news: List<NewsHeadline>): Map<String, SymbolNewsSummary> {
        data class Acc(var signed: Double = 0.0, var mat: Double = 0.0, var n: Int = 0, val items: MutableList<ScoredNewsItem> = mutableListOf())
        val map = LinkedHashMap<String, Acc>()
        news.forEach { h ->
            val dir = if (DEMO_BAD_REGEX.containsMatchIn(h.title)) -1.0 else 1.0
            val mat = if (DEMO_HIGH_IMPACT_REGEX.containsMatchIn(h.title)) 0.74 else 0.42
            val acc = map.getOrPut(h.sym) { Acc() }
            acc.signed += dir * mat
            acc.mat += mat
            acc.n++
            acc.items.add(
                ScoredNewsItem(
                    title = h.title,
                    direction = dir,
                    materiality = mat,
                    rationale = "demo",
                    decay = 0.9
                )
            )
        }
        return map.mapValues { (_, acc) ->
            val n = max(acc.n, 1)
            SymbolNewsSummary(
                direction = acc.signed / n,
                materiality = acc.mat / n,
                signed = acc.signed / sqrt(n.toDouble()),
                items = acc.items.sortedByDescending { it.materiality }
            )
        }
    }

    fun applyNews(rows: List<SignalRow>, newsMap: Map<String, SymbolNewsSummary>): List<SignalRow> {
        return rows.map { r ->
            val n = newsMap[r.symbol] ?: return@map r
            val updatedReasons = r.reasons.toMutableList()
            var action = r.action
            if (r.action == SignalAction.BUY && n.signed < -0.35 && n.materiality > 0.45) {
                action = SignalAction.HOLD
                updatedReasons.add(0, "news veto: negative catalyst against a long")
            } else if (r.action == SignalAction.SELL && n.signed > 0.35 && n.materiality > 0.45) {
                action = SignalAction.HOLD
                updatedReasons.add(0, "news veto: positive catalyst against a short")
            }
            r.copy(
                action = action,
                reasons = updatedReasons,
                newsDirection = n.direction,
                newsMateriality = n.materiality,
                newsItems = n.items,
                keyCatalyst = n.items.firstOrNull()?.title.orEmpty()
            )
        }
    }

    fun appendScanRegime(
        history: List<ScanRegimeSnapshot>,
        regime: MarketRegime,
        source: String,
        nowMs: Long = System.currentTimeMillis(),
        maxCount: Int = 5
    ): List<ScanRegimeSnapshot> {
        val next = ScanRegimeSnapshot(
            timestampMs = nowMs,
            label = regime.label,
            breadth = regime.breadth,
            source = source
        )
        return (history + next).takeLast(maxCount.coerceAtLeast(1))
    }

    fun buildRegimeHeatmap(regime: MarketRegime): RegimeHeatmapData {
        fun trendBucketFor(trend: Double): TrendBucket = when {
            trend >= 0.5 -> TrendBucket.BULL
            trend <= -0.5 -> TrendBucket.BEAR
            else -> TrendBucket.MIXED
        }

        fun volBucketFor(volPct: Double): VolatilityBucket = when {
            volPct > 0.90 -> VolatilityBucket.EXTREME
            volPct >= 0.80 -> VolatilityBucket.HIGH
            volPct >= 0.50 -> VolatilityBucket.NORMAL
            else -> VolatilityBucket.CALM
        }

        val activeVol = volBucketFor(regime.volPct)
        val activeTrend = trendBucketFor(regime.trendScore)
        val activeRow = VolatilityBucket.entries.indexOf(activeVol)
        val activeCol = TrendBucket.entries.indexOf(activeTrend)

        val counts = HashMap<Pair<Int, Int>, Int>()
        regime.timeline.forEach { pt ->
            val r = VolatilityBucket.entries.indexOf(volBucketFor(pt.volPct))
            val c = TrendBucket.entries.indexOf(trendBucketFor(pt.trend))
            counts[r to c] = (counts[r to c] ?: 0) + 1
        }

        val cells = ArrayList<RegimeMatrixCell>(12)
        VolatilityBucket.entries.forEachIndexed { r, volBucket ->
            TrendBucket.entries.forEachIndexed { c, trendBucket ->
                val (lbl, title, summary, health) = when (volBucket) {
                    VolatilityBucket.CALM -> when (trendBucket) {
                        TrendBucket.BULL -> Quad(RegimeLabel.RISK_ON, "Strong Risk-On", "Calm uptrend · 1.15x long bias", 92)
                        TrendBucket.MIXED -> Quad(RegimeLabel.NEUTRAL, "Calm Range", "Low-vol consolidation · 1.0x size", 68)
                        TrendBucket.BEAR -> Quad(RegimeLabel.RISK_OFF, "Orderly Drift", "Low-vol downtrend · 0.6x long cap", 34)
                    }
                    VolatilityBucket.NORMAL -> when (trendBucket) {
                        TrendBucket.BULL -> Quad(RegimeLabel.RISK_ON, "Risk-On", "Constructive trend · 1.15x long bias", 80)
                        TrendBucket.MIXED -> Quad(RegimeLabel.NEUTRAL, "Neutral Tape", "Balanced two-way market · 1.0x size", 54)
                        TrendBucket.BEAR -> Quad(RegimeLabel.RISK_OFF, "Risk-Off", "Bearish trend · 1.25x short / 0.6x long", 26)
                    }
                    VolatilityBucket.HIGH -> when (trendBucket) {
                        TrendBucket.BULL -> Quad(RegimeLabel.NEUTRAL, "Volatile Bull", "Elevated vol in uptrend · selective sizing", 56)
                        TrendBucket.MIXED -> Quad(RegimeLabel.RISK_OFF, "Choppy Stress", "High vol without trend · defensive", 36)
                        TrendBucket.BEAR -> Quad(RegimeLabel.RISK_OFF, "Heavy Risk-Off", "High-vol selloff · defensive posture", 18)
                    }
                    VolatilityBucket.EXTREME -> when (trendBucket) {
                        TrendBucket.BULL -> Quad(RegimeLabel.RISK_OFF, "Vol Shock Bull", "Extreme vol spike · reduce exposure", 38)
                        TrendBucket.MIXED -> Quad(RegimeLabel.RISK_OFF, "Vol Shock", "Unstable volatility spike · stay selective", 22)
                        TrendBucket.BEAR -> Quad(RegimeLabel.CRISIS, "Crisis", "Bearish crash regime · 0.6x long cap", 8)
                    }
                }
                cells.add(
                    RegimeMatrixCell(
                        row = r,
                        col = c,
                        volBucket = volBucket,
                        trendBucket = trendBucket,
                        regimeLabel = lbl,
                        title = title,
                        summary = summary,
                        healthScore = health,
                        isCurrent = (r == activeRow && c == activeCol),
                        historyCount = counts[r to c] ?: 0
                    )
                )
            }
        }

        val trendPts = ((regime.trendScore.coerceIn(-1.0, 1.0) + 1.0) / 2.0) * 40.0
        val breadthPts = regime.breadth.coerceIn(0.0, 1.0) * 35.0
        val calmPts = (1.0 - regime.volPct.coerceIn(0.0, 1.0)) * 25.0
        val overallHealth = (trendPts + breadthPts + calmPts).roundToInt().coerceIn(0, 100)
        val verdict = when {
            overallHealth >= 75 -> "Healthy"
            overallHealth >= 58 -> "Constructive"
            overallHealth >= 42 -> "Cautious"
            overallHealth >= 25 -> "Defensive"
            else -> "Stressed"
        }

        return RegimeHeatmapData(
            cells = cells,
            activeRow = activeRow,
            activeCol = activeCol,
            overallHealthScore = overallHealth,
            healthVerdict = verdict
        )
    }

    private data class Quad(
        val label: RegimeLabel,
        val title: String,
        val summary: String,
        val health: Int
    )
}
