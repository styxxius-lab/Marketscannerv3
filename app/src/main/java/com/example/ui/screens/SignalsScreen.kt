package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.engine.CouncilDecision
import com.example.domain.engine.QuantScannerUiState
import com.example.domain.engine.VixMonitorState
import com.example.domain.model.BotState
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.MarketMode
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSector
import com.example.domain.model.PriceAlert
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalFilter
import com.example.domain.model.SignalRow
import com.example.domain.model.SignalSort
import com.example.domain.model.VixSnapshot
import com.example.ui.WatchlistUiState
import com.example.ui.components.ActionChip
import com.example.ui.components.BannerMessage
import com.example.ui.components.CouncilLauncherCard
import com.example.ui.components.CryptoDerivsGuardCard
import com.example.ui.components.FinnhubWatchlistCard
import com.example.ui.components.MarketDashboard
import com.example.ui.components.MarketModeSwapBar
import com.example.ui.components.MarketRegimeCard
import com.example.ui.components.MarketRegimeDashboard
import com.example.ui.components.MarketRegimeHeatmapCard
import com.example.ui.components.MiniMetric
import com.example.ui.components.QuantEquitySignalScanner
import com.example.ui.components.SimulationFooterNotice
import com.example.ui.components.TagChip
import com.example.ui.components.TerminalCard
import com.example.ui.components.TrackedSymbolsDashboard
import com.example.ui.components.VixVolatilityMonitorCard
import com.example.ui.components.fmtMoney
import com.example.ui.components.fmtNum
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SignalsSubTab {
    SCANNER,
    WATCHLIST,
    REGIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsScreen(
    rows: List<SignalRow>,
    regime: MarketRegime,
    source: String,
    watchlist: List<String>,
    filter: SignalFilter,
    selectedSector: MarketSector?,
    sort: SignalSort,
    searchQuery: String,
    vixSnapshot: VixSnapshot?,
    vixMonitorState: VixMonitorState,
    vixAlertEnabled: Boolean,
    vixThresholdPct: Double,
    hasNotificationPermission: Boolean,
    onSelectFilter: (SignalFilter) -> Unit,
    onSelectSector: (MarketSector?) -> Unit,
    onSelectSort: (SignalSort) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddWatchlistTicker: (String) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onOpenSignal: (String) -> Unit,
    onToggleVixAlert: (Boolean) -> Unit,
    onUpdateVixThreshold: (Double) -> Unit,
    onRearmVixAlert: () -> Unit,
    onSimulateVixSpike: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    heldSymbols: Set<String> = emptySet(),
    advisorEnabled: Boolean = false,
    advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
    advisorCheckingSymbol: String? = null,
    onCheckAdvisorVeto: (String) -> Unit = {},
    quantScannerState: QuantScannerUiState = QuantScannerUiState(),
    onQuantScannerInputChange: (String) -> Unit = {},
    onRunQuantSignalScan: (String?) -> Unit = {},
    onClearQuantScannerScope: () -> Unit = {},
    onToggleQuantScannerLiveStream: (Boolean) -> Unit = {},
    onSelectQuantScannerActionFilter: (SignalAction?) -> Unit = {},
    onSelectQuantScannerMinConviction: (Double) -> Unit = {},
    onOpenTickerIndicators: () -> Unit = {},
    councilMemberCount: Int = 0,
    councilFamilyCount: Int = 0,
    councilLedgerCount: Int = 0,
    councilLastDecision: CouncilDecision? = null,
    onOpenCouncil: () -> Unit = {},
    watchlistUiState: WatchlistUiState = WatchlistUiState(),
    onWatchlistTickerInputChange: (String) -> Unit = {},
    onAddWatchlistFavorites: () -> Unit = {},
    onQuickAddWatchlistFavorite: (String) -> Unit = {},
    onRemoveWatchlistFavorite: (String) -> Unit = {},
    onRefreshWatchlistQuotes: () -> Unit = {},
    onToggleWatchlistAutoRefresh: (Boolean) -> Unit = {},
    onOpenFinnhubSettings: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    marketMode: MarketMode = MarketMode.STOCKS,
    stockEquity: Double = 10.0,
    cryptoEquity: Double = 10.0,
    onSelectMarketMode: (MarketMode) -> Unit = {},
    cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    cryptoFundingSampleCount: Int = 0,
    alerts: List<PriceAlert> = emptyList(),
    signalAlerts: List<SignalChangeAlert> = emptyList(),
    aiNewsAlertsEnabled: Boolean = false,
    onOpenSetAlert: (String?) -> Unit = {},
    onShowDisclaimer: () -> Unit = {},
    statusText: String = "",
    botState: BotState = BotState(),
    onNavigateToPortfolio: () -> Unit = {},
    onNavigateToTrade: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubTab by rememberSaveable { mutableStateOf(SignalsSubTab.SCANNER) }
    val cleanQuery = searchQuery.trim().uppercase()
    val watchlistSet = remember(watchlist) { watchlist.map { it.uppercase() }.toSet() }
    val sectorCounts = remember(rows) { rows.groupingBy { it.sector }.eachCount() }
    val availableSectors = remember(sectorCounts) {
        MarketSector.entries.filter { (sectorCounts[it] ?: 0) > 0 }
    }
    val filteredRows = remember(rows, watchlistSet, filter, selectedSector, sort, cleanQuery) {
        val base = when (filter) {
            SignalFilter.ALL -> rows
            SignalFilter.WATCHLIST -> rows.filter { watchlistSet.contains(it.symbol.uppercase()) }
            SignalFilter.BUY -> rows.filter { it.action == SignalAction.BUY }
            SignalFilter.SELL -> rows.filter { it.action == SignalAction.SELL }
        }
        val sectorFiltered = if (selectedSector == null) {
            base
        } else {
            base.filter { it.sector == selectedSector }
        }
        val searched = if (cleanQuery.isBlank()) sectorFiltered
        else sectorFiltered.filter { it.symbol.contains(cleanQuery, ignoreCase = true) }

        when (sort) {
            SignalSort.CONVICTION -> searched.sortedByDescending { it.conviction }
            SignalSort.RR_NET -> searched.sortedByDescending { it.cost?.rrNet ?: it.rr }
            SignalSort.CONFIDENCE -> searched.sortedByDescending { it.confidence }
            SignalSort.CHANGE_PCT -> searched.sortedByDescending { it.rs20 }
        }
    }
    val buys = remember(rows) { rows.count { it.action == SignalAction.BUY } }
    val sells = remember(rows) { rows.count { it.action == SignalAction.SELL } }
    val holds = rows.size - buys - sells
    val canAddTicker = cleanQuery.isNotEmpty() &&
        cleanQuery.length <= 12 &&
        cleanQuery.all { it.isLetterOrDigit() || it == '.' || it == '/' || it == '-' } &&
        rows.none { it.symbol.equals(cleanQuery, ignoreCase = true) }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("dashboard_pull_refresh_indicator")
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("signals_pull_refresh_box")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("signals_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        item(key = "signals_market_mode_bar") {
            MarketModeSwapBar(
                marketMode = marketMode,
                stockEquity = stockEquity,
                cryptoEquity = cryptoEquity,
                onSelectMarketMode = onSelectMarketMode,
                testTagPrefix = "signals_market_swap"
            )
        }

        item(key = "market_dashboard") {
            MarketDashboard(
                regime = regime,
                rows = rows,
                botState = botState,
                statusText = statusText,
                source = source,
                isScanning = isRefreshing,
                marketMode = marketMode,
                onRefresh = onRefresh,
                onOpenRegime = { activeSubTab = SignalsSubTab.REGIME },
                onOpenScanner = { activeSubTab = SignalsSubTab.SCANNER },
                onOpenSignal = onOpenSignal,
                onNavigateToPortfolio = onNavigateToPortfolio,
                onNavigateToTrade = onNavigateToTrade
            )
        }

        item(key = "signals_subtab_bar") {
            SignalsSubTabBar(
                selected = activeSubTab,
                watchlistCount = watchlistUiState.favorites.size.coerceAtLeast(watchlist.size),
                regimeLabel = regime.label,
                onSelect = { activeSubTab = it }
            )
        }

        when (activeSubTab) {
            SignalsSubTab.WATCHLIST -> {
                item(key = "finnhub_favorite_watchlist") {
                    FinnhubWatchlistCard(
                        uiState = watchlistUiState,
                        onTickerInputChange = onWatchlistTickerInputChange,
                        onAddTickers = onAddWatchlistFavorites,
                        onQuickAddTicker = onQuickAddWatchlistFavorite,
                        onRemoveTicker = onRemoveWatchlistFavorite,
                        onRefreshQuotes = onRefreshWatchlistQuotes,
                        onToggleAutoRefresh = onToggleWatchlistAutoRefresh,
                        onOpenTicker = onOpenSignal,
                        onOpenFinnhubSettings = onOpenFinnhubSettings
                    )
                }

                item(key = "tracked_symbols_dashboard") {
                    TrackedSymbolsDashboard(
                        rows = rows,
                        regime = regime,
                        watchlist = watchlist,
                        heldSymbols = heldSymbols,
                        vixSnapshot = vixSnapshot,
                        advisorEnabled = advisorEnabled,
                        advisorVerdicts = advisorVerdicts,
                        advisorCheckingSymbol = advisorCheckingSymbol,
                        onOpenSymbol = onOpenSignal,
                        onToggleWatchlist = onToggleWatchlist,
                        onCheckAdvisorVeto = onCheckAdvisorVeto
                    )
                }
            }

            SignalsSubTab.REGIME -> {
                item(key = "market_regime_dashboard") {
                    MarketRegimeDashboard(
                        regime = regime,
                        rows = rows,
                        vixSnapshot = vixSnapshot
                    )
                }

                item(key = "regime") {
                    MarketRegimeCard(regime = regime)
                }

                item(key = "regime_heatmap") {
                    MarketRegimeHeatmapCard(regime = regime)
                }

                if (marketMode == MarketMode.CRYPTO) {
                    item(key = "signals_crypto_derivs_guard") {
                        CryptoDerivsGuardCard(
                            derivsBySymbol = cryptoDerivsBySymbol,
                            guardsBySymbol = cryptoGuardsBySymbol,
                            fundingSampleCount = cryptoFundingSampleCount
                        )
                    }
                } else {
                    item(key = "vix_volatility_monitor") {
                        VixVolatilityMonitorCard(
                            vixSnapshot = vixSnapshot,
                            monitorState = vixMonitorState,
                            alertEnabled = vixAlertEnabled,
                            thresholdPct = vixThresholdPct,
                            hasNotificationPermission = hasNotificationPermission,
                            onToggleEnabled = onToggleVixAlert,
                            onUpdateThreshold = onUpdateVixThreshold,
                            onRearm = onRearmVixAlert,
                            onSimulateSpike = onSimulateVixSpike,
                            onRequestNotificationPermission = onRequestNotificationPermission
                        )
                    }
                }
            }

            SignalsSubTab.SCANNER -> {
                // Compact side-by-side Quick Launchers (Indicators + AI Council)
                item(key = "signals_quick_tools_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToolCard(
                            icon = Icons.Filled.QueryStats,
                            badge = stringResource(R.string.ticker_launcher_badge),
                            title = "Indicators",
                            subtitle = "RSI · MACD · MA",
                            onClick = onOpenTickerIndicators,
                            testTag = "ticker_indicators_launcher_card",
                            modifier = Modifier.weight(1f)
                        )
                        QuickToolCard(
                            icon = Icons.Outlined.Groups,
                            badge = if (councilMemberCount > 0) "${councilMemberCount} AI" else "COUNCIL",
                            title = "AI Council",
                            subtitle = councilLastDecision?.let { "${it.symbol} · ${it.action}" }
                                ?: "$councilFamilyCount families",
                            onClick = onOpenCouncil,
                            testTag = "council_launcher_quick_card",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "quant_equity_signal_scanner") {
                    QuantEquitySignalScanner(
                        scannerState = quantScannerState,
                        rows = rows,
                        regime = regime,
                        source = source,
                        onTickerInputChange = onQuantScannerInputChange,
                        onRunScan = onRunQuantSignalScan,
                        onClearScope = onClearQuantScannerScope,
                        onToggleLiveStream = onToggleQuantScannerLiveStream,
                        onSelectActionFilter = onSelectQuantScannerActionFilter,
                        onSelectMinConviction = onSelectQuantScannerMinConviction,
                        onOpenSignal = onOpenSignal,
                        alerts = alerts,
                        signalAlerts = signalAlerts,
                        aiNewsAlertsEnabled = aiNewsAlertsEnabled,
                        onOpenSetAlert = onOpenSetAlert
                    )
                }

                if (marketMode == MarketMode.CRYPTO) {
                    item(key = "crypto_feed_banner") {
                        BannerMessage(
                            text = if (source == "live") stringResource(R.string.banner_crypto_public_feed)
                            else stringResource(R.string.banner_crypto_demo_fallback),
                            isWarning = source != "live"
                        )
                    }
                } else if (source == "demo") {
                    item(key = "demo_banner") {
                        BannerMessage(
                            text = stringResource(R.string.banner_demo_mode),
                            isWarning = false
                        )
                    }
                }

                item(key = "summary_card") {
                    TerminalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStat(
                                count = rows.size,
                                label = stringResource(R.string.label_analysed),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStat(
                                count = buys,
                                label = stringResource(R.string.label_buy),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStat(
                                count = sells,
                                label = stringResource(R.string.label_sell),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStat(
                                count = holds,
                                label = stringResource(R.string.label_hold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item(key = "search_bar") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = "Search ticker or add to watchlist…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search ticker",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ticker_search_input")
                    )
                }

                if (canAddTicker) {
                    item(key = "quick_add_watchlist") {
                        Button(
                            onClick = { onAddWatchlistTicker(cleanQuery) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("quick_add_watchlist_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "ADD $cleanQuery TO WATCHLIST & RESCAN",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                item(key = "segmented_filter") {
                    SignalFilterBar(
                        selected = filter,
                        onSelect = onSelectFilter
                    )
                }

                if (rows.isNotEmpty()) {
                    item(key = "sector_dropdown") {
                        SectorFilterDropdown(
                            selectedSector = selectedSector,
                            availableSectors = availableSectors,
                            sectorCounts = sectorCounts,
                            totalCount = rows.size,
                            onSelectSector = onSelectSector
                        )
                    }
                }

                item(key = "sort_bar") {
                    SignalSortBar(
                        selected = sort,
                        onSelect = onSelectSort
                    )
                }

                if (filteredRows.isEmpty()) {
                    item(key = "empty_signals") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterListOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_signals_title),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.empty_signals_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredRows, key = { it.symbol }) { signal ->
                        val hasActiveAlert = alerts.any { it.active && it.symbol.equals(signal.symbol, ignoreCase = true) } ||
                            signalAlerts.any { it.enabled && it.symbol.equals(signal.symbol, ignoreCase = true) }
                        SignalCard(
                            signal = signal,
                            regime = regime,
                            advisorVerdict = advisorVerdicts[signal.symbol],
                            isWatchlisted = watchlistSet.contains(signal.symbol.uppercase()),
                            hasActiveAlert = hasActiveAlert,
                            onToggleWatchlist = { onToggleWatchlist(signal.symbol) },
                            onSetAlert = { onOpenSetAlert(signal.symbol) },
                            onClick = { onOpenSignal(signal.symbol) }
                        )
                    }
                }
            }
        }

        item(key = "signals_sim_footer") {
            SimulationFooterNotice(onClick = onShowDisclaimer)
        }
    }
    }
}

@Composable
private fun SignalsSubTabBar(
    selected: SignalsSubTab,
    watchlistCount: Int,
    regimeLabel: RegimeLabel,
    onSelect: (SignalsSubTab) -> Unit
) {
    val tabs = listOf(
        Triple(SignalsSubTab.SCANNER, stringResource(R.string.signals_subtab_scanner), Icons.Outlined.Analytics),
        Triple(
            SignalsSubTab.WATCHLIST,
            if (watchlistCount > 0) "${stringResource(R.string.signals_subtab_watchlist)} ($watchlistCount)"
            else stringResource(R.string.signals_subtab_watchlist),
            Icons.Outlined.StarOutline
        ),
        Triple(
            SignalsSubTab.REGIME,
            stringResource(R.string.signals_subtab_regime),
            Icons.Outlined.ShowChart
        )
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .testTag("signals_subtab_bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { (tab, label, icon) ->
            val active = selected == tab
            val accent = if (tab == SignalsSubTab.REGIME) {
                when (regimeLabel) {
                    RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
                    RegimeLabel.RISK_OFF, RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
                    RegimeLabel.NEUTRAL -> MaterialTheme.colorScheme.primary
                }
            } else {
                MaterialTheme.colorScheme.primary
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 6.dp)
                    .testTag("signals_subtab_${tab.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = label,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$badge · $subtitle",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SummaryStat(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            color = color,
            style = MonoTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SignalFilterBar(
    selected: SignalFilter,
    onSelect: (SignalFilter) -> Unit
) {
    val items = listOf(
        SignalFilter.ALL to stringResource(R.string.filter_all),
        SignalFilter.WATCHLIST to stringResource(R.string.filter_watchlist),
        SignalFilter.BUY to stringResource(R.string.filter_buy),
        SignalFilter.SELL to stringResource(R.string.filter_sell)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (filter, label) ->
            val active = selected == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
                    .clickable { onSelect(filter) }
                    .testTag("filter_${filter.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SignalSortBar(
    selected: SignalSort,
    onSelect: (SignalSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SORT",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
        SignalSort.entries.forEach { sortOption ->
            val active = selected == sortOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(sortOption) }
                    .testTag("sort_${sortOption.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sortOption.label,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SectorFilterDropdown(
    selectedSector: MarketSector?,
    availableSectors: List<MarketSector>,
    sectorCounts: Map<MarketSector, Int>,
    totalCount: Int,
    onSelectSector: (MarketSector?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedSector != null
    val activeLabel = if (selectedSector != null) {
        "${selectedSector.label} (${sectorCounts[selectedSector] ?: 0})"
    } else {
        "All Sectors ($totalCount)"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SECTOR",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isFiltered) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (isFiltered) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("sector_filter_dropdown"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Category,
                        contentDescription = null,
                        tint = if (isFiltered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = activeLabel,
                        color = if (isFiltered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFiltered) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectSector(null) }
                                .padding(2.dp)
                                .testTag("sector_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear sector filter",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Select sector",
                        tint = if (isFiltered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("sector_dropdown_menu")
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "All Sectors ($totalCount)",
                                color = if (selectedSector == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selectedSector == null) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    },
                    trailingIcon = {
                        if (selectedSector == null) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectSector(null)
                        expanded = false
                    },
                    modifier = Modifier.testTag("sector_option_all")
                )

                availableSectors.forEach { sector ->
                    val count = sectorCounts[sector] ?: 0
                    val isSelected = selectedSector == sector
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${sector.label} ($count)",
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            onSelectSector(sector)
                            expanded = false
                        },
                        modifier = Modifier.testTag("sector_option_${sector.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalCard(
    signal: SignalRow,
    regime: MarketRegime,
    advisorVerdict: AdvisorVetoVerdict?,
    isWatchlisted: Boolean,
    hasActiveAlert: Boolean,
    onToggleWatchlist: () -> Unit,
    onSetAlert: () -> Unit,
    onClick: () -> Unit
) {
    val actionColor = when (signal.action) {
        SignalAction.BUY -> MaterialTheme.colorScheme.secondary
        SignalAction.SELL -> MaterialTheme.colorScheme.error
        SignalAction.HOLD -> SignalHold
    }
    val confFraction = signal.confidence.coerceIn(0.0, 1.0).toFloat()
    val earningsDays = signal.event?.daysToEarnings
    val showEarnings = earningsDays != null && earningsDays <= 2
    val aiScore = ((signal.newsDirection.coerceIn(-1.0, 1.0) * signal.newsMateriality.coerceIn(0.0, 1.0)) * 100.0).roundToInt()
    val hasAiNewsScore = signal.newsItems.isNotEmpty() || signal.newsMateriality > 0.05 || abs(signal.newsDirection) > 0.05
    val regimeChipColor = when {
        regime.panicGuardActive -> MaterialTheme.colorScheme.error
        regime.label == RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
        regime.label == RegimeLabel.RISK_OFF || regime.label == RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (advisorVerdict?.isVeto == true) MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signal_card_${signal.symbol}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = signal.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ActionChip(action = signal.action)
                    if (advisorVerdict?.isVeto == true) {
                        TagChip(
                            text = "AI VETO ${(advisorVerdict.weight * 100).roundToInt()}%",
                            fg = MaterialTheme.colorScheme.error,
                            bg = MaterialTheme.colorScheme.errorContainer
                        )
                    } else if (hasAiNewsScore) {
                        val aiColor = when {
                            aiScore >= 15 -> MaterialTheme.colorScheme.secondary
                            aiScore <= -15 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        val sign = if (aiScore > 0) "+" else ""
                        TagChip(
                            text = "AI $sign$aiScore",
                            fg = aiColor,
                            bg = aiColor.copy(alpha = 0.14f)
                        )
                    } else if (showEarnings) {
                        TagChip(
                            text = "EARN ${earningsDays}d",
                            fg = MaterialTheme.colorScheme.tertiary,
                            bg = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = fmtMoney(signal.price),
                        style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                    IconButton(
                        onClick = onSetAlert,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("signal_card_set_alert_${signal.symbol}")
                    ) {
                        Icon(
                            imageVector = if (hasActiveAlert) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                            contentDescription = stringResource(R.string.quant_scanner_set_alert_desc, signal.symbol),
                            tint = if (hasActiveAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("watchlist_star_${signal.symbol}")
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isWatchlisted) {
                                "Remove ${signal.symbol} from watchlist"
                            } else {
                                "Add ${signal.symbol} to watchlist"
                            },
                            tint = if (isWatchlisted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(confFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(actionColor)
                    )
                }
                Text(
                    text = "${(signal.confidence * 100.0).roundToInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 11.sp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMetric(
                    label = stringResource(R.string.label_stop),
                    value = fmtMoney(signal.stop),
                    valueColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.label_target),
                    value = fmtMoney(signal.target),
                    valueColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.label_rr_net),
                    value = fmtNum(signal.cost?.rrNet ?: signal.rr, 2),
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.label_conv),
                    value = fmtNum(signal.conviction, 2),
                    valueColor = actionColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagChip(
                    text = if (regime.panicGuardActive) "PANIC 0.5×" else regime.label.name,
                    fg = regimeChipColor,
                    bg = regimeChipColor.copy(alpha = 0.14f)
                )
                TagChip(
                    text = signal.sector.label.uppercase(),
                    fg = MaterialTheme.colorScheme.primary,
                    bg = MaterialTheme.colorScheme.primaryContainer
                )
                if (signal.keyCatalyst.isNotBlank()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Newspaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = signal.keyCatalyst,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = signal.reasons.take(2).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
