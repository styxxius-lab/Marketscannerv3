package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.CryptoBacktestSummary
import com.example.domain.model.CryptoTraderSnapshot
import com.example.domain.model.DecayVerdict
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

/**
 * CryptoTrader 2.0 Risk Guards, Multi-Horizon TSMOM Scanner, and Deflated-Sharpe / PBO
 * Grid Backtest console shown when MarketMode.CRYPTO is active.
 */
@Composable
fun CryptoTrader2DashboardCard(
    snapshot: CryptoTraderSnapshot,
    backtest: CryptoBacktestSummary?,
    backtestRunning: Boolean,
    onRunBacktest: () -> Unit,
    onRefreshPeg: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btc = snapshot.btcRegime
    val heat = snapshot.portfolioHeat
    val peg = snapshot.pegStatus
    val decay = snapshot.decayReport

    val btcFg = when (btc.label) {
        "CRISIS" -> MaterialTheme.colorScheme.error
        "RISK_OFF", "ELEVATED" -> MaterialTheme.colorScheme.tertiary
        "RISK_ON" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pegFg = when {
        peg.anyBreach -> MaterialTheme.colorScheme.error
        peg.rows.any { it.price != null } -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val heatFg = when {
        heat.breach -> MaterialTheme.colorScheme.error
        heat.pctOfEquity >= heat.capPct * 0.75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val decayFg = when (decay.verdict) {
        DecayVerdict.DECAYED -> MaterialTheme.colorScheme.error
        DecayVerdict.WEAK -> MaterialTheme.colorScheme.tertiary
        DecayVerdict.OK -> MaterialTheme.colorScheme.secondary
        DecayVerdict.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    TerminalCard(modifier = modifier.testTag("crypto_trader2_dashboard_card")) {
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
                    imageVector = Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.crypto2_title))
            }
            IconButton(
                onClick = onRefreshPeg,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("crypto2_refresh_peg_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.crypto2_refresh_peg_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.crypto2_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2x2 Live Safety Guards Grid (BTC Regime, Stablecoin Peg, Portfolio Heat, Edge Decay)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuardMetricBox(
                    label = "BTC REGIME",
                    value = "${btc.label} ${String.format(Locale.US, "%.2f", btc.mult)}×",
                    detail = if (btc.btcPrice > 0.0) {
                        "EMA200 ${fmtMoney(btc.btcEma200)}"
                    } else "awaiting BTC bars",
                    accentColor = btcFg,
                    modifier = Modifier.weight(1f)
                )
                val pegSummary = peg.rows.firstOrNull { it.price != null }?.let { row ->
                    val sign = if ((row.devBps ?: 0.0) >= 0.0) "+" else ""
                    if (peg.anyBreach) "BREACH" else "$sign${String.format(Locale.US, "%.1f", row.devBps ?: 0.0)}bp"
                } ?: "CHECKING"
                val pegDetail = peg.rows.joinToString(" · ") { r ->
                    val base = r.symbol.substringBefore("/")
                    if (r.price != null) "$base $${String.format(Locale.US, "%.4f", r.price)}"
                    else "$base —"
                }.ifBlank { "USDT · USDC" }
                GuardMetricBox(
                    label = "STABLECOIN PEG",
                    value = pegSummary,
                    detail = pegDetail,
                    accentColor = pegFg,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuardMetricBox(
                    label = "PORTFOLIO HEAT",
                    value = "${String.format(Locale.US, "%.1f", heat.pctOfEquity)}% / ${String.format(Locale.US, "%.0f", heat.capPct)}%",
                    detail = "Headroom ${fmtMoney(heat.headroomUsd)}",
                    accentColor = heatFg,
                    modifier = Modifier.weight(1f)
                )
                GuardMetricBox(
                    label = "EDGE DECAY",
                    value = decay.verdict.name,
                    detail = decay.tStat?.let { t ->
                        "t=${if (t >= 0) "+" else ""}${String.format(Locale.US, "%.2f", t)} (${decay.sampleCount} trades)"
                    } ?: decay.note,
                    accentColor = decayFg,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Feed freshness & adverse event summary strip
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val freshCount = snapshot.feedFreshness.count { !it.stale }
            val totalFeeds = snapshot.feedFreshness.size
            TagChip(
                text = if (snapshot.stalePairCount > 0) "${snapshot.stalePairCount} STALE FEEDS"
                else if (totalFeeds > 0) "$freshCount/$totalFeeds FEEDS FRESH"
                else "48H FEED GUARD",
                fg = if (snapshot.stalePairCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                bg = if (snapshot.stalePairCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            )
            TagChip(
                text = if (snapshot.adverseEventsCount > 0) "${snapshot.adverseEventsCount} EVENT GATE HIT"
                else "EVENT GATE CLEAR",
                fg = if (snapshot.adverseEventsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                bg = if (snapshot.adverseEventsCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            )
        }

        // Multi-Horizon TSMOM Top Ranked Pairs
        if (snapshot.tsmomSignals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(text = "TSMOM 7/30/90D RANKING")
                Text(
                    text = "${snapshot.qualifiedSymbols.size} qualified",
                    color = if (snapshot.qualifiedSymbols.isNotEmpty()) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            snapshot.tsmomSignals.take(5).forEachIndexed { idx, sig ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sig.symbol,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.weight(1.1f),
                        maxLines = 1
                    )
                    Text(
                        text = "${if (sig.score >= 0) "+" else ""}${String.format(Locale.US, "%.2f", sig.score)}",
                        color = when {
                            sig.score >= 0.60 -> MaterialTheme.colorScheme.secondary
                            sig.score <= -0.20 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(0.75f),
                        maxLines = 1
                    )
                    Text(
                        text = "Vol ${(sig.annVol * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp),
                        modifier = Modifier.weight(0.85f),
                        maxLines = 1
                    )
                    val stackLabel = when {
                        sig.qualified -> "STACK ✓"
                        sig.trendStack -> "STACK"
                        sig.aboveEma50 -> "EMA50"
                        else -> "BELOW"
                    }
                    TagChip(
                        text = stackLabel,
                        fg = when {
                            sig.qualified -> MaterialTheme.colorScheme.secondary
                            sig.aboveEma50 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        bg = when {
                            sig.qualified -> MaterialTheme.colorScheme.secondaryContainer
                            sig.aboveEma50 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                }
                if (idx < minOf(4, snapshot.tsmomSignals.lastIndex)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                }
            }
        }

        // Interactive 9-Variant Backtest + DSR + PBO Runner
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

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
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.crypto2_backtest_title))
            }
            if (backtest != null) {
                TagChip(
                    text = if (backtest.passed) "PASS" else "FAIL",
                    fg = if (backtest.passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    bg = if (backtest.passed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRunBacktest,
            enabled = !backtestRunning,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("crypto2_run_backtest_button")
        ) {
            if (backtestRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.crypto2_backtest_running),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                Text(
                    text = stringResource(R.string.crypto2_backtest_cta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (backtest != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crypto2_backtest_results")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Best: max_pos=${backtest.bestMaxPos} · vol ${(backtest.bestTargetVol * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "${backtest.sessions}d · ${backtest.pairCount} pairs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "NET SHARPE",
                            value = "${if (backtest.netSharpe >= 0) "+" else ""}${String.format(Locale.US, "%.2f", backtest.netSharpe)}",
                            valueColor = if (backtest.netSharpe > 1.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "CAGR",
                            value = fmtPctSigned(backtest.cagrPct),
                            valueColor = if (backtest.cagrPct >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "MAX DD",
                            value = "${String.format(Locale.US, "%.1f", backtest.maxDrawdownPct)}%",
                            valueColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "DEFLATED SR",
                            value = String.format(Locale.US, "%.3f", backtest.deflatedSharpe),
                            valueColor = if (backtest.deflatedSharpe > 0.95) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "PBO (<0.50)",
                            value = String.format(Locale.US, "%.3f", backtest.pbo),
                            valueColor = if (backtest.pbo < 0.50) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "FEES+SLIP",
                            value = "${(backtest.feeBps + backtest.slipBps).toInt()}bp/side",
                            valueColor = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (backtest.passed) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = if (backtest.passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(14.dp)
                        )
                        Text(
                            text = if (backtest.passed) {
                                stringResource(R.string.crypto2_backtest_pass_note)
                            } else {
                                stringResource(R.string.crypto2_backtest_fail_note)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardMetricBox(
    label: String,
    value: String,
    detail: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = accentColor,
            style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
