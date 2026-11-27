package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BotState
import com.example.domain.model.SignalRow
import com.example.domain.model.ThreatLevel
import com.example.engine.ThreatCard
import com.example.ui.SimulatorUiState
import com.example.ui.ThreatScanState
import com.example.ui.screens.ThreatCardOutputComponent
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SimulatorThreatScannerCard(
    uiState: SimulatorUiState,
    botState: BotState,
    rows: List<SignalRow>,
    watchlist: List<String>,
    onSymbolInputChange: (String) -> Unit,
    onScanSymbol: (String) -> Unit,
    onToggleLiveMonitoring: (Boolean) -> Unit,
    onSelectRecentCard: (ThreatCard) -> Unit,
    onExitPosition: (String) -> Unit,
    onTrimPosition: (String, Double) -> Unit,
    onApplySuggestedStop: (String, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val quickSymbols = remember(botState.positions, watchlist, rows) {
        (botState.positions.map { it.symbol } +
            watchlist +
            listOf("NVDA", "SMCI", "TSLA", "META", "AAPL", "SPY") +
            rows.take(4).map { it.symbol })
            .distinct()
            .take(8)
    }

    TerminalCard(
        modifier = modifier.testTag("simulator_threat_scanner_card")
    ) {
        // Header Row + Real-Time Toggle
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
                    imageVector = Icons.Outlined.Radar,
                    contentDescription = "Real-time Threat Engine Scanner",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    SectionLabel(text = "REAL-TIME THREAT SCANNER")
                    val tsText = uiState.lastUpdatedMs?.let {
                        "Updated ${timeFormatter.format(Date(it))}"
                    } ?: "Quant + Narrative + Contagion"
                    Text(
                        text = tsText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.liveMonitoringEnabled) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                    Text(
                        text = if (uiState.liveMonitoringEnabled) "LIVE" else "PAUSED",
                        color = if (uiState.liveMonitoringEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
                Switch(
                    checked = uiState.liveMonitoringEnabled,
                    onCheckedChange = onToggleLiveMonitoring,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("simulator_live_monitor_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ticker Search + Scan Trigger Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.symbolInput,
                onValueChange = onSymbolInputChange,
                label = { Text("SYMBOL", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("NVDA", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onScanSymbol(uiState.symbolInput) }
                ),
                shape = RoundedCornerShape(11.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulator_threat_symbol_input")
            )

            Button(
                onClick = { onScanSymbol(uiState.symbolInput) },
                modifier = Modifier
                    .height(54.dp)
                    .testTag("simulator_threat_scan_button"),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "SCAN THREAT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }

        // Quick-pick ticker bar
        if (quickSymbols.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickSymbols.forEach { sym ->
                    val isActive = uiState.activeSymbol == sym
                    val isHeld = botState.positions.any { it.symbol == sym }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.background
                            )
                            .border(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onScanSymbol(sym) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("simulator_quick_symbol_$sym"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHeld) "$sym • HELD" else sym,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // Recent scan switchers
        if (uiState.recentCards.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp)
                )
                uiState.recentCards.forEach { recent ->
                    val col = when (recent.level) {
                        ThreatLevel.CRITICAL, ThreatLevel.HIGH -> MaterialTheme.colorScheme.error
                        ThreatLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary
                        ThreatLevel.LOW -> MaterialTheme.colorScheme.secondary
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(col.copy(alpha = 0.12f))
                            .clickable { onSelectRecentCard(recent) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${recent.symbol} ${recent.threatScore}",
                            color = col,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))

        when (val scanState = uiState.scanState) {
            is ThreatScanState.Idle -> {
                Text(
                    text = "Enter a ticker symbol above or tap a quick-pick to evaluate price shock, headline taxonomy, sector contagion, and exit/trim risk rules.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            is ThreatScanState.Scanning -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scanning ${scanState.symbol} tape shock, headline taxonomy & sector peers…",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is ThreatScanState.Error -> {
                BannerMessage(
                    text = scanState.message,
                    isWarning = true,
                    modifier = Modifier.testTag("simulator_threat_error_banner")
                )
            }

            is ThreatScanState.Success -> {
                val card = scanState.card
                val heldPos = botState.positions.find { it.symbol.equals(card.symbol, ignoreCase = true) }
                ThreatCardOutputComponent(
                    card = card,
                    currentStop = heldPos?.stop,
                    isHolding = heldPos != null,
                    onExitPosition = { onExitPosition(card.symbol) },
                    onTrimPosition = { onTrimPosition(card.symbol, 0.5) },
                    onApplySuggestedStop = { stopPx -> onApplySuggestedStop(card.symbol, stopPx) }
                )
            }
        }
    }
}
