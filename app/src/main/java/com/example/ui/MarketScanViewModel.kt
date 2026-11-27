package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EncryptedPrefsStore
import com.example.data.notification.NewsCatalystNotifier
import com.example.data.notification.PriceAlertNotifier
import com.example.data.notification.ThresholdAlertNotifier
import com.example.data.notification.TradeExecutionNotifier
import com.example.data.notification.VixAlertNotifier
import com.example.data.repository.MarketRepository
import com.example.data.worker.CatalystMonitorScheduler
import com.example.domain.engine.AdvisorEngine
import com.example.domain.engine.AdvisorEvent
import com.example.domain.engine.AdvisorJournalReport
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.engine.CryptoTraderEngine
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.engine.NewsCatalystAlertEngine
import com.example.domain.engine.PaperBotEngine
import com.example.domain.engine.PriceAlertEngine
import com.example.domain.engine.QuantEngine
import com.example.domain.engine.QuantScannerUiState
import com.example.domain.engine.QuantSignalScannerEngine
import com.example.domain.engine.ThresholdAlertEngine
import com.example.domain.engine.ThresholdAlertMonitorState
import com.example.domain.engine.VixAlertEngine
import com.example.domain.engine.VixMonitorState
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.AlertCondition
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.BotState
import com.example.domain.model.CryptoBacktestSummary
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoPanicPost
import com.example.domain.model.CryptoTraderSnapshot
import com.example.domain.model.DecayVerdict
import com.example.domain.model.FinnhubNewsFeedState
import com.example.domain.model.MainTab
import com.example.domain.model.MarketMode
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSector
import com.example.domain.model.ModelProbeResult
import com.example.domain.model.ModelTier
import com.example.domain.model.ModelTierFilter
import com.example.domain.model.NewsArticleFilter
import com.example.domain.model.NewsHeadline
import com.example.domain.model.PriceAlert
import com.example.domain.model.RadarModel
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ScanRegimeSnapshot
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalAlertMode
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalFilter
import com.example.domain.model.SignalRow
import com.example.domain.model.SignalSort
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThresholdAlertEvent
import com.example.domain.model.ThresholdAlertSettings
import com.example.domain.model.VixSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MarketScanUiState(
    val activeTab: MainTab = MainTab.SIGNALS,
    val filter: SignalFilter = SignalFilter.ALL,
    val selectedSector: MarketSector? = null,
    val sort: SignalSort = SignalSort.CONVICTION,
    val searchQuery: String = "",
    val rows: List<SignalRow> = emptyList(),
    val regime: MarketRegime = MarketRegime(
        label = RegimeLabel.NEUTRAL,
        breadth = 0.5,
        longMult = 1.0,
        shortMult = 1.0,
        note = ""
    ),
    val news: List<NewsHeadline> = emptyList(),
    val source: String = "",
    val statusText: String = "starting…",
    val isScanning: Boolean = false,
    val config: AppConfig = AppConfig(),
    val botState: BotState = BotState(),
    val scanRegimeHistory: List<ScanRegimeSnapshot> = emptyList(),
    val alerts: List<PriceAlert> = emptyList(),
    val signalAlerts: List<SignalChangeAlert> = emptyList(),
    val alertsSheetOpen: Boolean = false,
    val alertSheetSymbol: String? = null,
    val showSimulationDisclaimer: Boolean = false,
    val selectedSignal: SignalRow? = null,
    val vixSnapshot: VixSnapshot? = null,
    val vixMonitorState: VixMonitorState = VixMonitorState(),
    val threatsBySymbol: Map<String, ThreatAssessment> = emptyMap(),
    val assessingThreatSymbols: Set<String> = emptySet(),
    val barsBySymbol: Map<String, BarSeries> = emptyMap(),
    val advisorEnabled: Boolean = false,
    val advisorVetoReady: Boolean = false,
    val advisorModel: String = "",
    val advisorCallsUsed: Int = 0,
    val advisorMaxCallsPerHour: Int = 40,
    val advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
    val advisorCheckingSymbol: String? = null,
    val advisorJournal: AdvisorJournalReport? = null,
    val advisorJournalLoading: Boolean = false,
    val advisorEvents: List<AdvisorEvent> = emptyList(),
    val thresholdSettingsSheetOpen: Boolean = false,
    val thresholdMonitorState: ThresholdAlertMonitorState = ThresholdAlertMonitorState(),
    val recentThresholdAlerts: List<ThresholdAlertEvent> = emptyList(),
    val finnhubConfigured: Boolean = false,
    val finnhubNewsState: FinnhubNewsFeedState = FinnhubNewsFeedState.Idle,
    val isRefreshingFinnhubNews: Boolean = false,
    val newsFilter: NewsArticleFilter = NewsArticleFilter.ALL,
    val newsSearchQuery: String = "",
    val aiHubSheetOpen: Boolean = false,
    val radarModels: List<RadarModel> = ModelRadarEngine.builtInGeminiCatalog(),
    val radarSyncing: Boolean = false,
    val radarLastSyncedMs: Long = 0L,
    val radarErrors: Map<AiProviderId, String> = emptyMap(),
    val radarTierFilter: ModelTierFilter = ModelTierFilter.ALL,
    val radarProviderFilter: AiProviderId? = null,
    val radarSearchQuery: String = "",
    val probingModelKey: String? = null,
    val probeResultsByKey: Map<String, ModelProbeResult> = emptyMap(),
    val quantScannerState: QuantScannerUiState = QuantScannerUiState(),
    val marketMode: MarketMode = MarketMode.STOCKS,
    val stockBotState: BotState = BotState(),
    val cryptoBotState: BotState = BotState(),
    val cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    val cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    val cryptoPanicPosts: List<CryptoPanicPost> = emptyList(),
    val cryptoFundingSampleCount: Int = 0,
    val cryptoTraderSnapshot: CryptoTraderSnapshot = CryptoTraderSnapshot(),
    val cryptoBacktestSummary: CryptoBacktestSummary? = null,
    val cryptoBacktestRunning: Boolean = false
)

class MarketScanViewModel(application: Application) : AndroidViewModel(application) {

    private val store = EncryptedPrefsStore(application.applicationContext)
    private val repository = MarketRepository()
    private val advisorEngine = AdvisorEngine()
    private val modelRadarEngine = ModelRadarEngine()
    private val notifier = PriceAlertNotifier(application.applicationContext)
    private val tradeNotifier = TradeExecutionNotifier(application.applicationContext)
    private val newsNotifier = NewsCatalystNotifier(application.applicationContext)
    private val vixNotifier = VixAlertNotifier(application.applicationContext)
    private val thresholdNotifier = ThresholdAlertNotifier(application.applicationContext)
    private var notifiedNewsKeys: Set<String> = emptySet()
    private var latestBars: Map<String, BarSeries> = emptyMap()
    private var cryptoFundingHistory: List<CryptoFundingHistoryPoint> = emptyList()
    private var rescanJob: Job? = null

    private val _uiState = MutableStateFlow(
        MarketScanUiState(
            advisorEnabled = advisorEngine.enabled,
            advisorVetoReady = advisorEngine.vetoReady,
            advisorModel = advisorEngine.effectiveModel,
            advisorMaxCallsPerHour = advisorEngine.budget.perHour,
            finnhubConfigured = advisorEngine.config.cleanFinnhubKey.isNotEmpty()
        )
    )
    val uiState: StateFlow<MarketScanUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        notifier.ensureChannel()
        tradeNotifier.ensureChannel()
        newsNotifier.ensureChannel()
        vixNotifier.ensureChannel()
        thresholdNotifier.ensureChannel()
        viewModelScope.launch {
            val cfg = withContext(Dispatchers.IO) { store.loadConfig() }
            advisorEngine.setRuntimeFinnhubKey(cfg.finnhubKey)
            applyAiRoutingToAdvisor(cfg)
            val stockBot = withContext(Dispatchers.IO) { store.loadBotState(MarketMode.STOCKS) }
            val cryptoBot = withContext(Dispatchers.IO) { store.loadBotState(MarketMode.CRYPTO) }
            cryptoFundingHistory = withContext(Dispatchers.IO) { store.loadCryptoFundingHistory() }
            val activeBot = if (cfg.marketMode == MarketMode.CRYPTO) cryptoBot else stockBot
            val savedAlerts = withContext(Dispatchers.IO) { store.loadAlerts() }
            val savedSignalAlerts = withContext(Dispatchers.IO) { store.loadSignalAlerts() }
            val disclaimerAccepted = withContext(Dispatchers.IO) { store.loadDisclaimerAccepted() }
            val savedRegimeHistory = withContext(Dispatchers.IO) { store.loadScanRegimeHistory() }
            notifiedNewsKeys = withContext(Dispatchers.IO) { store.loadNotifiedNewsKeys() }
            val maxFundingSamples = cryptoFundingHistory.groupingBy { it.symbol }.eachCount().values.maxOrNull() ?: 0
            if (savedAlerts.any { it.active } || savedSignalAlerts.any { it.enabled }) {
                CatalystMonitorScheduler.ensureScheduledForAlerts(getApplication())
            }
            _uiState.update {
                it.copy(
                    config = cfg,
                    marketMode = cfg.marketMode,
                    botState = activeBot,
                    stockBotState = stockBot,
                    cryptoBotState = cryptoBot,
                    cryptoFundingSampleCount = maxFundingSamples,
                    alerts = savedAlerts,
                    signalAlerts = savedSignalAlerts,
                    showSimulationDisclaimer = !disclaimerAccepted,
                    scanRegimeHistory = savedRegimeHistory,
                    finnhubConfigured = advisorEngine.effectiveFinnhubKey.isNotEmpty(),
                    advisorEnabled = advisorEngine.enabled,
                    advisorVetoReady = advisorEngine.vetoReady,
                    advisorModel = advisorEngine.effectiveModel
                )
            }
            rescan(quiet = false)
            startBotLoop()
        }
    }

    private fun MarketScanUiState.withUpdatedBot(nextBot: BotState, mode: MarketMode): MarketScanUiState =
        when (mode) {
            MarketMode.STOCKS -> copy(
                botState = if (marketMode == MarketMode.STOCKS) nextBot else botState,
                stockBotState = nextBot
            )
            MarketMode.CRYPTO -> copy(
                botState = if (marketMode == MarketMode.CRYPTO) nextBot else botState,
                cryptoBotState = nextBot
            )
        }

    fun switchMarketMode(targetMode: MarketMode) {
        val current = _uiState.value
        if (current.marketMode == targetMode) return
        rescanJob?.cancel()
        val updatedCfg = current.config.copy(marketMode = targetMode)
        val swappedBot = if (targetMode == MarketMode.CRYPTO) current.cryptoBotState else current.stockBotState
        _uiState.update { state ->
            state.copy(
                config = updatedCfg,
                marketMode = targetMode,
                botState = swappedBot,
                filter = SignalFilter.ALL,
                selectedSector = null,
                searchQuery = "",
                selectedSignal = null,
                isScanning = false,
                statusText = if (targetMode == MarketMode.CRYPTO) {
                    "connecting Kraken 24/7 spot & futures…"
                } else {
                    "switching to stock market…"
                }
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            val eqStr = "$${"%.2f".format(Locale.US, PaperBotEngine.liveEquity(swappedBot))}"
            _messages.tryEmit(
                if (targetMode == MarketMode.CRYPTO) {
                    "Switched to Crypto 24/7 · Portfolio $eqStr · Kraken Spot & Futures"
                } else {
                    "Switched to Stock Market · Portfolio $eqStr"
                }
            )
            rescan(quiet = false, force = true)
        }
    }

    fun toggleMarketMode() {
        switchMarketMode(_uiState.value.marketMode.opposite)
    }

    private fun startBotLoop() {
        viewModelScope.launch {
            var tickCounter = 0
            while (isActive) {
                delay(2500L)
                tickCounter++
                val currentState = _uiState.value
                val rows = currentState.rows
                if (rows.isNotEmpty()) {
                    val activeAlertSyms = currentState.alerts.filter { it.active }.map { it.symbol }
                    val heldSyms = currentState.botState.positions.map { it.symbol }
                    val scannerSyms = if (currentState.quantScannerState.liveStreamEnabled) {
                        currentState.quantScannerState.activeSymbols.ifEmpty {
                            rows.take(8).map { it.symbol }
                        }
                    } else {
                        emptyList()
                    }
                    val activeMode = currentState.marketMode
                    val watchSyms = (heldSyms + activeAlertSyms + scannerSyms +
                        if (activeMode == MarketMode.CRYPTO && currentState.botState.running) rows.take(6).map { it.symbol } else emptyList()
                    ).distinct()
                    val shouldStepQuotes = currentState.quantScannerState.liveStreamEnabled ||
                        heldSyms.isNotEmpty() || activeAlertSyms.isNotEmpty() ||
                        (activeMode == MarketMode.CRYPTO && currentState.botState.running)

                    val updatedRows = when {
                        currentState.source == "demo" && shouldStepQuotes -> repository.stepDemoQuotes(rows)
                        currentState.source == "live" && watchSyms.isNotEmpty() -> {
                            val livePrices = withContext(Dispatchers.IO) {
                                if (activeMode == MarketMode.CRYPTO) {
                                    repository.fetchLatestCryptoPrices(watchSyms)
                                } else {
                                    repository.fetchLatestPrices(watchSyms, currentState.config)
                                }
                            }
                            if (livePrices.isNotEmpty()) {
                                rows.map { row ->
                                    val livePrice = livePrices[row.symbol]
                                    if (livePrice != null) {
                                        val nextSeries = if (row.series.isNotEmpty()) {
                                            row.series.drop(1) + livePrice
                                        } else {
                                            listOf(livePrice)
                                        }
                                        row.copy(price = livePrice, series = nextSeries)
                                    } else {
                                        row
                                    }
                                }
                            } else {
                                rows
                            }
                        }
                        else -> rows
                    }

                    // Step VIX in demo mode every loop tick; poll live CBOE/Yahoo every ~15s (6 ticks)
                    val shouldPollVix = activeMode == MarketMode.STOCKS && (currentState.config.demo || (tickCounter % 6 == 0))
                    val nextVix = if (shouldPollVix) {
                        withContext(Dispatchers.IO) {
                            repository.fetchVixSnapshot(
                                cfg = currentState.config,
                                regime = currentState.regime,
                                previous = currentState.vixSnapshot
                            )
                        }
                    } else {
                        currentState.vixSnapshot
                    }
                    val vixEval = VixAlertEngine.evaluate(
                        snapshot = nextVix,
                        thresholdPct = currentState.config.vixSpikeThresholdPct,
                        enabled = currentState.config.vixAlertEnabled && activeMode == MarketMode.STOCKS,
                        monitorState = currentState.vixMonitorState
                    )

                    val isCryptoLive = activeMode == MarketMode.CRYPTO
                    val snap2 = currentState.cryptoTraderSnapshot
                    val nextBot = PaperBotEngine.tick(
                        state = currentState.botState,
                        rows = updatedRows,
                        advisorVerdicts = currentState.advisorVerdicts,
                        advisorEnabled = advisorEngine.enabled,
                        regimeLabel = currentState.regime.label.name,
                        cryptoGuards = if (isCryptoLive) currentState.cryptoGuardsBySymbol else emptyMap(),
                        pegBreached = isCryptoLive && snap2.pegStatus.anyBreach,
                        edgeDecayed = isCryptoLive && snap2.decayReport.verdict == DecayVerdict.DECAYED,
                        heatBreached = isCryptoLive && snap2.portfolioHeat.breach,
                        barsBySymbol = if (isCryptoLive) latestBars else emptyMap()
                    )
                    val botChanged = nextBot != currentState.botState

                    val priceMap = updatedRows.associate { it.symbol to it.price }
                    val eval = PriceAlertEngine.evaluate(currentState.alerts, priceMap)
                    val sigEval = PriceAlertEngine.evaluateSignalChanges(currentState.signalAlerts, updatedRows)
                    val nowMs = System.currentTimeMillis()

                    _uiState.update { state ->
                        if (state.marketMode != activeMode) return@update state
                        val updatedSelected = state.selectedSignal?.let { sel ->
                            updatedRows.find { it.symbol == sel.symbol }
                        }
                        val nextScanner = if (state.quantScannerState.liveStreamEnabled) {
                            state.quantScannerState.copy(
                                lastUpdatedMs = nowMs,
                                tickCount = state.quantScannerState.tickCount + 1
                            )
                        } else {
                            state.quantScannerState
                        }
                        state.withUpdatedBot(nextBot, activeMode).copy(
                            rows = updatedRows,
                            alerts = eval.updatedAlerts,
                            signalAlerts = sigEval.updatedAlerts,
                            selectedSignal = updatedSelected,
                            vixSnapshot = nextVix,
                            vixMonitorState = vixEval.monitorState,
                            quantScannerState = nextScanner
                        )
                    }
                    if (botChanged) {
                        withContext(Dispatchers.IO) { store.saveBotState(nextBot, activeMode) }
                        handleBotTickTransitions(currentState.botState, nextBot)
                    }
                    if (eval.newlyTriggered.isNotEmpty()) {
                        withContext(Dispatchers.IO) { store.saveAlerts(eval.updatedAlerts) }
                        eval.newlyTriggered.forEach { fired ->
                            notifier.notifyTriggered(fired)
                            val dir = if (fired.condition == AlertCondition.ABOVE) "crossed ≥" else "dropped ≤"
                            _messages.tryEmit(
                                "Alert: ${fired.symbol} $dir $${"%.2f".format(Locale.US, fired.targetPrice)} (now $${"%.2f".format(Locale.US, fired.triggeredPrice ?: fired.targetPrice)})"
                            )
                        }
                    }
                    if (sigEval.updatedAlerts != currentState.signalAlerts) {
                        withContext(Dispatchers.IO) { store.saveSignalAlerts(sigEval.updatedAlerts) }
                    }
                    if (sigEval.triggeredEvents.isNotEmpty()) {
                        sigEval.triggeredEvents.forEach { ev ->
                            notifier.notifySignalChange(ev)
                            _messages.tryEmit(
                                "AI Signal Alert: ${ev.symbol} ${ev.previousAction.name} → ${ev.newAction.name} @ $${"%.2f".format(Locale.US, ev.price)}"
                            )
                        }
                    }
                    vixEval.triggeredEvent?.let { spike ->
                        vixNotifier.notifySpike(spike)
                        _messages.tryEmit(
                            "VIX Spike Alert: ${"%.2f".format(Locale.US, spike.vixLevel)} (+${"%.1f".format(Locale.US, spike.spikePct)}% intraday)"
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectFilter(filter: SignalFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun selectSector(sector: MarketSector?) {
        _uiState.update { it.copy(selectedSector = sector) }
    }

    fun selectSort(sort: SignalSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query.uppercase()) }
    }

    fun selectNewsFilter(filter: NewsArticleFilter) {
        _uiState.update { it.copy(newsFilter = filter) }
    }

    fun updateNewsSearchQuery(query: String) {
        _uiState.update { it.copy(newsSearchQuery = query) }
    }

    fun refreshFinnhubNews(isManualRefresh: Boolean = false) {
        if (_uiState.value.isRefreshingFinnhubNews) return
        viewModelScope.launch {
            val startedAtMs = System.currentTimeMillis()
            val hadArticles = _uiState.value.finnhubNewsState is FinnhubNewsFeedState.Success
            _uiState.update { state ->
                state.copy(
                    isRefreshingFinnhubNews = true,
                    finnhubNewsState = if (hadArticles) state.finnhubNewsState else FinnhubNewsFeedState.Loading
                )
            }
            try {
                val snap = _uiState.value
                val trackedSyms = (snap.config.activeWatchlist + snap.botState.positions.map { it.symbol } + snap.rows.take(5).map { it.symbol })
                    .distinct()
                val outcome = withContext(Dispatchers.IO) {
                    repository.fetchAnnotatedFinnhubNews(
                        trackedSymbols = trackedSyms,
                        cfg = snap.config,
                        advisorEngine = advisorEngine
                    )
                }
                val usedCalls = advisorEngine.budget.callsUsedLastHour()
                val nextFeedState = when {
                    outcome.errorMessage != null && outcome.articles.isEmpty() -> {
                        FinnhubNewsFeedState.Error(outcome.errorMessage)
                    }
                    else -> {
                        FinnhubNewsFeedState.Success(
                            articles = outcome.articles,
                            fetchedAtMs = System.currentTimeMillis(),
                            finnhubConnected = outcome.finnhubConnected,
                            aiScoringLabel = outcome.aiScoringLabel
                        )
                    }
                }
                if (isManualRefresh) {
                    val elapsed = System.currentTimeMillis() - startedAtMs
                    if (elapsed < 400L) delay(400L - elapsed)
                }
                _uiState.update {
                    it.copy(
                        finnhubNewsState = nextFeedState,
                        advisorCallsUsed = usedCalls
                    )
                }
                if (isManualRefresh) {
                    val feedName = if (snap.marketMode == MarketMode.CRYPTO) "CryptoPanic" else "Finnhub"
                    val msg = when (nextFeedState) {
                        is FinnhubNewsFeedState.Success -> if (nextFeedState.finnhubConnected) {
                            "$feedName feed updated · ${nextFeedState.articles.size} articles (${nextFeedState.aiScoringLabel})"
                        } else {
                            "Add FINNHUB_KEY in Secrets to stream live Finnhub news"
                        }
                        is FinnhubNewsFeedState.Error -> "$feedName refresh failed: ${nextFeedState.message}"
                        else -> null
                    }
                    msg?.let { _messages.tryEmit(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(finnhubNewsState = FinnhubNewsFeedState.Error(e.message ?: "News fetch failed"))
                }
            } finally {
                _uiState.update { it.copy(isRefreshingFinnhubNews = false) }
            }
        }
    }

    fun addTickerToWatchlist(symbolInput: String) {
        val cfg = _uiState.value.config
        val isCrypto = cfg.marketMode == MarketMode.CRYPTO
        val tokens = symbolInput.split(",")
            .map { raw ->
                if (isCrypto) {
                    CryptoFeedsEngine.normalizeCryptoSymbol(raw)
                } else {
                    raw.trim().uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '.' }
                }
            }
            .filter { it.isNotEmpty() }
            .distinct()
        if (tokens.isEmpty()) return

        val activeList = cfg.activeWatchlist
        val added = tokens.filterNot { activeList.contains(it) }
        if (added.isEmpty()) {
            _messages.tryEmit("${tokens.joinToString(", ")} already in watchlist")
            return
        }
        val updatedCfg = if (isCrypto) {
            cfg.copy(cryptoWatchlist = cfg.cryptoWatchlist + added)
        } else {
            cfg.copy(watchlist = cfg.watchlist + added)
        }
        val existingSyms = _uiState.value.rows.map { it.symbol }.toSet()
        val needsScan = added.any { !existingSyms.contains(it) }

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            _uiState.update { it.copy(config = updatedCfg, searchQuery = "") }
            _messages.tryEmit("Added ${added.joinToString(", ")} to ${if (isCrypto) "crypto" else "stock"} watchlist")
            if (needsScan) {
                rescan(quiet = false)
            } else {
                evaluateWatchlistNewsCatalysts(_uiState.value.rows, updatedCfg)
            }
        }
    }

    fun removeTickerFromWatchlist(symbol: String) {
        val cfg = _uiState.value.config
        val isCrypto = cfg.marketMode == MarketMode.CRYPTO
        val clean = if (isCrypto) CryptoFeedsEngine.normalizeCryptoSymbol(symbol) else symbol.trim().uppercase()
        if (clean.isEmpty()) return
        if (!cfg.activeWatchlist.contains(clean)) return
        val updatedCfg = if (isCrypto) {
            cfg.copy(cryptoWatchlist = cfg.cryptoWatchlist.filterNot { it == clean })
        } else {
            cfg.copy(watchlist = cfg.watchlist.filterNot { it == clean })
        }
        val baseUniverse = if (isCrypto) CryptoFeedsEngine.CRYPTO_UNIVERSE else QuantEngine.UNIVERSE
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            _uiState.update { state ->
                val updatedRows = if (baseUniverse.contains(clean)) {
                    state.rows
                } else {
                    state.rows.filterNot { it.symbol == clean }
                }
                state.copy(config = updatedCfg, rows = updatedRows)
            }
            _messages.tryEmit("Removed $clean from watchlist")
        }
    }

    fun toggleWatchlistTicker(symbol: String) {
        val cfg = _uiState.value.config
        val isCrypto = cfg.marketMode == MarketMode.CRYPTO
        val clean = if (isCrypto) {
            CryptoFeedsEngine.normalizeCryptoSymbol(symbol)
        } else {
            symbol.trim().uppercase().filter { it.isLetterOrDigit() || it == '.' }
        }
        if (clean.isEmpty()) return
        if (cfg.activeWatchlist.contains(clean)) {
            removeTickerFromWatchlist(clean)
        } else {
            addTickerToWatchlist(clean)
        }
    }

    fun openSignalDetail(symbol: String) {
        val row = _uiState.value.rows.find { it.symbol == symbol }
        _uiState.update { it.copy(selectedSignal = row) }
        if (row != null) {
            assessThreat(row.symbol)
        }
    }

    fun closeSignalDetail() {
        _uiState.update { it.copy(selectedSignal = null) }
    }

    fun openAlertsSheet(symbol: String? = null) {
        val clean = symbol?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
        _uiState.update { it.copy(alertsSheetOpen = true, alertSheetSymbol = clean) }
    }

    fun closeAlertsSheet() {
        _uiState.update { it.copy(alertsSheetOpen = false, alertSheetSymbol = null) }
    }

    fun openSimulationDisclaimer() {
        _uiState.update { it.copy(showSimulationDisclaimer = true) }
    }

    fun acceptSimulationDisclaimer() {
        _uiState.update { it.copy(showSimulationDisclaimer = false) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveDisclaimerAccepted(true) }
        }
    }

    fun openThresholdSettingsSheet() {
        _uiState.update { it.copy(thresholdSettingsSheetOpen = true) }
    }

    fun closeThresholdSettingsSheet() {
        _uiState.update { it.copy(thresholdSettingsSheetOpen = false) }
    }

    fun updateThresholdAlertSettings(settings: ThresholdAlertSettings) {
        val sanitized = settings.copy(
            aiNewsScoreDeltaThreshold = settings.aiNewsScoreDeltaThreshold.coerceIn(
                ThresholdAlertEngine.MIN_DELTA_THRESHOLD,
                ThresholdAlertEngine.MAX_DELTA_THRESHOLD
            ),
            aiNewsMinScoreAbsThreshold = settings.aiNewsMinScoreAbsThreshold.coerceIn(
                ThresholdAlertEngine.MIN_EXTREME_THRESHOLD,
                ThresholdAlertEngine.MAX_EXTREME_THRESHOLD
            )
        )
        val currentCfg = _uiState.value.config
        if (currentCfg.thresholdAlerts == sanitized) return
        val updatedCfg = currentCfg.copy(thresholdAlerts = sanitized)
        _uiState.update { it.copy(config = updatedCfg) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
        }
    }

    fun clearRecentThresholdAlerts() {
        _uiState.update { it.copy(recentThresholdAlerts = emptyList()) }
    }

    fun triggerTestThresholdAlert() {
        val state = _uiState.value
        val testEvent = ThresholdAlertEngine.buildTestAlert(
            settings = state.config.thresholdAlerts,
            regime = state.regime
        )
        thresholdNotifier.notifyEvent(testEvent)
        _uiState.update {
            it.copy(recentThresholdAlerts = (listOf(testEvent) + it.recentThresholdAlerts).take(10))
        }
        _messages.tryEmit(testEvent.title)
    }

    private fun evaluateThresholdAlerts(
        regime: MarketRegime,
        rows: List<SignalRow>,
        advisorVerdicts: Map<String, AdvisorVetoVerdict>,
        emitSnackbar: Boolean = true
    ) {
        val state = _uiState.value
        val eval = ThresholdAlertEngine.evaluate(
            settings = state.config.thresholdAlerts,
            regime = regime,
            rows = rows,
            watchlist = state.config.watchlist,
            advisorVerdicts = advisorVerdicts,
            monitorState = state.thresholdMonitorState
        )
        _uiState.update { current ->
            current.copy(
                thresholdMonitorState = eval.nextState,
                recentThresholdAlerts = if (eval.events.isNotEmpty()) {
                    (eval.events + current.recentThresholdAlerts).take(10)
                } else {
                    current.recentThresholdAlerts
                }
            )
        }
        if (eval.events.isNotEmpty()) {
            eval.events.forEach { ev -> thresholdNotifier.notifyEvent(ev) }
            if (emitSnackbar) {
                _messages.tryEmit(eval.events.first().title)
            }
        }
    }

    fun addPriceAlert(symbol: String, targetPrice: Double, condition: AlertCondition) {
        val cleanSym = symbol.trim().uppercase().filter { it.isLetterOrDigit() || it == '.' || it == '/' }
        if (cleanSym.isEmpty() || !targetPrice.isFinite() || targetPrice <= 0.0) {
            _messages.tryEmit("Enter a valid symbol and positive threshold price")
            return
        }
        viewModelScope.launch {
            val alert = PriceAlertEngine.createAlert(cleanSym, targetPrice, condition)
            val addedList = listOf(alert) + _uiState.value.alerts
            val priceMap = _uiState.value.rows.associate { it.symbol to it.price }
            val eval = PriceAlertEngine.evaluate(addedList, priceMap)
            _uiState.update { it.copy(alerts = eval.updatedAlerts) }
            withContext(Dispatchers.IO) { store.saveAlerts(eval.updatedAlerts) }
            if (eval.updatedAlerts.any { it.active }) {
                CatalystMonitorScheduler.ensureScheduledForAlerts(getApplication())
            }
            if (eval.newlyTriggered.isNotEmpty()) {
                eval.newlyTriggered.forEach { fired ->
                    notifier.notifyTriggered(fired)
                }
                _messages.tryEmit(
                    "Alert triggered immediately for $cleanSym @ $${"%.2f".format(Locale.US, targetPrice)}"
                )
            } else {
                _messages.tryEmit(
                    "Alert set: $cleanSym ${condition.symbolText} $${"%.2f".format(Locale.US, alert.targetPrice)}"
                )
            }
        }
    }

    fun removePriceAlert(id: String) {
        viewModelScope.launch {
            val updated = PriceAlertEngine.remove(_uiState.value.alerts, id)
            _uiState.update { it.copy(alerts = updated) }
            withContext(Dispatchers.IO) { store.saveAlerts(updated) }
        }
    }

    fun rearmPriceAlert(id: String) {
        viewModelScope.launch {
            val rearmed = PriceAlertEngine.rearm(_uiState.value.alerts, id)
            val priceMap = _uiState.value.rows.associate { it.symbol to it.price }
            val eval = PriceAlertEngine.evaluate(rearmed, priceMap)
            _uiState.update { it.copy(alerts = eval.updatedAlerts) }
            withContext(Dispatchers.IO) { store.saveAlerts(eval.updatedAlerts) }
            if (eval.updatedAlerts.any { it.active }) {
                CatalystMonitorScheduler.ensureScheduledForAlerts(getApplication())
            }
            if (eval.newlyTriggered.isNotEmpty()) {
                eval.newlyTriggered.forEach { notifier.notifyTriggered(it) }
            } else {
                _messages.tryEmit("Price alert re-armed")
            }
        }
    }

    fun togglePriceAlert(id: String) {
        viewModelScope.launch {
            val toggled = PriceAlertEngine.toggle(_uiState.value.alerts, id)
            val priceMap = _uiState.value.rows.associate { it.symbol to it.price }
            val eval = PriceAlertEngine.evaluate(toggled, priceMap)
            _uiState.update { it.copy(alerts = eval.updatedAlerts) }
            withContext(Dispatchers.IO) { store.saveAlerts(eval.updatedAlerts) }
            if (eval.updatedAlerts.any { it.active }) {
                CatalystMonitorScheduler.ensureScheduledForAlerts(getApplication())
            }
            if (eval.newlyTriggered.isNotEmpty()) {
                eval.newlyTriggered.forEach { notifier.notifyTriggered(it) }
            }
        }
    }

    fun toggleSignalChangeAlert(
        symbol: String,
        enabled: Boolean,
        mode: SignalAlertMode? = null
    ) {
        val cleanSym = symbol.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' || it == '/' }
        if (cleanSym.isEmpty()) return
        viewModelScope.launch {
            val currentRow = _uiState.value.rows.firstOrNull { it.symbol.equals(cleanSym, ignoreCase = true) }
            val updated = PriceAlertEngine.upsertSignalAlert(
                alerts = _uiState.value.signalAlerts,
                symbol = cleanSym,
                enabled = enabled,
                mode = mode,
                currentAction = currentRow?.action
            )
            _uiState.update { it.copy(signalAlerts = updated) }
            withContext(Dispatchers.IO) { store.saveSignalAlerts(updated) }
            if (enabled) {
                CatalystMonitorScheduler.ensureScheduledForAlerts(getApplication())
            }
            val activeMode = updated.firstOrNull { it.symbol == cleanSym }?.mode ?: SignalAlertMode.ANY_CHANGE
            _messages.tryEmit(
                if (enabled) "AI signal alert ON for $cleanSym (${activeMode.shortLabel})"
                else "AI signal alert paused for $cleanSym"
            )
        }
    }

    fun updateSignalAlertMode(symbol: String, mode: SignalAlertMode) {
        val cleanSym = symbol.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' || it == '/' }
        if (cleanSym.isEmpty()) return
        viewModelScope.launch {
            val existing = _uiState.value.signalAlerts.firstOrNull { it.symbol.equals(cleanSym, ignoreCase = true) }
            val currentRow = _uiState.value.rows.firstOrNull { it.symbol.equals(cleanSym, ignoreCase = true) }
            val updated = PriceAlertEngine.upsertSignalAlert(
                alerts = _uiState.value.signalAlerts,
                symbol = cleanSym,
                enabled = existing?.enabled ?: true,
                mode = mode,
                currentAction = currentRow?.action
            )
            _uiState.update { it.copy(signalAlerts = updated) }
            withContext(Dispatchers.IO) { store.saveSignalAlerts(updated) }
        }
    }

    fun removeSignalChangeAlert(id: String) {
        viewModelScope.launch {
            val updated = PriceAlertEngine.removeSignalAlert(_uiState.value.signalAlerts, id)
            _uiState.update { it.copy(signalAlerts = updated) }
            withContext(Dispatchers.IO) { store.saveSignalAlerts(updated) }
        }
    }

    fun toggleGlobalAiNewsAlerts(enabled: Boolean) {
        val current = _uiState.value.config.thresholdAlerts
        if (current.aiNewsAlertsEnabled == enabled) return
        updateThresholdAlertSettings(current.copy(aiNewsAlertsEnabled = enabled))
    }

    fun clearTriggeredAlerts() {
        viewModelScope.launch {
            val updated = PriceAlertEngine.clearTriggered(_uiState.value.alerts)
            _uiState.update { it.copy(alerts = updated) }
            withContext(Dispatchers.IO) { store.saveAlerts(updated) }
        }
    }

    fun openManualPosition(signal: SignalRow) {
        viewModelScope.launch {
            val current = _uiState.value.botState
            if (current.positions.any { it.symbol == signal.symbol }) {
                _messages.tryEmit("Already holding ${signal.symbol}")
                return@launch
            }
            if (current.halted) {
                _messages.tryEmit("Daily loss limit reached — halted until tomorrow")
                return@launch
            }
            val stateSnap = _uiState.value
            val verdict = stateSnap.advisorVerdicts[signal.symbol]
            val llmTag = when {
                verdict?.isVeto == true -> "veto"
                verdict != null -> verdict.llmTag
                advisorEngine.enabled -> "clear"
                else -> "off"
            }
            val mode = stateSnap.marketMode
            val opened = PaperBotEngine.openManual(
                state = current,
                signal = signal,
                llmVerdict = llmTag,
                regimeLabel = stateSnap.regime.label.name
            )
            val newPos = opened.positions.find { it.symbol == signal.symbol }
            if (newPos != null) {
                _uiState.update { it.withUpdatedBot(opened, mode).copy(selectedSignal = null, activeTab = MainTab.PORTFOLIO) }
                withContext(Dispatchers.IO) { store.saveBotState(opened, mode) }
                _messages.tryEmit("Paper bought ${newPos.qty} ${newPos.symbol} @ $${"%.2f".format(newPos.entry)}")
                assessThreat(newPos.symbol)
            } else {
                _messages.tryEmit("Insufficient paper cash for ${signal.symbol}")
            }
        }
    }

    fun openSizedPosition(signal: SignalRow, qty: Double) {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val current = stateSnap.botState
            val mode = stateSnap.marketMode
            if (current.positions.any { it.symbol == signal.symbol }) {
                _messages.tryEmit("Already holding ${signal.symbol}")
                return@launch
            }
            if (current.halted) {
                _messages.tryEmit("Daily loss limit reached — halted until tomorrow")
                return@launch
            }
            val minFloor = if (signal.symbol.contains("/")) 0.00001 else 0.0001
            val safeQty = qty.takeIf { it.isFinite() && it > minFloor } ?: run {
                _messages.tryEmit("Recommended position size is zero for ${signal.symbol}")
                return@launch
            }
            val verdict = stateSnap.advisorVerdicts[signal.symbol]
            val llmTag = when {
                verdict?.isVeto == true -> "veto"
                verdict != null -> verdict.llmTag
                advisorEngine.enabled -> "clear"
                else -> "off"
            }
            val opened = PaperBotEngine.openManual(
                state = current,
                signal = signal,
                customQty = safeQty,
                llmVerdict = llmTag,
                regimeLabel = stateSnap.regime.label.name
            )
            val newPos = opened.positions.find { it.symbol == signal.symbol }
            if (newPos != null) {
                _uiState.update { it.withUpdatedBot(opened, mode).copy(activeTab = MainTab.PORTFOLIO) }
                withContext(Dispatchers.IO) { store.saveBotState(opened, mode) }
                _messages.tryEmit("Simulated sized buy: ${newPos.qty} ${newPos.symbol} @ $${"%.2f".format(Locale.US, newPos.entry)}")
                assessThreat(newPos.symbol)
            } else {
                _messages.tryEmit("Insufficient paper cash for ${signal.symbol}")
            }
        }
    }

    fun updateAccountAndRisk(accountSize: Double, riskPct: Double) {
        val safeAccount = accountSize.takeIf { it.isFinite() && it > 0.0 } ?: return
        val safeRisk = riskPct.takeIf { it.isFinite() }?.coerceIn(0.1, 25.0) ?: return
        val currentCfg = _uiState.value.config
        val updatedCfg = currentCfg.copy(account = safeAccount, riskPct = safeRisk)
        _uiState.update { it.copy(config = updatedCfg) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            _messages.tryEmit(
                "Saved sizing defaults: $${"%.2f".format(Locale.US, safeAccount)} · ${"%.2f".format(Locale.US, safeRisk)}% risk"
            )
        }
    }

    fun closePositionNow(symbol: String) {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val current = stateSnap.botState
            val mode = stateSnap.marketMode
            val pos = current.positions.find { it.symbol == symbol } ?: return@launch
            val closed = PaperBotEngine.closeManual(current, symbol)
            _uiState.update { it.withUpdatedBot(closed, mode) }
            withContext(Dispatchers.IO) { store.saveBotState(closed, mode) }
            _messages.tryEmit("Closed ${pos.symbol} @ $${"%.2f".format(pos.last)}")
        }
    }

    fun trimPosition(symbol: String, fraction: Double = 0.5) {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val current = stateSnap.botState
            val mode = stateSnap.marketMode
            val pos = current.positions.find { it.symbol == symbol } ?: return@launch
            val trimmed = PaperBotEngine.trimManual(current, symbol, fraction = fraction, reason = "threat trim")
            _uiState.update { it.withUpdatedBot(trimmed, mode) }
            withContext(Dispatchers.IO) { store.saveBotState(trimmed, mode) }
            val pct = (fraction.coerceIn(0.05, 1.0) * 100.0).toInt()
            _messages.tryEmit("Trimmed $pct% of ${pos.symbol} @ $${"%.2f".format(Locale.US, pos.last)}")
            assessThreat(symbol)
        }
    }

    fun applySuggestedStop(symbol: String, stopPrice: Double) {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val current = stateSnap.botState
            val mode = stateSnap.marketMode
            val pos = current.positions.find { it.symbol == symbol } ?: return@launch
            val updated = PaperBotEngine.updatePositionStop(current, symbol, stopPrice)
            if (updated == current) {
                _messages.tryEmit("Stop for ${pos.symbol} already at or above $${"%.2f".format(Locale.US, stopPrice)}")
                return@launch
            }
            _uiState.update { it.withUpdatedBot(updated, mode) }
            withContext(Dispatchers.IO) { store.saveBotState(updated, mode) }
            _messages.tryEmit("Updated ${pos.symbol} stop to $${"%.2f".format(Locale.US, stopPrice)}")
            assessThreat(symbol)
        }
    }

    fun assessThreat(symbol: String) {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (cleanSym.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(assessingThreatSymbols = it.assessingThreatSymbols + cleanSym) }
            try {
                val state = _uiState.value
                val bars = latestBars[cleanSym] ?: withContext(Dispatchers.Default) {
                    QuantEngine.genSeries(cleanSym)
                }
                val targetSector = state.rows.find { it.symbol == cleanSym }?.sector
                    ?: QuantEngine.classifySector(cleanSym)
                val peerSyms = state.rows
                    .filter { it.symbol != cleanSym && it.sector == targetSector }
                    .take(8)
                    .map { it.symbol }
                val peerBars = peerSyms.mapNotNull { latestBars[it] }
                val benchBars = latestBars["SPY"] ?: latestBars["BTC/USD"]
                val heldPos = state.botState.positions.find { it.symbol == cleanSym }
                val posCtx = heldPos?.let {
                    com.example.domain.model.ThreatPositionContext(
                        qty = it.qty,
                        entry = it.entry,
                        initialStop = it.stop,
                        stop = it.stop,
                        upnl = it.upnl
                    )
                }
                val livePrice = state.rows.find { it.symbol == cleanSym }?.price ?: heldPos?.last

                val assessment = withContext(Dispatchers.IO) {
                    repository.assessThreat(
                        symbol = cleanSym,
                        bars = bars,
                        peerBars = peerBars,
                        benchBars = benchBars,
                        headlines = state.news,
                        position = posCtx,
                        price = livePrice,
                        cfg = state.config
                    )
                }
                if (assessment != null) {
                    _uiState.update { s ->
                        s.copy(threatsBySymbol = s.threatsBySymbol + (cleanSym to assessment))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(assessingThreatSymbols = it.assessingThreatSymbols - cleanSym) }
            }
        }
    }

    fun checkAdvisorVeto(
        symbol: String,
        quiet: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (cleanSym.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(advisorCheckingSymbol = cleanSym) }
            try {
                val verdict = withContext(Dispatchers.IO) {
                    advisorEngine.newsVeto(cleanSym, forceRefresh = forceRefresh)
                }
                val used = advisorEngine.budget.callsUsedLastHour()
                val updatedVerdicts = _uiState.value.advisorVerdicts + (cleanSym to verdict)
                _uiState.update { state ->
                    state.copy(
                        advisorVerdicts = updatedVerdicts,
                        advisorCallsUsed = used,
                        advisorEvents = advisorEngine.recentEvents()
                    )
                }
                val snap = _uiState.value
                evaluateThresholdAlerts(
                    regime = snap.regime,
                    rows = snap.rows,
                    advisorVerdicts = updatedVerdicts,
                    emitSnackbar = quiet
                )
                if (!quiet) {
                    val msg = when {
                        verdict.isVeto -> "Advisor VETO $cleanSym (${verdict.category}, ${(verdict.weight * 100).toInt()}%)"
                        !advisorEngine.enabled -> "Advisor OFF (llm=off) — configure Secrets to enable veto screening"
                        !advisorEngine.vetoReady -> "Advisor CLEAR $cleanSym — add FINNHUB_KEY in Secrets for headline veto"
                        else -> "Advisor CLEAR $cleanSym — no material adverse headlines (18h)"
                    }
                    _messages.tryEmit(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { state ->
                    if (state.advisorCheckingSymbol == cleanSym) {
                        state.copy(advisorCheckingSymbol = null)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun runAdvisorJournal() {
        if (_uiState.value.advisorJournalLoading) return
        if (!advisorEngine.enabled) {
            _messages.tryEmit("Configure LLM_BASE_URL, LLM_API_KEY, and LLM_MODEL in Secrets to run journal review")
            return
        }
        val entries = _uiState.value.botState.log
        if (entries.none { it.pnl != null }) {
            _messages.tryEmit("Close at least one trade to generate a quantitative journal review")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(advisorJournalLoading = true) }
            try {
                val report = withContext(Dispatchers.IO) {
                    advisorEngine.journal(entries)
                }
                val used = advisorEngine.budget.callsUsedLastHour()
                _uiState.update { state ->
                    state.copy(
                        advisorJournal = report ?: state.advisorJournal,
                        advisorCallsUsed = used,
                        advisorEvents = advisorEngine.recentEvents()
                    )
                }
                if (report != null) {
                    _messages.tryEmit("Advisor journal updated (${report.sampleSize} closed trades)")
                } else {
                    _messages.tryEmit("Advisor journal returned no opinion (fail-safe)")
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(advisorJournalLoading = false) }
            }
        }
    }

    fun toggleBotRunning() {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val mode = stateSnap.marketMode
            val current = stateSnap.botState
            val toggled = current.copy(running = !current.running)
            val isCryptoToggle = mode == MarketMode.CRYPTO
            val snap2 = stateSnap.cryptoTraderSnapshot
            val ticked = if (stateSnap.rows.isNotEmpty()) {
                PaperBotEngine.tick(
                    state = toggled,
                    rows = stateSnap.rows,
                    advisorVerdicts = stateSnap.advisorVerdicts,
                    advisorEnabled = advisorEngine.enabled,
                    regimeLabel = stateSnap.regime.label.name,
                    cryptoGuards = if (isCryptoToggle) stateSnap.cryptoGuardsBySymbol else emptyMap(),
                    pegBreached = isCryptoToggle && snap2.pegStatus.anyBreach,
                    edgeDecayed = isCryptoToggle && snap2.decayReport.verdict == DecayVerdict.DECAYED,
                    heatBreached = isCryptoToggle && snap2.portfolioHeat.breach,
                    barsBySymbol = if (isCryptoToggle) latestBars else emptyMap()
                )
            } else {
                toggled
            }
            _uiState.update { it.withUpdatedBot(ticked, mode) }
            withContext(Dispatchers.IO) { store.saveBotState(ticked, mode) }
            handleBotTickTransitions(toggled, ticked)
        }
    }

    fun setTradeNotificationsEnabled(enabled: Boolean) {
        val currentCfg = _uiState.value.config
        if (currentCfg.tradeNotificationsEnabled == enabled) return
        val updatedCfg = currentCfg.copy(tradeNotificationsEnabled = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            _uiState.update { it.copy(config = updatedCfg) }
            _messages.tryEmit(
                if (enabled) "Autotrader push notifications enabled"
                else "Autotrader push notifications muted"
            )
        }
    }

    fun setNewsNotificationsEnabled(enabled: Boolean) {
        val currentCfg = _uiState.value.config
        if (currentCfg.newsNotificationsEnabled == enabled) return
        val updatedCfg = currentCfg.copy(newsNotificationsEnabled = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            _uiState.update { it.copy(config = updatedCfg) }
            _messages.tryEmit(
                if (enabled) "Watchlist AI catalyst notifications enabled"
                else "Watchlist AI catalyst notifications muted"
            )
            if (enabled) {
                evaluateWatchlistNewsCatalysts(_uiState.value.rows, updatedCfg)
            }
        }
    }

    fun notifyMessage(text: String) {
        _messages.tryEmit(text)
    }

    private suspend fun evaluateWatchlistNewsCatalysts(
        rows: List<SignalRow>,
        cfg: AppConfig
    ) {
        if (!cfg.newsNotificationsEnabled || cfg.watchlist.isEmpty() || rows.isEmpty()) return
        val newlyDetected = NewsCatalystAlertEngine.detectHighImpactWatchlistEvents(
            rows = rows,
            watchlist = cfg.watchlist,
            alreadyNotifiedKeys = notifiedNewsKeys
        )
        if (newlyDetected.isEmpty()) return
        notifiedNewsKeys = (notifiedNewsKeys + newlyDetected.map { it.eventKey }).toList().takeLast(80).toSet()
        withContext(Dispatchers.IO) { store.saveNotifiedNewsKeys(notifiedNewsKeys) }
        newlyDetected.forEach { event ->
            newsNotifier.notifyHighImpact(event)
            val matPct = (event.materiality * 100.0).toInt()
            val dir = if (event.direction >= 0.0) "Bullish" else "Bearish"
            _messages.tryEmit(
                "Watchlist Catalyst: ${event.symbol} $dir ($matPct%) · ${event.title}"
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val currentCfg = _uiState.value.config
        if (currentCfg.themeMode == mode) return
        val updatedCfg = currentCfg.copy(themeMode = mode)
        _uiState.update { it.copy(config = updatedCfg) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
        }
    }

    fun toggleThemeMode() {
        val next = if (_uiState.value.config.themeMode == ThemeMode.DARK) {
            ThemeMode.LIGHT
        } else {
            ThemeMode.DARK
        }
        setThemeMode(next)
    }

    fun setVixAlertEnabled(enabled: Boolean) {
        val currentCfg = _uiState.value.config
        if (currentCfg.vixAlertEnabled == enabled) return
        val updatedCfg = currentCfg.copy(vixAlertEnabled = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            val currentState = _uiState.value
            val vixEval = VixAlertEngine.evaluate(
                snapshot = currentState.vixSnapshot,
                thresholdPct = updatedCfg.vixSpikeThresholdPct,
                enabled = enabled,
                monitorState = currentState.vixMonitorState
            )
            _uiState.update { it.copy(config = updatedCfg, vixMonitorState = vixEval.monitorState) }
            _messages.tryEmit(
                if (enabled) "VIX volatility spike alerts enabled (+${"%.1f".format(Locale.US, updatedCfg.vixSpikeThresholdPct)}%)"
                else "VIX volatility spike alerts paused"
            )
            vixEval.triggeredEvent?.let { spike ->
                vixNotifier.notifySpike(spike)
                _messages.tryEmit(
                    "VIX Spike Alert: ${"%.2f".format(Locale.US, spike.vixLevel)} (+${"%.1f".format(Locale.US, spike.spikePct)}% intraday)"
                )
            }
        }
    }

    fun updateVixSpikeThreshold(thresholdPct: Double) {
        val safeThresh = thresholdPct.takeIf { it.isFinite() }
            ?.coerceIn(VixAlertEngine.MIN_THRESHOLD_PCT, VixAlertEngine.MAX_THRESHOLD_PCT)
            ?: return
        val currentCfg = _uiState.value.config
        val updatedCfg = currentCfg.copy(vixSpikeThresholdPct = safeThresh)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            val currentState = _uiState.value
            val rearmed = currentState.vixMonitorState.copy(armed = true)
            val vixEval = VixAlertEngine.evaluate(
                snapshot = currentState.vixSnapshot,
                thresholdPct = safeThresh,
                enabled = updatedCfg.vixAlertEnabled,
                monitorState = rearmed
            )
            _uiState.update { it.copy(config = updatedCfg, vixMonitorState = vixEval.monitorState) }
            _messages.tryEmit(
                "VIX spike threshold set to +${"%.1f".format(Locale.US, safeThresh)}%"
            )
            vixEval.triggeredEvent?.let { spike ->
                vixNotifier.notifySpike(spike)
                _messages.tryEmit(
                    "VIX Spike Alert: ${"%.2f".format(Locale.US, spike.vixLevel)} (+${"%.1f".format(Locale.US, spike.spikePct)}% intraday)"
                )
            }
        }
    }

    fun rearmVixAlert() {
        val currentState = _uiState.value
        val rearmed = currentState.vixMonitorState.copy(
            armed = true,
            lastTriggeredAtMs = null,
            lastTriggeredSpikePct = null
        )
        _uiState.update { it.copy(vixMonitorState = rearmed) }
        _messages.tryEmit("VIX volatility alert re-armed")
    }

    fun triggerTestVixSpike() {
        val currentState = _uiState.value
        val baseSnap = currentState.vixSnapshot
            ?: VixAlertEngine.demoBaselineFromRegime(currentState.regime)
        val spikedSnap = VixAlertEngine.simulateSpike(baseSnap, currentState.config.vixSpikeThresholdPct)
        val armedMonitor = currentState.vixMonitorState.copy(
            armed = true,
            lastTriggeredAtMs = null,
            lastTriggeredSpikePct = null
        )
        val vixEval = VixAlertEngine.evaluate(
            snapshot = spikedSnap,
            thresholdPct = currentState.config.vixSpikeThresholdPct,
            enabled = true,
            monitorState = armedMonitor
        )
        _uiState.update { it.copy(vixSnapshot = spikedSnap, vixMonitorState = vixEval.monitorState) }
        vixEval.triggeredEvent?.let { spike ->
            vixNotifier.notifySpike(spike)
            _messages.tryEmit(
                "VIX Spike Alert: ${"%.2f".format(Locale.US, spike.vixLevel)} (+${"%.1f".format(Locale.US, spike.spikePct)}% intraday)"
            )
        }
    }

    private fun handleBotTickTransitions(before: BotState, after: BotState) {
        val newEntries = PaperBotEngine.detectNewLogEntries(before, after)
        if (newEntries.isEmpty()) return
        val notifyEnabled = _uiState.value.config.tradeNotificationsEnabled
        newEntries.forEach { entry ->
            val openPos = after.positions.find { it.symbol == entry.symbol }
            if (entry.type.equals("VETO", ignoreCase = true)) {
                _messages.tryEmit(
                    "Advisor vetoed ${entry.symbol} · ${entry.note}"
                )
                return@forEach
            }
            if (entry.type.equals("GUARD", ignoreCase = true)) {
                _messages.tryEmit(
                    "Crypto Guard blocked ${entry.symbol} · ${entry.note}"
                )
                return@forEach
            }
            if (notifyEnabled) {
                tradeNotifier.notifyExecution(entry, openPos, after)
            }
            when {
                entry.type.equals("OPEN", ignoreCase = true) -> {
                    _messages.tryEmit(
                        "Autotrader opened ${entry.symbol} @ $${"%.2f".format(Locale.US, entry.price)} (llm=${entry.llmVerdict})"
                    )
                }
                entry.type.equals("TRIM", ignoreCase = true) -> {
                    val pnl = entry.pnl ?: 0.0
                    val sign = if (pnl >= 0.0) "+" else "-"
                    val pnlStr = "$sign$${"%.2f".format(Locale.US, kotlin.math.abs(pnl))}"
                    _messages.tryEmit(
                        "Autotrader trimmed ${entry.symbol} ($pnlStr) · ${entry.note}"
                    )
                }
                else -> {
                    val pnl = entry.pnl ?: 0.0
                    val sign = if (pnl >= 0.0) "+" else "-"
                    val pnlStr = "$sign$${"%.2f".format(Locale.US, kotlin.math.abs(pnl))}"
                    _messages.tryEmit(
                        "Autotrader closed ${entry.symbol} ($pnlStr) · ${entry.note}"
                    )
                }
            }
        }
    }

    fun resetBot() {
        viewModelScope.launch {
            val stateSnap = _uiState.value
            val mode = stateSnap.marketMode
            val acct = stateSnap.config.account.takeIf { it > 0.0 } ?: 10.0
            val resetState = PaperBotEngine.reset(acct)
            _uiState.update { it.withUpdatedBot(resetState, mode) }
            withContext(Dispatchers.IO) { store.saveBotState(resetState, mode) }
            _messages.tryEmit("${mode.shortLabel} paper portfolio reset to $${"%.2f".format(acct)}")
        }
    }

    fun updateFinnhubKey(rawKey: String) {
        val clean = rawKey.trim()
        val updatedCfg = _uiState.value.config.copy(finnhubKey = clean)
        advisorEngine.setRuntimeFinnhubKey(clean)
        val hasKey = advisorEngine.effectiveFinnhubKey.isNotEmpty()
        _uiState.update {
            it.copy(
                config = updatedCfg,
                finnhubConfigured = hasKey,
                advisorVetoReady = advisorEngine.vetoReady
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
            if (clean.isNotEmpty()) {
                _messages.tryEmit("Finnhub API key saved — refreshing financial news…")
                refreshFinnhubNews(isManualRefresh = true)
            } else {
                _messages.tryEmit("Finnhub API key cleared")
                refreshFinnhubNews(isManualRefresh = false)
            }
        }
    }

    // ── AI Providers & ModelRadar Hub ────────────────────────────────────

    private fun applyAiRoutingToAdvisor(cfg: AppConfig) {
        val routing = cfg.aiRouting
        val activeProv = routing.activeProvider
        val activePs = routing.settingsFor(activeProv)

        if (!activePs.enabled) {
            advisorEngine.setRuntimeLlmRouting(
                baseUrl = null,
                apiKey = null,
                model = null,
                forceDisabled = true
            )
            return
        }

        if (activeProv != AiProviderId.GEMINI) {
            val key = ModelRadarEngine.resolveApiKey(activeProv, routing, cfg.geminiKey)
            val model = activePs.selectedModelId.trim().ifBlank {
                if (activeProv == AiProviderId.DEEPSEEK) "deepseek-chat" else ""
            }
            advisorEngine.setRuntimeLlmRouting(
                baseUrl = activeProv.baseUrl,
                apiKey = key,
                model = model,
                isOpenRouter = activeProv == AiProviderId.OPENROUTER,
                forceDisabled = key.isBlank() || model.isBlank()
            )
            return
        }

        // Active provider is Gemini: check if any enabled OpenAI-compatible provider is ready for Advisor vetoes/journal
        val fallbackOpenAi = listOf(AiProviderId.OPENROUTER, AiProviderId.AIHUBMIX, AiProviderId.DEEPSEEK)
            .firstOrNull { prov ->
                val ps = routing.settingsFor(prov)
                ps.enabled &&
                    ModelRadarEngine.resolveApiKey(prov, routing, cfg.geminiKey).isNotBlank() &&
                    ps.selectedModelId.isNotBlank()
            }
        if (fallbackOpenAi != null) {
            val ps = routing.settingsFor(fallbackOpenAi)
            val key = ModelRadarEngine.resolveApiKey(fallbackOpenAi, routing, cfg.geminiKey)
            advisorEngine.setRuntimeLlmRouting(
                baseUrl = fallbackOpenAi.baseUrl,
                apiKey = key,
                model = ps.selectedModelId,
                isOpenRouter = fallbackOpenAi == AiProviderId.OPENROUTER,
                forceDisabled = false
            )
        } else {
            val anyOpenAiEnabled = listOf(AiProviderId.OPENROUTER, AiProviderId.AIHUBMIX, AiProviderId.DEEPSEEK)
                .any { routing.settingsFor(it).enabled }
            advisorEngine.setRuntimeLlmRouting(
                baseUrl = null,
                apiKey = null,
                model = null,
                forceDisabled = !anyOpenAiEnabled
            )
        }
    }

    private fun commitAiRouting(nextRouting: AiRoutingConfig, toastMessage: String? = null) {
        val currentCfg = _uiState.value.config
        val syncedGeminiKey = nextRouting.settingsFor(AiProviderId.GEMINI).apiKey.trim()
        val updatedCfg = currentCfg.copy(
            geminiKey = syncedGeminiKey,
            aiRouting = nextRouting
        )
        applyAiRoutingToAdvisor(updatedCfg)
        _uiState.update { state ->
            val nextProviderFilter = if (state.radarProviderFilter != null &&
                !nextRouting.settingsFor(state.radarProviderFilter).enabled
            ) {
                null
            } else {
                state.radarProviderFilter
            }
            state.copy(
                config = updatedCfg,
                advisorEnabled = advisorEngine.enabled,
                advisorVetoReady = advisorEngine.vetoReady,
                advisorModel = advisorEngine.effectiveModel,
                radarProviderFilter = nextProviderFilter
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveConfig(updatedCfg) }
        }
        if (!toastMessage.isNullOrBlank()) {
            _messages.tryEmit(toastMessage)
        }
    }

    fun openAiHubSheet() {
        _uiState.update {
            it.copy(
                thresholdSettingsSheetOpen = false,
                aiHubSheetOpen = true
            )
        }
        if (_uiState.value.radarLastSyncedMs == 0L) {
            syncModelCatalog(forceRefresh = false)
        }
    }

    fun closeAiHubSheet() {
        _uiState.update { it.copy(aiHubSheetOpen = false) }
    }

    fun selectRadarTierFilter(filter: ModelTierFilter) {
        _uiState.update { it.copy(radarTierFilter = filter) }
    }

    fun selectRadarProviderFilter(provider: AiProviderId?) {
        _uiState.update { it.copy(radarProviderFilter = provider) }
    }

    fun updateRadarSearchQuery(query: String) {
        _uiState.update { it.copy(radarSearchQuery = query) }
    }

    fun setAiProviderEnabled(provider: AiProviderId, enabled: Boolean) {
        val currentRouting = _uiState.value.config.aiRouting
        val existing = currentRouting.settingsFor(provider)
        val updatedProviders = currentRouting.providers.toMutableMap().apply {
            put(provider, existing.copy(enabled = enabled))
        }
        val nextActive = when {
            enabled && !currentRouting.settingsFor(currentRouting.activeProvider).enabled -> provider
            !enabled && currentRouting.activeProvider == provider -> {
                AiProviderId.entries.firstOrNull { it != provider && (updatedProviders[it]?.enabled == true) }
                    ?: provider
            }
            else -> currentRouting.activeProvider
        }
        val nextRouting = currentRouting.copy(
            activeProvider = nextActive,
            providers = updatedProviders
        )
        commitAiRouting(
            nextRouting = nextRouting,
            toastMessage = if (enabled) "${provider.displayName} enabled" else "${provider.displayName} turned OFF"
        )
    }

    fun setActiveAiProvider(provider: AiProviderId) {
        val currentRouting = _uiState.value.config.aiRouting
        val existing = currentRouting.settingsFor(provider)
        val updatedProviders = currentRouting.providers.toMutableMap().apply {
            put(provider, existing.copy(enabled = true))
        }
        val nextRouting = currentRouting.copy(
            activeProvider = provider,
            providers = updatedProviders
        )
        commitAiRouting(
            nextRouting = nextRouting,
            toastMessage = "Active AI set to ${provider.displayName}"
        )
    }

    fun updateAiProviderApiKey(provider: AiProviderId, rawKey: String) {
        val clean = rawKey.trim()
        val currentRouting = _uiState.value.config.aiRouting
        val existing = currentRouting.settingsFor(provider)
        val updatedProviders = currentRouting.providers.toMutableMap().apply {
            put(provider, existing.copy(apiKey = clean))
        }
        val nextRouting = currentRouting.copy(providers = updatedProviders)
        commitAiRouting(
            nextRouting = nextRouting,
            toastMessage = if (clean.isNotEmpty()) "${provider.displayName} key saved" else "${provider.displayName} key cleared"
        )
        if (clean.isNotEmpty()) {
            syncModelCatalog(forceRefresh = true)
        }
    }

    fun selectRadarModel(model: RadarModel) {
        val currentRouting = _uiState.value.config.aiRouting
        val existing = currentRouting.settingsFor(model.provider)
        val tierPref = when (model.tier) {
            ModelTier.FREE -> ModelTierFilter.FREE
            ModelTier.PAID -> ModelTierFilter.PAID
            ModelTier.UNKNOWN -> ModelTierFilter.ALL
        }
        val updatedProviders = currentRouting.providers.toMutableMap().apply {
            put(
                model.provider,
                existing.copy(
                    enabled = true,
                    selectedModelId = model.id,
                    tierPreference = tierPref
                )
            )
        }
        val nextRouting = currentRouting.copy(
            activeProvider = model.provider,
            providers = updatedProviders
        )
        commitAiRouting(
            nextRouting = nextRouting,
            toastMessage = "Selected ${model.id} (${model.provider.displayName} · ${model.tier.label})"
        )
    }

    fun pickBestRadarModel(tier: ModelTierFilter) {
        val state = _uiState.value
        val best = ModelRadarEngine.pickBest(
            models = state.radarModels,
            tier = tier,
            provider = state.radarProviderFilter,
            enabledProviders = state.config.aiRouting.enabledProviders
        )
        if (best == null) {
            _messages.tryEmit("No ${tier.label} chat models found in enabled providers — tap Sync Catalog")
            return
        }
        selectRadarModel(best)
    }

    fun syncModelCatalog(forceRefresh: Boolean = false) {
        if (_uiState.value.radarSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(radarSyncing = true) }
            try {
                val routing = _uiState.value.config.aiRouting
                val result = withContext(Dispatchers.IO) {
                    modelRadarEngine.syncCatalog(routing = routing, forceRefresh = forceRefresh)
                }
                val merged = if (result.models.isEmpty()) {
                    ModelRadarEngine.builtInGeminiCatalog()
                } else {
                    result.models
                }
                _uiState.update {
                    it.copy(
                        radarModels = merged,
                        radarErrors = result.errorsByProvider,
                        radarLastSyncedMs = result.syncedAtMs
                    )
                }
                if (forceRefresh) {
                    val chatCount = merged.count { ModelRadarEngine.isChat(it) }
                    val freeCount = merged.count { ModelRadarEngine.isChat(it) && it.tier == ModelTier.FREE }
                    val paidCount = merged.count { ModelRadarEngine.isChat(it) && it.tier == ModelTier.PAID }
                    _messages.tryEmit("Synced $chatCount chat models ($freeCount free · $paidCount paid)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Model catalog sync failed: ${e.message ?: "network error"}")
            } finally {
                _uiState.update { it.copy(radarSyncing = false) }
            }
        }
    }

    fun probeRadarModel(model: RadarModel) {
        if (_uiState.value.probingModelKey != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(probingModelKey = model.key) }
            try {
                val routing = _uiState.value.config.aiRouting
                val res = withContext(Dispatchers.IO) {
                    modelRadarEngine.probeModel(model, routing)
                }
                _uiState.update {
                    it.copy(probeResultsByKey = it.probeResultsByKey + (model.key to res))
                }
                if (res.ok) {
                    _messages.tryEmit("Probe OK (${model.id}) · ${res.latencyMs}ms · \"${res.replyPreview}\"")
                } else {
                    _messages.tryEmit("Probe failed (${model.id}): ${res.error ?: "error"}")
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(probingModelKey = null) }
            }
        }
    }

    fun saveConfigAndRescan(newConfig: AppConfig) {
        viewModelScope.launch {
            val cleanGemini = newConfig.geminiKey.trim()
            val geminiSettings = newConfig.aiRouting.settingsFor(AiProviderId.GEMINI)
            val syncedRouting = newConfig.aiRouting.copy(
                providers = newConfig.aiRouting.providers.toMutableMap().apply {
                    put(AiProviderId.GEMINI, geminiSettings.copy(apiKey = cleanGemini))
                }
            )
            val sanitized = newConfig.copy(
                alpacaId = newConfig.alpacaId.trim(),
                alpacaSecret = newConfig.alpacaSecret.trim(),
                geminiKey = cleanGemini,
                finnhubKey = newConfig.finnhubKey.trim(),
                cryptoPanicToken = newConfig.cryptoPanicToken.trim(),
                account = if (newConfig.account > 0.0) newConfig.account else 10.0,
                riskPct = if (newConfig.riskPct > 0.0) newConfig.riskPct else 1.0,
                watchlist = newConfig.watchlist.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct(),
                cryptoWatchlist = newConfig.cryptoWatchlist.map { CryptoFeedsEngine.normalizeCryptoSymbol(it) }.filter { it.isNotEmpty() }.distinct(),
                aiRouting = syncedRouting
            )
            advisorEngine.setRuntimeFinnhubKey(sanitized.finnhubKey)
            applyAiRoutingToAdvisor(sanitized)
            withContext(Dispatchers.IO) { store.saveConfig(sanitized) }
            _uiState.update { state ->
                val swappedBot = if (sanitized.marketMode == MarketMode.CRYPTO) state.cryptoBotState else state.stockBotState
                state.copy(
                    config = sanitized,
                    marketMode = sanitized.marketMode,
                    botState = swappedBot,
                    finnhubConfigured = advisorEngine.effectiveFinnhubKey.isNotEmpty(),
                    advisorEnabled = advisorEngine.enabled,
                    advisorVetoReady = advisorEngine.vetoReady,
                    advisorModel = advisorEngine.effectiveModel
                )
            }
            _messages.tryEmit("Saved")
            rescan(quiet = false, force = true)
        }
    }

    fun refreshDashboard() {
        rescan(quiet = false, isManualPullRefresh = true)
    }

    fun rescan(quiet: Boolean = false, isManualPullRefresh: Boolean = false, force: Boolean = false) {
        if (_uiState.value.isScanning && !force) return
        if (force) rescanJob?.cancel()
        rescanJob = viewModelScope.launch {
            val startedAtMs = System.currentTimeMillis()
            val cfg = _uiState.value.config
            val scanMode = cfg.marketMode
            _uiState.update {
                it.copy(
                    isScanning = true,
                    statusText = if (!quiet) {
                        if (scanMode == MarketMode.CRYPTO) "scanning Kraken 24/7 spot, futures & guard…"
                        else "refreshing regime & AI scores…"
                    } else it.statusText
                )
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.scan(
                        cfg = cfg,
                        fundingHistory = cryptoFundingHistory,
                        classifyCryptoHeadline = if (scanMode == MarketMode.CRYPTO) {
                            { headline -> advisorEngine.classifyCryptoHeadline(headline) }
                        } else null
                    )
                }
                if (_uiState.value.marketMode != scanMode) return@launch
                if (result.bars.isNotEmpty()) {
                    latestBars = result.bars
                }
                if (scanMode == MarketMode.CRYPTO && result.cryptoDerivsBySymbol.isNotEmpty()) {
                    cryptoFundingHistory = CryptoFeedsEngine.appendFundingHistory(
                        existing = cryptoFundingHistory,
                        snapshots = result.cryptoDerivsBySymbol
                    )
                    withContext(Dispatchers.IO) {
                        store.saveCryptoFundingHistory(cryptoFundingHistory)
                    }
                }
                val maxFundingSamples = cryptoFundingHistory.groupingBy { it.symbol }.eachCount().values.maxOrNull() ?: 0
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val status = when {
                    scanMode == MarketMode.CRYPTO && result.source == "live" ->
                        "${result.rows.size} crypto pairs · Kraken 24/7 · $timeStr"
                    scanMode == MarketMode.CRYPTO ->
                        "${result.rows.size} crypto pairs · offline fallback"
                    result.source == "demo" ->
                        "${result.rows.size} analysed · demo data"
                    else ->
                        "${result.rows.size} analysed · live · $timeStr"
                }
                val stateBeforeTick = _uiState.value
                val prevBot = stateBeforeTick.botState
                val priceMap = result.rows.associate { it.symbol to it.price }
                val preHeat = if (scanMode == MarketMode.CRYPTO) {
                    CryptoTraderEngine.computeHeat(
                        positions = prevBot.positions,
                        equity = PaperBotEngine.liveEquity(prevBot),
                        livePrices = priceMap
                    )
                } else {
                    stateBeforeTick.cryptoTraderSnapshot.portfolioHeat
                }
                val preDecay = if (scanMode == MarketMode.CRYPTO) {
                    CryptoTraderEngine.evaluateDecay(prevBot.log)
                } else {
                    stateBeforeTick.cryptoTraderSnapshot.decayReport
                }
                val tickedBot = PaperBotEngine.tick(
                    state = prevBot,
                    rows = result.rows,
                    advisorVerdicts = stateBeforeTick.advisorVerdicts,
                    advisorEnabled = advisorEngine.enabled,
                    regimeLabel = result.regime.label.name,
                    cryptoGuards = if (scanMode == MarketMode.CRYPTO) result.cryptoGuardsBySymbol else emptyMap(),
                    pegBreached = scanMode == MarketMode.CRYPTO && result.cryptoPegStatus.anyBreach,
                    edgeDecayed = scanMode == MarketMode.CRYPTO && preDecay.verdict == DecayVerdict.DECAYED,
                    heatBreached = scanMode == MarketMode.CRYPTO && preHeat.breach,
                    barsBySymbol = if (scanMode == MarketMode.CRYPTO) result.bars else emptyMap()
                )
                withContext(Dispatchers.IO) { store.saveBotState(tickedBot, scanMode) }
                handleBotTickTransitions(prevBot, tickedBot)
                val cryptoSnapshot = if (scanMode == MarketMode.CRYPTO) {
                    val tsmom = CryptoTraderEngine.evaluateSignals(result.bars)
                    val freshness = CryptoTraderEngine.evaluateFeedFreshness(result.cryptoLastBarEpochSecBySymbol)
                    val adverseCount = result.cryptoGuardsBySymbol.values.count {
                        it.eventCategory.isNotBlank() && !it.eventCategory.equals("NONE", ignoreCase = true)
                    }
                    CryptoTraderSnapshot(
                        btcRegime = CryptoTraderEngine.btcRegime(result.bars),
                        tsmomSignals = tsmom,
                        qualifiedSymbols = tsmom.filter { it.qualified }.map { it.symbol },
                        portfolioHeat = CryptoTraderEngine.computeHeat(
                            positions = tickedBot.positions,
                            equity = PaperBotEngine.liveEquity(tickedBot),
                            livePrices = priceMap
                        ),
                        pegStatus = result.cryptoPegStatus,
                        decayReport = CryptoTraderEngine.evaluateDecay(tickedBot.log),
                        feedFreshness = freshness,
                        stalePairCount = freshness.count { it.stale },
                        adverseEventsCount = adverseCount,
                        updatedAtMs = System.currentTimeMillis()
                    )
                } else {
                    stateBeforeTick.cryptoTraderSnapshot
                }
                val updatedRegimeHistory = QuantEngine.appendScanRegime(
                    history = _uiState.value.scanRegimeHistory,
                    regime = result.regime,
                    source = result.source
                )
                withContext(Dispatchers.IO) { store.saveScanRegimeHistory(updatedRegimeHistory) }
                val eval = PriceAlertEngine.evaluate(_uiState.value.alerts, priceMap)
                if (eval.newlyTriggered.isNotEmpty()) {
                    withContext(Dispatchers.IO) { store.saveAlerts(eval.updatedAlerts) }
                    eval.newlyTriggered.forEach { fired ->
                        notifier.notifyTriggered(fired)
                        val dir = if (fired.condition == AlertCondition.ABOVE) "crossed ≥" else "dropped ≤"
                        _messages.tryEmit(
                            "Alert: ${fired.symbol} $dir $${"%.2f".format(Locale.US, fired.targetPrice)}"
                        )
                    }
                }
                val prevSignalAlerts = _uiState.value.signalAlerts
                val sigEval = PriceAlertEngine.evaluateSignalChanges(prevSignalAlerts, result.rows)
                if (sigEval.updatedAlerts != prevSignalAlerts) {
                    withContext(Dispatchers.IO) { store.saveSignalAlerts(sigEval.updatedAlerts) }
                }
                if (sigEval.triggeredEvents.isNotEmpty()) {
                    sigEval.triggeredEvents.forEach { ev ->
                        notifier.notifySignalChange(ev)
                        _messages.tryEmit(
                            "AI Signal Alert: ${ev.symbol} ${ev.previousAction.name} → ${ev.newAction.name} @ $${"%.2f".format(Locale.US, ev.price)}"
                        )
                    }
                }
                val vixSnap = if (scanMode == MarketMode.STOCKS) {
                    withContext(Dispatchers.IO) {
                        repository.fetchVixSnapshot(
                            cfg = cfg,
                            regime = result.regime,
                            previous = _uiState.value.vixSnapshot
                        )
                    }
                } else {
                    _uiState.value.vixSnapshot
                }
                val vixEval = VixAlertEngine.evaluate(
                    snapshot = vixSnap,
                    thresholdPct = cfg.vixSpikeThresholdPct,
                    enabled = cfg.vixAlertEnabled && scanMode == MarketMode.STOCKS,
                    monitorState = _uiState.value.vixMonitorState
                )
                _uiState.update { state ->
                    if (state.marketMode != scanMode) return@update state
                    val updatedSelected = state.selectedSignal?.let { sel ->
                        result.rows.find { it.symbol == sel.symbol }
                    }
                    state.withUpdatedBot(tickedBot, scanMode).copy(
                        rows = result.rows,
                        regime = result.regime,
                        scanRegimeHistory = updatedRegimeHistory,
                        news = result.news,
                        source = result.source,
                        statusText = status,
                        alerts = eval.updatedAlerts,
                        signalAlerts = sigEval.updatedAlerts,
                        selectedSignal = updatedSelected,
                        vixSnapshot = vixSnap,
                        vixMonitorState = vixEval.monitorState,
                        barsBySymbol = latestBars,
                        cryptoDerivsBySymbol = result.cryptoDerivsBySymbol,
                        cryptoGuardsBySymbol = result.cryptoGuardsBySymbol,
                        cryptoPanicPosts = result.cryptoPanicPosts,
                        cryptoFundingSampleCount = maxFundingSamples,
                        cryptoTraderSnapshot = cryptoSnapshot,
                        quantScannerState = state.quantScannerState.copy(
                            lastUpdatedMs = System.currentTimeMillis(),
                            errorMessage = null
                        )
                    )
                }
                vixEval.triggeredEvent?.let { spike ->
                    vixNotifier.notifySpike(spike)
                    _messages.tryEmit(
                        "VIX Spike Alert: ${"%.2f".format(Locale.US, spike.vixLevel)} (+${"%.1f".format(Locale.US, spike.spikePct)}% intraday)"
                    )
                }
                evaluateWatchlistNewsCatalysts(result.rows, cfg)
                evaluateThresholdAlerts(
                    regime = result.regime,
                    rows = result.rows,
                    advisorVerdicts = _uiState.value.advisorVerdicts,
                    emitSnackbar = !isManualPullRefresh
                )
                refreshFinnhubNews(isManualRefresh = false)
                tickedBot.positions.forEach { pos ->
                    assessThreat(pos.symbol)
                }
                // Non-blocking background veto pre-screen when advisor + Finnhub are configured (Stocks mode)
                if (scanMode == MarketMode.STOCKS && advisorEngine.vetoReady) {
                    val heldSyms = tickedBot.positions.map { it.symbol }
                    val topBuySyms = result.rows
                        .filter { it.action == SignalAction.BUY }
                        .map { it.symbol }
                    val targets = if (isManualPullRefresh) {
                        (heldSyms + cfg.watchlist + topBuySyms).distinct().take(3)
                    } else {
                        topBuySyms.take(1)
                    }
                    targets.forEach { sym ->
                        checkAdvisorVeto(sym, quiet = true, forceRefresh = isManualPullRefresh)
                    }
                }
                if (isManualPullRefresh) {
                    val elapsed = System.currentTimeMillis() - startedAtMs
                    if (elapsed < 450L) {
                        delay(450L - elapsed)
                    }
                    val label = if (scanMode == MarketMode.CRYPTO) "Crypto 24/7" else result.regime.label.name
                    _messages.tryEmit(
                        "Refreshed · $label · ${result.rows.size} ${if (scanMode == MarketMode.CRYPTO) "pairs" else "symbols"}"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown scan error"
                _uiState.update { it.copy(statusText = "scan failed: $msg") }
                _messages.tryEmit("Scan failed: $msg")
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    // ── Quantitative Equity Signal Scanner ───────────────────────────────

    fun updateQuantScannerInput(raw: String) {
        _uiState.update {
            it.copy(quantScannerState = it.quantScannerState.copy(tickerInput = raw.uppercase(Locale.US)))
        }
    }

    fun toggleQuantScannerLiveStream(enabled: Boolean) {
        _uiState.update {
            it.copy(quantScannerState = it.quantScannerState.copy(liveStreamEnabled = enabled))
        }
        _messages.tryEmit(
            if (enabled) "Real-time signal stream resumed (2.5s)" else "Real-time signal stream paused"
        )
    }

    fun setQuantScannerActionFilter(action: SignalAction?) {
        _uiState.update {
            it.copy(quantScannerState = it.quantScannerState.copy(actionFilter = action))
        }
    }

    fun setQuantScannerMinConviction(minConv: Double) {
        _uiState.update {
            it.copy(quantScannerState = it.quantScannerState.copy(minConviction = minConv.coerceAtLeast(0.0)))
        }
    }

    fun clearQuantScannerScope() {
        _uiState.update {
            it.copy(
                quantScannerState = it.quantScannerState.copy(
                    tickerInput = "",
                    activeSymbols = emptyList(),
                    errorMessage = null
                )
            )
        }
    }

    fun runQuantSignalScan(explicitInput: String? = null) {
        if (_uiState.value.quantScannerState.isFetching) return
        val inputToUse = explicitInput ?: _uiState.value.quantScannerState.tickerInput
        val parsedTickers = QuantSignalScannerEngine.parseTickerInput(inputToUse)

        if (parsedTickers.isEmpty()) {
            _uiState.update {
                it.copy(
                    quantScannerState = it.quantScannerState.copy(
                        tickerInput = "",
                        activeSymbols = emptyList(),
                        errorMessage = null
                    )
                )
            }
            rescan(quiet = false)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    quantScannerState = it.quantScannerState.copy(
                        tickerInput = parsedTickers.joinToString(", "),
                        isFetching = true,
                        errorMessage = null
                    )
                )
            }
            try {
                val cfg = _uiState.value.config
                val result = withContext(Dispatchers.IO) {
                    repository.scanCustomSymbols(parsedTickers, cfg)
                }
                if (result.bars.isNotEmpty()) {
                    latestBars = latestBars + result.bars
                }
                val fetchedBySym = result.rows.associateBy { it.symbol.uppercase(Locale.US) }
                val nowMs = System.currentTimeMillis()

                val priceMap = result.rows.associate { it.symbol to it.price }
                val priceEval = PriceAlertEngine.evaluate(_uiState.value.alerts, priceMap)
                val prevSigAlerts = _uiState.value.signalAlerts
                val sigEval = PriceAlertEngine.evaluateSignalChanges(prevSigAlerts, result.rows, nowMs)

                _uiState.update { state ->
                    val mergedMap = LinkedHashMap<String, SignalRow>()
                    state.rows.forEach { mergedMap[it.symbol.uppercase(Locale.US)] = it }
                    fetchedBySym.forEach { (sym, row) -> mergedMap[sym] = row }
                    val mergedRows = mergedMap.values.sortedByDescending { it.conviction }
                    val updatedSelected = state.selectedSignal?.let { sel ->
                        mergedMap[sel.symbol.uppercase(Locale.US)]
                    }
                    state.copy(
                        rows = mergedRows,
                        source = result.source,
                        alerts = priceEval.updatedAlerts,
                        signalAlerts = sigEval.updatedAlerts,
                        barsBySymbol = latestBars,
                        selectedSignal = updatedSelected,
                        quantScannerState = state.quantScannerState.copy(
                            activeSymbols = parsedTickers,
                            isFetching = false,
                            lastUpdatedMs = nowMs,
                            tickCount = state.quantScannerState.tickCount + 1,
                            errorMessage = null
                        )
                    )
                }

                if (priceEval.newlyTriggered.isNotEmpty()) {
                    withContext(Dispatchers.IO) { store.saveAlerts(priceEval.updatedAlerts) }
                    priceEval.newlyTriggered.forEach { notifier.notifyTriggered(it) }
                }
                if (sigEval.updatedAlerts != prevSigAlerts) {
                    withContext(Dispatchers.IO) { store.saveSignalAlerts(sigEval.updatedAlerts) }
                }
                if (sigEval.triggeredEvents.isNotEmpty()) {
                    sigEval.triggeredEvents.forEach { notifier.notifySignalChange(it) }
                }

                val scopedRows = result.rows.filter { parsedTickers.contains(it.symbol.uppercase(Locale.US)) }
                val buys = scopedRows.count { it.action == SignalAction.BUY }
                val sells = scopedRows.count { it.action == SignalAction.SELL }
                _messages.tryEmit(
                    "Scanned ${scopedRows.size} equities · $buys BUY · $sells SELL"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to fetch quantitative signals"
                _uiState.update {
                    it.copy(
                        quantScannerState = it.quantScannerState.copy(
                            isFetching = false,
                            errorMessage = msg
                        )
                    )
                }
            }
        }
    }

    /**
     * Runs the `cryptotrader.py 2.0` 9-variant grid backtest (`max_pos ∈ {3, 5, 7}` ×
     * `target_vol ∈ {25%, 35%, 50%}`) with fees + slippage both sides, Net Sharpe (365),
     * Deflated Sharpe Ratio (DSR), and 10-block CSCV PBO on the loaded Kraken daily OHLC bars.
     */
    fun runCryptoBacktest() {
        if (_uiState.value.cryptoBacktestRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(cryptoBacktestRunning = true) }
            try {
                val barsForBt = if (latestBars.keys.any { it.contains("/") } && latestBars.size >= 3) {
                    latestBars.filterKeys { it.contains("/") }
                } else {
                    val cfg = _uiState.value.config.copy(marketMode = MarketMode.CRYPTO)
                    withContext(Dispatchers.IO) {
                        repository.scan(cfg, cryptoFundingHistory).bars
                    }
                }
                val summary = withContext(Dispatchers.Default) {
                    CryptoTraderEngine.runGridBacktest(barsForBt)
                }
                _uiState.update {
                    it.copy(
                        cryptoBacktestRunning = false,
                        cryptoBacktestSummary = summary
                    )
                }
                if (summary != null) {
                    val verdict = if (summary.passed) "PASS" else "FAIL"
                    _messages.tryEmit(
                        "Crypto Backtest $verdict · Sharpe ${"%.2f".format(Locale.US, summary.netSharpe)} · DSR ${"%.2f".format(Locale.US, summary.deflatedSharpe)} · PBO ${"%.2f".format(Locale.US, summary.pbo)}"
                    )
                } else {
                    _messages.tryEmit("Need at least 140 daily crypto bars to run backtest")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(cryptoBacktestRunning = false) }
                _messages.tryEmit("Backtest failed: ${e.message?.take(60) ?: "unknown error"}")
            }
        }
    }

    /** Refreshes live Kraken public USDT/USD and USDC/USD peg deviation immediately. */
    fun refreshCryptoPeg() {
        viewModelScope.launch {
            val peg = withContext(Dispatchers.IO) { repository.fetchCryptoStablecoinPeg() }
            _uiState.update { state ->
                state.copy(
                    cryptoTraderSnapshot = state.cryptoTraderSnapshot.copy(
                        pegStatus = peg,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
            val msg = if (peg.anyBreach) {
                "ALERT: Stablecoin peg breach detected (>1.5% dev) — new entries paused"
            } else {
                val summary = peg.rows.mapNotNull { r ->
                    r.price?.let { "${r.symbol.substringBefore("/")} $${"%.4f".format(Locale.US, it)}" }
                }.joinToString(" · ")
                if (summary.isNotBlank()) "Peg check OK: $summary" else "Peg check refreshed"
            }
            _messages.tryEmit(msg)
        }
    }
}
