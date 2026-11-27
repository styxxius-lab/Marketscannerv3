package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.EncryptedPrefsStore
import com.example.data.service.MarketCatalystService
import com.example.data.worker.CatalystMonitorScheduler
import com.example.domain.engine.AiProviderCatalystRun
import com.example.domain.engine.AiProviderRunStatus
import com.example.domain.engine.BackgroundMonitorConfig
import com.example.domain.engine.BackgroundMonitorRunStatus
import com.example.domain.engine.CatalystImpactFilter
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.engine.TickerCatalystEngine
import com.example.domain.engine.TickerCatalystReport
import com.example.domain.engine.TickerCatalystSummary
import com.example.domain.model.AiProviderId
import com.example.domain.model.AppConfig
import com.example.domain.model.MarketMode
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed class CatalystServiceStatus {
    data object Idle : CatalystServiceStatus()
    data object Loading : CatalystServiceStatus()
    data object Success : CatalystServiceStatus()
    data class Error(val message: String) : CatalystServiceStatus()
}

data class MarketCatalystUiState(
    val status: CatalystServiceStatus = CatalystServiceStatus.Idle,
    val reports: List<TickerCatalystReport> = emptyList(),
    val summary: TickerCatalystSummary = TickerCatalystSummary(),
    val trackedTickers: List<String> = TickerCatalystEngine.DEFAULT_TICKERS,
    val tickerInput: String = "",
    val impactFilter: CatalystImpactFilter = CatalystImpactFilter.ALL,
    val expandedSymbol: String? = null,
    val scorerLabel: String = "READY",
    val usedLlm: Boolean = false,
    val headlineSourceLabel: String = "LIVE FEED",
    val lastUpdatedMs: Long = 0L,
    val warningMessage: String? = null,
    val isRefreshing: Boolean = false,
    val providerRuns: List<AiProviderCatalystRun> = emptyList(),
    val activeAiCount: Int = 0,
    val totalImplementedAiCount: Int = AiProviderId.entries.size,
    val monitorConfig: BackgroundMonitorConfig = BackgroundMonitorConfig(),
    val monitorStatus: BackgroundMonitorRunStatus = BackgroundMonitorRunStatus(),
    val isBackgroundCheckRunning: Boolean = false
) {
    val filteredReports: List<TickerCatalystReport>
        get() = TickerCatalystEngine.filterReports(reports, impactFilter)
}

class MarketCatalystViewModel(
    application: Application,
    private val service: MarketCatalystService = MarketCatalystService()
) : AndroidViewModel(application) {

    // Secondary constructor used by default ViewModelProvider.AndroidViewModelFactory
    constructor(application: Application) : this(application, MarketCatalystService())

    private val prefsStore = EncryptedPrefsStore(application.applicationContext)

    private val _uiState = MutableStateFlow(MarketCatalystUiState())
    val uiState: StateFlow<MarketCatalystUiState> = _uiState.asStateFlow()

    private var latestConfig: AppConfig = AppConfig()
    private var latestHeadlines: List<NewsHeadline> = emptyList()
    private var lastMarketMode: MarketMode? = null
    private var hasUserCustomizedTickers: Boolean = false
    private var hasInitialRun: Boolean = false
    private var analyzeJob: Job? = null

    init {
        viewModelScope.launch {
            val savedConfig = prefsStore.loadBackgroundMonitorConfig()
            _uiState.update { it.copy(monitorConfig = savedConfig) }
            runCatching {
                CatalystMonitorScheduler.syncSchedule(getApplication(), savedConfig)
            }
        }
        viewModelScope.launch {
            prefsStore.observeBackgroundMonitorStatus().collectLatest { runStatus ->
                _uiState.update { it.copy(monitorStatus = runStatus) }
            }
        }
        viewModelScope.launch {
            runCatching {
                WorkManager.getInstance(getApplication())
                    .getWorkInfosByTagFlow(CatalystMonitorScheduler.WORK_TAG)
            }.getOrNull()?.collectLatest { infos ->
                val running = infos.any { it.state == WorkInfo.State.RUNNING }
                _uiState.update { it.copy(isBackgroundCheckRunning = running) }
            }
        }
    }

    fun syncEnvironment(
        config: AppConfig,
        rows: List<SignalRow>,
        headlines: List<NewsHeadline>
    ) {
        val modeChanged = lastMarketMode != null && lastMarketMode != config.marketMode
        lastMarketMode = config.marketMode
        latestConfig = config
        latestHeadlines = headlines

        val configuredRoster = TickerCatalystEngine.buildConfiguredProviderRoster(config)
        _uiState.update { current ->
            val existingByProvider = current.providerRuns.associateBy { it.provider }
            val mergedRuns = configuredRoster.map { fresh ->
                val prev = existingByProvider[fresh.provider]
                if (prev != null &&
                    fresh.status == AiProviderRunStatus.READY &&
                    (prev.status == AiProviderRunStatus.SCORED || prev.status == AiProviderRunStatus.FAILED) &&
                    prev.modelId == fresh.modelId
                ) {
                    prev
                } else {
                    fresh
                }
            }
            current.copy(
                providerRuns = mergedRuns,
                totalImplementedAiCount = AiProviderId.entries.size
            )
        }

        if (modeChanged) {
            hasUserCustomizedTickers = false
            _uiState.update { it.copy(expandedSymbol = null) }
        }

        if (!hasUserCustomizedTickers) {
            val defaults = if (config.marketMode == MarketMode.CRYPTO) {
                CryptoFeedsEngine.CRYPTO_UNIVERSE.take(6)
            } else {
                TickerCatalystEngine.DEFAULT_TICKERS
            }
            val dynamicTickers = buildList {
                config.activeWatchlist.forEach { sym ->
                    val clean = sym.trim().uppercase(Locale.US)
                    if (clean.isNotEmpty() && !contains(clean)) add(clean)
                }
                rows.take(6).forEach { row ->
                    val clean = row.symbol.trim().uppercase(Locale.US)
                    if (clean.isNotEmpty() && !contains(clean)) add(clean)
                }
                defaults.forEach { sym ->
                    if (!contains(sym)) add(sym)
                }
            }.take(6)

            _uiState.update { it.copy(trackedTickers = dynamicTickers) }
            if (modeChanged && hasInitialRun) {
                analyzeTickers(explicitTickers = dynamicTickers)
            }
        }
    }

    fun ensureInitialLoad() {
        if (!hasInitialRun && _uiState.value.status is CatalystServiceStatus.Idle) {
            hasInitialRun = true
            analyzeTickers()
        }
    }

    fun updateTickerInput(value: String) {
        _uiState.update { it.copy(tickerInput = value) }
    }

    fun selectImpactFilter(filter: CatalystImpactFilter) {
        _uiState.update { it.copy(impactFilter = filter) }
    }

    fun toggleExpandedSymbol(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        _uiState.update { current ->
            current.copy(expandedSymbol = if (current.expandedSymbol == clean) null else clean)
        }
    }

    fun submitTickerInput() {
        val raw = _uiState.value.tickerInput.trim()
        if (raw.isEmpty()) {
            analyzeTickers()
            return
        }
        val parsed = TickerCatalystEngine.parseTickerList(
            raw = raw,
            fallback = _uiState.value.trackedTickers,
            maxCount = 8
        )
        hasUserCustomizedTickers = true
        _uiState.update {
            it.copy(
                trackedTickers = parsed,
                tickerInput = "",
                impactFilter = CatalystImpactFilter.ALL
            )
        }
        analyzeTickers(explicitTickers = parsed)
    }

    fun analyzeSingleTicker(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isEmpty()) return
        val updated = (listOf(clean) + _uiState.value.trackedTickers)
            .distinct()
            .take(6)
        hasUserCustomizedTickers = true
        _uiState.update {
            it.copy(
                trackedTickers = updated,
                expandedSymbol = clean,
                impactFilter = CatalystImpactFilter.ALL
            )
        }
        analyzeTickers(explicitTickers = updated)
    }

    fun updateBackgroundMonitorConfig(transform: (BackgroundMonitorConfig) -> BackgroundMonitorConfig) {
        val updated = transform(_uiState.value.monitorConfig)
        _uiState.update { it.copy(monitorConfig = updated) }
        viewModelScope.launch {
            prefsStore.saveBackgroundMonitorConfig(updated)
            runCatching {
                CatalystMonitorScheduler.syncSchedule(getApplication(), updated)
            }
        }
    }

    fun runBackgroundCheckNow() {
        _uiState.update { it.copy(isBackgroundCheckRunning = true) }
        runCatching {
            CatalystMonitorScheduler.runImmediateCheck(getApplication())
        }.onFailure {
            _uiState.update { it.copy(isBackgroundCheckRunning = false) }
        }
    }

    fun analyzeTickers(explicitTickers: List<String>? = null) {
        val targetTickers = (explicitTickers ?: _uiState.value.trackedTickers)
            .ifEmpty { TickerCatalystEngine.DEFAULT_TICKERS }

        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = if (it.reports.isEmpty()) CatalystServiceStatus.Loading else it.status,
                    isRefreshing = true,
                    warningMessage = null
                )
            }
            try {
                val result = service.analyzeTickers(
                    tickers = targetTickers,
                    config = latestConfig,
                    fallbackHeadlines = latestHeadlines
                )
                val summary = TickerCatalystEngine.summarize(result.reports)
                _uiState.update { current ->
                    current.copy(
                        status = CatalystServiceStatus.Success,
                        reports = result.reports,
                        summary = summary,
                        scorerLabel = result.scorerLabel,
                        usedLlm = result.usedLlm,
                        headlineSourceLabel = result.headlineSourceLabel,
                        lastUpdatedMs = result.fetchedAtMs,
                        warningMessage = result.warning,
                        isRefreshing = false,
                        providerRuns = result.providerRuns,
                        activeAiCount = result.activeAiCount,
                        totalImplementedAiCount = result.totalImplementedAiCount,
                        expandedSymbol = current.expandedSymbol
                            ?: result.reports.firstOrNull { it.headlines.isNotEmpty() }?.symbol
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        status = CatalystServiceStatus.Error(e.message ?: "Unable to fetch market headlines."),
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
