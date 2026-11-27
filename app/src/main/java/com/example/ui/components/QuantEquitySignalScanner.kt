package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.QuantScannerPreset
import com.example.domain.engine.QuantScannerUiState
import com.example.domain.engine.QuantSignalScannerEngine
import com.example.domain.model.MarketRegime
import com.example.domain.model.PriceAlert
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalRow
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reusable Quantitative Equity Signal Scanner UI Component.
 * Fetches live or simulated equity bars/quotes on demand and renders real-time
 * multi-factor quantitative signals, inline price sparklines, factor score strips,
 * and execution levels (Entry, Stop, Target, Net R:R, Conviction).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantEquitySignalScanner(
    scannerState: QuantScannerUiState,
    rows: List<SignalRow>,
    regime: MarketRegime,
    source: String,
    onTickerInputChange: (String) -> Unit,
    onRunScan: (String?) -> Unit,
    onClearScope: () -> Unit,
    onToggleLiveStream: (Boolean) -> Unit,
    onSelectActionFilter: (SignalAction?) -> Unit,
    onSelectMinConviction: (Double) -> Unit,
    onOpenSignal: (String) -> Unit,
    alerts: List<PriceAlert> = emptyList(),
    signalAlerts: List<SignalChangeAlert> = emptyList(),
    aiNewsAlertsEnabled: Boolean = false,
    onOpenSetAlert: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedAll by remember { mutableStateOf(false) }

    val armedPriceAlertsBySym = remember(alerts) {
        alerts.filter { it.active }.groupingBy { it.symbol.uppercase(Locale.US) }.eachCount()
    }
    val armedSignalAlertsBySym = remember(signalAlerts) {
        signalAlerts.filter { it.enabled }.map { it.symbol.uppercase(Locale.US) }.toSet()
    }
    val totalArmedAlerts = remember(alerts, signalAlerts, aiNewsAlertsEnabled) {
        alerts.count { it.active } + signalAlerts.count { it.enabled } + (if (aiNewsAlertsEnabled) 1 else 0)
    }

    val snapshot = remember(
        rows,
        scannerState.activeSymbols,
        scannerState.actionFilter,
        scannerState.minConviction
    ) {
        QuantSignalScannerEngine.buildSnapshot(
            allRows = rows,
            activeSymbols = scannerState.activeSymbols,
            actionFilter = scannerState.actionFilter,
            minConviction = scannerState.minConviction
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "quant_scanner_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val streamToggleDesc = stringResource(R.string.quant_scanner_stream_toggle_desc)
    val isLiveSource = source.equals("live", ignoreCase = true)

    TerminalCard(modifier = modifier.testTag("quant_equity_signal_scanner")) {
        // Header: Title, Live Pulse, Stream Switch, Refresh Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    SectionLabel(text = stringResource(R.string.quant_scanner_title))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (scannerState.liveStreamEnabled) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                    )
                    Text(
                        text = if (scannerState.liveStreamEnabled) {
                            stringResource(R.string.quant_scanner_live_badge)
                        } else {
                            stringResource(R.string.quant_scanner_paused_badge)
                        },
                        color = if (scannerState.liveStreamEnabled) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    TagChip(
                        text = if (isLiveSource) stringResource(R.string.quant_scanner_source_live)
                        else stringResource(R.string.quant_scanner_source_demo),
                        fg = if (isLiveSource) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        bg = if (isLiveSource) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                    if (scannerState.lastUpdatedMs > 0L) {
                        Text(
                            text = timeFmt.format(Date(scannerState.lastUpdatedMs)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Switch(
                    checked = scannerState.liveStreamEnabled,
                    onCheckedChange = onToggleLiveStream,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .semantics { contentDescription = streamToggleDesc }
                        .testTag("quant_scanner_live_switch")
                )
                IconButton(
                    onClick = { onRunScan(null) },
                    enabled = !scannerState.isFetching,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_refresh_button")
                ) {
                    if (scannerState.isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.quant_scanner_refresh_desc),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (scannerState.isFetching) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .testTag("quant_scanner_loading_bar")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prominent "Set Alert" Action Bar (Price Thresholds & AI Signal Changes)
        val hasArmedAlerts = totalArmedAlerts > 0
        OutlinedButton(
            onClick = { onOpenSetAlert(null) },
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (hasArmedAlerts) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (hasArmedAlerts) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.background,
                contentColor = if (hasArmedAlerts) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("quant_scanner_set_alert_button")
        ) {
            Icon(
                imageVector = if (hasArmedAlerts) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = if (hasArmedAlerts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasArmedAlerts) {
                    stringResource(R.string.quant_scanner_set_alert_count, totalArmedAlerts)
                } else {
                    stringResource(R.string.quant_scanner_set_alert)
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "PRICE · AI SIGNAL",
                color = if (hasArmedAlerts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // On-Demand Stock Ticker Fetch Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = scannerState.tickerInput,
                onValueChange = onTickerInputChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.quant_scanner_input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (scannerState.tickerInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onTickerInputChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.quant_scanner_clear_input_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onRunScan(scannerState.tickerInput) }
                ),
                shape = RoundedCornerShape(11.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("quant_scanner_ticker_input")
            )

            Button(
                onClick = { onRunScan(scannerState.tickerInput) },
                enabled = !scannerState.isFetching,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("quant_scanner_scan_button")
            ) {
                Text(
                    text = stringResource(R.string.quant_scanner_action_scan),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Quantitative Basket Presets
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quant_scanner_presets_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (scannerState.activeSymbols.isNotEmpty()) {
                FilterChip(
                    selected = false,
                    onClick = onClearScope,
                    label = {
                        Text(
                            text = stringResource(R.string.quant_scanner_all_universe),
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_clear_scope_chip")
                )
            }
            QuantScannerPreset.entries.forEach { preset ->
                val isSelectedPreset = scannerState.activeSymbols == preset.symbols
                FilterChip(
                    selected = isSelectedPreset,
                    onClick = { onRunScan(preset.symbols.joinToString(", ")) },
                    label = {
                        Text(
                            text = preset.label,
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelectedPreset) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_preset_${preset.name.lowercase(Locale.US)}")
                )
            }
        }

        if (!scannerState.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = scannerState.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onRunScan(scannerState.tickerInput) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_retry_button")
                ) {
                    Text(
                        text = stringResource(R.string.quant_scanner_retry),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Quantitative Telemetry Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = stringResource(R.string.quant_scanner_metric_scanned),
                value = snapshot.scopedTotalCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.quant_scanner_metric_buys),
                value = snapshot.buyCount.toString(),
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.quant_scanner_metric_sells),
                value = snapshot.sellCount.toString(),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.quant_scanner_metric_avg_rr),
                value = if (snapshot.avgNetRr > 0.0) "${fmtNum(snapshot.avgNetRr, 2)}R" else "—",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action & Conviction Filter Bar
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quant_scanner_filter_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val actions: List<Pair<SignalAction?, String>> = listOf(
                null to "ALL",
                SignalAction.BUY to "BUY",
                SignalAction.SELL to "SELL",
                SignalAction.HOLD to "HOLD"
            )
            actions.forEach { (action, label) ->
                val selected = scannerState.actionFilter == action
                FilterChip(
                    selected = selected,
                    onClick = { onSelectActionFilter(action) },
                    label = {
                        Text(
                            text = label,
                            style = MonoTextStyle.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_action_${label.lowercase(Locale.US)}")
                )
            }

            val convGates = listOf(
                0.0 to stringResource(R.string.quant_scanner_conv_all),
                0.6 to stringResource(R.string.quant_scanner_conv_med),
                1.2 to stringResource(R.string.quant_scanner_conv_high)
            )
            convGates.forEach { (gate, label) ->
                val selected = kotlin.math.abs(scannerState.minConviction - gate) < 0.01
                FilterChip(
                    selected = selected,
                    onClick = { onSelectMinConviction(gate) },
                    label = {
                        Text(
                            text = label,
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quant_scanner_conv_${(gate * 10).roundToInt()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        Spacer(modifier = Modifier.height(8.dp))

        // Live Quantitative Signal Rows
        if (snapshot.visibleSignals.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.quant_scanner_empty_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.quant_scanner_empty_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            val displayList = if (expandedAll) snapshot.visibleSignals else snapshot.visibleSignals.take(5)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayList.forEach { signal ->
                    val symKey = signal.symbol.uppercase(Locale.US)
                    val symPriceCount = armedPriceAlertsBySym[symKey] ?: 0
                    val symSignalArmed = armedSignalAlertsBySym.contains(symKey)
                    QuantSignalLiveRow(
                        signal = signal,
                        regime = regime,
                        armedAlertCount = symPriceCount + (if (symSignalArmed) 1 else 0),
                        hasAiSignalAlert = symSignalArmed,
                        onSetAlert = { onOpenSetAlert(signal.symbol) },
                        onClick = { onOpenSignal(signal.symbol) }
                    )
                }
            }

            if (snapshot.visibleSignals.size > 5) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { expandedAll = !expandedAll },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("quant_scanner_toggle_expand_button")
                ) {
                    Text(
                        text = if (expandedAll) {
                            stringResource(R.string.quant_scanner_show_less)
                        } else {
                            stringResource(R.string.quant_scanner_show_all, snapshot.visibleSignals.size)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantSignalLiveRow(
    signal: SignalRow,
    regime: MarketRegime,
    armedAlertCount: Int,
    hasAiSignalAlert: Boolean,
    onSetAlert: () -> Unit,
    onClick: () -> Unit
) {
    val actionColor = when (signal.action) {
        SignalAction.BUY -> MaterialTheme.colorScheme.secondary
        SignalAction.SELL -> MaterialTheme.colorScheme.error
        SignalAction.HOLD -> SignalHold
    }
    val topFactors = remember(signal.sub) { QuantSignalScannerEngine.topFactors(signal, limit = 4) }
    val rsColor = if (signal.rs20 >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val netRr = signal.cost?.rrNet ?: signal.rr

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = 1.dp,
            color = when (signal.action) {
                SignalAction.BUY -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                SignalAction.SELL -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                SignalAction.HOLD -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quant_scanner_row_${signal.symbol}")
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
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
                        style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1
                    )
                    ActionChip(action = signal.action)
                    if (signal.strategyTag.isNotBlank()) {
                        TagChip(
                            text = if (signal.strategyTag == "MEAN_REV") "MR" else "MOM",
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (signal.series.size >= 2) {
                        InlineMiniSparkline(
                            series = signal.series,
                            color = actionColor,
                            modifier = Modifier.size(width = 52.dp, height = 22.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = fmtMoney(signal.price),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "RS ${fmtPctSigned(signal.rs20 * 100.0)}",
                            color = rsColor,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            if (topFactors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    topFactors.forEach { (factorLabel, score) ->
                        val fColor = when {
                            score > 0.15 -> MaterialTheme.colorScheme.secondary
                            score < -0.15 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val sign = if (score >= 0.0) "+" else ""
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(fColor.copy(alpha = 0.10f))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$factorLabel $sign${fmtNum(score, 2)}",
                                color = fColor,
                                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "E ${fmtMoney(signal.entry)} · S ${fmtMoney(signal.stop)} · T ${fmtMoney(signal.target)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val regTag = if (regime.panicGuardActive) "0.5×" else if (regime.label == RegimeLabel.RISK_ON) "ON" else "OFF"
                Text(
                    text = "${fmtNum(netRr, 2)}R · C${fmtNum(signal.conviction, 2)} · $regTag",
                    color = actionColor,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Per-Equity "Set Alert" Quick Action Row
            val isArmed = armedAlertCount > 0
            val alertDesc = stringResource(R.string.quant_scanner_set_alert_desc, signal.symbol)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasAiSignalAlert) {
                    TagChip(
                        text = "AI FLIP ALERT ON",
                        fg = MaterialTheme.colorScheme.secondary,
                        bg = MaterialTheme.colorScheme.secondaryContainer
                    )
                } else {
                    Text(
                        text = "Tap to inspect or arm push alerts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isArmed) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isArmed) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable(onClick = onSetAlert)
                        .semantics { contentDescription = alertDesc }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("quant_scanner_set_alert_${signal.symbol}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        tint = if (isArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            armedAlertCount > 1 -> stringResource(R.string.quant_scanner_alert_on_count, armedAlertCount)
                            armedAlertCount == 1 -> stringResource(R.string.quant_scanner_alert_on)
                            else -> stringResource(R.string.quant_scanner_set_alert)
                        },
                        color = if (isArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMiniSparkline(
    series: List<Double>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val minVal = series.min()
        val maxVal = series.max()
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val w = size.width
        val h = size.height
        val pad = 2.dp.toPx()

        fun px(i: Int): Float = pad + (i.toFloat() / (series.size - 1)) * (w - pad * 2f)
        fun py(v: Double): Float = h - pad - (((v - minVal) / range).toFloat() * (h - pad * 2f))

        val path = Path().apply {
            moveTo(px(0), py(series[0]))
            for (i in 1 until series.size) {
                lineTo(px(i), py(series[i]))
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = color,
            radius = 2.2.dp.toPx(),
            center = Offset(px(series.size - 1), py(series.last()))
        )
    }
}
