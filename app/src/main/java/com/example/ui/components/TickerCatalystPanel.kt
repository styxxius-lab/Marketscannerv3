package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.AiProviderCatalystRun
import com.example.domain.engine.AiProviderRunStatus
import com.example.domain.engine.AiProviderTickerVerdict
import com.example.domain.engine.CatalystImpactFilter
import com.example.domain.engine.CatalystImpactLevel
import com.example.domain.engine.ScoredCatalystHeadline
import com.example.domain.engine.TickerCatalystReport
import com.example.domain.model.AiProviderId
import com.example.ui.CatalystServiceStatus
import com.example.ui.MarketCatalystUiState
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldContainer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Surfaces the live Multi-AI News Catalyst Scorer: fetches recent financial headlines via the
 * configured API, runs them in parallel across ALL implemented AI providers (Gemini, OpenRouter,
 * AIHubMix, DeepSeek, Groq), and displays consensus Impact Ratings + per-AI verdicts.
 */
@Composable
fun TickerCatalystPanel(
    uiState: MarketCatalystUiState,
    onTickerInputChange: (String) -> Unit,
    onSubmitTickers: () -> Unit,
    onRefresh: () -> Unit,
    onSelectImpactFilter: (CatalystImpactFilter) -> Unit,
    onToggleExpandTicker: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenAiHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filteredReports = remember(uiState.reports, uiState.impactFilter) {
        uiState.filteredReports
    }

    TerminalCard(
        modifier = modifier.testTag("ticker_catalyst_service_panel")
    ) {
        // Header row: Title, Multi-AI Consensus badge, Headline Feed badge, Refresh action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    SectionLabel(text = stringResource(R.string.catalyst_service_title))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagChip(
                        text = uiState.scorerLabel,
                        fg = if (uiState.usedLlm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        bg = if (uiState.usedLlm) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    )
                    TagChip(
                        text = uiState.headlineSourceLabel,
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !uiState.isRefreshing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("catalyst_service_refresh_button")
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.catalyst_service_refresh_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // All Implemented AI Engines Roster Strip (Gemini, OpenRouter, AIHubMix, DeepSeek, Groq)
        if (uiState.providerRuns.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            AiEngineRosterStrip(
                runs = uiState.providerRuns,
                activeAiCount = uiState.activeAiCount,
                totalImplementedAiCount = uiState.totalImplementedAiCount,
                onOpenAiHub = onOpenAiHub
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Impact Level Breakdown Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("catalyst_impact_summary_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ImpactCountTile(
                label = stringResource(R.string.catalyst_impact_critical),
                count = uiState.summary.criticalCount,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            ImpactCountTile(
                label = stringResource(R.string.catalyst_impact_high),
                count = uiState.summary.highCount,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            ImpactCountTile(
                label = stringResource(R.string.catalyst_impact_moderate),
                count = uiState.summary.moderateCount,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            ImpactCountTile(
                label = stringResource(R.string.catalyst_impact_low),
                count = uiState.summary.lowCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom ticker input + SCORE button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.tickerInput,
                onValueChange = onTickerInputChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.catalyst_service_ticker_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (uiState.tickerInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onTickerInputChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.catalyst_service_clear_input_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmitTickers() }),
                shape = RoundedCornerShape(10.dp),
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
                    .testTag("catalyst_service_ticker_input")
            )

            Button(
                onClick = onSubmitTickers,
                enabled = !uiState.isRefreshing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("catalyst_service_score_button")
            ) {
                Text(
                    text = stringResource(R.string.catalyst_service_action_score),
                    style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // Warning / fallback note if LLM key is missing or partial failure occurred
        if (!uiState.warningMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(15.dp)
                )
                Text(
                    text = uiState.warningMessage,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Impact Level Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("catalyst_impact_filter_row")
        ) {
            items(CatalystImpactFilter.entries, key = { it.name }) { filter ->
                val selected = uiState.impactFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectImpactFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            style = MonoTextStyle.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("catalyst_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status / Content area
        when (val status = uiState.status) {
            is CatalystServiceStatus.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.catalyst_service_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is CatalystServiceStatus.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("catalyst_service_retry_button")
                    ) {
                        Text(
                            text = stringResource(R.string.catalyst_service_retry),
                            style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            else -> {
                if (filteredReports.isEmpty()) {
                    Text(
                        text = if (uiState.reports.isEmpty()) {
                            stringResource(R.string.catalyst_service_empty)
                        } else {
                            stringResource(R.string.catalyst_service_empty_filtered)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredReports.forEach { report ->
                            TickerCatalystRowItem(
                                report = report,
                                expanded = uiState.expandedSymbol == report.symbol,
                                onToggleExpand = { onToggleExpandTicker(report.symbol) },
                                onOpenUrl = onOpenUrl
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiEngineRosterStrip(
    runs: List<AiProviderCatalystRun>,
    activeAiCount: Int,
    totalImplementedAiCount: Int,
    onOpenAiHub: () -> Unit
) {
    val activeOrReadyCount = if (activeAiCount > 0) {
        activeAiCount
    } else {
        runs.count { it.status == AiProviderRunStatus.READY || it.status == AiProviderRunStatus.SCORED }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("catalyst_ai_roster_strip"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(
                        R.string.catalyst_service_ai_roster_header,
                        activeOrReadyCount,
                        totalImplementedAiCount
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAiHub)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("catalyst_open_ai_hub_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.catalyst_service_open_ai_hub_desc),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = stringResource(R.string.catalyst_service_open_ai_hub),
                    color = MaterialTheme.colorScheme.primary,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(runs, key = { it.provider.name }) { run ->
                val (dotColor, statusText) = when (run.status) {
                    AiProviderRunStatus.SCORED -> {
                        val msText = if (run.latencyMs > 0L) " · ${run.latencyMs}ms" else ""
                        MaterialTheme.colorScheme.secondary to "SCORED$msText"
                    }
                    AiProviderRunStatus.READY -> MaterialTheme.colorScheme.primary to "READY"
                    AiProviderRunStatus.FAILED -> MaterialTheme.colorScheme.error to "ERROR"
                    AiProviderRunStatus.NO_KEY -> MaterialTheme.colorScheme.tertiary to "NO KEY"
                    AiProviderRunStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to "OFF"
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            width = 1.dp,
                            color = if (run.status == AiProviderRunStatus.SCORED) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("catalyst_ai_chip_${run.provider.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = shortProviderName(run.provider),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = statusText,
                        color = dotColor,
                        style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImpactCountTile(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 0.4.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TickerCatalystRowItem(
    report: TickerCatalystReport,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val (impactFg, impactBg) = impactColors(report.impactLevel)
    val (scoreFg, scoreBg) = when {
        report.catalystScore >= 12 -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        report.catalystScore <= -12 -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> SignalHold to SignalHoldContainer
    }
    val signedScoreText = if (report.catalystScore > 0) "+${report.catalystScore}" else "${report.catalystScore}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = 1.dp,
                color = if (report.impactLevel == CatalystImpactLevel.CRITICAL) {
                    impactFg.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggleExpand)
            .padding(12.dp)
            .testTag("ticker_catalyst_card_${report.symbol}")
    ) {
        // Row 1: Symbol + Prominent Impact Rating badge | Signed Catalyst Score pill + chevron
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
                    text = report.symbol,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                )
                // Prominent Impact Rating Badge (e.g. CRITICAL BULLISH, HIGH BEARISH)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(impactBg)
                        .border(1.dp, impactFg.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("ticker_impact_badge_${report.symbol}")
                ) {
                    Text(
                        text = report.impactRatingLabel,
                        color = impactFg,
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scoreBg)
                        .border(1.dp, scoreFg.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("ticker_catalyst_score_${report.symbol}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = scoreFg,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = signedScoreText,
                        color = scoreFg,
                        style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.catalyst_service_collapse_desc)
                    } else {
                        stringResource(R.string.catalyst_service_expand_desc)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 1b: Primary Catalyst Tag + Multi-AI Agreement Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TagChip(
                text = report.primaryTag,
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
                bg = MaterialTheme.colorScheme.surfaceVariant
            )
            if (report.aiVerdicts.isNotEmpty()) {
                val agreeFg = when {
                    report.aiAgreementPct >= 75 -> MaterialTheme.colorScheme.secondary
                    report.aiAgreementPct >= 50 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                val agreeBg = when {
                    report.aiAgreementPct >= 75 -> MaterialTheme.colorScheme.secondaryContainer
                    report.aiAgreementPct >= 50 -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                }
                TagChip(
                    text = stringResource(
                        R.string.catalyst_service_agreement_pill,
                        report.aiVerdicts.size,
                        report.aiAgreementPct
                    ),
                    fg = agreeFg,
                    bg = agreeBg
                )
            }
        }

        // Compact Per-AI Verdict Chips (e.g. Gemini +78 · DeepSeek +72 · Groq +66)
        if (report.aiVerdicts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ticker_ai_verdicts_row_${report.symbol}")
            ) {
                items(report.aiVerdicts, key = { "${report.symbol}_${it.provider.name}" }) { verdict ->
                    val vColor = when {
                        verdict.catalystScore >= 12 -> MaterialTheme.colorScheme.secondary
                        verdict.catalystScore <= -12 -> MaterialTheme.colorScheme.error
                        else -> SignalHold
                    }
                    val vSigned = if (verdict.catalystScore > 0) "+${verdict.catalystScore}" else "${verdict.catalystScore}"
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, vColor.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = shortProviderName(verdict.provider),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = vSigned,
                            color = vColor,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Bidirectional Catalyst Score bar + Impact / Materiality metadata
        TickerCatalystBar(
            score = report.catalystScore,
            impactScore = report.impactScore,
            materialityPct = (report.materiality * 100.0).roundToInt(),
            headlineCount = report.headlineCount,
            barColor = scoreFg
        )

        Spacer(modifier = Modifier.height(7.dp))

        // Row 3: Multi-AI Consensus Catalyst Rationale
        Text(
            text = report.llmSummary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.5.sp),
            maxLines = if (expanded) 4 else 2,
            overflow = TextOverflow.Ellipsis
        )

        // Expandable Per-AI Verdicts & Scored Headlines breakdown
        AnimatedVisibility(visible = expanded && (report.aiVerdicts.isNotEmpty() || report.headlines.isNotEmpty())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (report.aiVerdicts.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Text(
                        text = stringResource(
                            R.string.catalyst_service_multi_ai_verdicts_header,
                            report.aiVerdicts.size,
                            report.aiAgreementPct
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    report.aiVerdicts.forEach { verdict ->
                        AiProviderVerdictDetailRow(verdict = verdict)
                    }
                }

                if (report.headlines.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Text(
                        text = stringResource(R.string.catalyst_service_headlines_header, report.headlineCount, report.headlineSource),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    )
                    report.headlines.forEach { item ->
                        ScoredHeadlineRow(
                            item = item,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiProviderVerdictDetailRow(verdict: AiProviderTickerVerdict) {
    val (impactFg, impactBg) = impactColors(verdict.impactLevel)
    val scoreColor = when {
        verdict.catalystScore >= 12 -> MaterialTheme.colorScheme.secondary
        verdict.catalystScore <= -12 -> MaterialTheme.colorScheme.error
        else -> SignalHold
    }
    val signed = if (verdict.catalystScore > 0) "+${verdict.catalystScore}" else "${verdict.catalystScore}"
    val modelTail = verdict.modelId.substringAfterLast('/').ifBlank { verdict.providerName }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = verdict.providerName.uppercase(),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "· $modelTail",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(impactBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = verdict.impactLevel.shortLabel,
                        color = impactFg,
                        style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
                Text(
                    text = signed,
                    color = scoreColor,
                    style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }
        Text(
            text = verdict.rationale,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TickerCatalystBar(
    score: Int,
    impactScore: Int,
    materialityPct: Int,
    headlineCount: Int,
    barColor: Color
) {
    val magnitude = abs(score).coerceIn(0, 100) / 100f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IMPACT $impactScore/100 · MAT $materialityPct%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.5.sp)
            )
            Text(
                text = "$headlineCount HEADLINES",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.5.sp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (score < 0 && magnitude > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(magnitude)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (score > 0 && magnitude > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(magnitude)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoredHeadlineRow(
    item: ScoredCatalystHeadline,
    onOpenUrl: (String) -> Unit
) {
    val (impactFg, _) = impactColors(item.impactLevel)
    val scoreColor = when {
        item.catalystScore >= 12 -> MaterialTheme.colorScheme.secondary
        item.catalystScore <= -12 -> MaterialTheme.colorScheme.error
        else -> SignalHold
    }
    val scoreText = if (item.catalystScore > 0) "+${item.catalystScore}" else "${item.catalystScore}"
    val hasUrl = item.url.startsWith("https://") || item.url.startsWith("http://")
    val aiCountSuffix = if (item.aiModelsCount > 1) " · ${item.aiModelsCount} AIs" else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .then(if (hasUrl) Modifier.clickable { onOpenUrl(item.url) } else Modifier)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.impactLevel.shortLabel,
                    color = impactFg,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "· ${item.catalystTag} · ${item.source}$aiCountSuffix",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = scoreText,
                    color = scoreColor,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                )
                if (hasUrl) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.finnhub_open_article_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Text(
            text = item.headline,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.rationale,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shortProviderName(provider: AiProviderId): String = when (provider) {
    AiProviderId.GEMINI -> "Gemini"
    AiProviderId.OPENROUTER -> "OpenRouter"
    AiProviderId.AIHUBMIX -> "AIHubMix"
    AiProviderId.DEEPSEEK -> "DeepSeek"
    AiProviderId.GROQ -> "Groq"
}

@Composable
private fun impactColors(level: CatalystImpactLevel): Pair<Color, Color> = when (level) {
    CatalystImpactLevel.CRITICAL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    CatalystImpactLevel.HIGH -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    CatalystImpactLevel.MODERATE -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    CatalystImpactLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
}
