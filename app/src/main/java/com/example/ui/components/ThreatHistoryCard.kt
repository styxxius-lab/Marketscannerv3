package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ThreatHistoryEntry
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatLevel
import com.example.ui.screens.ThreatDriversList
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays a persistent SQLite/Room log of past [com.example.engine.ThreatCard] scan assessments
 * with symbol filtering, expandable diagnostics, rescan trigger, and per-entry/clear deletion.
 */
@Composable
fun ThreatHistoryCard(
    entries: List<ThreatHistoryEntry>,
    activeSymbolFilter: String?,
    watchlist: List<String>,
    heldSymbols: List<String>,
    onSelectSymbolFilter: (String?) -> Unit,
    onRescanSymbol: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault()) }
    var showAll by remember { mutableStateOf(false) }

    val filterSymbols = remember(entries, watchlist, heldSymbols) {
        (entries.map { it.symbol } + heldSymbols + watchlist)
            .map { it.uppercase(Locale.US) }
            .distinct()
            .take(10)
    }

    val filteredEntries = remember(entries, activeSymbolFilter) {
        if (activeSymbolFilter == null) entries
        else entries.filter { it.symbol.equals(activeSymbolFilter, ignoreCase = true) }
    }

    val visibleEntries = if (showAll) filteredEntries else filteredEntries.take(8)

    TerminalCard(
        modifier = modifier.testTag("simulator_threat_history_card")
    ) {
        // Header Row
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
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Threat History log",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                SectionLabel(text = "THREAT HISTORY")
                TagChip(
                    text = "${filteredEntries.size} LOGGED",
                    fg = MaterialTheme.colorScheme.primary,
                    bg = MaterialTheme.colorScheme.primaryContainer
                )
            }

            if (filteredEntries.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("threat_history_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear threat history",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (activeSymbolFilter != null) "CLEAR $activeSymbolFilter" else "CLEAR",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Symbol Filter Strip
        if (filterSymbols.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryFilterPill(
                    label = "ALL (${entries.size})",
                    selected = activeSymbolFilter == null,
                    onClick = { onSelectSymbolFilter(null) },
                    testTag = "threat_history_filter_ALL"
                )
                filterSymbols.forEach { sym ->
                    val count = entries.count { it.symbol.equals(sym, ignoreCase = true) }
                    HistoryFilterPill(
                        label = if (count > 0) "$sym ($count)" else sym,
                        selected = activeSymbolFilter.equals(sym, ignoreCase = true),
                        onClick = {
                            onSelectSymbolFilter(
                                if (activeSymbolFilter.equals(sym, ignoreCase = true)) null else sym
                            )
                        },
                        testTag = "threat_history_filter_$sym"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        if (filteredEntries.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("threat_history_empty"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Radar,
                    contentDescription = "No logged threat assessments",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (activeSymbolFilter != null) {
                        "No ThreatCard scans recorded for $activeSymbolFilter yet. Run a scan above to log one locally."
                    } else {
                        "No past ThreatCard results logged yet. Scan any symbol above to record assessments to local database storage."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleEntries.forEach { entry ->
                    ThreatHistoryRow(
                        entry = entry,
                        formattedTime = timeFmt.format(Date(entry.timestampMs)),
                        onRescan = { onRescanSymbol(entry.symbol) },
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                }

                if (filteredEntries.size > 8) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("threat_history_show_more_button")
                    ) {
                        Text(
                            text = if (showAll) "SHOW RECENT 8" else "SHOW ALL ${filteredEntries.size} LOGGED SCANS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ThreatHistoryRow(
    entry: ThreatHistoryEntry,
    formattedTime: String,
    onRescan: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val (levelFg, levelBg) = levelColors(entry.level)
    val (actionFg, actionBg) = actionColors(entry.action)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
            .clickable { expanded = !expanded }
            .padding(10.dp)
            .testTag("threat_history_item_${entry.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Threat Score Pill
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(levelBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${entry.threatScore}",
                        color = levelFg,
                        style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        text = entry.level.label.take(4),
                        color = levelFg,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.symbol,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        text = fmtMoney(entry.price),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    )
                    TagChip(text = entry.action.label, fg = actionFg, bg = actionBg)
                }

                Text(
                    text = "$formattedTime · ${entry.categoryLabel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = entry.verdict,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = if (expanded) 5 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse history details" else "Expand history details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("threat_history_delete_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${entry.symbol} log entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Top Drivers Summary Strip when collapsed
        if (!expanded && entry.drivers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entry.drivers.take(3).forEach { drv ->
                    TagChip(
                        text = "${drv.name.uppercase(Locale.US)} ${drv.score}",
                        fg = MaterialTheme.colorScheme.onSurfaceVariant,
                        bg = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Expandable Stored Diagnostics + Rescan Action
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniMetric("QUANT", "${entry.priceActionScore}/100", modifier = Modifier.weight(1f))
                    MiniMetric("NARRATIVE", "${entry.narrativeScore}/100", modifier = Modifier.weight(1f))
                    MiniMetric("RSI(14)", fmtNum(entry.rsi, 1), modifier = Modifier.weight(1f))
                    MiniMetric(
                        label = "STOP",
                        value = entry.suggestedStop?.let(::fmtMoney) ?: "—",
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }

                ThreatDriversList(drivers = entry.drivers)

                if (entry.invalidation.isNotBlank()) {
                    Text(
                        text = "Invalidation: ${entry.invalidation}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("threat_history_rescan_${entry.id}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Rescan ${entry.symbol}",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESCAN ${entry.symbol}",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun levelColors(level: ThreatLevel): Pair<Color, Color> = when (level) {
    ThreatLevel.CRITICAL, ThreatLevel.HIGH -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    ThreatLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    ThreatLevel.LOW -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun actionColors(action: ThreatAction): Pair<Color, Color> = when (action) {
    ThreatAction.EXIT_NOW, ThreatAction.SELL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    ThreatAction.TRIM -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    ThreatAction.HOLD -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
}
