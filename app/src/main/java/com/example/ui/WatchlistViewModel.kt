package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WatchlistDataStore
import com.example.data.service.FinnhubWatchlistQuoteService
import com.example.data.service.WatchlistQuoteSnippet
import com.example.domain.model.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val favorites: List<String> = WatchlistDataStore.DEFAULT_FAVORITES,
    val quotesBySymbol: Map<String, WatchlistQuoteSnippet> = emptyMap(),
    val tickerInput: String = "",
    val isLoading: Boolean = false,
    val autoRefreshEnabled: Boolean = true,
    val finnhubConfigured: Boolean = false,
    val finnhubConnected: Boolean = false,
    val feedLabel: String = "READY",
    val lastUpdatedMs: Long = 0L,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

class WatchlistViewModel(
    application: Application,
    private val quoteService: FinnhubWatchlistQuoteService = FinnhubWatchlistQuoteService()
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, FinnhubWatchlistQuoteService())

    private val dataStore = WatchlistDataStore(application.applicationContext)

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    private var userFinnhubKey: String = ""
    private var hasSeeded = false
    private var fetchJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            dataStore.observeFavorites().collectLatest { favs ->
                _uiState.update { it.copy(favorites = favs) }
                refreshQuotes(explicitFavorites = favs)
            }
        }
        viewModelScope.launch {
            dataStore.observeAutoRefresh().collectLatest { enabled ->
                _uiState.update { it.copy(autoRefreshEnabled = enabled) }
                if (enabled) startPolling() else stopPolling()
            }
        }
    }

    fun syncConfig(config: AppConfig) {
        val newKey = config.finnhubKey.trim()
        val keyChanged = newKey != userFinnhubKey
        userFinnhubKey = newKey
        val configured = FinnhubWatchlistQuoteService.resolveFinnhubToken(newKey).isNotBlank()
        _uiState.update { it.copy(finnhubConfigured = configured) }

        if (!hasSeeded) {
            hasSeeded = true
            viewModelScope.launch {
                dataStore.seedIfUninitialized(config.watchlist)
            }
        } else if (keyChanged) {
            refreshQuotes()
        }
    }

    fun updateTickerInput(value: String) {
        _uiState.update { it.copy(tickerInput = value, errorMessage = null) }
    }

    fun addFavoritesFromInput() {
        val raw = _uiState.value.tickerInput.trim()
        if (raw.isEmpty()) return
        val parsed = WatchlistDataStore.parseTickers(raw)
        if (parsed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid ticker symbol (e.g. NVDA, AAPL, BRK.B).") }
            return
        }
        _uiState.update { it.copy(tickerInput = "", errorMessage = null) }
        viewModelScope.launch {
            dataStore.addTickers(parsed.joinToString(","))
        }
    }

    fun quickAddTicker(symbol: String) {
        viewModelScope.launch {
            dataStore.addTickers(symbol)
        }
    }

    fun removeFavorite(symbol: String) {
        viewModelScope.launch {
            dataStore.removeTicker(symbol)
        }
    }

    fun toggleAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAutoRefresh(enabled)
        }
    }

    fun refreshQuotes(explicitFavorites: List<String>? = null) {
        val targets = explicitFavorites ?: _uiState.value.favorites
        fetchJob?.cancel()
        if (targets.isEmpty()) {
            _uiState.update {
                it.copy(
                    quotesBySymbol = emptyMap(),
                    isLoading = false,
                    feedLabel = "EMPTY",
                    warningMessage = null
                )
            }
            return
        }

        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = quoteService.fetchQuotes(
                    symbols = targets,
                    userFinnhubKey = userFinnhubKey
                )
                _uiState.update {
                    it.copy(
                        quotesBySymbol = result.quotesBySymbol,
                        isLoading = false,
                        finnhubConnected = result.finnhubConnected,
                        feedLabel = result.feedLabel,
                        lastUpdatedMs = result.fetchedAtMs,
                        warningMessage = result.warning
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unable to fetch real-time quotes."
                    )
                }
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_uiState.value.favorites.isNotEmpty() && !_uiState.value.isLoading) {
                    refreshQuotes()
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val POLL_INTERVAL_MS = 15_000L
    }
}
