package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DailyPnlSnapshotEntity
import com.example.data.local.MarketScanDatabase
import com.example.data.local.PaperAccountEntity
import com.example.data.local.PaperHoldingEntity
import com.example.data.local.PaperTradeExecutionEntity
import com.example.data.remote.YahooChartClient
import com.example.data.repository.PaperHoldingsRepository
import com.example.data.service.CryptoMarketService
import com.example.data.service.FinnhubWatchlistQuoteService
import com.example.data.service.WatchlistQuoteSnippet
import com.example.domain.engine.DailyPnlChartAnalytics
import com.example.domain.engine.PaperDailyPnlEngine
import com.example.domain.engine.PaperHoldingsPnlEngine
import com.example.domain.engine.PaperHoldingsPortfolioSummary
import com.example.domain.engine.PaperSimulatorMetrics
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.MarketMode
import com.example.domain.model.SignalRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PaperOrderSide(val label: String) {
    BUY("BUY"),
    SELL("SELL")
}

data class PaperHoldingsUiState(
    val holdings: List<PaperHoldingEntity> = emptyList(),
    val summary: PaperHoldingsPortfolioSummary = PaperHoldingsPortfolioSummary(),
    val account: PaperAccountEntity = PaperAccountEntity(),
    val simulatorMetrics: PaperSimulatorMetrics = PaperSimulatorMetrics(),
    val recentTrades: List<PaperTradeExecutionEntity> = emptyList(),
    val dailyPnlSnapshots: List<DailyPnlSnapshotEntity> = emptyList(),
    val dailyPnlAnalytics: DailyPnlChartAnalytics = DailyPnlChartAnalytics(),
    val isSyncingDailyHistory: Boolean = false,
    val quotesBySymbol: Map<String, WatchlistQuoteSnippet> = emptyMap(),
    val isRefreshingQuotes: Boolean = false,
    val autoRefreshEnabled: Boolean = true,
    val feedLabel: String = "ROOM DB",
    val finnhubConnected: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val warningMessage: String? = null,
    // Inline holdings card form state
    val formExpanded: Boolean = false,
    val editingHoldingId: Long? = null,
    val symbolInput: String = "",
    val sharesInput: String = "10",
    val purchasePriceInput: String = "",
    val notesInput: String = "",
    val formError: String? = null,
    val statusMessage: String? = null,
    val isFetchingPrefillPrice: Boolean = false,
    // Full Paper Trading Simulator order ticket state
    val orderSide: PaperOrderSide = PaperOrderSide.BUY,
    val orderSymbolInput: String = "",
    val orderSharesInput: String = "10",
    val orderPriceInput: String = "",
    val orderNotesInput: String = "",
    val orderError: String? = null,
    val isFetchingOrderPrice: Boolean = false
)

class PaperHoldingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MarketScanDatabase.getInstance(application)
    private val repository = PaperHoldingsRepository(
        dao = database.paperHoldingDao(),
        dailyPnlDao = database.dailyPnlSnapshotDao(),
        accountDao = database.paperAccountDao()
    )
    private val quoteService = FinnhubWatchlistQuoteService()
    private val cryptoService = CryptoMarketService()
    private val yahooClient = YahooChartClient(
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    )

    private val _uiState = MutableStateFlow(PaperHoldingsUiState())
    val uiState: StateFlow<PaperHoldingsUiState> = _uiState.asStateFlow()

    private var currentFinnhubKey: String = ""
    private var currentMarketMode: MarketMode = MarketMode.STOCKS
    private var latestScannerRows: List<SignalRow> = emptyList()
    private var latestBarsBySymbol: Map<String, BarSeries> = emptyMap()
    private val fetchedClosesCache = LinkedHashMap<String, List<Double>>()
    private var latestHoldings: List<PaperHoldingEntity> = emptyList()
    private var latestAccount: PaperAccountEntity = PaperAccountEntity()
    private var refreshJob: Job? = null
    private var historySyncJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureAccountInitialized()
        }

        // Observe Room simulated account (cash, starting capital, realized P&L)
        viewModelScope.launch {
            repository.accountFlow.collect { acct ->
                latestAccount = acct
                recomputeSummary()
            }
        }

        // Observe Room simulated trade execution ledger
        viewModelScope.launch {
            repository.recentTradesFlow.collect { trades ->
                _uiState.update { it.copy(recentTrades = trades) }
            }
        }
        // Observe Room 30-day daily P&L snapshots reactively
        viewModelScope.launch {
            repository.recentDailyPnlFlow.collect { snapshots ->
                val analytics = PaperDailyPnlEngine.summarize(snapshots)
                _uiState.update {
                    it.copy(
                        dailyPnlSnapshots = snapshots,
                        dailyPnlAnalytics = analytics
                    )
                }
            }
        }

        // Observe Room holdings reactively
        viewModelScope.launch {
            repository.holdingsFlow.collect { list ->
                val symbolsChanged = list.map { it.symbol } != latestHoldings.map { it.symbol }
                latestHoldings = list
                recomputeSummary()
                if (list.isEmpty()) {
                    stopPolling()
                    historySyncJob?.cancel()
                    repository.clearDailyPnlSnapshots()
                    _uiState.update {
                        it.copy(
                            feedLabel = "ROOM DB",
                            warningMessage = null,
                            isSyncingDailyHistory = false
                        )
                    }
                } else {
                    if (symbolsChanged || _uiState.value.quotesBySymbol.isEmpty()) {
                        refreshQuotes(silent = true)
                    } else {
                        rebuildAndPersistDailyPnlHistory(forceFetch = false)
                    }
                    if (_uiState.value.autoRefreshEnabled) {
                        ensurePolling()
                    }
                }
            }
        }
    }

    fun syncMarketEnvironment(
        config: AppConfig,
        scannerRows: List<SignalRow>,
        barsBySymbol: Map<String, BarSeries> = emptyMap()
    ) {
        val keyChanged = currentFinnhubKey != config.finnhubKey.trim()
        val barsExpanded = barsBySymbol.size != latestBarsBySymbol.size
        currentFinnhubKey = config.finnhubKey.trim()
        currentMarketMode = config.marketMode
        latestScannerRows = scannerRows
        if (barsBySymbol.isNotEmpty()) {
            latestBarsBySymbol = barsBySymbol
        }
        recomputeSummary()
        if (latestHoldings.isNotEmpty()) {
            if (keyChanged) {
                refreshQuotes(silent = true)
            } else if (barsExpanded && _uiState.value.dailyPnlSnapshots.isEmpty()) {
                rebuildAndPersistDailyPnlHistory(forceFetch = false)
            }
        }
    }

    fun toggleFormExpanded() {
        _uiState.update { state ->
            if (state.formExpanded) {
                state.copy(
                    formExpanded = false,
                    editingHoldingId = null,
                    formError = null
                )
            } else {
                state.copy(
                    formExpanded = true,
                    editingHoldingId = null,
                    formError = null
                )
            }
        }
    }

    fun startEditHolding(holding: PaperHoldingEntity) {
        _uiState.update {
            it.copy(
                formExpanded = true,
                editingHoldingId = holding.id,
                symbolInput = holding.symbol,
                sharesInput = formatCleanNumber(holding.shares),
                purchasePriceInput = String.format(Locale.US, "%.2f", holding.purchasePrice),
                notesInput = holding.notes,
                formError = null
            )
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                formExpanded = false,
                editingHoldingId = null,
                symbolInput = "",
                sharesInput = "10",
                purchasePriceInput = "",
                notesInput = "",
                formError = null
            )
        }
    }

    fun updateSymbolInput(value: String) {
        val sanitized = value.uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '/' || it == '$' }.take(12)
        _uiState.update { it.copy(symbolInput = sanitized, formError = null) }
    }

    fun selectQuickSymbol(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        val livePrice = findKnownLivePrice(clean)
        val prefillPrice = livePrice?.let { String.format(Locale.US, "%.2f", it * 0.97) }
            ?: _uiState.value.purchasePriceInput
        _uiState.update {
            it.copy(
                formExpanded = true,
                editingHoldingId = null,
                symbolInput = clean,
                purchasePriceInput = prefillPrice,
                formError = null
            )
        }
    }

    fun updateSharesInput(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }.take(12)
        _uiState.update { it.copy(sharesInput = cleaned, formError = null) }
    }

    fun updatePurchasePriceInput(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }.take(12)
        _uiState.update { it.copy(purchasePriceInput = cleaned, formError = null) }
    }

    fun updateNotesInput(value: String) {
        _uiState.update { it.copy(notesInput = value.take(48)) }
    }

    fun prefillDummyPriceFromLive() {
        val clean = PaperHoldingsRepository.normalizeSymbol(_uiState.value.symbolInput)
        if (clean == null) {
            _uiState.update { it.copy(formError = "Enter a valid ticker symbol first.") }
            return
        }
        val known = findKnownLivePrice(clean)
        if (known != null) {
            val dummy = known * 0.97
            _uiState.update {
                it.copy(
                    purchasePriceInput = String.format(Locale.US, "%.2f", dummy),
                    formError = null
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingPrefillPrice = true, formError = null) }
            val result = quoteService.fetchQuotes(listOf(clean), currentFinnhubKey)
            val live = result.quotesBySymbol[clean]?.currentPrice?.takeIf { it > 0.0 }
            if (live != null) {
                val mergedQuotes = _uiState.value.quotesBySymbol + result.quotesBySymbol
                _uiState.update {
                    it.copy(
                        quotesBySymbol = mergedQuotes,
                        purchasePriceInput = String.format(Locale.US, "%.2f", live * 0.97),
                        isFetchingPrefillPrice = false,
                        formError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFetchingPrefillPrice = false,
                        formError = "Could not fetch live quote for $clean — enter any dummy buy price manually."
                    )
                }
            }
        }
    }

    fun saveHolding() {
        val state = _uiState.value
        val shares = state.sharesInput.trim().toDoubleOrNull()
        val buyPrice = state.purchasePriceInput.trim().toDoubleOrNull()
        if (shares == null || shares <= 0.0) {
            _uiState.update { it.copy(formError = "Enter a valid share quantity greater than 0.") }
            return
        }
        if (buyPrice == null || buyPrice <= 0.0) {
            _uiState.update { it.copy(formError = "Enter a valid dummy purchase price greater than $0.") }
            return
        }

        val editId = state.editingHoldingId
        viewModelScope.launch {
            val result = if (editId != null) {
                repository.updateHolding(
                    id = editId,
                    shares = shares,
                    purchasePrice = buyPrice,
                    notes = state.notesInput
                )
            } else {
                repository.addOrMergeHolding(
                    rawSymbol = state.symbolInput,
                    shares = shares,
                    purchasePrice = buyPrice,
                    notes = state.notesInput
                )
            }

            result.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        formExpanded = false,
                        editingHoldingId = null,
                        symbolInput = "",
                        sharesInput = "10",
                        purchasePriceInput = "",
                        notesInput = "",
                        formError = null,
                        statusMessage = if (editId != null) {
                            "Updated ${saved.symbol} dummy entry in Room"
                        } else {
                            "Saved ${saved.symbol} paper holding to Room"
                        }
                    )
                }
                refreshQuotes(silent = true)
            }.onFailure { err ->
                _uiState.update { it.copy(formError = err.message ?: "Unable to save holding.") }
            }
        }
    }

    fun deleteHolding(id: Long) {
        viewModelScope.launch {
            repository.deleteHolding(id)
            _uiState.update {
                val clearedEdit = if (it.editingHoldingId == id) null else it.editingHoldingId
                it.copy(editingHoldingId = clearedEdit, statusMessage = "Closed paper holding in Room")
            }
        }
    }

    fun clearAllHoldings() {
        viewModelScope.launch {
            repository.clearAll()
            repository.clearDailyPnlSnapshots()
            _uiState.update {
                it.copy(
                    editingHoldingId = null,
                    formExpanded = false,
                    statusMessage = "Cleared all Room paper holdings"
                )
            }
        }
    }

    fun seedDummyHoldings() {
        viewModelScope.launch {
            val knownPrices = mutableMapOf<String, Double>()
            latestScannerRows.forEach { row ->
                if (row.price > 0.0) knownPrices[row.symbol.uppercase(Locale.US)] = row.price
            }
            _uiState.value.quotesBySymbol.forEach { (sym, quote) ->
                if (quote.currentPrice > 0.0) knownPrices[sym.uppercase(Locale.US)] = quote.currentPrice
            }
            repository.seedDummyHoldings(knownPrices)
            _uiState.update { it.copy(statusMessage = "Loaded 3 starter dummy lots into Room") }
            refreshQuotes(silent = false)
        }
    }

    fun toggleAutoRefresh() {
        val next = !_uiState.value.autoRefreshEnabled
        _uiState.update { it.copy(autoRefreshEnabled = next) }
        if (next && latestHoldings.isNotEmpty()) {
            ensurePolling()
        } else {
            stopPolling()
        }
    }

    fun refreshDailyHistory() {
        if (latestHoldings.isEmpty()) return
        rebuildAndPersistDailyPnlHistory(forceFetch = true)
    }

    // ── Paper Trading Simulator Order Ticket & Account Actions ─────────────────────────────

    fun selectOrderSide(side: PaperOrderSide) {
        _uiState.update { it.copy(orderSide = side, orderError = null) }
    }

    fun updateOrderSymbol(value: String) {
        val sanitized = value.uppercase(Locale.US)
            .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '/' || it == '$' }
            .take(12)
        _uiState.update { it.copy(orderSymbolInput = sanitized, orderError = null) }
    }

    fun selectQuickOrderSymbol(symbol: String, preferredSide: PaperOrderSide? = null) {
        val clean = symbol.trim().uppercase(Locale.US)
        val live = findKnownLivePrice(clean)
        val held = latestHoldings.firstOrNull { it.symbol.equals(clean, ignoreCase = true) }
        val side = preferredSide ?: _uiState.value.orderSide
        val suggestedShares = if (side == PaperOrderSide.SELL && held != null) {
            formatCleanNumber(held.shares)
        } else if (clean.contains("/") && live != null && live > 1000.0) {
            "0.05"
        } else {
            _uiState.value.orderSharesInput.ifBlank { "10" }
        }
        _uiState.update {
            it.copy(
                orderSide = side,
                orderSymbolInput = clean,
                orderSharesInput = suggestedShares,
                orderPriceInput = live?.let { px -> formatPriceForInput(px) } ?: it.orderPriceInput,
                orderError = null
            )
        }
        if (live == null) {
            fillOrderMarketPrice()
        }
    }

    fun updateOrderShares(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }.take(12)
        _uiState.update { it.copy(orderSharesInput = cleaned, orderError = null) }
    }

    fun updateOrderPrice(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }.take(12)
        _uiState.update { it.copy(orderPriceInput = cleaned, orderError = null) }
    }

    fun updateOrderNotes(value: String) {
        _uiState.update { it.copy(orderNotesInput = value.take(48)) }
    }

    fun fillOrderMarketPrice() {
        val clean = PaperHoldingsRepository.normalizeSymbol(_uiState.value.orderSymbolInput)
        if (clean == null) {
            _uiState.update { it.copy(orderError = "Enter a ticker symbol first (e.g. NVDA, BTC/USD).") }
            return
        }
        val known = findKnownLivePrice(clean)
        if (known != null) {
            _uiState.update {
                it.copy(orderPriceInput = formatPriceForInput(known), orderError = null)
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingOrderPrice = true, orderError = null) }
            val live = fetchSingleLivePrice(clean)
            if (live != null) {
                _uiState.update {
                    it.copy(
                        orderPriceInput = formatPriceForInput(live),
                        isFetchingOrderPrice = false,
                        orderError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFetchingOrderPrice = false,
                        orderError = "No live quote for $clean — enter a simulated fill price manually."
                    )
                }
            }
        }
    }

    fun fillOrderSizingFraction(fraction: Double) {
        val state = _uiState.value
        val pct = fraction.coerceIn(0.05, 1.0)
        if (state.orderSide == PaperOrderSide.SELL) {
            val clean = PaperHoldingsRepository.normalizeSymbol(state.orderSymbolInput) ?: return
            val held = latestHoldings.firstOrNull { it.symbol.equals(clean, ignoreCase = true) } ?: return
            val qty = held.shares * pct
            _uiState.update { it.copy(orderSharesInput = formatCleanNumber(qty), orderError = null) }
        } else {
            val price = state.orderPriceInput.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: PaperHoldingsRepository.normalizeSymbol(state.orderSymbolInput)?.let { findKnownLivePrice(it) }
                ?: return
            val budget = latestAccount.cashBalance * pct
            if (budget <= 0.0) return
            val rawQty = budget / price
            val formatted = if (state.orderSymbolInput.contains("/") || price >= 500.0) {
                formatCleanNumber(rawQty)
            } else {
                val whole = kotlin.math.floor(rawQty)
                if (whole >= 1.0) whole.toLong().toString() else formatCleanNumber(rawQty)
            }
            _uiState.update {
                it.copy(
                    orderSharesInput = formatted,
                    orderPriceInput = formatPriceForInput(price),
                    orderError = null
                )
            }
        }
    }

    fun submitSimulatedOrder() {
        val state = _uiState.value
        val shares = state.orderSharesInput.trim().toDoubleOrNull()
        val price = state.orderPriceInput.trim().toDoubleOrNull()
        if (shares == null || shares <= 0.0) {
            _uiState.update { it.copy(orderError = "Enter a valid quantity greater than 0.") }
            return
        }
        if (price == null || price <= 0.0) {
            _uiState.update { it.copy(orderError = "Enter a valid fill price greater than $0 (or tap LIVE PX).") }
            return
        }

        viewModelScope.launch {
            val result = if (state.orderSide == PaperOrderSide.BUY) {
                repository.executeSimulatedBuy(
                    rawSymbol = state.orderSymbolInput,
                    shares = shares,
                    executionPrice = price,
                    notes = state.orderNotesInput
                )
            } else {
                repository.executeSimulatedSell(
                    rawSymbol = state.orderSymbolInput,
                    sharesToSell = shares,
                    executionPrice = price,
                    notes = state.orderNotesInput
                )
            }

            result.onSuccess { exec ->
                val msg = if (exec.side == "BUY") {
                    "Simulated BUY ${formatCleanNumber(exec.shares)} ${exec.symbol} @ $${formatPriceForInput(exec.executionPrice)}"
                } else {
                    val pnl = exec.realizedPnl ?: 0.0
                    val sign = if (pnl >= 0.0) "+" else "-"
                    "Simulated SELL ${formatCleanNumber(exec.shares)} ${exec.symbol} (${sign}$${String.format(Locale.US, "%.2f", kotlin.math.abs(pnl))} realized)"
                }
                _uiState.update {
                    it.copy(
                        orderNotesInput = "",
                        orderError = null,
                        statusMessage = msg
                    )
                }
                refreshQuotes(silent = true)
            }.onFailure { err ->
                _uiState.update { it.copy(orderError = err.message ?: "Simulated order failed.") }
            }
        }
    }

    fun quickSellHolding(symbol: String, fraction: Double = 1.0) {
        val holding = latestHoldings.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: return
        val sellQty = (holding.shares * fraction.coerceIn(0.05, 1.0)).coerceAtMost(holding.shares)
        val marketPx = findKnownLivePrice(holding.symbol) ?: holding.purchasePrice
        viewModelScope.launch {
            val res = repository.executeSimulatedSell(
                rawSymbol = holding.symbol,
                sharesToSell = sellQty,
                executionPrice = marketPx,
                notes = if (fraction >= 0.99) "Closed simulated position" else "Trimmed ${(fraction * 100).toInt()}%"
            )
            res.onSuccess { exec ->
                val pnl = exec.realizedPnl ?: 0.0
                val sign = if (pnl >= 0.0) "+" else "-"
                _uiState.update {
                    it.copy(
                        statusMessage = "Sold ${formatCleanNumber(exec.shares)} ${exec.symbol} @ $${formatPriceForInput(exec.executionPrice)} (${sign}$${String.format(Locale.US, "%.2f", kotlin.math.abs(pnl))})"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(orderError = err.message) }
            }
        }
    }

    fun depositSimulatorCash(amount: Double = 5_000.0) {
        viewModelScope.launch {
            repository.depositCash(amount).onSuccess {
                _uiState.update {
                    it.copy(statusMessage = "Added $${String.format(Locale.US, "%,.0f", amount)} simulated cash to Room account")
                }
            }
        }
    }

    fun resetSimulatorAccount(startingCapital: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL) {
        viewModelScope.launch {
            repository.resetSimulatorAccount(startingCapital)
            _uiState.update {
                it.copy(
                    orderError = null,
                    formExpanded = false,
                    editingHoldingId = null,
                    statusMessage = "Reset Paper Simulator to $${String.format(Locale.US, "%,.0f", startingCapital)} cash"
                )
            }
        }
    }

    fun seedSimulatorStarterPortfolio() {
        viewModelScope.launch {
            val knownPrices = mutableMapOf<String, Double>()
            latestScannerRows.forEach { row ->
                if (row.price > 0.0) knownPrices[row.symbol.uppercase(Locale.US)] = row.price
            }
            _uiState.value.quotesBySymbol.forEach { (sym, quote) ->
                if (quote.currentPrice > 0.0) knownPrices[sym.uppercase(Locale.US)] = quote.currentPrice
            }
            repository.seedSimulatorStarterPortfolio(
                livePricesBySymbol = knownPrices,
                isCryptoMode = currentMarketMode == MarketMode.CRYPTO
            )
            _uiState.update { it.copy(statusMessage = "Loaded starter simulated portfolio into Room") }
            refreshQuotes(silent = false)
        }
    }

    private suspend fun fetchSingleLivePrice(symbol: String): Double? {
        return if (symbol.contains("/")) {
            val map = runCatching { cryptoService.fetchLatestCryptoPrices(listOf(symbol)) }.getOrDefault(emptyMap())
            val px = map[symbol]?.takeIf { it > 0.0 }
            if (px != null) {
                val snippet = WatchlistQuoteSnippet(
                    symbol = symbol,
                    currentPrice = px,
                    changeDollar = 0.0,
                    changePct = 0.0,
                    dayHigh = px,
                    dayLow = px,
                    dayOpen = px,
                    prevClose = px,
                    updatedAtMs = System.currentTimeMillis(),
                    sourceLabel = "KRAKEN 24/7",
                    isFinnhubLive = false
                )
                _uiState.update { it.copy(quotesBySymbol = it.quotesBySymbol + (symbol to snippet)) }
            }
            px
        } else {
            val result = quoteService.fetchQuotes(listOf(symbol), currentFinnhubKey)
            val live = result.quotesBySymbol[symbol]?.currentPrice?.takeIf { it > 0.0 }
            if (live != null) {
                _uiState.update { it.copy(quotesBySymbol = it.quotesBySymbol + result.quotesBySymbol) }
            }
            live
        }
    }

    fun refreshQuotes(silent: Boolean = false) {
        val symbols = latestHoldings.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isRefreshingQuotes = true) }
            }
            val cryptoSyms = symbols.filter { it.contains("/") }
            val stockSyms = symbols.filterNot { it.contains("/") }

            val mergedNew = LinkedHashMap<String, WatchlistQuoteSnippet>()
            var feedLabel = "ROOM DB"
            var finnhubConnected = false
            var warning: String? = null
            var fetchedAt = System.currentTimeMillis()

            if (stockSyms.isNotEmpty()) {
                val batch = quoteService.fetchQuotes(stockSyms, currentFinnhubKey)
                mergedNew.putAll(batch.quotesBySymbol)
                feedLabel = batch.feedLabel
                finnhubConnected = batch.finnhubConnected
                warning = batch.warning
                fetchedAt = batch.fetchedAtMs
            }

            if (cryptoSyms.isNotEmpty()) {
                val cryptoPrices = runCatching { cryptoService.fetchLatestCryptoPrices(cryptoSyms) }
                    .getOrDefault(emptyMap())
                cryptoPrices.forEach { (sym, px) ->
                    if (px > 0.0) {
                        val prev = _uiState.value.quotesBySymbol[sym]?.prevClose?.takeIf { it > 0.0 }
                            ?: latestScannerRows.firstOrNull { it.symbol.equals(sym, ignoreCase = true) }
                                ?.series?.dropLast(1)?.lastOrNull()?.takeIf { it > 0.0 }
                            ?: px
                        val chg = px - prev
                        val chgPct = if (prev > 0.0) (chg / prev) * 100.0 else 0.0
                        mergedNew[sym] = WatchlistQuoteSnippet(
                            symbol = sym,
                            currentPrice = px,
                            changeDollar = chg,
                            changePct = chgPct,
                            dayHigh = px,
                            dayLow = px,
                            dayOpen = prev,
                            prevClose = prev,
                            updatedAtMs = fetchedAt,
                            sourceLabel = "KRAKEN 24/7",
                            isFinnhubLive = false
                        )
                    }
                }
                if (stockSyms.isEmpty() && cryptoPrices.isNotEmpty()) {
                    feedLabel = "KRAKEN 24/7"
                }
            }

            _uiState.update { state ->
                val mergedQuotes = state.quotesBySymbol + mergedNew
                val summary = PaperHoldingsPnlEngine.evaluate(
                    holdings = latestHoldings,
                    quotesBySymbol = mergedQuotes,
                    scannerRows = latestScannerRows
                )
                val simMetrics = PaperHoldingsPnlEngine.evaluateSimulator(
                    account = latestAccount,
                    summary = summary
                )
                state.copy(
                    holdings = latestHoldings,
                    summary = summary,
                    account = latestAccount,
                    simulatorMetrics = simMetrics,
                    quotesBySymbol = mergedQuotes,
                    isRefreshingQuotes = false,
                    feedLabel = feedLabel,
                    finnhubConnected = finnhubConnected,
                    lastUpdatedMs = fetchedAt,
                    warningMessage = warning
                )
            }
            rebuildAndPersistDailyPnlHistory(forceFetch = !silent)
        }
    }

    /**
     * Reconstructs the last 30 days of simulated daily P&L for the current Room holdings using
     * real historical daily closes (from scanner bars + Yahoo Finance 3-month daily series) and
     * persists the snapshots into Room's `daily_pnl_snapshots` table.
     */
    private fun rebuildAndPersistDailyPnlHistory(forceFetch: Boolean) {
        val holdingsSnapshot = latestHoldings.toList()
        if (holdingsSnapshot.isEmpty()) return

        historySyncJob?.cancel()
        historySyncJob = viewModelScope.launch {
            _uiState.update { it.copy(isSyncingDailyHistory = true) }
            val closesMap = LinkedHashMap<String, List<Double>>()

            val symbols = holdingsSnapshot.map { it.symbol.trim().uppercase(Locale.US) }.distinct()
            val missingFromCache = mutableListOf<String>()

            symbols.forEach { sym ->
                val cached = fetchedClosesCache[sym]
                val scannerBars = latestBarsBySymbol[sym]?.c?.filter { it.isFinite() && it > 0.0 }
                val rowSeries = latestScannerRows.firstOrNull { it.symbol.equals(sym, ignoreCase = true) }
                    ?.series?.filter { it.isFinite() && it > 0.0 }

                when {
                    !forceFetch && cached != null && cached.size >= 31 -> closesMap[sym] = cached
                    !forceFetch && scannerBars != null && scannerBars.size >= 31 -> closesMap[sym] = scannerBars
                    else -> {
                        if (cached != null && cached.size >= 2) {
                            closesMap[sym] = cached
                        } else if (scannerBars != null && scannerBars.size >= 2) {
                            closesMap[sym] = scannerBars
                        } else if (rowSeries != null && rowSeries.size >= 2) {
                            closesMap[sym] = rowSeries
                        }
                        missingFromCache.add(sym)
                    }
                }
            }

            if (missingFromCache.isNotEmpty()) {
                val fetchedPairs = withContext(Dispatchers.IO) {
                    coroutineScope {
                        missingFromCache.map { sym ->
                            async {
                                try {
                                    val chart = yahooClient.fetchChart(symbol = sym, range = "3mo", interval = "1d")
                                    sym to chart?.closes?.filter { it.isFinite() && it > 0.0 }.orEmpty()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    sym to emptyList<Double>()
                                }
                            }
                        }.awaitAll()
                    }
                }
                fetchedPairs.forEach { (sym, closes) ->
                    if (closes.size >= 2) {
                        fetchedClosesCache[sym] = closes
                        closesMap[sym] = closes
                    }
                }
            }

            val snapshots = PaperDailyPnlEngine.build30DaySnapshots(
                holdings = holdingsSnapshot,
                closesBySymbol = closesMap,
                liveQuotesBySymbol = _uiState.value.quotesBySymbol,
                windowDays = PaperDailyPnlEngine.WINDOW_DAYS
            )
            repository.replaceDailyPnlWindow(snapshots)
            _uiState.update { it.copy(isSyncingDailyHistory = false) }
        }
    }

    private fun recomputeSummary() {
        _uiState.update { state ->
            val summary = PaperHoldingsPnlEngine.evaluate(
                holdings = latestHoldings,
                quotesBySymbol = state.quotesBySymbol,
                scannerRows = latestScannerRows
            )
            val simMetrics = PaperHoldingsPnlEngine.evaluateSimulator(
                account = latestAccount,
                summary = summary
            )
            state.copy(
                holdings = latestHoldings,
                summary = summary,
                account = latestAccount,
                simulatorMetrics = simMetrics
            )
        }
    }

    private fun findKnownLivePrice(symbol: String): Double? {
        val quotePx = _uiState.value.quotesBySymbol[symbol]?.currentPrice?.takeIf { it > 0.0 }
        if (quotePx != null) return quotePx
        return latestScannerRows.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            ?.price
            ?.takeIf { it > 0.0 }
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_uiState.value.autoRefreshEnabled && latestHoldings.isNotEmpty()) {
                    refreshQuotes(silent = true)
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        refreshJob?.cancel()
        historySyncJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 15_000L

        private fun formatCleanNumber(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
            }
        }

        private fun formatPriceForInput(price: Double): String {
            return if (price < 5.0) {
                String.format(Locale.US, "%.4f", price)
            } else {
                String.format(Locale.US, "%.2f", price)
            }
        }
    }
}
