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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.AdvisorEngine
import com.example.domain.engine.AdvisorJournalReport
import com.example.domain.engine.AdvisorVetoVerdict
import com.example.domain.model.BotState
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

@Composable
fun AdvisorCard(
    advisorEnabled: Boolean,
    advisorVetoReady: Boolean,
    advisorModel: String,
    callsUsed: Int,
    maxCallsPerHour: Int,
    verdicts: Map<String, AdvisorVetoVerdict>,
    checkingSymbol: String?,
    journalReport: AdvisorJournalReport?,
    journalLoading: Boolean,
    botState: BotState,
    rows: List<SignalRow>,
    onCheckVeto: (String) -> Unit,
    onRunJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val candidateSymbols = remember(botState.positions, rows) {
        val held = botState.positions.map { it.symbol }
        val buys = rows.filter { it.action == SignalAction.BUY }.map { it.symbol }
        val top = rows.take(4).map { it.symbol }
        (held + buys + top).distinct().take(6)
    }
    var selectedSymbol by remember(candidateSymbols) {
        mutableStateOf(candidateSymbols.firstOrNull().orEmpty())
    }
    if (selectedSymbol.isEmpty() && candidateSymbols.isNotEmpty()) {
        selectedSymbol = candidateSymbols.first()
    }

    val population = remember(botState.log) {
        AdvisorEngine.buildPopulationReport(botState.log)
    }

    val statusText: String
    val statusFg = when {
        advisorVetoReady -> {
            statusText = stringResource(R.string.advisor_status_veto_journal)
            MaterialTheme.colorScheme.secondary
        }
        advisorEnabled -> {
            statusText = stringResource(R.string.advisor_status_journal_only)
            MaterialTheme.colorScheme.primary
        }
        else -> {
            statusText = stringResource(R.string.advisor_status_secrets_needed)
            MaterialTheme.colorScheme.tertiary
        }
    }
    val statusBg = statusFg.copy(alpha = 0.14f)

    TerminalCard(modifier = modifier.testTag("advisor_card")) {
        // Header row
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
                    imageVector = Icons.Outlined.FactCheck,
                    contentDescription = null,
                    tint = statusFg,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.label_advisor_layer))
            }
            TagChip(text = statusText, fg = statusFg, bg = statusBg)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Three Rules Strip
        Text(
            text = stringResource(R.string.advisor_three_rules),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Active Model & Budget Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniMetric(
                label = stringResource(R.string.advisor_label_model),
                value = if (advisorEnabled) advisorModel.take(18) else "OFF",
                valueColor = if (advisorEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.3f)
            )
            MiniMetric(
                label = stringResource(R.string.advisor_label_budget),
                value = "$callsUsed/$maxCallsPerHour hr",
                valueColor = if (callsUsed >= maxCallsPerHour) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.9f)
            )
            MiniMetric(
                label = stringResource(R.string.advisor_label_vetoes),
                value = population.vetoEventsLogged.toString(),
                valueColor = if (population.vetoEventsLogged > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(0.8f)
            )
        }

        if (!advisorEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.advisor_setup_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            )
        } else if (!advisorVetoReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.advisor_finnhub_optional_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        // Veto Screening Controls
        SectionLabel(text = stringResource(R.string.advisor_section_veto))
        Spacer(modifier = Modifier.height(6.dp))

        if (candidateSymbols.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                candidateSymbols.forEach { sym ->
                    val isSelected = sym == selectedSymbol
                    val symVerdict = verdicts[sym]
                    val pillBorder = when {
                        symVerdict?.isVeto == true -> MaterialTheme.colorScheme.error
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val pillBg = when {
                        symVerdict?.isVeto == true -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                            .clickable { selectedSymbol = sym }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("advisor_symbol_chip_$sym"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (symVerdict?.isVeto == true) "$sym · VETO" else sym,
                            color = when {
                                symVerdict?.isVeto == true -> MaterialTheme.colorScheme.error
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val isCheckingSelected = checkingSymbol != null && checkingSymbol == selectedSymbol
        OutlinedButton(
            onClick = { if (selectedSymbol.isNotEmpty()) onCheckVeto(selectedSymbol) },
            enabled = selectedSymbol.isNotEmpty() && checkingSymbol == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("advisor_check_veto_button"),
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(
                1.dp,
                if (advisorVetoReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline
            )
        ) {
            if (isCheckingSelected) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SCREENING $selectedSymbol HEADLINES…",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.GppGood,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedSymbol.isNotEmpty()) "CHECK VETO · $selectedSymbol (18H)" else "CHECK VETO",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        val activeVerdict = verdicts[selectedSymbol]
        if (activeVerdict != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val verdictColor = if (activeVerdict.isVeto) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondary
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(verdictColor.copy(alpha = 0.08f))
                    .border(1.dp, verdictColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                            imageVector = if (activeVerdict.isVeto) Icons.Outlined.GppMaybe else Icons.Outlined.GppGood,
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (activeVerdict.isVeto) {
                                "VETO ${activeVerdict.symbol} · ${activeVerdict.category.uppercase(Locale.US)}"
                            } else {
                                "CLEAR ${activeVerdict.symbol} · llm=${activeVerdict.llmTag}"
                            },
                            color = verdictColor,
                            style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    if (activeVerdict.isVeto) {
                        Text(
                            text = "conf ${(activeVerdict.weight * 100).toInt()}%",
                            color = verdictColor,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                if (activeVerdict.why.isNotBlank()) {
                    Text(
                        text = activeVerdict.why,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (activeVerdict.headline.isNotBlank()) {
                    Text(
                        text = "\"${activeVerdict.headline}\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        // Rule #3 Population Audit
        SectionLabel(text = stringResource(R.string.advisor_section_population))
        Spacer(modifier = Modifier.height(6.dp))

        if (population.totalClosedTrades == 0 && population.vetoEventsLogged == 0) {
            Text(
                text = stringResource(R.string.advisor_population_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                population.buckets.forEach { bucket ->
                    val pnlColor = if (bucket.totalPnl >= 0.0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "llm=${bucket.llmTag}",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${bucket.tradeCount} tr · ${"%.0f".format(Locale.US, bucket.winRatePct)}% win",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 11.sp),
                            modifier = Modifier.weight(1.3f)
                        )
                        val rPart = bucket.avgRMultiple?.let { " · ${"%.2f".format(Locale.US, it)}R" }.orEmpty()
                        Text(
                            text = "${fmtSignedMoney(bucket.totalPnl)}$rPart",
                            color = pnlColor,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        // Quantitative Post-Trade Journal Review
        SectionLabel(text = stringResource(R.string.advisor_section_journal))
        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onRunJournal,
            enabled = advisorEnabled && population.totalClosedTrades > 0 && !journalLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("advisor_run_journal_button"),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (journalLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REVIEWING LAST ${minOf(population.totalClosedTrades, AdvisorEngine.MAX_JOURNAL_TRADES)} TRADES…",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.advisor_action_run_journal),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        if (journalReport != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = journalReport.headline,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                journalReport.observations.forEach { obs ->
                    Text(
                        text = "· $obs",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                }
                journalReport.concerns.forEach { concern ->
                    Text(
                        text = "! $concern",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}
