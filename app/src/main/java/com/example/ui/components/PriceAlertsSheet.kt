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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AlertCondition
import com.example.domain.model.PriceAlert
import com.example.domain.model.SignalAlertMode
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalRow
import com.example.domain.model.VixSnapshot
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertsSheet(
    alerts: List<PriceAlert>,
    rows: List<SignalRow>,
    vixSnapshot: VixSnapshot?,
    vixAlertEnabled: Boolean,
    vixThresholdPct: Double,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleVixAlert: (Boolean) -> Unit,
    onUpdateVixThreshold: (Double) -> Unit,
    onAddAlert: (String, Double, AlertCondition) -> Unit,
    onRemoveAlert: (String) -> Unit,
    onRearmAlert: (String) -> Unit,
    onClearTriggered: () -> Unit,
    onDismiss: () -> Unit,
    initialSymbol: String? = null,
    signalAlerts: List<SignalChangeAlert> = emptyList(),
    aiNewsAlertsEnabled: Boolean = false,
    onTogglePriceAlert: (String) -> Unit = {},
    onToggleSignalAlert: (String, Boolean, SignalAlertMode?) -> Unit = { _, _, _ -> },
    onUpdateSignalAlertMode: (String, SignalAlertMode) -> Unit = { _, _ -> },
    onRemoveSignalAlert: (String) -> Unit = {},
    onToggleGlobalAiNewsAlerts: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rowLookup = remember(rows) { rows.associateBy { it.symbol.uppercase(Locale.US) } }

    val seedSymbol = initialSymbol?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
        ?: rows.firstOrNull()?.symbol
        ?: "NVDA"
    val seedPrice = rowLookup[seedSymbol]?.price ?: rows.firstOrNull()?.price ?: 100.0

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Price Threshold, 1 = AI Signal Change
    var symbolInput by remember { mutableStateOf(seedSymbol) }
    var thresholdInput by remember { mutableStateOf("%.2f".format(Locale.US, seedPrice * 1.02)) }
    var condition by remember { mutableStateOf(AlertCondition.ABOVE) }

    LaunchedEffect(initialSymbol) {
        val sym = initialSymbol?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        symbolInput = sym
        val livePx = rowLookup[sym]?.price
        if (livePx != null && livePx > 0.0) {
            val pct = if (condition == AlertCondition.ABOVE) 1.02 else 0.98
            thresholdInput = "%.2f".format(Locale.US, livePx * pct)
        }
    }

    val managedPriceAlerts = remember(alerts) { alerts.filter { it.active || it.triggeredAtMs == null } }
    val activePriceCount = remember(alerts) { alerts.count { it.active } }
    val activeSignalCount = remember(signalAlerts, aiNewsAlertsEnabled) {
        signalAlerts.count { it.enabled } + (if (aiNewsAlertsEnabled) 1 else 0)
    }
    val triggeredAlerts = remember(alerts) { alerts.filter { !it.active && it.triggeredAtMs != null } }
    val totalArmed = activePriceCount + activeSignalCount
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("price_alerts_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.set_alert_sheet_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp)
                        )
                        Text(
                            text = stringResource(R.string.set_alert_sheet_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TagChip(
                    text = "$totalArmed ARMED",
                    fg = if (totalArmed > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    bg = if (totalArmed > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Notification Permission Prompt Banner (Android 13+ when denied)
            if (!hasNotificationPermission) {
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System notifications disabled",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Enable Android notifications to receive price threshold & AI signal push alerts.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("enable_notifications_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "ENABLE",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
            }

            // Segmented Mode Switcher: PRICE THRESHOLD vs AI SIGNAL CHANGE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(3.dp)
                    .testTag("set_alert_mode_tabs"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    Triple(0, stringResource(R.string.set_alert_tab_price), Icons.Outlined.ShowChart to activePriceCount),
                    Triple(1, stringResource(R.string.set_alert_tab_signal), Icons.Outlined.AutoAwesome to activeSignalCount)
                )
                tabs.forEach { (idx, label, iconAndCount) ->
                    val (icon, count) = iconAndCount
                    val selected = selectedTab == idx
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 8.dp)
                            .testTag(if (idx == 0) "set_alert_tab_price" else "set_alert_tab_signal"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.size(5.dp))
                        Text(
                            text = if (count > 0) "$label ($count)" else label,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            if (selectedTab == 1) {
                SignalChangeAlertsSection(
                    selectedSymbol = symbolInput,
                    rows = rows,
                    rowLookup = rowLookup,
                    signalAlerts = signalAlerts,
                    aiNewsAlertsEnabled = aiNewsAlertsEnabled,
                    onSelectSymbol = { sym ->
                        symbolInput = sym
                        val livePx = rowLookup[sym.uppercase(Locale.US)]?.price
                        if (livePx != null && livePx > 0.0) {
                            val pct = if (condition == AlertCondition.ABOVE) 1.02 else 0.98
                            thresholdInput = "%.2f".format(Locale.US, livePx * pct)
                        }
                    },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onToggleSignalAlert = onToggleSignalAlert,
                    onUpdateSignalAlertMode = onUpdateSignalAlertMode,
                    onRemoveSignalAlert = onRemoveSignalAlert,
                    onToggleGlobalAiNewsAlerts = onToggleGlobalAiNewsAlerts
                )
            } else {
                // Create New Price Alert Card
                TerminalCard {
                    SectionLabel(text = "CREATE PRICE THRESHOLD ALERT")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Ticker Chips
                    val quickSymbols = remember(rows) { rows.take(8) }
                    if (quickSymbols.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSymbols.forEach { r ->
                                val selected = symbolInput.trim().equals(r.symbol, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            symbolInput = r.symbol
                                            val pct = if (condition == AlertCondition.ABOVE) 1.02 else 0.98
                                            thresholdInput = "%.2f".format(Locale.US, r.price * pct)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = r.symbol,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = symbolInput,
                            onValueChange = { symbolInput = it.uppercase(Locale.US) },
                            label = { Text("SYMBOL", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false
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
                                .testTag("alert_symbol_input")
                        )

                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { thresholdInput = it },
                            label = { Text("TARGET ($)", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(11.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("alert_threshold_input")
                        )
                    }

                    val matchedRow = rowLookup[symbolInput.trim().uppercase(Locale.US)]
                    if (matchedRow != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${matchedRow.symbol} live: ${fmtMoney(matchedRow.price)} · Target ${fmtMoney(matchedRow.target)} · Stop ${fmtMoney(matchedRow.stop)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // One-tap level presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = listOf(
                                Triple("+2%", matchedRow.price * 1.02, AlertCondition.ABOVE),
                                Triple("-2%", matchedRow.price * 0.98, AlertCondition.BELOW),
                                Triple("Target", matchedRow.target, AlertCondition.ABOVE),
                                Triple("Stop", matchedRow.stop, AlertCondition.BELOW)
                            )
                            presets.forEach { (presetLabel, px, presetCond) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (px > 0.0) {
                                                thresholdInput = "%.2f".format(Locale.US, px)
                                                condition = presetCond
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = presetLabel,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Condition Selector (ABOVE >= / BELOW <=)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlertCondition.entries.forEach { cond ->
                            val active = condition == cond
                            val fg = if (cond == AlertCondition.ABOVE) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (active) fg.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (active) fg else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(11.dp)
                                    )
                                    .clickable { condition = cond }
                                    .testTag("alert_cond_${cond.name.lowercase(Locale.US)}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cond.label,
                                    color = if (active) fg else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val parsedPrice = thresholdInput.toDoubleOrNull()
                    val canSubmit = symbolInput.trim().isNotEmpty() && parsedPrice != null && parsedPrice > 0.0

                    Button(
                        onClick = {
                            if (parsedPrice != null && parsedPrice > 0.0) {
                                onRequestNotificationPermission()
                                onAddAlert(symbolInput, parsedPrice, condition)
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_alert_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "SET PRICE ALERT",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }

                // Active & Paused Price Alerts Card (with Toggle Switches)
                TerminalCard {
                    SectionLabel(text = "PRICE THRESHOLD ALERTS")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (managedPriceAlerts.isEmpty()) {
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
                                text = "No price threshold alerts yet. Create one above or tap any equity in the scanner.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        managedPriceAlerts.forEachIndexed { idx, alert ->
                            val livePx = rowLookup[alert.symbol.uppercase(Locale.US)]?.price
                            val condCol = if (alert.condition == AlertCondition.ABOVE) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag("active_alert_row_${alert.symbol}"),
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
                                            text = "${alert.condition.symbolText} ${fmtMoney(alert.targetPrice)}",
                                            fg = if (alert.active) condCol else MaterialTheme.colorScheme.onSurfaceVariant,
                                            bg = if (alert.active) condCol.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                    val subtitle = if (!alert.active) {
                                        stringResource(R.string.set_alert_paused_badge)
                                    } else if (livePx != null && livePx > 0.0) {
                                        val diffPct = ((alert.targetPrice - livePx) / livePx) * 100.0
                                        "Live ${fmtMoney(livePx)} · ${fmtPctSigned(diffPct)} to target"
                                    } else {
                                        stringResource(R.string.set_alert_armed_badge)
                                    }
                                    Text(
                                        text = subtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MonoTextStyle.copy(fontSize = 11.sp)
                                    )
                                }
                                Switch(
                                    checked = alert.active,
                                    onCheckedChange = { enabled ->
                                        if (enabled) onRequestNotificationPermission()
                                        onTogglePriceAlert(alert.id)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_price_alert_switch_${alert.symbol}")
                                )
                                IconButton(
                                    onClick = { onRemoveAlert(alert.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_alert_button_${alert.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete alert for ${alert.symbol}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < managedPriceAlerts.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Triggered Alerts History Card
                if (triggeredAlerts.isNotEmpty()) {
                    TerminalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(text = "TRIGGERED")
                            TextButton(onClick = onClearTriggered) {
                                Text(
                                    text = "CLEAR ALL",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        triggeredAlerts.forEachIndexed { idx, alert ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            text = "HIT ${alert.condition.symbolText} ${fmtMoney(alert.targetPrice)}",
                                            fg = MaterialTheme.colorScheme.tertiary,
                                            bg = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    }
                                    val ts = alert.triggeredAtMs?.let { timeFormatter.format(Date(it)) } ?: ""
                                    val pxStr = fmtMoney(alert.triggeredPrice ?: alert.targetPrice)
                                    Text(
                                        text = "Triggered @ $pxStr ${if (ts.isNotEmpty()) "· $ts" else ""}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MonoTextStyle.copy(fontSize = 11.sp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRearmAlert(alert.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Re-arm alert for ${alert.symbol}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveAlert(alert.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete triggered alert for ${alert.symbol}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < triggeredAlerts.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // CBOE VIX Intraday Spike Monitor Summary
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel(text = "CBOE VIX VOLATILITY SPIKE")
                            val vixLine = if (vixSnapshot != null) {
                                "Live ${fmtNum(vixSnapshot.current, 2)} (+${fmtNum(vixSnapshot.intradaySpikePct, 1)}% intraday) · Trigger +${fmtNum(vixThresholdPct, 0)}%"
                            } else {
                                "Intraday spike push trigger: +${fmtNum(vixThresholdPct, 0)}%"
                            }
                            Text(
                                text = vixLine,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 11.sp)
                            )
                        }
                        Switch(
                            checked = vixAlertEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) onRequestNotificationPermission()
                                onToggleVixAlert(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sheet_vix_alert_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5.0, 10.0, 15.0, 20.0).forEach { pct ->
                            val active = kotlin.math.abs(vixThresholdPct - pct) < 0.05
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onUpdateVixThreshold(pct) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${pct.toInt()}%",
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
