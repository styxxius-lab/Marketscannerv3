package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.AdvisorJournalReport
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.engine.CouncilDecision
import com.example.domain.engine.PaperBotEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.local.PaperHoldingEntity
import com.example.domain.model.AppConfig
import com.example.domain.model.BotState
import com.example.domain.model.CryptoBacktestSummary
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.CryptoTraderSnapshot
import com.example.domain.model.MarketMode
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.engine.ThreatCard
import com.example.ui.PaperHoldingsUiState
import com.example.ui.SimulatorUiState
import com.example.ui.components.AdvisorCard
import com.example.ui.components.AutotraderMarketSwapButton
import com.example.ui.components.BannerMessage
import com.example.ui.components.CouncilLauncherCard
import com.example.ui.components.CryptoDerivsGuardCard
import com.example.ui.components.CryptoTrader2DashboardCard
import com.example.ui.components.MarketModeSwapBar
import com.example.ui.components.MetricTile
import com.example.ui.components.MiniMetric
import com.example.ui.components.PaperDailyPnlChartCard
import com.example.ui.components.PaperEquityCurveDashboard
import com.example.ui.components.PaperHoldingsRoomCard
import com.example.ui.components.PositionSizingCalculatorCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SimulatorThreatScannerCard
import com.example.ui.components.TagChip
import com.example.ui.components.TerminalCard
import com.example.ui.components.ThreatHistoryCard
import com.example.ui.components.fmtMoney
import com.example.ui.components.fmtNum
import com.example.ui.components.fmtPctSigned
import com.example.ui.components.fmtSignedMoney
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TradeScreen(
    botState: BotState,
    rows: List<SignalRow>,
    regime: MarketRegime,
    headlines: List<NewsHeadline>,
    config: AppConfig,
    simulatorUiState: SimulatorUiState = SimulatorUiState(),
    tradeNotificationsEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onToggleBot: () -> Unit,
    onResetBot: () -> Unit,
    onToggleTradeNotifications: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onExportFeedback: (String) -> Unit,
    onSaveSizingDefaults: (Double, Double) -> Unit,
    onSimulateSizedBuy: (SignalRow, Double) -> Unit,
    onThreatSymbolInputChange: (String) -> Unit = {},
    onScanThreatSymbol: (String) -> Unit = {},
    onToggleThreatLiveMonitoring: (Boolean) -> Unit = {},
    onSelectRecentThreatCard: (ThreatCard) -> Unit = {},
    onExitPosition: (String) -> Unit = {},
    onTrimPosition: (String, Double) -> Unit = { _, _ -> },
    onApplySuggestedStop: (String, Double) -> Unit = { _, _ -> },
    onSelectThreatHistoryFilter: (String?) -> Unit = {},
    onDeleteThreatHistoryEntry: (Long) -> Unit = {},
    onClearThreatHistory: () -> Unit = {},
    advisorEnabled: Boolean = false,
    advisorVetoReady: Boolean = false,
    advisorModel: String = "",
    advisorCallsUsed: Int = 0,
    advisorMaxCallsPerHour: Int = 40,
    advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
    advisorCheckingSymbol: String? = null,
    advisorJournal: AdvisorJournalReport? = null,
    advisorJournalLoading: Boolean = false,
    onCheckAdvisorVeto: (String) -> Unit = {},
    onRunAdvisorJournal: () -> Unit = {},
    councilMemberCount: Int = 0,
    councilFamilyCount: Int = 0,
    councilLedgerCount: Int = 0,
    councilLastDecision: CouncilDecision? = null,
    onOpenCouncil: () -> Unit = {},
    paperHoldingsUiState: PaperHoldingsUiState = PaperHoldingsUiState(),
    onTogglePaperHoldingsForm: () -> Unit = {},
    onPaperHoldingSymbolChange: (String) -> Unit = {},
    onSelectPaperHoldingQuickSymbol: (String) -> Unit = {},
    onPaperHoldingSharesChange: (String) -> Unit = {},
    onPaperHoldingBuyPriceChange: (String) -> Unit = {},
    onPaperHoldingNotesChange: (String) -> Unit = {},
    onPrefillPaperHoldingLivePrice: () -> Unit = {},
    onSavePaperHolding: () -> Unit = {},
    onCancelPaperHoldingEdit: () -> Unit = {},
    onEditPaperHolding: (PaperHoldingEntity) -> Unit = {},
    onDeletePaperHolding: (Long) -> Unit = {},
    onSeedDummyPaperHoldings: () -> Unit = {},
    onClearAllPaperHoldings: () -> Unit = {},
    onRefreshPaperHoldingsQuotes: () -> Unit = {},
    onRefreshPaperDailyPnlHistory: () -> Unit = {},
    onTogglePaperHoldingsAutoRefresh: () -> Unit = {},
    onOpenSignal: ((String) -> Unit)? = null,
    marketMode: MarketMode = MarketMode.STOCKS,
    stockEquity: Double = 10.0,
    cryptoEquity: Double = 10.0,
    onSelectMarketMode: (MarketMode) -> Unit = {},
    onToggleMarketMode: () -> Unit = {},
    cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    cryptoFundingSampleCount: Int = 0,
    cryptoTraderSnapshot: CryptoTraderSnapshot = CryptoTraderSnapshot(),
    cryptoBacktestSummary: CryptoBacktestSummary? = null,
    cryptoBacktestRunning: Boolean = false,
    onRunCryptoBacktest: () -> Unit = {},
    onRefreshCryptoPeg: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportFormat by remember { mutableStateOf(PaperBotEngine.LogExportFormat.CSV) }

    val csvCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = PaperBotEngine.exportTradeLog(botState, PaperBotEngine.LogExportFormat.CSV)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                onExportFeedback(
                    if (ok) "Exported ${botState.log.size} log entries to CSV"
                    else "Could not write CSV log file"
                )
            }
        }
    }

    val tsvCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = PaperBotEngine.exportTradeLog(botState, PaperBotEngine.LogExportFormat.TSV)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                onExportFeedback(
                    if (ok) "Exported ${botState.log.size} log entries to TXT"
                    else "Could not write TXT log file"
                )
            }
        }
    }

    val baseEquity = if (botState.initialEquity > 0.0) botState.initialEquity else 10.0
    val liveEquity = PaperBotEngine.liveEquity(botState)
    val pnl = liveEquity - baseEquity
    val pnlColor = if (pnl >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val pos = botState.pos
    val costRejected = remember(rows) {
        rows.count { it.cost != null && !it.cost.viable && it.action != SignalAction.HOLD }
    }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("trade_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "trade_market_mode_bar") {
            MarketModeSwapBar(
                marketMode = marketMode,
                stockEquity = stockEquity,
                cryptoEquity = cryptoEquity,
                onSelectMarketMode = onSelectMarketMode,
                testTagPrefix = "trade_market_swap"
            )
        }

        // Hero Card
        item(key = "hero_equity") {
            val isCrypto = marketMode == MarketMode.CRYPTO
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MarketScanTheme.extraColors.heroGradStart,
                                MarketScanTheme.extraColors.heroGradEnd
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(
                        text = if (isCrypto) "CRYPTO 24/7 PAPER EQUITY"
                        else stringResource(R.string.label_paper_equity)
                    )
                    TagChip(
                        text = if (isCrypto) stringResource(R.string.crypto_sim_badge)
                        else stringResource(R.string.stock_sim_badge),
                        fg = if (isCrypto) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        bg = if (isCrypto) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = fmtMoney(liveEquity),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${fmtSignedMoney(pnl)} (${fmtPctSigned(pnl / baseEquity * 100.0)})",
                        color = pnlColor,
                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    )
                    Text(
                        text = stringResource(R.string.label_since_open),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = stringResource(R.string.label_cash),
                            value = fmtMoney(botState.cash),
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = stringResource(R.string.label_trades_today),
                            value = "${botState.tradesToday}/${PaperBotEngine.MAX_TRADES_PER_DAY}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val positions = botState.positions
                        val totalUpnl = PaperBotEngine.totalUnrealizedPnl(botState)
                        MetricTile(
                            label = stringResource(R.string.label_position),
                            value = when {
                                positions.size > 1 -> "${positions.size} open"
                                pos != null -> pos.symbol
                                else -> "flat"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        val upnlColor = if (totalUpnl >= 0.0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        MetricTile(
                            label = stringResource(R.string.label_unrealized),
                            value = if (positions.isNotEmpty()) fmtMoney(totalUpnl) else "—",
                            valueColor = if (positions.isNotEmpty()) upnlColor else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AutotraderMarketSwapButton(
                    marketMode = marketMode,
                    oppositeEquity = if (marketMode == MarketMode.CRYPTO) stockEquity else cryptoEquity,
                    onSwap = onToggleMarketMode
                )
            }
        }

        if (marketMode == MarketMode.CRYPTO) {
            item(key = "trade_crypto_derivs_guard") {
                CryptoDerivsGuardCard(
                    derivsBySymbol = cryptoDerivsBySymbol,
                    guardsBySymbol = cryptoGuardsBySymbol,
                    fundingSampleCount = cryptoFundingSampleCount
                )
            }
            item(key = "trade_crypto_trader2_dashboard") {
                CryptoTrader2DashboardCard(
                    snapshot = cryptoTraderSnapshot,
                    backtest = cryptoBacktestSummary,
                    backtestRunning = cryptoBacktestRunning,
                    onRunBacktest = onRunCryptoBacktest,
                    onRefreshPeg = onRefreshCryptoPeg
                )
            }
        }

        // Interactive Paper Trading Equity Curve Dashboard
        item(key = "paper_equity_curve_dashboard") {
            PaperEquityCurveDashboard(
                botState = botState,
                testTagPrefix = "trade_equity_dashboard"
            )
        }

        // Room-Backed Paper Trading Holdings & Real-Time P&L Module
        item(key = "trade_room_paper_holdings") {
            PaperHoldingsRoomCard(
                uiState = paperHoldingsUiState,
                watchlistSymbols = config.activeWatchlist,
                knownSignalSymbols = remember(rows) { rows.map { it.symbol.uppercase() }.toSet() },
                onToggleForm = onTogglePaperHoldingsForm,
                onSymbolInputChange = onPaperHoldingSymbolChange,
                onSelectQuickSymbol = onSelectPaperHoldingQuickSymbol,
                onSharesInputChange = onPaperHoldingSharesChange,
                onPurchasePriceInputChange = onPaperHoldingBuyPriceChange,
                onNotesInputChange = onPaperHoldingNotesChange,
                onPrefillLivePrice = onPrefillPaperHoldingLivePrice,
                onSaveHolding = onSavePaperHolding,
                onCancelEdit = onCancelPaperHoldingEdit,
                onEditHolding = onEditPaperHolding,
                onDeleteHolding = onDeletePaperHolding,
                onSeedDummyHoldings = onSeedDummyPaperHoldings,
                onClearAllHoldings = onClearAllPaperHoldings,
                onRefreshQuotes = onRefreshPaperHoldingsQuotes,
                onToggleAutoRefresh = onTogglePaperHoldingsAutoRefresh,
                onOpenSignal = onOpenSignal,
                testTagPrefix = "trade_room_holdings"
            )
        }

        // Room-Backed 30-Day Daily P&L Performance Chart
        item(key = "trade_room_daily_pnl_chart") {
            PaperDailyPnlChartCard(
                analytics = paperHoldingsUiState.dailyPnlAnalytics,
                isSyncing = paperHoldingsUiState.isSyncingDailyHistory,
                onRefreshHistory = onRefreshPaperDailyPnlHistory,
                onSeedDummyHoldings = onSeedDummyPaperHoldings,
                testTagPrefix = "trade_daily_pnl_30d"
            )
        }

        // Algorithmic Position Sizing & Risk Management Calculator
        item(key = "position_sizing_calculator") {
            PositionSizingCalculatorCard(
                rows = rows,
                regime = regime,
                headlines = headlines,
                config = config,
                botState = botState,
                onSaveSizingDefaults = onSaveSizingDefaults,
                onSimulateSizedBuy = onSimulateSizedBuy
            )
        }

        // Real-Time Threat Engine Scanner bridged to SimulatorViewModel
        item(key = "simulator_threat_scanner") {
            SimulatorThreatScannerCard(
                uiState = simulatorUiState,
                botState = botState,
                rows = rows,
                watchlist = config.activeWatchlist,
                onSymbolInputChange = onThreatSymbolInputChange,
                onScanSymbol = onScanThreatSymbol,
                onToggleLiveMonitoring = onToggleThreatLiveMonitoring,
                onSelectRecentCard = onSelectRecentThreatCard,
                onExitPosition = onExitPosition,
                onTrimPosition = onTrimPosition,
                onApplySuggestedStop = onApplySuggestedStop
            )
        }

        // Persisted Threat History Log (Room local SQLite)
        item(key = "simulator_threat_history") {
            ThreatHistoryCard(
                entries = simulatorUiState.threatHistory,
                activeSymbolFilter = simulatorUiState.historySymbolFilter,
                watchlist = config.activeWatchlist,
                heldSymbols = botState.positions.map { it.symbol },
                onSelectSymbolFilter = onSelectThreatHistoryFilter,
                onRescanSymbol = onScanThreatSymbol,
                onDeleteEntry = onDeleteThreatHistoryEntry,
                onClearHistory = onClearThreatHistory
            )
        }

        // AlphaTrader LLM Advisor Layer (Veto-Only Screening, Rule #3 Audit & Quantitative Journal)
        item(key = "advisor_layer_card") {
            AdvisorCard(
                advisorEnabled = advisorEnabled,
                advisorVetoReady = advisorVetoReady,
                advisorModel = advisorModel,
                callsUsed = advisorCallsUsed,
                maxCallsPerHour = advisorMaxCallsPerHour,
                verdicts = advisorVerdicts,
                checkingSymbol = advisorCheckingSymbol,
                journalReport = advisorJournal,
                journalLoading = advisorJournalLoading,
                botState = botState,
                rows = rows,
                onCheckVeto = onCheckAdvisorVeto,
                onRunJournal = onRunAdvisorJournal
            )
        }

        // Simulation banner
        item(key = "sim_banner") {
            BannerMessage(
                text = stringResource(R.string.banner_simulation),
                isWarning = false
            )
        }

        if (botState.halted) {
            item(key = "halted_banner") {
                BannerMessage(
                    text = stringResource(R.string.banner_halted),
                    isWarning = true
                )
            }
        }

        if (costRejected > 0) {
            item(key = "cost_rejected_banner") {
                BannerMessage(
                    text = "$costRejected signal(s) rejected by the cost model — spread eats the edge.",
                    isWarning = true
                )
            }
        }

        // Current Position Cards
        if (botState.positions.isNotEmpty()) {
            items(botState.positions, key = { "trade_pos_${it.symbol}" }) { holding ->
                val upnlColor = if (holding.upnl >= 0.0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                }
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = holding.symbol,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            TagChip(
                                text = "LONG",
                                fg = MaterialTheme.colorScheme.secondary,
                                bg = MaterialTheme.colorScheme.secondaryContainer
                            )
                            val rColor = if (holding.rMultiple >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            TagChip(
                                text = "${if (holding.rMultiple >= 0) "+" else ""}${"%.1f".format(holding.rMultiple)}R",
                                fg = rColor,
                                bg = rColor.copy(alpha = 0.15f)
                            )
                        }
                        Text(
                            text = fmtSignedMoney(holding.upnl),
                            color = upnlColor,
                            style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    if (holding.partialTaken || holding.isTrailing || holding.sessionAge > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (holding.partialTaken) {
                                TagChip(
                                    text = "50% TRIMMED (BE)",
                                    fg = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                            if (holding.isTrailing) {
                                TagChip(
                                    text = "TRAILING (2×ATR)",
                                    fg = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                )
                            }
                            TagChip(
                                text = "AGE ${holding.sessionAge} SESS",
                                fg = MaterialTheme.colorScheme.onSurfaceVariant,
                                bg = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMetric(
                            label = stringResource(R.string.label_qty),
                            value = fmtNum(holding.qty, 4),
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetric(
                            label = stringResource(R.string.label_entry),
                            value = fmtMoney(holding.entry),
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetric(
                            label = if (holding.isTrailing) "TRAIL STOP" else stringResource(R.string.label_stop),
                            value = fmtMoney(holding.stop),
                            valueColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetric(
                            label = stringResource(R.string.label_target),
                            value = fmtMoney(holding.target),
                            valueColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            item(key = "position_card_empty") {
                TerminalCard {
                    Text(
                        text = if (botState.running) stringResource(R.string.flat_running)
                        else stringResource(R.string.flat_stopped),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        // Bot Controls
        item(key = "bot_controls") {
            val isCrypto = marketMode == MarketMode.CRYPTO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggleBot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bot_toggle_button"),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (botState.running) MaterialTheme.colorScheme.surfaceVariant
                        else if (isCrypto) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                        contentColor = if (botState.running) MaterialTheme.colorScheme.onBackground
                        else if (isCrypto) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = if (botState.running) {
                            if (isCrypto) stringResource(R.string.action_stop_crypto_bot)
                            else stringResource(R.string.action_stop_bot)
                        } else {
                            if (isCrypto) stringResource(R.string.action_start_crypto_bot, fmtMoney(baseEquity))
                            else stringResource(R.string.action_start_stock_bot, fmtMoney(baseEquity))
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                OutlinedButton(
                    onClick = onToggleMarketMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bot_controls_swap_market_button"),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCrypto) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isCrypto) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(
                        text = if (isCrypto) stringResource(R.string.market_mode_swap_to_stocks)
                        else stringResource(R.string.market_mode_swap_to_crypto),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                OutlinedButton(
                    onClick = onResetBot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bot_reset_button"),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Text(
                        text = if (isCrypto) stringResource(R.string.action_reset_crypto_bot)
                        else stringResource(R.string.action_reset_bot),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }

        // Trade Execution Push Notifications Card
        item(key = "trade_push_alerts") {
            TerminalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (tradeNotificationsEnabled) Icons.Outlined.NotificationsActive
                        else Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = if (tradeNotificationsEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autotrader Push Alerts",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Send a local notification whenever the bot opens or closes a position.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = tradeNotificationsEnabled,
                        onCheckedChange = onToggleTradeNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("trade_notifications_switch")
                    )
                }

                if (tradeNotificationsEnabled && !hasNotificationPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Android notification permission is currently off.",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("trade_enable_notifications_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "ALLOW",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Multi-Model AI Investment Council Card
        item(key = "trade_council_launcher") {
            CouncilLauncherCard(
                memberCount = councilMemberCount,
                familyCount = councilFamilyCount,
                ledgerCount = councilLedgerCount,
                lastDecision = councilLastDecision,
                onOpenCouncil = onOpenCouncil
            )
        }

        // Active Rules Card
        item(key = "active_rules") {
            TerminalCard {
                SectionLabel(text = stringResource(R.string.label_active_rules))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (marketMode == MarketMode.CRYPTO) {
                        "· 24/7 Kraken Spot & Futures public market feeds (simulated paper book)\n" +
                            "· CryptoPanic Guard: deterministic vote scoring + 12h half-life decay\n" +
                            "· Kraken Perpetual Funding crowding scales entry size [0.25× – 1.00×]\n" +
                            "· Guard filters entries only — never panic-sells an already-held coin\n" +
                            "· Half-Kelly sizing + 2.0× ATR trailing stop past +1.0R partial trim\n" +
                            "· Daily circuit breaker halt at −3% on the day"
                    } else {
                        "· Half-Kelly (½-Kelly) sizing bounded to risk budget\n" +
                            "· Daniel & Moskowitz Panic Guard cuts exposure 50% on crash trap\n" +
                            "· Broker bracket safety: whole-shares when retaining ≥60% notional\n" +
                            "· +1.0R Partial Exit: trims 50% & ratchets stop to Breakeven\n" +
                            "· Trailing stop ratchet: 2.0× ATR trail activated past +1.0R\n" +
                            "· Time Stop: closes trade after 15 sessions if profit < 0.3R\n" +
                            "· Daily circuit breaker halt at −3% on the day"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                )
            }
        }

        // Trade Log Card
        item(key = "trade_log") {
            TerminalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "${stringResource(R.string.label_trade_log)} (${botState.log.size})")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PaperBotEngine.LogExportFormat.entries.forEach { fmt ->
                            val selected = exportFormat == fmt
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent
                                    )
                                    .clickable { exportFormat = fmt }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("export_format_${fmt.shortLabel.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt.shortLabel,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val fileName = PaperBotEngine.suggestedExportFileName(exportFormat)
                            if (exportFormat == PaperBotEngine.LogExportFormat.CSV) {
                                csvCreateLauncher.launch(fileName)
                            } else {
                                tsvCreateLauncher.launch(fileName)
                            }
                        },
                        enabled = botState.log.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_log_button"),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(
                            1.dp,
                            if (botState.log.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = if (botState.log.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "EXPORT ${exportFormat.shortLabel}",
                            color = if (botState.log.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val textPayload = PaperBotEngine.exportTradeLog(botState, exportFormat)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, PaperBotEngine.suggestedExportFileName(exportFormat))
                                putExtra(Intent.EXTRA_TEXT, textPayload)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Trade Log (${exportFormat.shortLabel})"))
                        },
                        enabled = botState.log.isNotEmpty(),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("share_log_button"),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share trade log",
                            tint = if (botState.log.isNotEmpty()) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (botState.log.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_trade_log),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val entries = botState.log.take(12)
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = timeFormatter.format(Date(entry.timestampMs)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 11.sp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val headerText = if (entry.type.equals("VETO", ignoreCase = true)) {
                                    "VETO ${entry.symbol} @ ${fmtMoney(entry.price)} · llm=veto"
                                } else {
                                    val llmPart = if (entry.llmVerdict.isNotBlank()) " · llm=${entry.llmVerdict}" else ""
                                    "${entry.type} ${entry.symbol} ${fmtNum(entry.qty, 4)} @ ${fmtMoney(entry.price)}$llmPart"
                                }
                                Text(
                                    text = headerText,
                                    color = if (entry.type.equals("VETO", ignoreCase = true)) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        text = entry.note,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (entry.pnl != null) {
                                val entryPnlColor = if (entry.pnl >= 0) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                                Text(
                                    text = fmtSignedMoney(entry.pnl),
                                    color = entryPnlColor,
                                    style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
