package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MarketRepository
import com.example.domain.engine.IndicatorMode
import com.example.domain.engine.TickerDirectionFilter
import com.example.domain.engine.TickerIndicatorEngine
import com.example.domain.engine.TickerIndicatorQuote
import com.example.domain.engine.TickerSortField
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TickerFeedStatus {
    data object Loading : TickerFeedStatus
    data object Ready : TickerFeedStatus
    data class Error(val message: String) : TickerFeedStatus
}

data class TickerIndicatorsUiState(
    val status: TickerFeedStatus = TickerFeedStatus.Loading,
    val symbols: List<String> = TickerIndicatorEngine.DEFAULT_TICKERS,
    val quotes: List<TickerIndicatorQuote> = emptyList(),
    val indicatorMode: IndicatorMode = IndicatorMode.RSI_14,
    val directionFilter: TickerDirectionFilter = TickerDirectionFilter.ALL,
    val sortField: TickerSortField = TickerSortField.SIGNAL,
    val sortDescending: Boolean = true,
    val tickerInput: String = "",
    val searchQuery: String = "",
    val expandedSymbol: String? = null,
    val autoRefreshEnabled: Boolean = true,
    val isRefreshing: Boolean = false,
    val feedLabel: String = "LIVE MARKET API",
    val isLiveApi: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val warningMessage: String? = null
)

class TickerIndicatorsViewModel(
    private val repository: MarketRepository = MarketRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TickerIndicatorsUiState())
    val uiState: StateFlow<TickerIndicatorsUiState> = _uiState.asStateFlow()

    private var currentConfig: AppConfig = AppConfig()
    private var cachedBars: Map<String, BarSeries> = emptyMap()
    private var fetchJob: Job? = null
    private var pollJob: Job? = null
    private var seededFromWatchlist: Boolean = false

    fun syncEnvironment(config: AppConfig, barsBySymbol: Map<String, BarSeries>) {
        currentConfig = config
        cachedBars = barsBySymbol
        if (!seededFromWatchlist) {
            seededFromWatchlist = true
            val merged = (config.watchlist + TickerIndicatorEngine.DEFAULT_TICKERS)
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(16)
            _uiState.update { it.copy(symbols = merged) }
        }
    }

    fun startLivePolling() {
        if (_uiState.value.quotes.isEmpty()) {
            refresh(isManual = false)
        }
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                if (_uiState.value.autoRefreshEnabled && !_uiState.value.isRefreshing) {
                    refresh(isManual = false)
                }
            }
        }
    }

    fun stopLivePolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh(isManual: Boolean = true) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isRefreshing = true,
                    status = if (state.quotes.isEmpty()) TickerFeedStatus.Loading else state.status,
                    warningMessage = if (isManual) null else state.warningMessage
                )
            }
            try {
                val targetSymbols = _uiState.value.symbols
                val result = withContext(Dispatchers.IO) {
                    repository.fetchTickerIndicators(
                        symbols = targetSymbols,
                        cfg = currentConfig,
                        cachedBars = cachedBars
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        status = TickerFeedStatus.Ready,
                        quotes = result.quotes,
                        feedLabel = result.feedLabel,
                        isLiveApi = result.isLiveApi,
                        lastUpdatedMs = result.fetchedAtMs,
                        warningMessage = result.warning,
                        isRefreshing = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    val errMsg = e.message ?: "Unable to reach market data API."
                    state.copy(
                        status = if (state.quotes.isEmpty()) TickerFeedStatus.Error(errMsg) else TickerFeedStatus.Ready,
                        warningMessage = errMsg,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun updateTickerInput(input: String) {
        _uiState.update { it.copy(tickerInput = input) }
    }

    fun addTickersFromInput() {
        val parsed = TickerIndicatorEngine.parseSymbols(_uiState.value.tickerInput)
        if (parsed.isEmpty()) return
        val merged = (parsed + _uiState.value.symbols).distinct().take(24)
        _uiState.update {
            it.copy(
                symbols = merged,
                tickerInput = "",
                warningMessage = null
            )
        }
        refresh(isManual = true)
    }

    fun removeTicker(symbol: String) {
        val next = _uiState.value.symbols.filterNot { it.equals(symbol, ignoreCase = true) }
        if (next.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                symbols = next,
                quotes = state.quotes.filterNot { it.symbol.equals(symbol, ignoreCase = true) },
                expandedSymbol = if (state.expandedSymbol.equals(symbol, ignoreCase = true)) null else state.expandedSymbol
            )
        }
    }

    fun selectIndicatorMode(mode: IndicatorMode) {
        _uiState.update { it.copy(indicatorMode = mode) }
    }

    fun selectDirectionFilter(filter: TickerDirectionFilter) {
        _uiState.update { it.copy(directionFilter = filter) }
    }

    fun selectSort(field: TickerSortField) {
        _uiState.update { state ->
            val nextDesc = if (state.sortField == field) {
                !state.sortDescending
            } else {
                field != TickerSortField.SYMBOL
            }
            state.copy(sortField = field, sortDescending = nextDesc)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleExpanded(symbol: String) {
        _uiState.update { state ->
            val next = if (state.expandedSymbol == symbol) null else symbol
            state.copy(expandedSymbol = next)
        }
    }

    fun toggleAutoRefresh(enabled: Boolean) {
        _uiState.update { it.copy(autoRefreshEnabled = enabled) }
        if (enabled) {
            refresh(isManual = false)
        }
    }
}
