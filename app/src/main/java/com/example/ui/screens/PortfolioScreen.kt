package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PaperHoldingEntity
import com.example.domain.engine.PaperBotEngine
import com.example.domain.model.BotState
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.MarketMode
import com.example.domain.model.SignalRow
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatLevel
import com.example.domain.model.TradePosition
import com.example.ui.PaperHoldingsUiState
import com.example.ui.components.BannerMessage
import com.example.ui.components.CryptoDerivsGuardCard
import com.example.ui.components.MarketModeSwapBar
import com.example.ui.components.MetricTile
import com.example.ui.components.MiniMetric
import com.example.ui.components.PaperDailyPnlChartCard
import com.example.ui.components.PaperEquityCurveDashboard
import com.example.ui.components.PaperHoldingsRoomCard
import com.example.ui.components.PriceSparklineChart
import com.example.ui.components.SectionLabel
import com.example.ui.components.TagChip
import com.example.ui.components.TerminalCard
import com.example.ui.components.ThreatAssessmentCard
import com.example.ui.components.fmtMoney
import com.example.ui.components.fmtNum
import com.example.ui.components.fmtPctSigned
import com.example.ui.components.fmtSignedMoney
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PortfolioScreen(
    botState: BotState,
    rows: List<SignalRow>,
    source: String,
    threatsBySymbol: Map<String, ThreatAssessment> = emptyMap(),
    assessingThreatSymbols: Set<String> = emptySet(),
    onOpenSignal: (String) -> Unit,
    onClosePosition: (String) -> Unit,
    onTrimPosition: (String, Double) -> Unit = { _, _ -> },
    onApplySuggestedStop: (String, Double) -> Unit = { _, _ -> },
    onAssessThreat: (String) -> Unit = {},
    onNavigateToTrade: () -> Unit,
    onNavigateToSignals: () -> Unit,
    watchlistSymbols: List<String> = emptyList(),
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
    marketMode: MarketMode = MarketMode.STOCKS,
    stockEquity: Double = 10.0,
    cryptoEquity: Double = 10.0,
    onSelectMarketMode: (MarketMode) -> Unit = {},
    cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    cryptoFundingSampleCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val positions = botState.positions
    val baseEquity = if (botState.initialEquity > 0.0) botState.initialEquity else 10.0
    val marketValue = remember(positions) { PaperBotEngine.totalMarketValue(botState) }
    val liveEquity = botState.cash + marketValue
    val totalPnl = liveEquity - baseEquity
    val totalPnlPct = if (baseEquity > 0.0) (totalPnl / baseEquity) * 100.0 else 0.0
    val upnl = remember(positions) { PaperBotEngine.totalUnrealizedPnl(botState) }
    val realizedPnl = remember(botState.equity, baseEquity) { botState.equity - baseEquity }

    val totalColor by animateColorAsState(
        targetValue = if (totalPnl >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        label = "portfolio_total_pnl_color"
    )
    val upnlColor by animateColorAsState(
        targetValue = if (upnl >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        label = "portfolio_upnl_color"
    )

    val investedShare = if (liveEquity > 0.0) (marketValue / liveEquity).coerceIn(0.0, 1.0).toFloat() else 0f
    val closedTrades = remember(botState.log) { botState.log.filter { it.type == "CLOSE" } }
    val winCount = remember(closedTrades) { closedTrades.count { (it.pnl ?: 0.0) > 0.0 } }
    val avgTradePnl = remember(closedTrades) {
        if (closedTrades.isEmpty()) 0.0
        else closedTrades.sumOf { it.pnl ?: 0.0 } / closedTrades.size
    }
    val haltFloor = baseEquity * (1.0 - PaperBotEngine.DAILY_LOSS_LIMIT)
    val bufferRemaining = ((liveEquity - haltFloor) / (baseEquity * PaperBotEngine.DAILY_LOSS_LIMIT))
        .coerceIn(0.0, 1.0)
        .toFloat()

    val equityCurveSeries = remember(botState.equityHistory, liveEquity, baseEquity) {
        val pts = botState.equityHistory.map { it.equity }.toMutableList()
        if (pts.isEmpty()) pts.add(baseEquity)
        pts.add(liveEquity)
        pts
    }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("portfolio_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "portfolio_market_mode_bar") {
            MarketModeSwapBar(
                marketMode = marketMode,
                stockEquity = stockEquity,
                cryptoEquity = cryptoEquity,
                onSelectMarketMode = onSelectMarketMode,
                testTagPrefix = "portfolio_market_swap"
            )
        }

        // Live Net Worth / Portfolio Value Hero + Equity Curve
        item(key = "portfolio_hero") {
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
                        text = if (isCrypto) stringResource(R.string.portfolio_title_crypto)
                        else stringResource(R.string.portfolio_title_stocks)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCrypto) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.secondary
                                )
                        )
                        Text(
                            text = when {
                                isCrypto && source == "live" -> stringResource(R.string.crypto_247_badge)
                                isCrypto -> "CRYPTO SIM"
                                source == "live" -> "LIVE FEED"
                                else -> "DEMO TICK"
                            },
                            color = if (isCrypto) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fmtMoney(liveEquity),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("portfolio_live_equity")
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${fmtSignedMoney(totalPnl)} (${fmtPctSigned(totalPnlPct)})",
                        color = totalColor,
                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    )
                    Text(
                        text = "total return",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                EquityCurveChart(
                    points = equityCurveSeries,
                    baseline = baseEquity,
                    lineColor = totalColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Allocation Bar (Invested vs Cash)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Invested ${(investedShare * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Cash ${((1f - investedShare) * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (investedShare > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(investedShare)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "UNREALIZED PNL",
                        value = if (positions.isNotEmpty()) fmtSignedMoney(upnl) else "—",
                        valueColor = if (positions.isNotEmpty()) upnlColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("portfolio_unrealized_pnl")
                    )
                    MetricTile(
                        label = "REALIZED PNL",
                        value = fmtSignedMoney(realizedPnl),
                        valueColor = if (realizedPnl >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "TOTAL MARKET VALUE",
                        value = fmtMoney(marketValue),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("portfolio_market_value")
                    )
                    MetricTile(
                        label = "AVAILABLE CASH",
                        value = fmtMoney(botState.cash),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (marketMode == MarketMode.CRYPTO) {
            item(key = "portfolio_crypto_derivs_guard") {
                CryptoDerivsGuardCard(
                    derivsBySymbol = cryptoDerivsBySymbol,
                    guardsBySymbol = cryptoGuardsBySymbol,
                    fundingSampleCount = cryptoFundingSampleCount
                )
            }
        }

        // Interactive Paper Trading Equity Curve Dashboard
        item(key = "portfolio_equity_curve_dashboard") {
            PaperEquityCurveDashboard(
                botState = botState,
                testTagPrefix = "portfolio_equity_dashboard"
            )
        }

        // Room-Backed Paper Trading Holdings & Real-Time P&L Module
        item(key = "portfolio_room_paper_holdings") {
            PaperHoldingsRoomCard(
                uiState = paperHoldingsUiState,
                watchlistSymbols = watchlistSymbols,
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
                testTagPrefix = "portfolio_room_holdings"
            )
        }

        // Room-Backed 30-Day Daily P&L Performance Chart
        item(key = "portfolio_room_daily_pnl_chart") {
            PaperDailyPnlChartCard(
                analytics = paperHoldingsUiState.dailyPnlAnalytics,
                isSyncing = paperHoldingsUiState.isSyncingDailyHistory,
                onRefreshHistory = onRefreshPaperDailyPnlHistory,
                onSeedDummyHoldings = onSeedDummyPaperHoldings,
                testTagPrefix = "portfolio_daily_pnl_30d"
            )
        }

        if (source == "demo") {
            item(key = "portfolio_demo_banner") {
                BannerMessage(
                    text = "Simulated paper holdings — quotes tick automatically in Demo mode. Connect Alpaca keys in SETUP for live quotes.",
                    isWarning = false
                )
            }
        }

        val severeThreats = positions.mapNotNull { pos ->
            threatsBySymbol[pos.symbol]?.takeIf { it.level >= ThreatLevel.HIGH }
        }
        if (severeThreats.isNotEmpty()) {
            item(key = "portfolio_threat_alert_banner") {
                val summary = severeThreats.joinToString(", ") {
                    "${it.symbol} (${it.level.label} ${it.threatScore} -> ${it.action.label})"
                }
                BannerMessage(
                    text = "Risk Threat Warning on open exposure: $summary",
                    isWarning = true,
                    modifier = Modifier.testTag("portfolio_threat_warning_banner")
                )
            }
        }

        // Open Holdings Header
        item(key = "holdings_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(text = "OPEN HOLDINGS")
                TagChip(
                    text = "${positions.size} ACTIVE",
                    fg = if (positions.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    bg = if (positions.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Holding Cards or Empty State
        if (positions.isNotEmpty()) {
            items(positions, key = { it.symbol }) { holding ->
                val matchingSignal = rows.find { it.symbol == holding.symbol }
                val holdingColor = if (holding.upnl >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                val threat = threatsBySymbol[holding.symbol]
                val assessing = assessingThreatSymbols.contains(holding.symbol)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldingCard(
                        pos = holding,
                        signal = matchingSignal,
                        upnlColor = holdingColor,
                        onOpenSignal = { onOpenSignal(holding.symbol) },
                        onClosePosition = { onClosePosition(holding.symbol) }
                    )
                    ThreatAssessmentCard(
                        symbol = holding.symbol,
                        assessment = threat,
                        isAssessing = assessing,
                        isHolding = true,
                        currentStop = holding.stop,
                        onRefresh = { onAssessThreat(holding.symbol) },
                        onExitPosition = { onClosePosition(holding.symbol) },
                        onTrimPosition = { onTrimPosition(holding.symbol, 0.5) },
                        onApplySuggestedStop = { stopPx -> onApplySuggestedStop(holding.symbol, stopPx) },
                        initiallyExpanded = threat?.level?.let { it >= ThreatLevel.ELEVATED } ?: false
                    )
                }
            }
        } else {
            item(key = "holding_empty") {
                TerminalCard(modifier = Modifier.testTag("portfolio_empty_card")) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No open holdings",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (botState.running) {
                                "Autotrader is active and scanning for a BUY signal that clears the 3x cost gate."
                            } else {
                                "Your paper account is 100% cash (${fmtMoney(botState.cash)}). Start the autotrader or tap any signal to Paper Buy."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToTrade,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("go_to_trade_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text(
                                    text = "AUTOTRADER",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                            OutlinedButton(
                                onClick = onNavigateToSignals,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("browse_signals_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                )
                            ) {
                                Text(
                                    text = "SIGNALS",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Performance & Risk Metrics Card
        item(key = "risk_metrics") {
            TerminalCard {
                SectionLabel(text = "PERFORMANCE & RISK")
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val winRateStr = if (closedTrades.isEmpty()) "—"
                    else "${(winCount.toDouble() / closedTrades.size * 100.0).toInt()}%"
                    MiniMetric(
                        label = "WIN RATE",
                        value = winRateStr,
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetric(
                        label = "AVG PNL",
                        value = if (closedTrades.isEmpty()) "—" else fmtSignedMoney(avgTradePnl),
                        valueColor = if (avgTradePnl >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetric(
                        label = "CLOSED",
                        value = closedTrades.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetric(
                        label = "TODAY",
                        value = "${botState.tradesToday}/${PaperBotEngine.MAX_TRADES_PER_DAY}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily Loss Halt Buffer (-4%)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${(bufferRemaining * 100).toInt()}% left",
                        color = if (bufferRemaining > 0.35f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferRemaining)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (bufferRemaining > 0.35f) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                }
            }
        }

        // Realized Closed Positions History
        item(key = "closed_history") {
            TerminalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "CLOSED POSITIONS")
                    if (closedTrades.isNotEmpty()) {
                        val winRate = (winCount.toDouble() / closedTrades.size * 100.0).toInt()
                        Text(
                            text = "$winCount/${closedTrades.size} wins ($winRate%)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 11.sp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (closedTrades.isEmpty()) {
                    Text(
                        text = "No closed positions yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val visible = closedTrades.take(10)
                    visible.forEachIndexed { idx, entry ->
                        val pnl = entry.pnl ?: 0.0
                        val pnlCol = if (pnl >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeFormatter.format(Date(entry.timestampMs)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 11.sp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.symbol} · ${fmtNum(entry.qty, 4)} sh @ ${fmtMoney(entry.price)}",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        text = "exit: ${entry.note}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                text = fmtSignedMoney(pnl),
                                color = pnlCol,
                                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        if (idx < visible.lastIndex) {
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

@Composable
private fun EquityCurveChart(
    points: List<Double>,
    baseline: Double,
    lineColor: Color
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
    val borderColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 10.dp.toPx()

            val minV = minOf(points.min(), baseline)
            val maxV = maxOf(points.max(), baseline)
            val range = (maxV - minV).takeIf { it > 0.0001 } ?: (baseline * 0.02).coerceAtLeast(0.1)
            val lo = minV - range * 0.15
            val hi = maxV + range * 0.15
            val span = hi - lo

            fun px(i: Int): Float = if (points.size <= 1) w - pad
            else pad + (i.toFloat() / (points.size - 1)) * (w - pad * 2f)

            fun py(v: Double): Float = h - pad - (((v - lo) / span).toFloat() * (h - pad * 2f))

            val baseY = py(baseline)
            drawLine(
                color = muted.copy(alpha = 0.35f),
                start = Offset(pad, baseY),
                end = Offset(w - pad, baseY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(px(0), py(points[0]))
                    for (i in 1 until points.size) {
                        lineTo(px(i), py(points[i]))
                    }
                }
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(px(points.size - 1), h)
                    lineTo(px(0), h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val lastX = px(points.size - 1)
                val lastY = py(points.last())
                drawCircle(color = lineColor.copy(alpha = 0.3f), radius = 5.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(color = lineColor, radius = 2.8.dp.toPx(), center = Offset(lastX, lastY))
            }
        }
    }
}

@Composable
private fun HoldingCard(
    pos: TradePosition,
    signal: SignalRow?,
    upnlColor: Color,
    onOpenSignal: () -> Unit,
    onClosePosition: () -> Unit
) {
    val costBasis = pos.entry * pos.qty
    val mktValue = pos.last * pos.qty
    val returnPct = if (costBasis > 0.0) (pos.upnl / costBasis) * 100.0 else 0.0
    val bracketRange = (pos.target - pos.stop).takeIf { abs(it) > 0.0001 } ?: 1.0
    val progressInBracket = ((pos.last - pos.stop) / bracketRange).coerceIn(0.0, 1.0).toFloat()

    TerminalCard(
        modifier = Modifier.testTag("portfolio_holding_card_${pos.symbol}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pos.symbol,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp)
                    )
                    TagChip(
                        text = "LONG",
                        fg = MaterialTheme.colorScheme.secondary,
                        bg = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${fmtNum(pos.qty, 4)} shares · Val ${fmtMoney(mktValue)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 11.5.sp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = fmtSignedMoney(pos.upnl),
                    color = upnlColor,
                    style = MonoTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.testTag("holding_upnl_value_${pos.symbol}")
                )
                Text(
                    text = fmtPctSigned(returnPct),
                    color = upnlColor,
                    style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = "ENTRY",
                value = fmtMoney(pos.entry),
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "LAST",
                value = fmtMoney(pos.last),
                valueColor = upnlColor,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "STOP",
                value = fmtMoney(pos.stop),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "TARGET",
                value = fmtMoney(pos.target),
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bracket Progress Bar (Stop -> Target)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stop ${fmtMoney(pos.stop)}",
                color = MaterialTheme.colorScheme.error,
                style = MonoTextStyle.copy(fontSize = 10.sp)
            )
            Text(
                text = "Bracket ${(progressInBracket * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.sp)
            )
            Text(
                text = "Target ${fmtMoney(pos.target)}",
                color = MaterialTheme.colorScheme.secondary,
                style = MonoTextStyle.copy(fontSize = 10.sp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressInBracket)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(upnlColor)
            )
        }

        if (signal != null && signal.series.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            PriceSparklineChart(
                series = signal.series,
                lineColor = upnlColor,
                stopPrice = pos.stop,
                targetPrice = pos.target
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (signal != null) {
                OutlinedButton(
                    onClick = onOpenSignal,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("holding_inspect_button_${pos.symbol}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Text(
                        text = "VIEW SIGNAL",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
            Button(
                onClick = onClosePosition,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("close_position_button_${pos.symbol}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "CLOSE POSITION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }
    }
}
