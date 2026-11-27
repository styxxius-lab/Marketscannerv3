package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.SignalAlertMode
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalRow
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SignalChangeAlertsSection(
    selectedSymbol: String,
    rows: List<SignalRow>,
    rowLookup: Map<String, SignalRow>,
    signalAlerts: List<SignalChangeAlert>,
    aiNewsAlertsEnabled: Boolean,
    onSelectSymbol: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onToggleSignalAlert: (String, Boolean, SignalAlertMode?) -> Unit,
    onUpdateSignalAlertMode: (String, SignalAlertMode) -> Unit,
    onRemoveSignalAlert: (String) -> Unit,
    onToggleGlobalAiNewsAlerts: (Boolean) -> Unit
) {
    val cleanSym = selectedSymbol.trim().uppercase(Locale.US)
    val selectedRow = rowLookup[cleanSym]
    val existingForSymbol = remember(signalAlerts, cleanSym) {
        signalAlerts.firstOrNull { it.symbol.equals(cleanSym, ignoreCase = true) }
    }
    val selectedEnabled = existingForSymbol?.enabled == true
    val selectedMode = existingForSymbol?.mode ?: SignalAlertMode.ANY_CHANGE
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Per-Ticker AI Signal Change Push Toggle Card
    TerminalCard(modifier = Modifier.testTag("ai_signal_alert_config_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.set_alert_signal_ticker_title))
            }
            if (selectedRow != null) {
                ActionChip(action = selectedRow.action)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Ticker Chips
        val quickSymbols = remember(rows) { rows.take(10) }
        if (quickSymbols.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickSymbols.forEach { r ->
                    val isSelected = cleanSym == r.symbol.uppercase(Locale.US)
                    val isArmed = signalAlerts.any { it.enabled && it.symbol.equals(r.symbol, ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isArmed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                                else if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSymbol(r.symbol) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("signal_alert_chip_${r.symbol}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isArmed) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = r.symbol,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Selected Symbol Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(
                    width = 1.dp,
                    color = if (selectedEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(11.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = if (cleanSym.isNotEmpty()) "$cleanSym AI Signal Flip Push" else "Select an equity above",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (cleanSym.isNotEmpty()) {
                        stringResource(R.string.set_alert_signal_ticker_desc, cleanSym)
                    } else {
                        "Pick a ticker to enable push alerts on BUY/SELL/HOLD flips."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = selectedEnabled,
                enabled = cleanSym.isNotEmpty(),
                onCheckedChange = { enabled ->
                    if (enabled) onRequestNotificationPermission()
                    onToggleSignalAlert(cleanSym, enabled, selectedMode)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_ai_signal_alert_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trigger Condition Mode Selector (ANY FLIP / → BUY / → SELL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SignalAlertMode.entries.forEach { mode ->
                val active = selectedMode == mode
                val accent = when (mode) {
                    SignalAlertMode.BUY_ONLY -> MaterialTheme.colorScheme.secondary
                    SignalAlertMode.SELL_ONLY -> MaterialTheme.colorScheme.error
                    SignalAlertMode.ANY_CHANGE -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) accent.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (active) accent else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = cleanSym.isNotEmpty()) {
                            onRequestNotificationPermission()
                            onUpdateSignalAlertMode(cleanSym, mode)
                        }
                        .testTag("signal_alert_mode_${mode.name.lowercase(Locale.US)}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.shortLabel,
                        color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }

    // Global AI News Catalyst Shift Push Toggle Card
    TerminalCard(modifier = Modifier.testTag("ai_news_shift_quick_toggle_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = stringResource(R.string.set_alert_signal_global_news_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.set_alert_signal_global_news_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = aiNewsAlertsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) onRequestNotificationPermission()
                    onToggleGlobalAiNewsAlerts(enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sheet_ai_news_alerts_switch")
            )
        }
    }

    // Active & Paused Per-Ticker AI Signal Watches
    TerminalCard {
        SectionLabel(text = stringResource(R.string.set_alert_signal_list_header))
        Spacer(modifier = Modifier.height(8.dp))
        if (signalAlerts.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.set_alert_signal_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            signalAlerts.forEachIndexed { idx, alert ->
                val liveRow = rowLookup[alert.symbol.uppercase(Locale.US)]
                val modeColor = when (alert.mode) {
                    SignalAlertMode.BUY_ONLY -> MaterialTheme.colorScheme.secondary
                    SignalAlertMode.SELL_ONLY -> MaterialTheme.colorScheme.error
                    SignalAlertMode.ANY_CHANGE -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("ai_signal_watch_row_${alert.symbol}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = alert.symbol,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            TagChip(
                                text = alert.mode.shortLabel,
                                fg = modeColor,
                                bg = modeColor.copy(alpha = 0.14f)
                            )
                            if (liveRow != null) {
                                ActionChip(action = liveRow.action)
                            }
                        }
                        val subLine = when {
                            alert.lastTransitionLabel != null && alert.lastTriggeredAtMs != null -> {
                                "Last flip: ${alert.lastTransitionLabel} · ${timeFormatter.format(Date(alert.lastTriggeredAtMs))}"
                            }
                            liveRow != null -> {
                                "Watching from ${liveRow.action.name} @ ${fmtMoney(liveRow.price)}"
                            }
                            else -> {
                                if (alert.enabled) "Armed for next AI signal change" else "Paused"
                            }
                        }
                        Text(
                            text = subLine,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 11.sp)
                        )
                    }
                    Switch(
                        checked = alert.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) onRequestNotificationPermission()
                            onToggleSignalAlert(alert.symbol, enabled, alert.mode)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_signal_row_switch_${alert.symbol}")
                    )
                    IconButton(
                        onClick = { onRemoveSignalAlert(alert.id) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_signal_alert_button_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete AI signal alert for ${alert.symbol}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (idx < signalAlerts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
