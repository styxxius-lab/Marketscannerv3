package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.domain.engine.CouncilEngine
import com.example.domain.engine.PaperBotEngine
import com.example.domain.model.MainTab
import com.example.domain.model.MarketMode
import com.example.domain.model.ScanRegimeSnapshot
import com.example.ui.components.AiModelHubSheet
import com.example.ui.components.PriceAlertsSheet
import com.example.ui.components.SignalDetailSheet
import com.example.ui.components.SignalsHeaderRegimeBar
import com.example.ui.components.SimulationDisclaimerDialog
import com.example.ui.components.ThresholdAlertSettingsSheet
import com.example.ui.components.TopBarMarketModeChip
import com.example.ui.screens.CouncilScreen
import com.example.ui.screens.NewsScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TickerIndicatorsScreen
import com.example.ui.screens.TradeScreen

private data class TabItem(
    val tab: MainTab,
    val labelRes: Int,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TAB_ITEMS = listOf(
    TabItem(
        tab = MainTab.SIGNALS,
        labelRes = R.string.tab_signals,
        titleRes = R.string.title_signals,
        selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
        testTag = "tab_signals"
    ),
    TabItem(
        tab = MainTab.PORTFOLIO,
        labelRes = R.string.tab_portfolio,
        titleRes = R.string.title_portfolio,
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
        testTag = "tab_portfolio"
    ),
    TabItem(
        tab = MainTab.TRADE,
        labelRes = R.string.tab_trade,
        titleRes = R.string.title_trade,
        selectedIcon = Icons.Filled.MonitorHeart,
        unselectedIcon = Icons.Outlined.MonitorHeart,
        testTag = "tab_trade"
    ),
    TabItem(
        tab = MainTab.NEWS,
        labelRes = R.string.tab_news,
        titleRes = R.string.title_news,
        selectedIcon = Icons.Filled.Newspaper,
        unselectedIcon = Icons.Outlined.Newspaper,
        testTag = "tab_news"
    ),
    TabItem(
        tab = MainTab.SETUP,
        labelRes = R.string.tab_setup,
        titleRes = R.string.title_setup,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "tab_setup"
    )
)

@Composable
fun MarketScanApp(
    requestedTab: MainTab? = null,
    onConsumedRequestedTab: () -> Unit = {},
    viewModel: MarketScanViewModel = viewModel(),
    simulatorViewModel: SimulatorViewModel = viewModel(
        factory = SimulatorViewModel.factory(LocalContext.current)
    ),
    tickerIndicatorsViewModel: TickerIndicatorsViewModel = viewModel(),
    councilViewModel: CouncilViewModel = viewModel(),
    marketCatalystViewModel: MarketCatalystViewModel = viewModel(),
    watchlistViewModel: WatchlistViewModel = viewModel(),
    paperHoldingsViewModel: PaperHoldingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val simulatorUiState by simulatorViewModel.uiState.collectAsStateWithLifecycle()
    val tickerIndicatorsUiState by tickerIndicatorsViewModel.uiState.collectAsStateWithLifecycle()
    val councilUiState by councilViewModel.uiState.collectAsStateWithLifecycle()
    val catalystUiState by marketCatalystViewModel.uiState.collectAsStateWithLifecycle()
    val watchlistUiState by watchlistViewModel.uiState.collectAsStateWithLifecycle()
    val paperHoldingsUiState by paperHoldingsViewModel.uiState.collectAsStateWithLifecycle()
    var showTickerIndicatorsScreen by remember { mutableStateOf(false) }
    var showCouncilScreen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.config, state.barsBySymbol) {
        tickerIndicatorsViewModel.syncEnvironment(
            config = state.config,
            barsBySymbol = state.barsBySymbol
        )
    }

    LaunchedEffect(
        state.config,
        state.rows,
        state.barsBySymbol,
        state.regime,
        state.botState,
        state.news,
        state.radarModels
    ) {
        councilViewModel.syncMarketContext(
            config = state.config,
            rows = state.rows,
            barsBySymbol = state.barsBySymbol,
            regime = state.regime,
            botState = state.botState,
            headlines = state.news,
            radarModels = state.radarModels
        )
    }

    LaunchedEffect(
        state.barsBySymbol,
        state.rows,
        state.news,
        state.botState,
        state.config
    ) {
        simulatorViewModel.syncMarketSnapshot(
            barsBySymbol = state.barsBySymbol,
            rows = state.rows,
            headlines = state.news,
            botState = state.botState,
            config = state.config
        )
    }

    LaunchedEffect(state.config, state.rows, state.news) {
        marketCatalystViewModel.syncEnvironment(
            config = state.config,
            rows = state.rows,
            headlines = state.news
        )
    }

    LaunchedEffect(state.config) {
        watchlistViewModel.syncConfig(state.config)
    }

    LaunchedEffect(state.config, state.rows, state.barsBySymbol) {
        paperHoldingsViewModel.syncMarketEnvironment(
            config = state.config,
            scannerRows = state.rows,
            barsBySymbol = state.barsBySymbol
        )
    }

    LaunchedEffect(state.activeTab) {
        if (state.activeTab == MainTab.NEWS) {
            marketCatalystViewModel.ensureInitialLoad()
        }
    }

    var hasNotifPerm by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            viewModel.selectTab(requestedTab)
            onConsumedRequestedTab()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(
        enabled = !showTickerIndicatorsScreen &&
            !showCouncilScreen &&
            state.activeTab != MainTab.SIGNALS &&
            state.selectedSignal == null
    ) {
        viewModel.selectTab(MainTab.SIGNALS)
    }

    val currentTabMeta = remember(state.activeTab) {
        TAB_ITEMS.first { it.tab == state.activeTab }
    }
    val activeAlertCount = remember(state.alerts, state.signalAlerts, state.config.vixAlertEnabled) {
        state.alerts.count { it.active } +
            state.signalAlerts.count { it.enabled } +
            (if (state.config.vixAlertEnabled) 1 else 0)
    }
    val thresholdAlertsActive = remember(state.config.thresholdAlerts) {
        val ta = state.config.thresholdAlerts
        (ta.regimeAlertsEnabled && (ta.watchedRegimes.isNotEmpty() || ta.alertOnPanicGuard)) ||
            ta.aiNewsAlertsEnabled
    }
    val knownAnalysisSymbols = remember(state.rows) {
        state.rows.map { it.symbol }.toSet()
    }
    val councilFamilyCount = remember(councilUiState.members) {
        councilUiState.members.map { CouncilEngine.family(it.substringAfter(":")) }.toSet().size
    }
    val councilQuickSymbols = remember(
        state.botState.positions,
        state.config.activeWatchlist,
        state.rows,
        state.marketMode
    ) {
        val defaults = if (state.marketMode == MarketMode.CRYPTO) {
            listOf("BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD")
        } else {
            listOf("AAPL", "NVDA", "MSFT", "SPY")
        }
        (state.botState.positions.map { it.symbol } +
            state.config.activeWatchlist +
            state.rows.take(10).map { it.symbol } +
            defaults)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val stockEquity = remember(state.stockBotState) {
        PaperBotEngine.liveEquity(state.stockBotState)
    }
    val cryptoEquity = remember(state.cryptoBotState) {
        PaperBotEngine.liveEquity(state.cryptoBotState)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                MarketTopBar(
                    title = stringResource(currentTabMeta.titleRes),
                    statusText = state.statusText,
                    isScanning = state.isScanning,
                    marketMode = state.marketMode,
                    activeAlertCount = activeAlertCount,
                    thresholdAlertsActive = thresholdAlertsActive,
                    showRegimeTimeline = state.activeTab == MainTab.SIGNALS,
                    scanRegimeHistory = state.scanRegimeHistory,
                    onToggleMarketMode = viewModel::toggleMarketMode,
                    onOpenTickerIndicators = { showTickerIndicatorsScreen = true },
                    onOpenAlerts = { viewModel.openAlertsSheet(null) },
                    onOpenSettings = viewModel::openThresholdSettingsSheet,
                    onRescan = { viewModel.rescan(quiet = false) }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        tonalElevation = 0.dp
                    ) {
                        TAB_ITEMS.forEach { item ->
                            val selected = state.activeTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        TAB_ITEMS.forEach { item ->
                            val selected = state.activeTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)

                    Crossfade(
                        targetState = state.activeTab,
                        label = "tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            MainTab.SIGNALS -> SignalsScreen(
                                rows = state.rows,
                                regime = state.regime,
                                source = state.source,
                                watchlist = state.config.activeWatchlist,
                                filter = state.filter,
                                selectedSector = state.selectedSector,
                                sort = state.sort,
                                searchQuery = state.searchQuery,
                                vixSnapshot = state.vixSnapshot,
                                vixMonitorState = state.vixMonitorState,
                                vixAlertEnabled = state.config.vixAlertEnabled,
                                vixThresholdPct = state.config.vixSpikeThresholdPct,
                                hasNotificationPermission = hasNotifPerm,
                                onSelectFilter = viewModel::selectFilter,
                                onSelectSector = viewModel::selectSector,
                                onSelectSort = viewModel::selectSort,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onAddWatchlistTicker = viewModel::addTickerToWatchlist,
                                onToggleWatchlist = viewModel::toggleWatchlistTicker,
                                onOpenSignal = viewModel::openSignalDetail,
                                onToggleVixAlert = { enabled ->
                                    if (enabled) requestNotificationPermissionIfNeeded()
                                    viewModel.setVixAlertEnabled(enabled)
                                },
                                onUpdateVixThreshold = { pct ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.updateVixSpikeThreshold(pct)
                                },
                                onRearmVixAlert = viewModel::rearmVixAlert,
                                onSimulateVixSpike = viewModel::triggerTestVixSpike,
                                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                                heldSymbols = state.botState.positions.map { it.symbol }.toSet(),
                                advisorEnabled = state.advisorEnabled,
                                advisorVerdicts = state.advisorVerdicts,
                                advisorCheckingSymbol = state.advisorCheckingSymbol,
                                onCheckAdvisorVeto = { sym -> viewModel.checkAdvisorVeto(sym) },
                                quantScannerState = state.quantScannerState,
                                onQuantScannerInputChange = viewModel::updateQuantScannerInput,
                                onRunQuantSignalScan = viewModel::runQuantSignalScan,
                                onClearQuantScannerScope = viewModel::clearQuantScannerScope,
                                onToggleQuantScannerLiveStream = viewModel::toggleQuantScannerLiveStream,
                                onSelectQuantScannerActionFilter = viewModel::setQuantScannerActionFilter,
                                onSelectQuantScannerMinConviction = viewModel::setQuantScannerMinConviction,
                                onOpenTickerIndicators = { showTickerIndicatorsScreen = true },
                                councilMemberCount = councilUiState.members.size,
                                councilFamilyCount = councilFamilyCount,
                                councilLedgerCount = councilUiState.ledger.size,
                                councilLastDecision = councilUiState.latestDecision,
                                onOpenCouncil = { showCouncilScreen = true },
                                watchlistUiState = watchlistUiState,
                                onWatchlistTickerInputChange = watchlistViewModel::updateTickerInput,
                                onAddWatchlistFavorites = watchlistViewModel::addFavoritesFromInput,
                                onQuickAddWatchlistFavorite = watchlistViewModel::quickAddTicker,
                                onRemoveWatchlistFavorite = watchlistViewModel::removeFavorite,
                                onRefreshWatchlistQuotes = { watchlistViewModel.refreshQuotes() },
                                onToggleWatchlistAutoRefresh = watchlistViewModel::toggleAutoRefresh,
                                onOpenFinnhubSettings = viewModel::openThresholdSettingsSheet,
                                isRefreshing = state.isScanning,
                                onRefresh = {
                                    viewModel.refreshDashboard()
                                    watchlistViewModel.refreshQuotes()
                                },
                                marketMode = state.marketMode,
                                stockEquity = stockEquity,
                                cryptoEquity = cryptoEquity,
                                onSelectMarketMode = viewModel::switchMarketMode,
                                cryptoDerivsBySymbol = state.cryptoDerivsBySymbol,
                                cryptoGuardsBySymbol = state.cryptoGuardsBySymbol,
                                cryptoFundingSampleCount = state.cryptoFundingSampleCount,
                                alerts = state.alerts,
                                signalAlerts = state.signalAlerts,
                                aiNewsAlertsEnabled = state.config.thresholdAlerts.aiNewsAlertsEnabled,
                                onOpenSetAlert = { sym -> viewModel.openAlertsSheet(sym) },
                                onShowDisclaimer = viewModel::openSimulationDisclaimer,
                                statusText = state.statusText,
                                botState = state.botState,
                                onNavigateToPortfolio = { viewModel.selectTab(MainTab.PORTFOLIO) },
                                onNavigateToTrade = { viewModel.selectTab(MainTab.TRADE) },
                                modifier = contentModifier
                            )

                            MainTab.PORTFOLIO -> PortfolioScreen(
                                botState = state.botState,
                                rows = state.rows,
                                source = state.source,
                                threatsBySymbol = state.threatsBySymbol,
                                assessingThreatSymbols = state.assessingThreatSymbols,
                                onOpenSignal = viewModel::openSignalDetail,
                                onClosePosition = viewModel::closePositionNow,
                                onTrimPosition = viewModel::trimPosition,
                                onApplySuggestedStop = viewModel::applySuggestedStop,
                                onAssessThreat = viewModel::assessThreat,
                                onNavigateToTrade = { viewModel.selectTab(MainTab.TRADE) },
                                onNavigateToSignals = { viewModel.selectTab(MainTab.SIGNALS) },
                                watchlistSymbols = state.config.activeWatchlist,
                                paperHoldingsUiState = paperHoldingsUiState,
                                onTogglePaperHoldingsForm = paperHoldingsViewModel::toggleFormExpanded,
                                onPaperHoldingSymbolChange = paperHoldingsViewModel::updateSymbolInput,
                                onSelectPaperHoldingQuickSymbol = paperHoldingsViewModel::selectQuickSymbol,
                                onPaperHoldingSharesChange = paperHoldingsViewModel::updateSharesInput,
                                onPaperHoldingBuyPriceChange = paperHoldingsViewModel::updatePurchasePriceInput,
                                onPaperHoldingNotesChange = paperHoldingsViewModel::updateNotesInput,
                                onPrefillPaperHoldingLivePrice = paperHoldingsViewModel::prefillDummyPriceFromLive,
                                onSavePaperHolding = paperHoldingsViewModel::saveHolding,
                                onCancelPaperHoldingEdit = paperHoldingsViewModel::cancelEdit,
                                onEditPaperHolding = paperHoldingsViewModel::startEditHolding,
                                onDeletePaperHolding = paperHoldingsViewModel::deleteHolding,
                                onSeedDummyPaperHoldings = paperHoldingsViewModel::seedDummyHoldings,
                                onClearAllPaperHoldings = paperHoldingsViewModel::clearAllHoldings,
                                onRefreshPaperHoldingsQuotes = { paperHoldingsViewModel.refreshQuotes(silent = false) },
                                onRefreshPaperDailyPnlHistory = paperHoldingsViewModel::refreshDailyHistory,
                                onTogglePaperHoldingsAutoRefresh = paperHoldingsViewModel::toggleAutoRefresh,
                                marketMode = state.marketMode,
                                stockEquity = stockEquity,
                                cryptoEquity = cryptoEquity,
                                onSelectMarketMode = viewModel::switchMarketMode,
                                cryptoDerivsBySymbol = state.cryptoDerivsBySymbol,
                                cryptoGuardsBySymbol = state.cryptoGuardsBySymbol,
                                cryptoFundingSampleCount = state.cryptoFundingSampleCount,
                                modifier = contentModifier
                            )

                            MainTab.TRADE -> TradeScreen(
                                botState = state.botState,
                                rows = state.rows,
                                regime = state.regime,
                                headlines = state.news,
                                config = state.config,
                                simulatorUiState = simulatorUiState,
                                tradeNotificationsEnabled = state.config.tradeNotificationsEnabled,
                                hasNotificationPermission = hasNotifPerm,
                                onToggleBot = {
                                    if (!state.botState.running && state.config.tradeNotificationsEnabled) {
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    viewModel.toggleBotRunning()
                                },
                                onResetBot = viewModel::resetBot,
                                onToggleTradeNotifications = { enabled ->
                                    if (enabled) {
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    viewModel.setTradeNotificationsEnabled(enabled)
                                },
                                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                                onExportFeedback = viewModel::notifyMessage,
                                onSaveSizingDefaults = viewModel::updateAccountAndRisk,
                                onSimulateSizedBuy = viewModel::openSizedPosition,
                                onThreatSymbolInputChange = simulatorViewModel::updateSymbolInput,
                                onScanThreatSymbol = simulatorViewModel::scanSymbol,
                                onToggleThreatLiveMonitoring = simulatorViewModel::toggleLiveMonitoring,
                                onSelectRecentThreatCard = simulatorViewModel::selectRecentCard,
                                onExitPosition = viewModel::closePositionNow,
                                onTrimPosition = viewModel::trimPosition,
                                onApplySuggestedStop = viewModel::applySuggestedStop,
                                onSelectThreatHistoryFilter = simulatorViewModel::setHistorySymbolFilter,
                                onDeleteThreatHistoryEntry = simulatorViewModel::deleteHistoryEntry,
                                onClearThreatHistory = simulatorViewModel::clearThreatHistory,
                                advisorEnabled = state.advisorEnabled,
                                advisorVetoReady = state.advisorVetoReady,
                                advisorModel = state.advisorModel,
                                advisorCallsUsed = state.advisorCallsUsed,
                                advisorMaxCallsPerHour = state.advisorMaxCallsPerHour,
                                advisorVerdicts = state.advisorVerdicts,
                                advisorCheckingSymbol = state.advisorCheckingSymbol,
                                advisorJournal = state.advisorJournal,
                                advisorJournalLoading = state.advisorJournalLoading,
                                onCheckAdvisorVeto = { sym -> viewModel.checkAdvisorVeto(sym) },
                                onRunAdvisorJournal = viewModel::runAdvisorJournal,
                                councilMemberCount = councilUiState.members.size,
                                councilFamilyCount = councilFamilyCount,
                                councilLedgerCount = councilUiState.ledger.size,
                                councilLastDecision = councilUiState.latestDecision,
                                onOpenCouncil = { showCouncilScreen = true },
                                paperHoldingsUiState = paperHoldingsUiState,
                                onTogglePaperHoldingsForm = paperHoldingsViewModel::toggleFormExpanded,
                                onPaperHoldingSymbolChange = paperHoldingsViewModel::updateSymbolInput,
                                onSelectPaperHoldingQuickSymbol = paperHoldingsViewModel::selectQuickSymbol,
                                onPaperHoldingSharesChange = paperHoldingsViewModel::updateSharesInput,
                                onPaperHoldingBuyPriceChange = paperHoldingsViewModel::updatePurchasePriceInput,
                                onPaperHoldingNotesChange = paperHoldingsViewModel::updateNotesInput,
                                onPrefillPaperHoldingLivePrice = paperHoldingsViewModel::prefillDummyPriceFromLive,
                                onSavePaperHolding = paperHoldingsViewModel::saveHolding,
                                onCancelPaperHoldingEdit = paperHoldingsViewModel::cancelEdit,
                                onEditPaperHolding = paperHoldingsViewModel::startEditHolding,
                                onDeletePaperHolding = paperHoldingsViewModel::deleteHolding,
                                onSeedDummyPaperHoldings = paperHoldingsViewModel::seedDummyHoldings,
                                onClearAllPaperHoldings = paperHoldingsViewModel::clearAllHoldings,
                                onRefreshPaperHoldingsQuotes = { paperHoldingsViewModel.refreshQuotes(silent = false) },
                                onRefreshPaperDailyPnlHistory = paperHoldingsViewModel::refreshDailyHistory,
                                onTogglePaperHoldingsAutoRefresh = paperHoldingsViewModel::toggleAutoRefresh,
                                onOpenSignal = viewModel::openSignalDetail,
                                marketMode = state.marketMode,
                                stockEquity = stockEquity,
                                cryptoEquity = cryptoEquity,
                                onSelectMarketMode = viewModel::switchMarketMode,
                                onToggleMarketMode = viewModel::toggleMarketMode,
                                cryptoDerivsBySymbol = state.cryptoDerivsBySymbol,
                                cryptoGuardsBySymbol = state.cryptoGuardsBySymbol,
                                cryptoFundingSampleCount = state.cryptoFundingSampleCount,
                                cryptoTraderSnapshot = state.cryptoTraderSnapshot,
                                cryptoBacktestSummary = state.cryptoBacktestSummary,
                                cryptoBacktestRunning = state.cryptoBacktestRunning,
                                onRunCryptoBacktest = viewModel::runCryptoBacktest,
                                onRefreshCryptoPeg = viewModel::refreshCryptoPeg,
                                modifier = contentModifier
                            )

                            MainTab.NEWS -> NewsScreen(
                                rows = state.rows,
                                headlines = state.news,
                                source = state.source,
                                watchlist = state.config.activeWatchlist,
                                newsNotificationsEnabled = state.config.newsNotificationsEnabled,
                                hasNotificationPermission = hasNotifPerm,
                                onToggleNewsNotifications = { enabled ->
                                    if (enabled) {
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    viewModel.setNewsNotificationsEnabled(enabled)
                                },
                                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                                finnhubConfigured = state.finnhubConfigured,
                                finnhubNewsState = state.finnhubNewsState,
                                isRefreshingFinnhubNews = state.isRefreshingFinnhubNews,
                                newsFilter = state.newsFilter,
                                newsSearchQuery = state.newsSearchQuery,
                                onSelectNewsFilter = viewModel::selectNewsFilter,
                                onNewsSearchQueryChange = viewModel::updateNewsSearchQuery,
                                onRefreshFinnhubNews = {
                                    viewModel.refreshFinnhubNews(isManualRefresh = true)
                                    marketCatalystViewModel.analyzeTickers()
                                },
                                catalystUiState = catalystUiState,
                                onCatalystTickerInputChange = marketCatalystViewModel::updateTickerInput,
                                onSubmitCatalystTickers = marketCatalystViewModel::submitTickerInput,
                                onRefreshCatalystService = { marketCatalystViewModel.analyzeTickers() },
                                onSelectCatalystImpactFilter = marketCatalystViewModel::selectImpactFilter,
                                onToggleExpandCatalystTicker = marketCatalystViewModel::toggleExpandedSymbol,
                                onOpenAiHub = viewModel::openAiHubSheet,
                                onToggleBackgroundMonitor = { enabled ->
                                    if (enabled) requestNotificationPermissionIfNeeded()
                                    marketCatalystViewModel.updateBackgroundMonitorConfig { it.copy(enabled = enabled) }
                                },
                                onSelectBackgroundMonitorInterval = { minutes ->
                                    marketCatalystViewModel.updateBackgroundMonitorConfig { it.copy(intervalMinutes = minutes) }
                                },
                                onSelectBackgroundMonitorScoreThreshold = { scoreAbs ->
                                    marketCatalystViewModel.updateBackgroundMonitorConfig { it.copy(minCatalystScoreAbs = scoreAbs) }
                                },
                                onSelectBackgroundMonitorMoveThreshold = { movePct ->
                                    marketCatalystViewModel.updateBackgroundMonitorConfig { it.copy(minPriceMovePct = movePct) }
                                },
                                onToggleBackgroundMonitorWatchlistOnly = { watchOnly ->
                                    marketCatalystViewModel.updateBackgroundMonitorConfig { it.copy(watchlistOnly = watchOnly) }
                                },
                                onRunBackgroundCheckNow = {
                                    requestNotificationPermissionIfNeeded()
                                    marketCatalystViewModel.runBackgroundCheckNow()
                                },
                                marketMode = state.marketMode,
                                stockEquity = stockEquity,
                                cryptoEquity = cryptoEquity,
                                onSelectMarketMode = viewModel::switchMarketMode,
                                cryptoDerivsBySymbol = state.cryptoDerivsBySymbol,
                                cryptoGuardsBySymbol = state.cryptoGuardsBySymbol,
                                cryptoFundingSampleCount = state.cryptoFundingSampleCount,
                                modifier = contentModifier
                            )

                            MainTab.SETUP -> SetupScreen(
                                config = state.config,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onAddWatchlistTickers = viewModel::addTickerToWatchlist,
                                onRemoveWatchlistTicker = viewModel::removeTickerFromWatchlist,
                                onSaveAndRescan = viewModel::saveConfigAndRescan,
                                radarModels = state.radarModels,
                                onToggleAiProvider = viewModel::setAiProviderEnabled,
                                onSelectActiveAiProvider = viewModel::setActiveAiProvider,
                                onOpenAiModelHub = viewModel::openAiHubSheet,
                                currentRegime = state.regime.label,
                                hasNotificationPermission = hasNotifPerm,
                                recentThresholdAlerts = state.recentThresholdAlerts,
                                onUpdateThresholdAlerts = viewModel::updateThresholdAlertSettings,
                                onTestThresholdAlert = viewModel::triggerTestThresholdAlert,
                                onClearThresholdAlerts = viewModel::clearRecentThresholdAlerts,
                                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                                stockEquity = stockEquity,
                                cryptoEquity = cryptoEquity,
                                onSelectMarketMode = viewModel::switchMarketMode,
                                finnhubConfigured = state.finnhubConfigured,
                                advisorEnabled = state.advisorEnabled,
                                onShowDisclaimer = viewModel::openSimulationDisclaimer,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }

        if (showTickerIndicatorsScreen) {
            TickerIndicatorsScreen(
                uiState = tickerIndicatorsUiState,
                knownAnalysisSymbols = knownAnalysisSymbols,
                onStartPolling = tickerIndicatorsViewModel::startLivePolling,
                onStopPolling = tickerIndicatorsViewModel::stopLivePolling,
                onBack = { showTickerIndicatorsScreen = false },
                onRefresh = { tickerIndicatorsViewModel.refresh(isManual = true) },
                onToggleAutoRefresh = tickerIndicatorsViewModel::toggleAutoRefresh,
                onTickerInputChange = tickerIndicatorsViewModel::updateTickerInput,
                onAddTickers = tickerIndicatorsViewModel::addTickersFromInput,
                onRemoveTicker = tickerIndicatorsViewModel::removeTicker,
                onSelectIndicatorMode = tickerIndicatorsViewModel::selectIndicatorMode,
                onSelectDirectionFilter = tickerIndicatorsViewModel::selectDirectionFilter,
                onSelectSort = tickerIndicatorsViewModel::selectSort,
                onSearchQueryChange = tickerIndicatorsViewModel::updateSearchQuery,
                onToggleExpandRow = tickerIndicatorsViewModel::toggleExpanded,
                onOpenFullAnalysis = { sym -> viewModel.openSignalDetail(sym) }
            )
        }

        if (showCouncilScreen) {
            CouncilScreen(
                uiState = councilUiState,
                aiRouting = state.config.aiRouting,
                geminiFallbackKey = state.config.geminiKey,
                radarModels = state.radarModels,
                quickSymbols = councilQuickSymbols,
                heldSymbols = state.botState.positions.map { it.symbol.uppercase() }.toSet(),
                onBack = { showCouncilScreen = false },
                onSelectTab = councilViewModel::selectTab,
                onSelectSymbol = councilViewModel::selectSymbol,
                onSymbolInputChange = councilViewModel::updateSymbolInput,
                onDeliberate = councilViewModel::deliberate,
                onSelectMemberProvider = councilViewModel::updateMemberProvider,
                onMemberModelInputChange = councilViewModel::updateMemberModelInput,
                onAddMember = councilViewModel::addMember,
                onRemoveMember = councilViewModel::removeMember,
                onAutoBuildDiversePanel = councilViewModel::autoBuildDiversePanel,
                onOpenAiKeysHub = viewModel::openAiHubSheet,
                onSelectLedgerFilter = councilViewModel::selectLedgerFilter,
                onResolveOutcomes = councilViewModel::resolveLedgerOutcomes,
                onClearLedger = councilViewModel::clearLedger
            )
        }

        state.selectedSignal?.let { selected ->
            val symbolAlerts = remember(state.alerts, selected.symbol) {
                state.alerts.filter { it.symbol == selected.symbol }
            }
            val isWatchlisted = remember(state.config.activeWatchlist, selected.symbol) {
                state.config.activeWatchlist.any { it.equals(selected.symbol, ignoreCase = true) }
            }
            SignalDetailSheet(
                signal = selected,
                botState = state.botState,
                symbolAlerts = symbolAlerts,
                isWatchlisted = isWatchlisted,
                threatAssessment = state.threatsBySymbol[selected.symbol],
                isAssessingThreat = state.assessingThreatSymbols.contains(selected.symbol),
                onToggleWatchlist = viewModel::toggleWatchlistTicker,
                onPaperBuy = viewModel::openManualPosition,
                onRefreshThreat = viewModel::assessThreat,
                onClosePosition = viewModel::closePositionNow,
                onTrimPosition = viewModel::trimPosition,
                onApplySuggestedStop = viewModel::applySuggestedStop,
                onAddAlert = { sym, px, cond ->
                    requestNotificationPermissionIfNeeded()
                    viewModel.addPriceAlert(sym, px, cond)
                },
                onRemoveAlert = viewModel::removePriceAlert,
                onDismiss = viewModel::closeSignalDetail
            )
        }

        if (state.alertsSheetOpen) {
            PriceAlertsSheet(
                alerts = state.alerts,
                rows = state.rows,
                vixSnapshot = state.vixSnapshot,
                vixAlertEnabled = state.config.vixAlertEnabled,
                vixThresholdPct = state.config.vixSpikeThresholdPct,
                hasNotificationPermission = hasNotifPerm,
                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                onToggleVixAlert = viewModel::setVixAlertEnabled,
                onUpdateVixThreshold = viewModel::updateVixSpikeThreshold,
                onAddAlert = { sym, px, cond ->
                    requestNotificationPermissionIfNeeded()
                    viewModel.addPriceAlert(sym, px, cond)
                },
                onRemoveAlert = viewModel::removePriceAlert,
                onRearmAlert = viewModel::rearmPriceAlert,
                onClearTriggered = viewModel::clearTriggeredAlerts,
                onDismiss = viewModel::closeAlertsSheet,
                initialSymbol = state.alertSheetSymbol,
                signalAlerts = state.signalAlerts,
                aiNewsAlertsEnabled = state.config.thresholdAlerts.aiNewsAlertsEnabled,
                onTogglePriceAlert = { id ->
                    requestNotificationPermissionIfNeeded()
                    viewModel.togglePriceAlert(id)
                },
                onToggleSignalAlert = { sym, enabled, mode ->
                    if (enabled) requestNotificationPermissionIfNeeded()
                    viewModel.toggleSignalChangeAlert(sym, enabled, mode)
                },
                onUpdateSignalAlertMode = { sym, mode ->
                    requestNotificationPermissionIfNeeded()
                    viewModel.updateSignalAlertMode(sym, mode)
                },
                onRemoveSignalAlert = viewModel::removeSignalChangeAlert,
                onToggleGlobalAiNewsAlerts = { enabled ->
                    if (enabled) requestNotificationPermissionIfNeeded()
                    viewModel.toggleGlobalAiNewsAlerts(enabled)
                }
            )
        }

        if (state.thresholdSettingsSheetOpen) {
            ThresholdAlertSettingsSheet(
                themeMode = state.config.themeMode,
                onSelectThemeMode = viewModel::setThemeMode,
                aiRouting = state.config.aiRouting,
                geminiFallbackKey = state.config.geminiKey,
                radarModels = state.radarModels,
                onToggleAiProvider = viewModel::setAiProviderEnabled,
                onSelectActiveAiProvider = viewModel::setActiveAiProvider,
                onOpenAiModelHub = viewModel::openAiHubSheet,
                finnhubKey = state.config.finnhubKey,
                finnhubConfigured = state.finnhubConfigured,
                onSaveFinnhubKey = viewModel::updateFinnhubKey,
                settings = state.config.thresholdAlerts,
                currentRegime = state.regime.label,
                watchlistCount = state.config.activeWatchlist.size,
                hasNotificationPermission = hasNotifPerm,
                recentEvents = state.recentThresholdAlerts,
                onUpdateSettings = viewModel::updateThresholdAlertSettings,
                onTestAlert = viewModel::triggerTestThresholdAlert,
                onClearEvents = viewModel::clearRecentThresholdAlerts,
                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                onDismiss = viewModel::closeThresholdSettingsSheet
            )
        }

        if (state.aiHubSheetOpen) {
            AiModelHubSheet(
                aiRouting = state.config.aiRouting,
                geminiFallbackKey = state.config.geminiKey,
                radarModels = state.radarModels,
                radarSyncing = state.radarSyncing,
                radarLastSyncedMs = state.radarLastSyncedMs,
                radarErrors = state.radarErrors,
                tierFilter = state.radarTierFilter,
                providerFilter = state.radarProviderFilter,
                searchQuery = state.radarSearchQuery,
                probingModelKey = state.probingModelKey,
                probeResultsByKey = state.probeResultsByKey,
                onToggleProvider = viewModel::setAiProviderEnabled,
                onSelectActiveProvider = viewModel::setActiveAiProvider,
                onUpdateProviderApiKey = viewModel::updateAiProviderApiKey,
                onSyncCatalog = viewModel::syncModelCatalog,
                onSelectTierFilter = viewModel::selectRadarTierFilter,
                onSelectProviderFilter = viewModel::selectRadarProviderFilter,
                onSearchQueryChange = viewModel::updateRadarSearchQuery,
                onPickBestTier = viewModel::pickBestRadarModel,
                onSelectModel = viewModel::selectRadarModel,
                onProbeModel = viewModel::probeRadarModel,
                onDismiss = viewModel::closeAiHubSheet
            )
        }

        if (state.showSimulationDisclaimer) {
            SimulationDisclaimerDialog(
                config = state.config,
                finnhubConfigured = state.finnhubConfigured,
                advisorEnabled = state.advisorEnabled,
                hasNotificationPermission = hasNotifPerm,
                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                onOpenSetup = {
                    viewModel.acceptSimulationDisclaimer()
                    viewModel.selectTab(MainTab.SETUP)
                },
                onContinue = viewModel::acceptSimulationDisclaimer
            )
        }
    }
}

@Composable
private fun MarketTopBar(
    title: String,
    statusText: String,
    isScanning: Boolean,
    marketMode: MarketMode,
    activeAlertCount: Int,
    thresholdAlertsActive: Boolean,
    showRegimeTimeline: Boolean,
    scanRegimeHistory: List<ScanRegimeSnapshot>,
    onToggleMarketMode: () -> Unit,
    onOpenTickerIndicators: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TopBarMarketModeChip(
                marketMode = marketMode,
                onToggle = onToggleMarketMode
            )

            IconButton(
                onClick = onOpenTickerIndicators,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_ticker_indicators_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.QueryStats,
                    contentDescription = stringResource(R.string.ticker_open_button_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier.testTag("alerts_bell_container"),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = onOpenAlerts,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("alerts_bell_button")
                ) {
                    Icon(
                        imageVector = if (activeAlertCount > 0) Icons.Filled.NotificationsActive
                        else Icons.Outlined.NotificationsNone,
                        contentDescription = "Manage price alerts",
                        tint = if (activeAlertCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeAlertCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeAlertCount > 9) "9+" else activeAlertCount.toString(),
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.testTag("settings_menu_container"),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_menu_content_desc),
                        tint = if (thresholdAlertsActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (thresholdAlertsActive) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, end = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !isScanning, onClick = onRescan)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("rescan_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_rescan),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                )
            }
        }
        if (showRegimeTimeline && scanRegimeHistory.isNotEmpty()) {
            SignalsHeaderRegimeBar(history = scanRegimeHistory)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
