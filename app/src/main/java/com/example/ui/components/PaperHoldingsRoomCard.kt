package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PaperHoldingEntity
import com.example.domain.engine.HoldingPnlValuation
import com.example.ui.PaperHoldingsUiState
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val QUICK_PAPER_TICKERS = listOf("NVDA", "AAPL", "MSFT", "SPY", "TSLA", "AMZN", "META", "AMD")

@Composable
fun PaperHoldingsRoomCard(
    uiState: PaperHoldingsUiState,
    watchlistSymbols: List<String> = emptyList(),
    knownSignalSymbols: Set<String> = emptySet(),
    onToggleForm: () -> Unit,
    onSymbolInputChange: (String) -> Unit,
    onSelectQuickSymbol: (String) -> Unit,
    onSharesInputChange: (String) -> Unit,
    onPurchasePriceInputChange: (String) -> Unit,
    onNotesInputChange: (String) -> Unit,
    onPrefillLivePrice: () -> Unit,
    onSaveHolding: () -> Unit,
    onCancelEdit: () -> Unit,
    onEditHolding: (PaperHoldingEntity) -> Unit,
    onDeleteHolding: (Long) -> Unit,
    onSeedDummyHoldings: () -> Unit,
    onClearAllHoldings: () -> Unit,
    onRefreshQuotes: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onOpenSignal: ((String) -> Unit)? = null,
    testTagPrefix: String = "room_paper_holdings",
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary
    val totalPnlColor by animateColorAsState(
        targetValue = if (summary.totalUnrealizedPnl >= 0.0) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.error
        },
        label = "room_holdings_total_pnl_color"
    )
    val dayPnlColor by animateColorAsState(
        targetValue = if (summary.totalDayPnl >= 0.0) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.error
        },
        label = "room_holdings_day_pnl_color"
    )
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val quickPicks = remember(watchlistSymbols) {
        (watchlistSymbols.map { it.trim().uppercase(Locale.US) } + QUICK_PAPER_TICKERS)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
    }

    TerminalCard(
        modifier = modifier.testTag("${testTagPrefix}_card")
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
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    SectionLabel(text = stringResource(R.string.paper_holdings_title))
                    Text(
                        text = if (uiState.lastUpdatedMs > 0L && uiState.holdings.isNotEmpty()) {
                            "${uiState.holdings.size} Room lots · ${uiState.feedLabel} · ${timeFormatter.format(Date(uiState.lastUpdatedMs))}"
                        } else {
                            stringResource(R.string.paper_holdings_subtitle)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.holdings.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (uiState.autoRefreshEnabled) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (uiState.autoRefreshEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onToggleAutoRefresh)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("${testTagPrefix}_poll_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.autoRefreshEnabled) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                            Text(
                                text = if (uiState.autoRefreshEnabled) {
                                    stringResource(R.string.paper_holdings_poll_live)
                                } else {
                                    stringResource(R.string.paper_holdings_poll_paused)
                                },
                                color = if (uiState.autoRefreshEnabled) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshQuotes,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_refresh_button")
                    ) {
                        if (uiState.isRefreshingQuotes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.paper_holdings_refresh_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Aggregate Simulated P&L Summary Strip
        if (uiState.holdings.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MarketScanTheme.extraColors.heroGradStart.copy(alpha = 0.72f),
                                MarketScanTheme.extraColors.heroGradEnd.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.paper_holdings_total_value),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                        )
                        Text(
                            text = fmtMoney(summary.totalMarketValue),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.testTag("${testTagPrefix}_total_value")
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = fmtSignedMoney(summary.totalUnrealizedPnl),
                            color = totalPnlColor,
                            style = MonoTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.testTag("${testTagPrefix}_total_pnl")
                        )
                        Text(
                            text = "${fmtPctSigned(summary.totalUnrealizedPnlPct)} · ${summary.winnersCount}W / ${summary.losersCount}L",
                            color = totalPnlColor,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniMetric(
                        label = stringResource(R.string.paper_holdings_cost_basis),
                        value = fmtMoney(summary.totalCostBasis),
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetric(
                        label = stringResource(R.string.paper_holdings_day_pnl),
                        value = fmtSignedMoney(summary.totalDayPnl),
                        valueColor = dayPnlColor,
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetric(
                        label = stringResource(R.string.paper_holdings_live_priced),
                        value = "${summary.livePricedCount}/${uiState.holdings.size}",
                        valueColor = if (summary.livePricedCount == uiState.holdings.size) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Action Bar: Add Holding + Seed Dummy Lots / Clear All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleForm,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("${testTagPrefix}_toggle_form_button"),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.formExpanded) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (uiState.formExpanded) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (uiState.formExpanded) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = if (uiState.formExpanded) {
                        stringResource(R.string.paper_holdings_action_close_form)
                    } else {
                        stringResource(R.string.paper_holdings_action_add_lot)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    maxLines = 1
                )
            }

            if (uiState.holdings.isEmpty()) {
                OutlinedButton(
                    onClick = onSeedDummyHoldings,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_seed_button"),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        text = stringResource(R.string.paper_holdings_action_seed),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onClearAllHoldings,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_clear_all_button"),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = stringResource(R.string.paper_holdings_action_clear_all),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Expandable Add / Edit Holding Form
        AnimatedVisibility(visible = uiState.formExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(
                        text = if (uiState.editingHoldingId != null) {
                            stringResource(R.string.paper_holdings_form_edit_title, uiState.symbolInput)
                        } else {
                            stringResource(R.string.paper_holdings_form_add_title)
                        }
                    )
                    if (uiState.editingHoldingId != null) {
                        Text(
                            text = stringResource(R.string.paper_holdings_action_cancel),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = onCancelEdit)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                // Quick-pick ticker chips (when adding a new lot)
                if (uiState.editingHoldingId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickPicks.forEach { sym ->
                            val selected = uiState.symbolInput.equals(sym, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectQuickSymbol(sym) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("${testTagPrefix}_quick_$sym"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sym,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground,
                                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.symbolInput,
                        onValueChange = onSymbolInputChange,
                        enabled = uiState.editingHoldingId == null,
                        singleLine = true,
                        label = { Text(stringResource(R.string.paper_holdings_field_ticker)) },
                        placeholder = { Text("NVDA") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${testTagPrefix}_symbol_input")
                    )

                    OutlinedTextField(
                        value = uiState.sharesInput,
                        onValueChange = onSharesInputChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.paper_holdings_field_shares)) },
                        placeholder = { Text("10") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${testTagPrefix}_shares_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.purchasePriceInput,
                        onValueChange = onPurchasePriceInputChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.paper_holdings_field_buy_price)) },
                        placeholder = { Text("132.50") },
                        prefix = { Text("$", style = MonoTextStyle) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSaveHolding() }),
                        textStyle = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${testTagPrefix}_price_input")
                    )

                    OutlinedButton(
                        onClick = onPrefillLivePrice,
                        enabled = !uiState.isFetchingPrefillPrice && uiState.symbolInput.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("${testTagPrefix}_prefill_live_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    ) {
                        if (uiState.isFetchingPrefillPrice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.paper_holdings_action_live_discount),
                                color = MaterialTheme.colorScheme.primary,
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.notesInput,
                    onValueChange = onNotesInputChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.paper_holdings_field_notes)) },
                    placeholder = { Text(stringResource(R.string.paper_holdings_field_notes_hint)) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTagPrefix}_notes_input")
                )

                if (uiState.formError != null) {
                    Text(
                        text = uiState.formError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onSaveHolding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("${testTagPrefix}_save_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = if (uiState.editingHoldingId != null) {
                            stringResource(R.string.paper_holdings_action_update_room)
                        } else {
                            stringResource(R.string.paper_holdings_action_save_room)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }

        // Empty State or Persisted Holdings List
        Spacer(modifier = Modifier.height(10.dp))

        if (summary.valuations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(vertical = 18.dp, horizontal = 14.dp)
                    .testTag("${testTagPrefix}_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = stringResource(R.string.paper_holdings_empty_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.paper_holdings_empty_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            summary.valuations.forEachIndexed { index, valuation ->
                PaperHoldingRow(
                    valuation = valuation,
                    canOpenSignal = onOpenSignal != null && knownSignalSymbols.contains(valuation.holding.symbol),
                    onEdit = { onEditHolding(valuation.holding) },
                    onDelete = { onDeleteHolding(valuation.holding.id) },
                    onOpenSignal = { onOpenSignal?.invoke(valuation.holding.symbol) },
                    testTagPrefix = testTagPrefix
                )
                if (index < summary.valuations.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperHoldingRow(
    valuation: HoldingPnlValuation,
    canOpenSignal: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenSignal: () -> Unit,
    testTagPrefix: String
) {
    val holding = valuation.holding
    val pnlColor = when {
        !valuation.isLivePriced -> MaterialTheme.colorScheme.onSurfaceVariant
        valuation.unrealizedPnl >= 0.0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val barFill = (abs(valuation.unrealizedPnlPct) / 20.0).coerceIn(0.05, 1.0).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_row_${holding.symbol}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = holding.symbol,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    TagChip(
                        text = "${fmtNum(holding.shares, 2)} SH",
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                    TagChip(
                        text = valuation.priceSource,
                        fg = if (valuation.isLivePriced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        bg = if (valuation.isLivePriced) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (holding.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = holding.notes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (valuation.isLivePriced) fmtSignedMoney(valuation.unrealizedPnl) else "—",
                    color = pnlColor,
                    style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.testTag("${testTagPrefix}_pnl_${holding.symbol}")
                )
                Text(
                    text = if (valuation.isLivePriced) fmtPctSigned(valuation.unrealizedPnlPct) else "awaiting tick",
                    color = pnlColor,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = stringResource(R.string.paper_holdings_col_dummy_buy),
                value = fmtMoney(holding.purchasePrice),
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.paper_holdings_col_live_px),
                value = valuation.livePrice?.let { fmtMoney(it) } ?: "—",
                valueColor = pnlColor,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.paper_holdings_col_mkt_val),
                value = fmtMoney(valuation.marketValue),
                modifier = Modifier.weight(1f)
            )
        }

        // Visual P&L Magnitude Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (valuation.isLivePriced) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFill)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(pnlColor)
                )
            }
        }

        // Row Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("${testTagPrefix}_edit_${holding.symbol}"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(5.dp))
                Text(
                    text = stringResource(R.string.paper_holdings_action_edit),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }

            if (canOpenSignal) {
                OutlinedButton(
                    onClick = onOpenSignal,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_signal_${holding.symbol}"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stringResource(R.string.paper_holdings_action_signal),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                    .testTag("${testTagPrefix}_delete_${holding.symbol}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.paper_holdings_delete_desc, holding.symbol),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
