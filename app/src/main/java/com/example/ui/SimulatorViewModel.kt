package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.MarketScanDatabase
import com.example.data.local.ThreatHistoryEntry
import com.example.data.repository.InMemoryThreatHistoryStore
import com.example.data.repository.MarketRepository
import com.example.data.repository.RoomThreatHistoryRepository
import com.example.data.repository.ThreatHistoryStore
import com.example.domain.engine.QuantEngine
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.BotState
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalRow
import com.example.engine.ThreatCard
import com.example.engine.ThreatContext
import com.example.engine.ThreatEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface ThreatScanState {
    data object Idle : ThreatScanState
    data class Scanning(val symbol: String) : ThreatScanState
    data class Success(val card: ThreatCard) : ThreatScanState
    data class Error(val symbol: String, val message: String) : ThreatScanState
}

data class SimulatorUiState(
    val symbolInput: String = "NVDA",
    val activeSymbol: String? = null,
    val liveMonitoringEnabled: Boolean = true,
    val scanState: ThreatScanState = ThreatScanState.Idle,
    val recentCards: List<ThreatCard> = emptyList(),
    val threatHistory: List<ThreatHistoryEntry> = emptyList(),
    val historySymbolFilter: String? = null,
    val lastUpdatedMs: Long? = null
)

/**
 * Bridges [ThreatEngine] and local Room [ThreatHistoryStore] with the Simulator UI so users can
 * scan any ticker on demand, observe real-time risk recommendations, and browse past ThreatCard logs.
 */
class SimulatorViewModel(
    private val repository: MarketRepository = MarketRepository(),
    private val threatEngine: ThreatEngine = ThreatEngine(),
    private val historyStore: ThreatHistoryStore = InMemoryThreatHistoryStore(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private data class MarketSnapshot(
        val barsBySymbol: Map<String, BarSeries> = emptyMap(),
        val rows: List<SignalRow> = emptyList(),
        val headlines: List<NewsHeadline> = emptyList(),
        val botState: BotState = BotState(),
        val config: AppConfig = AppConfig()
    )

    private var marketSnapshot = MarketSnapshot()
    private val cachedCustomBars = HashMap<String, BarSeries>()
    private var activeScanJob: Job? = null

    private val _uiState = MutableStateFlow(SimulatorUiState())
    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyStore.observeHistory(limit = 100)
                .flowOn(ioDispatcher)
                .collect { entries ->
                    _uiState.update { state ->
                        val activeFilter = state.historySymbolFilter
                        val filterStillRelevant = activeFilter == null ||
                            entries.any { it.symbol.equals(activeFilter, ignoreCase = true) }
                        state.copy(
                            threatHistory = entries,
                            historySymbolFilter = if (filterStillRelevant) activeFilter else null
                        )
                    }
                }
        }
    }

    fun updateSymbolInput(raw: String) {
        val cleaned = sanitizeSymbol(raw)
        _uiState.update { it.copy(symbolInput = cleaned) }
    }

    fun toggleLiveMonitoring(enabled: Boolean) {
        _uiState.update { it.copy(liveMonitoringEnabled = enabled) }
        if (enabled) {
            recomputeActiveCardRealtime()
        }
    }

    fun selectRecentCard(card: ThreatCard) {
        _uiState.update {
            it.copy(
                symbolInput = card.symbol,
                activeSymbol = card.symbol,
                scanState = ThreatScanState.Success(card),
                lastUpdatedMs = card.timestampMs
            )
        }
        recomputeActiveCardRealtime()
    }

    fun setHistorySymbolFilter(symbol: String?) {
        val cleaned = symbol?.let(::sanitizeSymbol)?.takeIf { it.isNotEmpty() }
        _uiState.update { it.copy(historySymbolFilter = cleaned) }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            historyStore.deleteEntry(id)
        }
    }

    fun clearThreatHistory() {
        viewModelScope.launch(ioDispatcher) {
            val activeFilter = _uiState.value.historySymbolFilter
            if (activeFilter != null) {
                historyStore.deleteSymbolHistory(activeFilter)
            } else {
                historyStore.clearAll()
            }
        }
    }

    fun scanSymbol(rawSymbol: String = _uiState.value.symbolInput) {
        val sym = sanitizeSymbol(rawSymbol)
        if (sym.isEmpty()) {
            _uiState.update {
                it.copy(
                    scanState = ThreatScanState.Error(
                        symbol = "",
                        message = "Enter a valid ticker symbol (e.g. NVDA, SMCI, TSLA)"
                    )
                )
            }
            return
        }

        activeScanJob?.cancel()
        _uiState.update {
            it.copy(
                symbolInput = sym,
                activeSymbol = sym,
                scanState = ThreatScanState.Scanning(sym)
            )
        }

        activeScanJob = viewModelScope.launch {
            try {
                val snap = marketSnapshot
                val cachedBars = snap.barsBySymbol[sym] ?: cachedCustomBars[sym]
                val ctx = buildContextForSymbol(sym, snap)
                val pos = snap.botState.positions
                    .find { it.symbol.equals(sym, ignoreCase = true) }
                    ?.let { ThreatEngine.posFromTrade(it) }
                val livePrice = ctx.priceOverride

                val card = withContext(ioDispatcher) {
                    val bars = cachedBars ?: repository.resolveBarsForSymbol(sym, snap.config)?.also {
                        cachedCustomBars[sym] = it
                    } ?: return@withContext null
                    repository.assessThreatCard(
                        symbol = sym,
                        cachedBars = bars,
                        peerBars = ctx.peerBars,
                        benchBars = ctx.benchBars,
                        cachedHeadlines = snap.headlines,
                        position = pos,
                        price = livePrice,
                        cfg = snap.config
                    )
                }

                if (card == null) {
                    _uiState.update {
                        it.copy(
                            scanState = ThreatScanState.Error(
                                symbol = sym,
                                message = "Insufficient historical bars (need >= 60 daily candles) to assess $sym."
                            )
                        )
                    }
                    return@launch
                }

                recordSuccessCard(card, forceHistoryLog = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scanState = ThreatScanState.Error(
                            symbol = sym,
                            message = e.message ?: "Unable to complete threat scan for $sym"
                        )
                    )
                }
            }
        }
    }

    fun syncMarketSnapshot(
        barsBySymbol: Map<String, BarSeries>,
        rows: List<SignalRow>,
        headlines: List<NewsHeadline>,
        botState: BotState,
        config: AppConfig
    ) {
        marketSnapshot = MarketSnapshot(
            barsBySymbol = barsBySymbol,
            rows = rows,
            headlines = headlines,
            botState = botState,
            config = config
        )

        val current = _uiState.value
        // Run an initial default scan automatically once market data is ready
        if (current.activeSymbol == null && current.scanState is ThreatScanState.Idle && rows.isNotEmpty()) {
            val defaultSym = botState.positions.firstOrNull()?.symbol
                ?: rows.first().symbol
            scanSymbol(defaultSym)
            return
        }

        if (current.liveMonitoringEnabled && current.scanState is ThreatScanState.Success) {
            recomputeActiveCardRealtime()
        }
    }

    private fun recomputeActiveCardRealtime() {
        val currentSuccess = _uiState.value.scanState as? ThreatScanState.Success ?: return
        val existingCard = currentSuccess.card
        val sym = existingCard.symbol
        val snap = marketSnapshot
        val bars = snap.barsBySymbol[sym] ?: cachedCustomBars[sym] ?: return
        val ctx = buildContextForSymbol(sym, snap)
        val pos = snap.botState.positions
            .find { it.symbol.equals(sym, ignoreCase = true) }
            ?.let { ThreatEngine.posFromTrade(it) }
        val symHeadlines = snap.headlines.filter { it.sym.equals(sym, ignoreCase = true) }

        val updatedCard = threatEngine.assessWithNarrative(
            symbol = sym,
            bars = bars,
            headlines = symHeadlines,
            narrative = existingCard.narrative,
            position = pos,
            ctx = ctx
        ) ?: return

        recordSuccessCard(updatedCard, forceHistoryLog = false)
    }

    private fun recordSuccessCard(card: ThreatCard, forceHistoryLog: Boolean) {
        _uiState.update { state ->
            val updatedHistory = (listOf(card) + state.recentCards.filterNot { it.symbol == card.symbol })
                .take(6)
            state.copy(
                activeSymbol = card.symbol,
                scanState = ThreatScanState.Success(card),
                recentCards = updatedHistory,
                lastUpdatedMs = card.timestampMs
            )
        }
        viewModelScope.launch(ioDispatcher) {
            historyStore.recordScan(card, forceRecord = forceHistoryLog)
        }
    }

    private fun buildContextForSymbol(symbol: String, snap: MarketSnapshot): ThreatContext {
        val targetRow = snap.rows.find { it.symbol.equals(symbol, ignoreCase = true) }
        val sector = targetRow?.sector ?: QuantEngine.classifySector(symbol)
        val peerBars = snap.rows
            .asSequence()
            .filter { !it.symbol.equals(symbol, ignoreCase = true) && it.sector == sector }
            .take(8)
            .mapNotNull { snap.barsBySymbol[it.symbol] }
            .toList()
        val heldLast = snap.botState.positions
            .find { it.symbol.equals(symbol, ignoreCase = true) }
            ?.last
        return ThreatContext(
            benchBars = snap.barsBySymbol["SPY"],
            peerBars = peerBars,
            priceOverride = targetRow?.price ?: heldLast
        )
    }

    private fun sanitizeSymbol(raw: String): String =
        raw.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' }.take(10)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dao = MarketScanDatabase.getInstance(appContext).threatHistoryDao()
                    return SimulatorViewModel(
                        repository = MarketRepository(),
                        threatEngine = ThreatEngine(),
                        historyStore = RoomThreatHistoryRepository(dao)
                    ) as T
                }
            }
        }
    }
}
