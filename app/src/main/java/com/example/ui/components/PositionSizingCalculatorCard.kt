package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.PaperBotEngine
import com.example.domain.engine.PositionSizingEngine
import com.example.domain.model.AppConfig
import com.example.domain.model.BotState
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.ui.theme.MonoTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val RISK_PRESETS = listOf(0.5, 1.0, 1.5, 2.0)

@Composable
fun PositionSizingCalculatorCard(
    rows: List<SignalRow>,
    regime: MarketRegime,
    headlines: List<NewsHeadline>,
    config: AppConfig,
    botState: BotState,
    onSaveSizingDefaults: (Double, Double) -> Unit,
    onSimulateSizedBuy: (SignalRow, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedCandidates = remember(rows) {
        val buys = rows.filter { it.action == SignalAction.BUY }
        val rest = rows.filter { it.action != SignalAction.BUY }
        (buys + rest).take(16)
    }

    var selectedSymbol by remember(orderedCandidates) {
        mutableStateOf(orderedCandidates.firstOrNull()?.symbol.orEmpty())
    }
    val selectedSignal = remember(orderedCandidates, selectedSymbol) {
        orderedCandidates.firstOrNull { it.symbol == selectedSymbol } ?: orderedCandidates.firstOrNull()
    }

    val liveEq = PaperBotEngine.liveEquity(botState)
    val initialAccount = config.account.takeIf { it > 0.0 } ?: liveEq.coerceAtLeast(10.0)
    var accountInput by remember { mutableStateOf(formatInputNumber(initialAccount)) }
    var riskInput by remember { mutableStateOf(formatInputNumber(config.riskPct)) }

    LaunchedEffect(config.account, config.riskPct) {
        accountInput = formatInputNumber(config.account)
        riskInput = formatInputNumber(config.riskPct)
    }

    val parsedAccount = accountInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: initialAccount
    val parsedRisk = riskInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: config.riskPct
    val inputsDirty = abs(parsedAccount - config.account) > 0.01 || abs(parsedRisk - config.riskPct) > 0.01

    TerminalCard(
        modifier = modifier.testTag("position_sizing_calculator_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(text = "POSITION SIZING & RISK CALCULATOR")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ATR risk · Regime · AI news · ¼-Kelly projections",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
            TagChip(
                text = "ALGO",
                fg = MaterialTheme.colorScheme.primary,
                bg = MaterialTheme.colorScheme.primaryContainer
            )
        }

        if (selectedSignal == null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Scanning universe for signals and market news…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            return@TerminalCard
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Symbol Selector Strip (shows Action color dot & News icon if catalysts present)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calc_symbol_selector")
        ) {
            items(orderedCandidates, key = { it.symbol }) { item ->
                val isSelected = item.symbol == selectedSignal.symbol
                val dotColor = when (item.action) {
                    SignalAction.BUY -> MaterialTheme.colorScheme.secondary
                    SignalAction.SELL -> MaterialTheme.colorScheme.error
                    SignalAction.HOLD -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedSymbol = item.symbol }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("calc_symbol_chip_${item.symbol}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = item.symbol,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    )
                    if (item.newsItems.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Newspaper,
                            contentDescription = "Has scored news",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Account Size ($) & Risk (%) Inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = accountInput,
                onValueChange = { accountInput = it },
                label = { Text("Account ($)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("calc_account_input")
            )
            OutlinedTextField(
                value = riskInput,
                onValueChange = { riskInput = it },
                label = { Text("Risk (%)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_risk_input")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Risk Presets + Save Defaults Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RISK_PRESETS.forEach { preset ->
                val isCurrent = abs(parsedRisk - preset) < 0.05
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                        )
                        .border(
                            1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { riskInput = formatInputNumber(preset) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("calc_risk_preset_${preset}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${formatInputNumber(preset)}%",
                        color = if (isCurrent) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (inputsDirty) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onSaveSizingDefaults(parsedAccount, parsedRisk) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("calc_save_defaults_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = "Save account and risk defaults",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "SAVE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }

        val result = remember(selectedSignal, regime, parsedAccount, parsedRisk, headlines) {
            PositionSizingEngine.calculate(
                signal = selectedSignal,
                regime = regime,
                accountSize = parsedAccount,
                riskPct = parsedRisk,
                marketTapeHeadlines = headlines
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Recommended Position Sizing Hero Readout
        val verdictColor = when {
            result.verdictLabel.contains("BLOCKED") || result.verdictLabel.contains("VETO") ->
                MaterialTheme.colorScheme.error
            result.verdictLabel.contains("WARNING") || result.verdictLabel.contains("CAPPED") ->
                MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.secondary
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                .border(1.dp, verdictColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("calc_result_panel")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RECOMMENDED POSITION (${result.symbol})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${fmtNum(result.recommendedShares, 4)} sh · ${fmtMoney(result.recommendedNotional)}",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.testTag("calc_recommended_shares")
                    )
                }
                TagChip(
                    text = result.verdictLabel,
                    fg = verdictColor,
                    bg = verdictColor.copy(alpha = 0.16f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.verdictNote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagChip(
                        text = if (result.bracketEligibleWholeShares) "${result.wholeShares} WHOLE SH (${fmtNum(result.breakdown.wholeSharesRetainedPct, 0)}%)"
                        else "FRACTIONAL (${fmtNum(result.recommendedShares, 2)} sh)",
                        fg = if (result.bracketEligibleWholeShares) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        bg = if (result.bracketEligibleWholeShares) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                    TagChip(
                        text = if (result.bracketEligibleWholeShares) "BRACKET SAFE" else "CLIENT TRAIL",
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                if (result.panicGuardActive) {
                    TagChip(
                        text = "PANIC GUARD -50%",
                        fg = MaterialTheme.colorScheme.error,
                        bg = MaterialTheme.colorScheme.errorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMetric(
                    label = "ENTRY",
                    value = fmtMoney(result.entryPrice),
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "STOP (${fmtNum(result.stopDistancePct, 1)}%)",
                    value = fmtMoney(result.stopPrice),
                    valueColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "TARGET",
                    value = fmtMoney(result.targetPrice),
                    valueColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "AT RISK",
                    value = fmtMoney(result.actualRiskDollars),
                    valueColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Algorithmic Multiplier & Half/Quarter-Kelly Calibration
        SectionLabel(text = "ALGORITHM DRIVERS & KELLY CALIBRATION")
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = "REGIME",
                value = "${fmtNum(result.breakdown.regimeMultiplier, 2)}x",
                valueColor = if (result.breakdown.regimeMultiplier >= 1.0) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "AI NEWS",
                value = "${fmtNum(result.breakdown.newsMultiplier, 2)}x",
                valueColor = when {
                    result.breakdown.newsMultiplier > 1.0 -> MaterialTheme.colorScheme.secondary
                    result.breakdown.newsMultiplier < 1.0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "½-KELLY",
                value = "${fmtNum(result.breakdown.halfKellyRiskPct, 2)}%",
                valueColor = if (parsedRisk <= result.breakdown.halfKellyRiskPct + 0.5) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "WIN EST",
                value = "${(result.breakdown.estimatedWinProb * 100).roundToInt()}%",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3-Scenario Trade & Expectancy Projection
        SectionLabel(text = "SCENARIO & EXPECTANCY PROJECTION")
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = "IF STOP HIT",
                value = fmtSignedMoney(result.projections.stopPnlDollars),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "EXP VALUE",
                value = fmtSignedMoney(result.projections.expectedValueDollars),
                valueColor = if (result.projections.expectedValueDollars >= 0.0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "IF TARGET",
                value = fmtSignedMoney(result.projections.targetPnlDollars),
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "10-trade EV projection: ${fmtMoney(result.projections.expectedAccountAfter10Trades)} · Net R:R ${fmtNum(result.breakdown.netRewardRiskRatio, 2)} · Friction ${fmtMoney(result.breakdown.roundTripCostDollars)} · Daily halt buffer: ${result.projections.dailyHaltTradesCount} stop(s)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 10.sp, lineHeight = 15.sp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Readable Market & AI News Feed driving the projection
        SectionLabel(text = "READABLE MARKET & NEWS FEED (${result.symbol})")
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("calc_readable_news_feed")
        ) {
            result.readableBriefs.forEachIndexed { idx, brief ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = if (brief.direction >= 0) "▲" else "▼",
                        color = if (brief.direction >= 0) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = brief.headline,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (brief.rationale.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${brief.sourceLabel} · ${brief.rationale}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (idx < result.readableBriefs.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (selectedSignal.action != SignalAction.SELL && result.recommendedShares > 0.0001) {
            Spacer(modifier = Modifier.height(10.dp))
            val alreadyHeld = botState.positions.any { it.symbol == selectedSignal.symbol }
            val affordable = botState.cash >= (result.recommendedShares * selectedSignal.price * 0.25)
            Button(
                onClick = { onSimulateSizedBuy(selectedSignal, result.recommendedShares) },
                enabled = !botState.halted && !alreadyHeld && affordable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("calc_simulate_buy_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = when {
                        alreadyHeld -> "ALREADY HOLDING ${selectedSignal.symbol}"
                        botState.halted -> "SIMULATOR HALTED FOR TODAY"
                        else -> "SIMULATE BUY · ${fmtNum(result.recommendedShares, 4)} ${selectedSignal.symbol}"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }
    }
}

private fun formatInputNumber(v: Double): String {
    return if (abs(v - v.roundToInt()) < 0.005) {
        v.roundToInt().toString()
    } else {
        "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }
}
