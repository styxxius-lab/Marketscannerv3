package com.example.domain.model

import java.util.Locale

/**
 * Exact categorical risk gating taxonomy from `crypto_events.py` and `cryptotrader.py 2.0`.
 * Every AI/event gate can ONLY shrink risk (mult in [0, 1] and optional entry block);
 * it can never increase position size or force an exit.
 */
enum class CryptoEventCategory(
    val mult: Double,
    val block: Boolean,
    val why: String
) {
    HACK(0.0, true, "exploit, bridge drain, protocol compromise"),
    HALT(0.0, true, "exchange halts deposits, withdrawals or trading"),
    DELIST(0.0, true, "delisting or trading suspension"),
    DEPEG(0.3, true, "stablecoin or wrapped asset loses peg"),
    REGULATORY(0.4, false, "SEC/CFTC action, enforcement, ban"),
    UNLOCK(0.5, false, "large token unlock or vesting cliff"),
    OUTAGE(0.6, false, "chain halt, validator or node outage"),
    NONE(1.0, false, "no material adverse event");

    companion object {
        fun fromWire(raw: String?): CryptoEventCategory {
            val key = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return entries.firstOrNull { it.name == key } ?: NONE
        }
    }
}

data class CryptoEventClassification(
    val category: CryptoEventCategory = CryptoEventCategory.NONE,
    val mult: Double = 1.0,
    val block: Boolean = false,
    val confidence: Double = 0.0,
    val span: String = "",
    val headline: String = "",
    val viaLlm: Boolean = false
) {
    val isAdverse: Boolean get() = category != CryptoEventCategory.NONE

    companion object {
        fun none(headline: String = ""): CryptoEventClassification = CryptoEventClassification(
            category = CryptoEventCategory.NONE,
            mult = 1.0,
            block = false,
            confidence = 0.0,
            span = "",
            headline = headline,
            viaLlm = false
        )
    }
}

/**
 * Multi-horizon risk-adjusted time-series momentum signal (`cryptotrader.py 2.0`).
 */
data class CryptoTsmomSignal(
    val symbol: String,
    val price: Double,
    val score: Double,
    val annVol: Double,
    val atr: Double,
    val aboveEma50: Boolean,
    val trendStack: Boolean,
    val expectedEdgeBps: Double,
    val costBps: Double,
    val costViable: Boolean,
    val qualified: Boolean
)

/**
 * BTC macro regime scaler (`_btc_regime` in `cryptotrader.py 2.0`).
 */
data class CryptoBtcRegime(
    val label: String = "NO_BTC",
    val mult: Double = 1.0,
    val btcPrice: Double = 0.0,
    val btcEma200: Double = 0.0,
    val volPercentile: Double = 0.5
)

data class CryptoHeatRow(
    val symbol: String,
    val riskUsd: Double
)

/**
 * Portfolio heat cap: total dollar & % capital at risk across all open stops.
 */
data class CryptoPortfolioHeat(
    val totalRiskUsd: Double = 0.0,
    val pctOfEquity: Double = 0.0,
    val capPct: Double = 6.0,
    val headroomUsd: Double = 0.0,
    val breach: Boolean = false,
    val rows: List<CryptoHeatRow> = emptyList()
)

data class StablecoinPegRow(
    val symbol: String,
    val price: Double? = null,
    val devBps: Double? = null,
    val breach: Boolean = false,
    val error: String? = null
)

data class StablecoinPegStatus(
    val rows: List<StablecoinPegRow> = emptyList(),
    val anyBreach: Boolean = false,
    val checkedAtMs: Long = 0L
)

enum class DecayVerdict {
    INSUFFICIENT,
    OK,
    WEAK,
    DECAYED
}

data class CryptoDecayReport(
    val sampleCount: Int = 0,
    val totalClosed: Int = 0,
    val meanPct: Double = 0.0,
    val sdPct: Double = 0.0,
    val tStat: Double? = null,
    val priorMeanPct: Double? = null,
    val verdict: DecayVerdict = DecayVerdict.INSUFFICIENT,
    val note: String = "need 20 closed trades"
)

data class CryptoFeedFreshness(
    val symbol: String,
    val ageHours: Double?,
    val stale: Boolean
)

/**
 * Result of the 9-variant grid backtest with Deflated Sharpe Ratio (DSR)
 * and Probability of Backtest Overfitting (PBO, 10-block CSCV).
 */
data class CryptoBacktestSummary(
    val bestMaxPos: Int,
    val bestTargetVol: Double,
    val sessions: Int,
    val years: Double,
    val cagrPct: Double,
    val netSharpe: Double,
    val maxDrawdownPct: Double,
    val bestDayPct: Double,
    val worstDayPct: Double,
    val variantsTried: Int,
    val deflatedSharpe: Double,
    val pbo: Double,
    val passed: Boolean,
    val pairCount: Int,
    val feeBps: Double,
    val slipBps: Double,
    val ranAtMs: Long = System.currentTimeMillis()
)

/**
 * Consolidated live snapshot of all `cryptotrader.py 2.0` risk guards & signals.
 */
data class CryptoTraderSnapshot(
    val btcRegime: CryptoBtcRegime = CryptoBtcRegime(),
    val tsmomSignals: List<CryptoTsmomSignal> = emptyList(),
    val qualifiedSymbols: List<String> = emptyList(),
    val portfolioHeat: CryptoPortfolioHeat = CryptoPortfolioHeat(),
    val pegStatus: StablecoinPegStatus = StablecoinPegStatus(),
    val decayReport: CryptoDecayReport = CryptoDecayReport(),
    val feedFreshness: List<CryptoFeedFreshness> = emptyList(),
    val stalePairCount: Int = 0,
    val adverseEventsCount: Int = 0,
    val updatedAtMs: Long = 0L
)
