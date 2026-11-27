package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.EncryptedPrefsStore
import com.example.data.notification.CatalystMarketAlertNotifier
import com.example.data.notification.PriceAlertNotifier
import com.example.data.remote.YahooChartClient
import com.example.data.repository.MarketRepository
import com.example.data.service.CryptoMarketService
import com.example.data.service.MarketCatalystService
import com.example.domain.engine.BackgroundCatalystMonitorEngine
import com.example.domain.engine.BackgroundMonitorRunStatus
import com.example.domain.engine.PriceAlertEngine
import com.example.domain.engine.QuantEngine
import com.example.domain.engine.TickerCatalystEngine
import com.example.domain.engine.TickerPriceMove
import com.example.domain.model.AppConfig
import com.example.domain.model.PriceAlert
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Jetpack WorkManager background worker that periodically:
 * 1. Checks armed user Price Threshold alerts and AI Signal Change alerts (for both US equities
 *    and 24/7 Kraken crypto pairs) and fires Android push notifications via [PriceAlertNotifier].
 * 2. Fetches latest market headlines + LLM Catalyst Scores via [MarketCatalystService] and checks
 *    intraday price moves via [YahooChartClient], dispatching local notifications via
 *    [CatalystMarketAlertNotifier] when enabled.
 */
class CatalystMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = EncryptedPrefsStore(applicationContext)
        val catalystNotifier = CatalystMarketAlertNotifier(applicationContext)
        val priceAlertNotifier = PriceAlertNotifier(applicationContext)
        val nowMs = System.currentTimeMillis()
        val isManualCheck = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)

        try {
            val appConfig = store.loadConfig()
            val monitorConfig = store.loadBackgroundMonitorConfig()
            val savedPriceAlerts = store.loadAlerts()
            val savedSignalAlerts = store.loadSignalAlerts()

            val hasArmedUserAlerts = savedPriceAlerts.any { it.active } ||
                savedSignalAlerts.any { it.enabled }
            val runCatalystCheck = monitorConfig.enabled || isManualCheck

            if (!runCatalystCheck && !hasArmedUserAlerts) {
                return@withContext Result.success(workDataOf(OUT_ALERTS_COUNT to 0))
            }

            val okHttp = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val chartClient = YahooChartClient(okHttp)

            // 1) Evaluate armed Price Threshold & AI Signal Change alerts (Stocks + Crypto 24/7)
            val userAlertsSummary = if (hasArmedUserAlerts) {
                evaluateUserPriceAndSignalAlerts(
                    store = store,
                    priceAlertNotifier = priceAlertNotifier,
                    chartClient = chartClient,
                    appConfig = appConfig,
                    savedPriceAlerts = savedPriceAlerts,
                    savedSignalAlerts = savedSignalAlerts,
                    nowMs = nowMs
                )
            } else {
                UserAlertCheckCounts(priceTriggered = 0, signalTriggered = 0, checkedSymbols = 0)
            }

            // 2) Evaluate Catalyst & Market Movement Monitor if enabled or manually triggered
            var catalystAlertsCount = 0
            var catalystTickersChecked = 0
            var catalystScorerLabel = ""
            var catalystTopTitle: String? = null
            var catalystSentCount = 0

            if (runCatalystCheck) {
                val botState = store.loadBotState()
                val tickers = resolveMonitoredTickers(
                    watchlist = appConfig.watchlist,
                    openPositionSymbols = botState.positions.map { it.symbol },
                    watchlistOnly = monitorConfig.watchlistOnly
                )
                catalystTickersChecked = tickers.size

                val catalystService = MarketCatalystService()
                val (catalystBatch, priceMoves) = coroutineScope {
                    val catalystDeferred = async {
                        catalystService.analyzeTickers(
                            tickers = tickers,
                            config = appConfig,
                            maxHeadlinesPerTicker = 3
                        )
                    }
                    val priceMovesDeferred = async {
                        tickers.map { sym ->
                            async {
                                val series = runCatching {
                                    chartClient.fetchChart(sym, range = "5d", interval = "1d")
                                }.getOrNull()
                                if (series != null && series.previousClose > 0.0) {
                                    val chgPct = ((series.currentPrice - series.previousClose) / series.previousClose) * 100.0
                                    sym to TickerPriceMove(
                                        symbol = sym,
                                        currentPrice = series.currentPrice,
                                        previousClose = series.previousClose,
                                        changePct = chgPct
                                    )
                                } else {
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }
                    catalystDeferred.await() to priceMovesDeferred.await()
                }

                catalystScorerLabel = catalystBatch.scorerLabel
                val existingKeys = store.loadBackgroundMonitorNotifiedKeys()
                val effectiveConfig = if (isManualCheck) monitorConfig.copy(enabled = true) else monitorConfig

                val newAlerts = BackgroundCatalystMonitorEngine.evaluateAlerts(
                    reports = catalystBatch.reports,
                    priceMovesBySymbol = priceMoves,
                    config = effectiveConfig,
                    watchlist = appConfig.watchlist,
                    alreadyNotifiedKeys = if (isManualCheck) emptySet() else existingKeys,
                    nowMs = nowMs
                )

                catalystAlertsCount = newAlerts.size
                catalystTopTitle = newAlerts.firstOrNull()?.title
                catalystSentCount = if (newAlerts.isNotEmpty()) {
                    catalystNotifier.notifyAlerts(newAlerts)
                } else {
                    0
                }
                if (newAlerts.isNotEmpty()) {
                    val updatedKeys = existingKeys + newAlerts.map { it.dedupeKey }
                    store.saveBackgroundMonitorNotifiedKeys(updatedKeys)
                }
            }

            val totalTriggered = catalystAlertsCount + userAlertsSummary.priceTriggered + userAlertsSummary.signalTriggered
            val totalChecked = maxOf(catalystTickersChecked, userAlertsSummary.checkedSymbols)

            val summaryParts = buildList {
                if (userAlertsSummary.priceTriggered > 0) {
                    add("${userAlertsSummary.priceTriggered} price alert(s)")
                }
                if (userAlertsSummary.signalTriggered > 0) {
                    add("${userAlertsSummary.signalTriggered} AI signal flip(s)")
                }
                if (catalystAlertsCount > 0) {
                    add("$catalystAlertsCount catalyst alert(s)")
                }
            }

            val topAlertSummary = when {
                summaryParts.isNotEmpty() -> {
                    val permHint = if (catalystSentCount == 0 && !catalystNotifier.hasNotificationPermission()) {
                        " (grant notification permission)"
                    } else ""
                    val headlineSuffix = catalystTopTitle?.let { ": $it" }.orEmpty()
                    "Triggered ${summaryParts.joinToString(", ")}$headlineSuffix$permHint"
                }
                else -> "Checked $totalChecked tickers (price, AI signal & catalyst) — no breaches."
            }

            store.saveBackgroundMonitorStatus(
                BackgroundMonitorRunStatus(
                    lastRunAtMs = nowMs,
                    tickersChecked = totalChecked,
                    alertsTriggered = totalTriggered,
                    lastSummary = topAlertSummary,
                    scorerLabel = catalystScorerLabel.ifBlank { "Price & Signal Watch" },
                    succeeded = true
                )
            )

            Result.success(
                workDataOf(
                    OUT_ALERTS_COUNT to totalTriggered,
                    OUT_TICKERS_CHECKED to totalChecked,
                    OUT_SUMMARY to topAlertSummary
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.saveBackgroundMonitorStatus(
                BackgroundMonitorRunStatus(
                    lastRunAtMs = nowMs,
                    tickersChecked = 0,
                    alertsTriggered = 0,
                    lastSummary = "Check deferred: ${e.message?.take(72) ?: "network unavailable"}",
                    scorerLabel = "",
                    succeeded = false
                )
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private data class UserAlertCheckCounts(
        val priceTriggered: Int,
        val signalTriggered: Int,
        val checkedSymbols: Int
    )

    private suspend fun evaluateUserPriceAndSignalAlerts(
        store: EncryptedPrefsStore,
        priceAlertNotifier: PriceAlertNotifier,
        chartClient: YahooChartClient,
        appConfig: AppConfig,
        savedPriceAlerts: List<PriceAlert>,
        savedSignalAlerts: List<SignalChangeAlert>,
        nowMs: Long
    ): UserAlertCheckCounts {
        val activePriceSyms = savedPriceAlerts.filter { it.active }
            .map { it.symbol.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
        val activeSignalSyms = savedSignalAlerts.filter { it.enabled }
            .map { it.symbol.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()

        val allSyms = (activePriceSyms + activeSignalSyms).distinct()
        if (allSyms.isEmpty()) {
            return UserAlertCheckCounts(0, 0, 0)
        }

        val livePrices = LinkedHashMap<String, Double>()
        val scannedRows = mutableListOf<SignalRow>()

        val cryptoPriceSyms = activePriceSyms.filter { it.contains("/") }
        val stockPriceSyms = activePriceSyms.filterNot { it.contains("/") }
        val cryptoSignalSyms = activeSignalSyms.filter { it.contains("/") }
        val stockSignalSyms = activeSignalSyms.filterNot { it.contains("/") }

        val cryptoService = CryptoMarketService()
        val repository = MarketRepository()

        coroutineScope {
            // Live Kraken Spot prices for armed crypto price alerts
            val cryptoPriceJob = async {
                if (cryptoPriceSyms.isNotEmpty()) {
                    runCatching { cryptoService.fetchLatestCryptoPrices(cryptoPriceSyms) }
                        .getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }

            // Live Yahoo prices for armed stock price alerts (skipping ones covered by full signal scan)
            val stockOnlyPriceSyms = stockPriceSyms.filterNot { stockSignalSyms.contains(it) }
            val stockPriceJob = async {
                stockOnlyPriceSyms.map { sym ->
                    async {
                        val series = runCatching {
                            chartClient.fetchChart(sym, range = "5d", interval = "1d")
                        }.getOrNull()
                        if (series != null && series.currentPrice > 0.0) sym to series.currentPrice else null
                    }
                }.awaitAll().filterNotNull().toMap()
            }

            // Full quant/AI signal scan for armed equity signal-change alerts
            val stockSignalJob = async {
                if (stockSignalSyms.isNotEmpty()) {
                    runCatching { repository.scanCustomSymbols(stockSignalSyms, appConfig).rows }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

            // Full 24/7 Kraken OHLC + quant signal analysis for armed crypto signal-change alerts
            val cryptoSignalJob = async {
                if (cryptoSignalSyms.isNotEmpty()) {
                    runCatching {
                        val bundle = cryptoService.fetchCryptoMarketBundle(
                            symbols = cryptoSignalSyms,
                            cryptoPanicToken = appConfig.cryptoPanicToken,
                            fundingHistory = emptyList()
                        )
                        if (bundle.bars.isNotEmpty()) {
                            QuantEngine.runEngine(bundle.bars, bundle.snapshots, appConfig).rows
                        } else {
                            emptyList()
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

            livePrices.putAll(cryptoPriceJob.await())
            livePrices.putAll(stockPriceJob.await())
            val stockRows = stockSignalJob.await()
            val cryptoRows = cryptoSignalJob.await()
            scannedRows.addAll(stockRows)
            scannedRows.addAll(cryptoRows)
            scannedRows.forEach { row ->
                if (row.price > 0.0) {
                    livePrices[row.symbol.uppercase(Locale.US)] = row.price
                }
            }
        }

        // Evaluate armed price threshold alerts
        var priceTriggeredCount = 0
        if (savedPriceAlerts.any { it.active } && livePrices.isNotEmpty()) {
            val priceEval = PriceAlertEngine.evaluate(savedPriceAlerts, livePrices, nowMs)
            if (priceEval.newlyTriggered.isNotEmpty()) {
                store.saveAlerts(priceEval.updatedAlerts)
                priceEval.newlyTriggered.forEach { fired ->
                    priceAlertNotifier.notifyTriggered(fired)
                }
                priceTriggeredCount = priceEval.newlyTriggered.size
            }
        }

        // Evaluate armed AI signal-change alerts
        var signalTriggeredCount = 0
        if (savedSignalAlerts.any { it.enabled } && scannedRows.isNotEmpty()) {
            val sigEval = PriceAlertEngine.evaluateSignalChanges(savedSignalAlerts, scannedRows, nowMs)
            if (sigEval.updatedAlerts != savedSignalAlerts) {
                store.saveSignalAlerts(sigEval.updatedAlerts)
            }
            if (sigEval.triggeredEvents.isNotEmpty()) {
                sigEval.triggeredEvents.forEach { ev ->
                    priceAlertNotifier.notifySignalChange(ev)
                }
                signalTriggeredCount = sigEval.triggeredEvents.size
            }
        }

        return UserAlertCheckCounts(
            priceTriggered = priceTriggeredCount,
            signalTriggered = signalTriggeredCount,
            checkedSymbols = allSyms.size
        )
    }

    private fun resolveMonitoredTickers(
        watchlist: List<String>,
        openPositionSymbols: List<String>,
        watchlistOnly: Boolean
    ): List<String> {
        val cleanWatch = watchlist.map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.distinct()
        if (watchlistOnly && cleanWatch.isNotEmpty()) {
            return cleanWatch.take(MAX_MONITORED_TICKERS)
        }
        return buildList {
            addAll(cleanWatch)
            openPositionSymbols.forEach { sym ->
                val clean = sym.trim().uppercase(Locale.US)
                if (clean.isNotEmpty() && !contains(clean)) add(clean)
            }
            add("SPY")
            TickerCatalystEngine.DEFAULT_TICKERS.forEach { sym ->
                if (!contains(sym)) add(sym)
            }
        }.distinct().take(MAX_MONITORED_TICKERS)
    }

    companion object {
        const val KEY_MANUAL_TRIGGER = "manual_trigger"
        const val OUT_ALERTS_COUNT = "alerts_count"
        const val OUT_TICKERS_CHECKED = "tickers_checked"
        const val OUT_SUMMARY = "summary"
        private const val MAX_MONITORED_TICKERS = 8
    }
}
