package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AlertCondition
import com.example.domain.model.BotState
import com.example.domain.model.PriceAlert
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatLevel
import com.example.ui.theme.MonoTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalDetailSheet(
    signal: SignalRow,
    botState: BotState,
    symbolAlerts: List<PriceAlert>,
    isWatchlisted: Boolean,
    threatAssessment: ThreatAssessment? = null,
    isAssessingThreat: Boolean = false,
    onToggleWatchlist: (String) -> Unit,
    onPaperBuy: (SignalRow) -> Unit,
    onRefreshThreat: (String) -> Unit = {},
    onClosePosition: (String) -> Unit = {},
    onTrimPosition: (String, Double) -> Unit = { _, _ -> },
    onApplySuggestedStop: (String, Double) -> Unit = { _, _ -> },
    onAddAlert: (String, Double, AlertCondition) -> Unit,
    onRemoveAlert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chartColor = if (signal.action == SignalAction.SELL) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("signal_detail_sheet")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = signal.symbol,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp)
                    )
                    ActionChip(action = signal.action)
                    TagChip(
                        text = signal.sector.label.uppercase(),
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                    IconButton(
                        onClick = { onToggleWatchlist(signal.symbol) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_watchlist_star_${signal.symbol}")
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isWatchlisted) {
                                "Remove ${signal.symbol} from watchlist"
                            } else {
                                "Add ${signal.symbol} to watchlist"
                            },
                            tint = if (isWatchlisted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtMoney(signal.price),
                        style = MonoTextStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "conviction ${fmtNum(signal.conviction, 2)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Sparkline Chart
            PriceSparklineChart(
                series = signal.series,
                lineColor = chartColor,
                stopPrice = signal.stop,
                targetPrice = signal.target
            )

            // 2x2 Grid: Entry, Stop, Target, Net R:R / Size
            val cost = signal.cost
            val rrDisplay = fmtNum(cost?.rrNet ?: signal.rr, 2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = stringResource(R.string.label_entry),
                        value = fmtMoney(signal.entry),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.label_stop),
                        value = fmtMoney(signal.stop),
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = stringResource(R.string.label_target),
                        value = fmtMoney(signal.target),
                        valueColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.label_net_rr_size),
                        value = "$rrDisplay · ${fmtNum(signal.size, 3)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Paper Buy Action
            val alreadyHolding = botState.positions.any { it.symbol == signal.symbol }
            val canBuy = !alreadyHolding && !botState.halted && signal.price > 0.0 && botState.cash > 0.01
            val buyLabel = when {
                alreadyHolding -> "ALREADY HOLDING ${signal.symbol}"
                botState.halted -> "HALTED — DAILY LOSS LIMIT REACHED"
                botState.cash <= 0.01 -> "INSUFFICIENT PAPER CASH"
                else -> "PAPER BUY ${signal.symbol} (SIMULATED)"
            }
            Button(
                onClick = { onPaperBuy(signal) },
                enabled = canBuy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("paper_buy_button"),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = buyLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            // Risk Threat Assessment Card (Quant Shock + Narrative Taxonomy + Contagion)
            val heldPos = botState.positions.find { it.symbol == signal.symbol }
            ThreatAssessmentCard(
                symbol = signal.symbol,
                assessment = threatAssessment,
                isAssessing = isAssessingThreat,
                isHolding = heldPos != null,
                currentStop = heldPos?.stop,
                onRefresh = { onRefreshThreat(signal.symbol) },
                onExitPosition = { onClosePosition(signal.symbol) },
                onTrimPosition = { onTrimPosition(signal.symbol, 0.5) },
                onApplySuggestedStop = { stopPx -> onApplySuggestedStop(signal.symbol, stopPx) },
                initiallyExpanded = threatAssessment?.level?.let { it >= ThreatLevel.ELEVATED } ?: false
            )

            // Price Alert Card
            val defaultThreshold = remember(signal.symbol) {
                "%.2f".format(Locale.US, if (signal.target > signal.price) signal.target else signal.price * 1.02)
            }
            var alertInput by remember(signal.symbol) { mutableStateOf(defaultThreshold) }
            var alertCond by remember(signal.symbol) { mutableStateOf(AlertCondition.ABOVE) }
            val activeSymbolAlerts = remember(symbolAlerts) { symbolAlerts.filter { it.active } }

            TerminalCard {
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
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        SectionLabel(text = "PRICE ALERT")
                    }
                    if (activeSymbolAlerts.isNotEmpty()) {
                        TagChip(
                            text = "${activeSymbolAlerts.size} ARMED",
                            fg = MaterialTheme.colorScheme.secondary,
                            bg = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("-2%", signal.price * 0.98, AlertCondition.BELOW),
                        Triple("STOP", signal.stop, AlertCondition.BELOW),
                        Triple("+2%", signal.price * 1.02, AlertCondition.ABOVE),
                        Triple("TARGET", signal.target, AlertCondition.ABOVE)
                    ).forEach { (chipLabel, chipPx, chipCond) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable {
                                    alertInput = "%.2f".format(Locale.US, chipPx)
                                    alertCond = chipCond
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chipLabel,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = alertInput,
                        onValueChange = { alertInput = it },
                        label = { Text("TARGET ($)", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("detail_alert_threshold_input")
                    )

                    AlertCondition.entries.forEach { cond ->
                        val active = alertCond == cond
                        val fg = if (cond == AlertCondition.ABOVE) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        Box(
                            modifier = Modifier
                                .weight(0.75f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) fg.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.background
                                )
                                .border(
                                    1.dp,
                                    if (active) fg else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { alertCond = cond }
                                .testTag("detail_alert_cond_${cond.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cond.symbolText,
                                color = if (active) fg else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val parsedThreshold = alertInput.toDoubleOrNull()
                Button(
                    onClick = {
                        if (parsedThreshold != null && parsedThreshold > 0.0) {
                            onAddAlert(signal.symbol, parsedThreshold, alertCond)
                        }
                    },
                    enabled = parsedThreshold != null && parsedThreshold > 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detail_set_alert_button"),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "SET ${signal.symbol} ALERT",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                if (activeSymbolAlerts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    activeSymbolAlerts.forEach { existing ->
                        val condCol = if (existing.condition == AlertCondition.ABOVE) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${existing.condition.label} ${fmtMoney(existing.targetPrice)}",
                                color = condCol,
                                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            )
                            IconButton(
                                onClick = { onRemoveAlert(existing.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove alert for ${existing.symbol}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Cost Model Card
            if (cost != null) {
                TerminalCard {
                    SectionLabel(text = stringResource(R.string.label_cost))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Spread ${fmtNum(cost.spreadBps, 1)} bps · impact ${fmtNum(cost.impactBps, 1)} bps · round trip ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                                append("${fmtNum(cost.roundTripBps, 1)} bps")
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (cost.viable) {
                        Text(
                            text = "Passes the 3x cost test.",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Rejected: ${cost.reason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Factors Breakdown Card
            val sortedFactors = signal.sub.entries.sortedByDescending { abs(it.value) }
            TerminalCard {
                SectionLabel(text = stringResource(R.string.label_factors))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    sortedFactors.forEach { (factorName, value) ->
                        val barColor = if (value >= 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        val fraction = (abs(value) / 100.0).coerceIn(0.0, 1.0).toFloat()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = factorName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(74.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(barColor)
                                )
                            }
                            Text(
                                text = value.roundToInt().toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 11.sp),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(38.dp)
                            )
                        }
                    }
                }
            }

            // Why Card
            TerminalCard {
                SectionLabel(text = stringResource(R.string.label_why))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    signal.reasons.take(8).forEach { reason ->
                        Text(
                            text = "· $reason",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                        )
                    }
                }
            }

            // Catalyst Card
            if (signal.keyCatalyst.isNotBlank()) {
                TerminalCard {
                    SectionLabel(text = stringResource(R.string.label_catalyst))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = signal.keyCatalyst,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = stringResource(R.string.label_disclaimer_scores),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
