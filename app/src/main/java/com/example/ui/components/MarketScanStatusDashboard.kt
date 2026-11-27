package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.PaperBotEngine
import com.example.domain.model.BotState
import com.example.domain.model.MarketMode
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.TradePosition
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

/**
 * Main Market Scan Status Dashboard hero card displayed at the top of the primary landing screen.
 * Summarizes at a glance:
 * - Current Market Scan status (scanning / live / 24/7 / demo feed + rescan control)
 * - Active market regime label with breadth & sizing multiplier
 * - Active actionable signal count (BUY + SELL breakdown)
 * - Summary of current paper positions, equity, unrealized P&L, and autotrader state
 */
@Composable
fun MarketScanStatusDashboard(
    statusText: String,
    source: String,
    isScanning: Boolean,
    marketMode: MarketMode,
    regime: MarketRegime,
    rows: List<SignalRow>,
    botState: BotState,
    onRefresh: () -> Unit,
    onOpenRegime: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenSignal: (String) -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToTrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buyCount = remember(rows) { rows.count { it.action == SignalAction.BUY } }
    val sellCount = remember(rows) { rows.count { it.action == SignalAction.SELL } }
    val activeSignalsCount = buyCount + sellCount
    val holdCount = (rows.size - activeSignalsCount).coerceAtLeast(0)

    val liveEquity = remember(botState) { PaperBotEngine.liveEquity(botState) }
    val totalUpnl = remember(botState) { PaperBotEngine.totalUnrealizedPnl(botState) }
    val baseEquity = if (botState.initialEquity > 0.0) botState.initialEquity else 10.0
    val totalReturnPct = if (baseEquity > 0.0) ((liveEquity - baseEquity) / baseEquity) * 100.0 else 0.0

    val regimeFg = when (regime.label) {
        RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
        RegimeLabel.NEUTRAL -> MaterialTheme.colorScheme.tertiary
        RegimeLabel.RISK_OFF, RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
    }
    val regimeBg = when (regime.label) {
        RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondaryContainer
        RegimeLabel.NEUTRAL -> MaterialTheme.colorScheme.tertiaryContainer
        RegimeLabel.RISK_OFF, RegimeLabel.CRISIS -> MaterialTheme.colorScheme.errorContainer
    }
    val regimeDisplayName = when (regime.label) {
        RegimeLabel.RISK_ON -> "RISK ON"
        RegimeLabel.NEUTRAL -> "NEUTRAL"
        RegimeLabel.RISK_OFF -> "RISK OFF"
        RegimeLabel.CRISIS -> "CRISIS"
    }

    val isLive = source.equals("live", ignoreCase = true)
    val statusDotColor = when {
        isScanning -> MaterialTheme.colorScheme.primary
        isLive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val feedBadgeText = when {
        isScanning -> stringResource(R.string.dashboard_status_scanning)
        marketMode == MarketMode.CRYPTO && isLive -> stringResource(R.string.dashboard_status_crypto_live)
        isLive -> stringResource(R.string.dashboard_status_live)
        else -> stringResource(R.string.dashboard_status_demo)
    }

    val botStatusLabel = when {
        botState.halted -> stringResource(R.string.dashboard_bot_halted)
        botState.running -> stringResource(R.string.dashboard_bot_running)
        else -> stringResource(R.string.dashboard_bot_paused)
    }
    val botStatusFg = when {
        botState.halted -> MaterialTheme.colorScheme.error
        botState.running -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val botStatusBg = when {
        botState.halted -> MaterialTheme.colorScheme.errorContainer
        botState.running -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = modifier
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
            .padding(14.dp)
            .testTag("market_scan_status_dashboard")
    ) {
        // 1. Header: Market Scan Status + Live Feed Badge + Rescan Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_status_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TagChip(
                        text = feedBadgeText,
                        fg = statusDotColor,
                        bg = statusDotColor.copy(alpha = 0.16f)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = statusText.ifBlank { stringResource(R.string.dashboard_status_ready) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("dashboard_status_text")
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isScanning,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_status_refresh_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.dashboard_refresh_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Primary KPI Pair: Active Market Regime & Active Signals Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active Market Regime Card
            Surface(
                onClick = onOpenRegime,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, regimeFg.copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_regime_tile")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = regimeFg,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_active_regime_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = regimeBg,
                        border = BorderStroke(1.dp, regimeFg.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = regimeDisplayName,
                            color = regimeFg,
                            style = MonoTextStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("dashboard_regime_label_text"),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${(regime.breadth * 100).toInt()}% breadth · ${String.format(Locale.US, "%.2f", regime.longMult)}× size",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 9.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Active Signals Tile
            Surface(
                onClick = onOpenScanner,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_active_signals_tile")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_active_signals_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = activeSignalsCount.toString(),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            modifier = Modifier.testTag("dashboard_active_signals_count")
                        )
                        Text(
                            text = "/ ${rows.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$buyCount BUY",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$sellCount SELL",
                            color = MaterialTheme.colorScheme.error,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$holdCount HOLD",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Current Positions Summary Container
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_positions_summary_card")
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (marketMode == MarketMode.CRYPTO) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.dashboard_positions_header,
                                botState.positions.size
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TagChip(
                        text = botStatusLabel,
                        fg = botStatusFg,
                        bg = botStatusBg
                    )
                }

                // Portfolio Equity & Unrealized P&L Quick Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val eqRetColor = if (totalReturnPct >= 0.0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error
                    val upnlColor = if (totalUpnl >= 0.0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error

                    CompactSummaryMetric(
                        label = stringResource(R.string.dashboard_equity_label),
                        value = fmtMoney(liveEquity),
                        subValue = fmtPctSigned(totalReturnPct),
                        subColor = eqRetColor,
                        modifier = Modifier.weight(1f)
                    )
                    CompactSummaryMetric(
                        label = stringResource(R.string.dashboard_upnl_label),
                        value = if (botState.positions.isNotEmpty()) fmtSignedMoney(totalUpnl) else "$0.00",
                        subValue = if (botState.positions.isNotEmpty()) {
                            "${botState.positions.size} open"
                        } else {
                            "Cash ${fmtMoney(botState.cash)}"
                        },
                        subColor = if (botState.positions.isNotEmpty()) upnlColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

                if (botState.positions.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dashboard_positions_flat_title),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(
                                    R.string.dashboard_positions_flat_subtitle,
                                    fmtMoney(botState.cash)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                            )
                        }
                        DashboardLinkButton(
                            label = stringResource(R.string.dashboard_open_trade_cta),
                            onClick = onNavigateToTrade,
                            testTag = "dashboard_open_trade_button"
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        botState.positions.take(3).forEach { pos ->
                            PositionSummaryRow(
                                position = pos,
                                onClick = { onOpenSignal(pos.symbol) }
                            )
                        }
                        if (botState.positions.size > 3) {
                            Text(
                                text = stringResource(
                                    R.string.dashboard_positions_more,
                                    botState.positions.size - 3
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 10.sp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        DashboardLinkButton(
                            label = stringResource(R.string.dashboard_view_portfolio_cta),
                            onClick = onNavigateToPortfolio,
                            testTag = "dashboard_view_portfolio_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSummaryMetric(
    label: String,
    value: String,
    subValue: String,
    subColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subValue,
            color = subColor,
            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PositionSummaryRow(
    position: TradePosition,
    onClick: () -> Unit
) {
    val pnlColor = if (position.upnl >= 0.0) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.error
    val qtyText = if (position.qty < 1.0) {
        String.format(Locale.US, "%.4f", position.qty)
    } else {
        String.format(Locale.US, "%.2f", position.qty)
    }
    val rowDesc = "${position.symbol} position, unrealized P&L ${fmtSignedMoney(position.upnl)}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = rowDesc }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("dashboard_position_row_${position.symbol}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.1f)) {
            Text(
                text = position.symbol,
                color = MaterialTheme.colorScheme.onBackground,
                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                maxLines = 1
            )
            Text(
                text = "$qtyText @ ${fmtMoney(position.entry)} · Last ${fmtMoney(position.last)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = fmtSignedMoney(position.upnl),
                color = pnlColor,
                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                maxLines = 1
            )
            Text(
                text = "${if (position.rMultiple >= 0) "+" else ""}${String.format(Locale.US, "%.2f", position.rMultiple)}R · Stop ${fmtMoney(position.stop)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.5.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardLinkButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
