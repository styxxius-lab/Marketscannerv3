package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

/**
 * Material 3 `MarketDashboard` component displaying:
 * 1. Current Market Scan status header with live feed state and rescan action
 * 2. Current market regime status card (label, breadth, size multiplier, and note)
 * 3. Active scan signal count card (actionable BUY + SELL count and full breakdown)
 * 4. Lazy list (`LazyRow`) of current open positions rendered as Material 3 cards
 */
@Composable
fun MarketDashboard(
    regime: MarketRegime,
    rows: List<SignalRow>,
    botState: BotState,
    statusText: String = "",
    source: String = "live",
    isScanning: Boolean = false,
    marketMode: MarketMode = MarketMode.STOCKS,
    onRefresh: () -> Unit = {},
    onOpenRegime: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenSignal: (String) -> Unit = {},
    onNavigateToPortfolio: () -> Unit = {},
    onNavigateToTrade: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val buyCount = remember(rows) { rows.count { it.action == SignalAction.BUY } }
    val sellCount = remember(rows) { rows.count { it.action == SignalAction.SELL } }
    val activeSignalCount = buyCount + sellCount
    val holdCount = (rows.size - activeSignalCount).coerceAtLeast(0)

    val liveEquity = remember(botState) { PaperBotEngine.liveEquity(botState) }
    val totalUpnl = remember(botState) { PaperBotEngine.totalUnrealizedPnl(botState) }
    val positions = botState.positions

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
            .testTag("market_dashboard"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Market Scan Status Header Card (Material 3 ElevatedCard)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("market_dashboard_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                            modifier = Modifier.testTag("market_dashboard_status_text")
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isScanning,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_dashboard_refresh_button")
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
        }

        // 2. Regime Status & Active Scan Signal Count (Side-by-Side Material 3 Cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Current Regime Status Card
            OutlinedCard(
                onClick = onOpenRegime,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, regimeFg.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("market_dashboard_regime_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = regimeFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_active_regime_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = regimeBg,
                        border = BorderStroke(1.dp, regimeFg.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = regimeDisplayName,
                            color = regimeFg,
                            style = MonoTextStyle.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("market_dashboard_regime_label"),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${(regime.breadth * 100).toInt()}% breadth · ${String.format(Locale.US, "%.2f", regime.longMult)}× size",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Active Scan Signal Count Card
            OutlinedCard(
                onClick = onOpenScanner,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .testTag("market_dashboard_signals_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_active_signals_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = activeSignalCount.toString(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyle.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            modifier = Modifier.testTag("market_dashboard_active_signal_count")
                        )
                        Text(
                            text = "/ ${rows.size} scanned",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp),
                            modifier = Modifier.padding(bottom = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$buyCount BUY",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$sellCount SELL",
                            color = MaterialTheme.colorScheme.error,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$holdCount HOLD",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp)
                        )
                    }
                }
            }
        }

        // 3. Current Positions Section with Lazy List of Material 3 Position Cards
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("market_dashboard_positions_section")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
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
                            modifier = Modifier.size(15.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_positions_header, positions.size),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val upnlText = if (positions.isNotEmpty()) {
                                "${fmtMoney(liveEquity)} eq · ${fmtSignedMoney(totalUpnl)} uPnL"
                            } else {
                                "${fmtMoney(liveEquity)} eq · Cash ${fmtMoney(botState.cash)}"
                            }
                            Text(
                                text = upnlText,
                                color = if (positions.isNotEmpty() && totalUpnl < 0.0) {
                                    MaterialTheme.colorScheme.error
                                } else if (positions.isNotEmpty() && totalUpnl > 0.0) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TagChip(
                            text = botStatusLabel,
                            fg = botStatusFg,
                            bg = botStatusBg
                        )
                        Surface(
                            onClick = if (positions.isNotEmpty()) onNavigateToPortfolio else onNavigateToTrade,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("market_dashboard_positions_action_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = if (positions.isNotEmpty()) {
                                        stringResource(R.string.dashboard_view_portfolio_cta)
                                    } else {
                                        stringResource(R.string.dashboard_open_trade_cta)
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                if (positions.isEmpty()) {
                    OutlinedCard(
                        onClick = onNavigateToTrade,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("market_dashboard_positions_empty_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_positions_flat_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(
                                    R.string.dashboard_positions_flat_subtitle,
                                    fmtMoney(botState.cash)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("market_dashboard_positions_lazy_list")
                    ) {
                        items(
                            items = positions,
                            key = { it.symbol }
                        ) { position ->
                            DashboardPositionCard(
                                position = position,
                                onClick = { onOpenSignal(position.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPositionCard(
    position: TradePosition,
    onClick: () -> Unit
) {
    val pnlPositive = position.upnl >= 0.0
    val pnlColor = if (pnlPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val pnlBg = if (pnlPositive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val qtyText = if (position.qty < 1.0) {
        String.format(Locale.US, "%.4f", position.qty)
    } else {
        String.format(Locale.US, "%.2f", position.qty)
    }
    val cardDesc = "${position.symbol} position, ${fmtSignedMoney(position.upnl)} unrealized P&L"

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(224.dp)
            .semantics { contentDescription = cardDesc }
            .testTag("market_dashboard_position_card_${position.symbol}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = position.symbol,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MonoTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TagChip(
                    text = "${if (position.rMultiple >= 0) "+" else ""}${String.format(Locale.US, "%.2f", position.rMultiple)}R",
                    fg = pnlColor,
                    bg = pnlBg
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "LAST",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = fmtMoney(position.last),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "UNREALIZED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = fmtSignedMoney(position.upnl),
                        color = pnlColor,
                        style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$qtyText @ ${fmtMoney(position.entry)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "SL ${fmtMoney(position.stop)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1
                )
            }
        }
    }
}
