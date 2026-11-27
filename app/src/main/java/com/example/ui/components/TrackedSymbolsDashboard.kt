package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.engine.AiVerdictTone
import com.example.domain.engine.DashboardSymbolFilter
import com.example.domain.engine.RegimeAlignmentStatus
import com.example.domain.engine.TrackedSymbolDashboardEngine
import com.example.domain.engine.TrackedSymbolDashboardRow
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalRow
import com.example.domain.model.VixSnapshot
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import java.util.Locale
import kotlin.math.abs

/**
 * Primary Material 3 UI dashboard displaying tracked symbols alongside their current
 * market regime status, alignment badge, and recent AI news verdict scores.
 */
@Composable
fun TrackedSymbolsDashboard(
    rows: List<SignalRow>,
    regime: MarketRegime,
    watchlist: List<String>,
    heldSymbols: Set<String>,
    vixSnapshot: VixSnapshot?,
    advisorEnabled: Boolean,
    advisorVerdicts: Map<String, AdvisorVetoVerdict>,
    advisorCheckingSymbol: String?,
    onOpenSymbol: (String) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onCheckAdvisorVeto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(DashboardSymbolFilter.TRACKED) }
    var showAllExpanded by remember { mutableStateOf(false) }

    val allDashboardRows = remember(rows, regime, watchlist, heldSymbols, advisorVerdicts, advisorEnabled, vixSnapshot) {
        TrackedSymbolDashboardEngine.buildRows(
            rows = rows,
            regime = regime,
            watchlist = watchlist,
            heldSymbols = heldSymbols,
            advisorVerdicts = advisorVerdicts,
            advisorEnabled = advisorEnabled,
            vixSnapshot = vixSnapshot
        )
    }

    val visibleRows = remember(allDashboardRows, activeFilter) {
        TrackedSymbolDashboardEngine.selectForFilter(
            allRows = allDashboardRows,
            filter = activeFilter,
            maxTrackedFallback = 6
        )
    }

    val summary = remember(visibleRows, regime) {
        TrackedSymbolDashboardEngine.summarize(visibleRows, regime)
    }

    val displayLimit = if (showAllExpanded) 20 else 5
    val displayedRows = remember(visibleRows, displayLimit) { visibleRows.take(displayLimit) }

    val regimeColor = regimeAccentColor(regime)
    val hasExplicitTracked = remember(watchlist, heldSymbols) {
        watchlist.isNotEmpty() || heldSymbols.isNotEmpty()
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f))
            .testTag("tracked_symbols_dashboard"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title, Live Regime Pill, and AI Verdict Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        SectionLabel(text = stringResource(R.string.dashboard_tracked_title))
                        Text(
                            text = if (hasExplicitTracked && activeFilter == DashboardSymbolFilter.TRACKED) {
                                stringResource(R.string.dashboard_tracked_subtitle_custom, visibleRows.size)
                            } else {
                                stringResource(R.string.dashboard_tracked_subtitle_top, visibleRows.size)
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Active Market Regime Status Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = regimeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, regimeColor.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("dashboard_regime_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(regimeColor)
                        )
                        Text(
                            text = if (regime.panicGuardActive) {
                                "PANIC · 0.5×"
                            } else {
                                "${regime.label.name} · ${"%.2f".format(Locale.US, regime.longMult)}×"
                            },
                            color = regimeColor,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            // Compact AI News Verdict Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = stringResource(R.string.dashboard_metric_ai_bull),
                    value = summary.bullishAiCount.toString(),
                    valueColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = stringResource(R.string.dashboard_metric_ai_bear),
                    value = summary.bearishAiCount.toString(),
                    valueColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = stringResource(R.string.dashboard_metric_vetoed),
                    value = summary.vetoedCount.toString(),
                    valueColor = if (summary.vetoedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = stringResource(R.string.dashboard_metric_scored),
                    value = "${summary.scoredCount}/${summary.totalTracked}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Material 3 FilterChip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DashboardSymbolFilter.entries.forEach { filterOption ->
                    val selected = activeFilter == filterOption
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeFilter = filterOption
                            showAllExpanded = false
                        },
                        label = {
                            Text(
                                text = filterOption.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_filter_${filterOption.name.lowercase()}")
                    )
                }
            }

            // Tracked Symbol Cards List
            if (displayedRows.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_empty_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_empty_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayedRows.forEach { item ->
                        TrackedSymbolRowCard(
                            item = item,
                            isCheckingVeto = advisorCheckingSymbol == item.signal.symbol,
                            onOpen = { onOpenSymbol(item.signal.symbol) },
                            onToggleWatchlist = { onToggleWatchlist(item.signal.symbol) },
                            onCheckVeto = { onCheckAdvisorVeto(item.signal.symbol) }
                        )
                    }
                }

                AnimatedVisibility(visible = visibleRows.size > 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showAllExpanded = !showAllExpanded },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_expand_toggle")
                        ) {
                            Text(
                                text = if (showAllExpanded) {
                                    "SHOW TOP 5"
                                } else {
                                    "SHOW MORE (${visibleRows.size - displayedRows.size} MORE)"
                                },
                                color = MaterialTheme.colorScheme.primary,
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
    }
}

@Composable
private fun TrackedSymbolRowCard(
    item: TrackedSymbolDashboardRow,
    isCheckingVeto: Boolean,
    onOpen: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onCheckVeto: () -> Unit
) {
    val signal = item.signal
    val regimeColor = regimeAlignmentColor(item.regimeAlignment)
    val verdictColor = aiVerdictColor(item.aiVerdictTone)

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.62f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.aiVerdictTone == AiVerdictTone.VETOED) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_symbol_row_${signal.symbol}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Ticker + Action + Held badge + Price + Watchlist Star
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
                    Text(
                        text = signal.symbol,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ActionChip(action = signal.action)
                    if (item.isHeld) {
                        TagChip(
                            text = "HELD",
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = fmtMoney(signal.price),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        val rsColor = if (signal.rs20 >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        Text(
                            text = "RS ${fmtPctSigned(signal.rs20 * 100.0)}",
                            color = rsColor,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_star_${signal.symbol}")
                    ) {
                        Icon(
                            imageVector = if (item.isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (item.isWatchlisted) {
                                "Remove ${signal.symbol} from tracked watchlist"
                            } else {
                                "Add ${signal.symbol} to tracked watchlist"
                            },
                            tint = if (item.isWatchlisted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Middle Row: Market Regime Status Badge + Alignment + Advisor Veto Action Button
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
                    TagChip(
                        text = item.regimeStatusText,
                        fg = regimeColor,
                        bg = regimeColor.copy(alpha = 0.14f)
                    )
                    TagChip(
                        text = item.regimeAlignment.label,
                        fg = MaterialTheme.colorScheme.onSurfaceVariant,
                        bg = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = onCheckVeto,
                    enabled = !isCheckingVeto,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = verdictColor.copy(alpha = 0.14f),
                        contentColor = verdictColor
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dashboard_veto_check_${signal.symbol}")
                ) {
                    if (isCheckingVeto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = verdictColor
                        )
                    } else {
                        Icon(
                            imageVector = when (item.aiVerdictTone) {
                                AiVerdictTone.VETOED -> Icons.Outlined.GppBad
                                AiVerdictTone.CLEAR, AiVerdictTone.BULLISH -> Icons.Outlined.GppGood
                                else -> Icons.Outlined.FactCheck
                            },
                            contentDescription = "Screen AI news verdict for ${signal.symbol}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            // Bottom Block: Recent AI News Verdict Score + Progress Bar + Rationale
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "${signal.symbol} AI news verdict ${item.aiVerdictTone.label}, score ${item.aiNewsScore}, advisor ${item.advisorTag}"
                    },
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = when (item.aiVerdictTone) {
                                AiVerdictTone.BULLISH -> Icons.Outlined.TrendingUp
                                AiVerdictTone.BEARISH, AiVerdictTone.VETOED -> Icons.Outlined.TrendingDown
                                else -> Icons.Outlined.Newspaper
                            },
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "AI NEWS VERDICT",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            )
                        )
                        TagChip(
                            text = "llm=${item.advisorTag}",
                            fg = verdictColor,
                            bg = verdictColor.copy(alpha = 0.14f)
                        )
                    }

                    val scoreLabel = when (item.aiVerdictTone) {
                        AiVerdictTone.VETOED -> {
                            val conf = ((item.advisorVerdict?.weight ?: 0.0) * 100.0).toInt()
                            "VETO $conf%"
                        }
                        AiVerdictTone.UNSCORED -> "0 · NO NEWS"
                        AiVerdictTone.CLEAR -> "CLEAR · 18H"
                        else -> {
                            val sign = if (item.aiNewsScore > 0) "+" else ""
                            "$sign${item.aiNewsScore} · ${item.aiVerdictTone.label}"
                        }
                    }
                    Text(
                        text = scoreLabel,
                        color = verdictColor,
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }

                val progress = when (item.aiVerdictTone) {
                    AiVerdictTone.VETOED -> (item.advisorVerdict?.weight ?: 0.85).toFloat().coerceIn(0.1f, 1f)
                    AiVerdictTone.UNSCORED -> 0.04f
                    AiVerdictTone.CLEAR -> 0.50f
                    else -> (abs(item.aiNewsScore) / 100f).coerceIn(0.08f, 1f)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = verdictColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                val detailText = if (item.recentHeadline.isNotBlank()) {
                    item.recentHeadline
                } else {
                    item.verdictSummary
                }
                if (detailText.isNotBlank()) {
                    Text(
                        text = detailText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.5.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun regimeAccentColor(regime: MarketRegime): Color = when {
    regime.panicGuardActive -> MaterialTheme.colorScheme.error
    regime.label == RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
    regime.label == RegimeLabel.RISK_OFF || regime.label == RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun regimeAlignmentColor(alignment: RegimeAlignmentStatus): Color = when (alignment) {
    RegimeAlignmentStatus.ALIGNED -> MaterialTheme.colorScheme.secondary
    RegimeAlignmentStatus.PANIC_GUARD -> MaterialTheme.colorScheme.error
    RegimeAlignmentStatus.GATED_MR -> MaterialTheme.colorScheme.tertiary
    RegimeAlignmentStatus.DEFENSIVE -> MaterialTheme.colorScheme.error
    RegimeAlignmentStatus.NEUTRAL -> MaterialTheme.colorScheme.primary
}

@Composable
private fun aiVerdictColor(tone: AiVerdictTone): Color = when (tone) {
    AiVerdictTone.VETOED -> MaterialTheme.colorScheme.error
    AiVerdictTone.BULLISH -> MaterialTheme.colorScheme.secondary
    AiVerdictTone.BEARISH -> MaterialTheme.colorScheme.error
    AiVerdictTone.MIXED -> MaterialTheme.colorScheme.tertiary
    AiVerdictTone.CLEAR -> MaterialTheme.colorScheme.primary
    AiVerdictTone.UNSCORED -> SignalHold
}
