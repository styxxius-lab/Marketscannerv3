package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.CouncilEngine
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.RadarModel
import com.example.ui.CouncilLedgerFilter
import com.example.ui.CouncilSubTab
import com.example.ui.CouncilUiState
import com.example.ui.components.CouncilDecisionCard
import com.example.ui.components.CouncilLedgerRowCard
import com.example.ui.components.CouncilVoteCard
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

@Composable
fun CouncilScreen(
    uiState: CouncilUiState,
    aiRouting: AiRoutingConfig,
    geminiFallbackKey: String,
    radarModels: List<RadarModel>,
    quickSymbols: List<String>,
    heldSymbols: Set<String>,
    onBack: () -> Unit,
    onSelectTab: (CouncilSubTab) -> Unit,
    onSelectSymbol: (String) -> Unit,
    onSymbolInputChange: (String) -> Unit,
    onDeliberate: (String?) -> Unit,
    onSelectMemberProvider: (AiProviderId) -> Unit,
    onMemberModelInputChange: (String) -> Unit,
    onAddMember: (AiProviderId, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onAutoBuildDiversePanel: () -> Unit,
    onOpenAiKeysHub: () -> Unit,
    onSelectLedgerFilter: (CouncilLedgerFilter) -> Unit,
    onResolveOutcomes: () -> Unit,
    onClearLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val distinctFamilies = remember(uiState.members) {
        uiState.members.map { CouncilEngine.family(it.substringAfter(":")) }.toSet()
    }
    val filteredLedger = remember(uiState.ledger, uiState.ledgerFilter) {
        when (uiState.ledgerFilter) {
            CouncilLedgerFilter.ALL -> uiState.ledger
            CouncilLedgerFilter.PENDING -> uiState.ledger.filter { it.outcome == null }
            CouncilLedgerFilter.RESOLVED -> uiState.ledger.filter { it.outcome != null }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("council_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CouncilTopBar(
                memberCount = uiState.members.size,
                familyCount = distinctFamilies.size,
                onBack = onBack,
                onOpenAiKeysHub = onOpenAiKeysHub
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Three Rules Guardrail Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.council_rules_strip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 4-way Sub-Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CouncilSubTab.entries.forEach { tab ->
                        val selected = tab == uiState.activeTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { onSelectTab(tab) }
                                .padding(vertical = 8.dp)
                                .testTag("council_tab_${tab.name.lowercase(Locale.US)}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (!uiState.statusNotice.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = uiState.statusNotice,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("council_content_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (uiState.activeTab) {
                        CouncilSubTab.DECIDE -> {
                            item(key = "decide_controls") {
                                DecideControlCard(
                                    selectedSymbol = uiState.selectedSymbol,
                                    symbolInput = uiState.symbolInput,
                                    quickSymbols = quickSymbols,
                                    heldSymbols = heldSymbols,
                                    isDeliberating = uiState.isDeliberating,
                                    familyCount = distinctFamilies.size,
                                    onSelectSymbol = onSelectSymbol,
                                    onSymbolInputChange = onSymbolInputChange,
                                    onDeliberate = { onDeliberate(null) }
                                )
                            }

                            if (distinctFamilies.size < 2) {
                                item(key = "diversity_warning_decide") {
                                    DiversityWarningBanner(onFixClick = { onSelectTab(CouncilSubTab.MEMBERS) })
                                }
                            }

                            val decision = uiState.latestDecision
                            if (decision == null) {
                                item(key = "empty_decide") {
                                    EmptyCouncilBox(
                                        title = stringResource(R.string.council_empty_decide_title),
                                        subtitle = stringResource(R.string.council_empty_decide_subtitle)
                                    )
                                }
                            } else {
                                item(key = "verdict_card") {
                                    CouncilDecisionCard(
                                        decision = decision,
                                        features = uiState.latestFeatures
                                    )
                                }
                                item(key = "votes_header") {
                                    Text(
                                        text = "COMMITTEE VOTES (${decision.votes.size})",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MonoTextStyle.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.6.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(
                                    items = decision.votes,
                                    key = { "vote_${decision.id}_${it.member}" }
                                ) { vote ->
                                    CouncilVoteCard(
                                        vote = vote,
                                        weight = uiState.weights[vote.member]?.weight ?: 1.0
                                    )
                                }
                            }
                        }

                        CouncilSubTab.MEMBERS -> {
                            item(key = "members_summary") {
                                MembersSummaryCard(
                                    memberCount = uiState.members.size,
                                    families = distinctFamilies.sorted(),
                                    onAutoBuild = onAutoBuildDiversePanel,
                                    onOpenKeys = onOpenAiKeysHub
                                )
                            }

                            if (distinctFamilies.size < 2) {
                                item(key = "diversity_warning_members") {
                                    DiversityWarningBanner(onFixClick = onAutoBuildDiversePanel)
                                }
                            }

                            item(key = "add_member_panel") {
                                AddMemberCard(
                                    selectedProvider = uiState.memberProviderInput,
                                    modelInput = uiState.memberModelInput,
                                    radarModels = radarModels,
                                    onSelectProvider = onSelectMemberProvider,
                                    onModelInputChange = onMemberModelInputChange,
                                    onAddMember = onAddMember
                                )
                            }

                            items(
                                items = uiState.members,
                                key = { "member_$it" }
                            ) { spec ->
                                val provWire = spec.substringBefore(":")
                                val modelId = spec.substringAfter(":")
                                val provEnum = AiProviderId.fromWire(provWire)
                                val hasKey = provEnum != null &&
                                    ModelRadarEngine.resolveApiKey(provEnum, aiRouting, geminiFallbackKey).isNotBlank()
                                val fam = CouncilEngine.family(modelId)
                                val weight = uiState.weights[spec]?.weight ?: 1.0

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("council_member_row_${spec.replace(":", "_")}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = spec,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = fam.uppercase(Locale.US),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                                Text(
                                                    text = "w=${String.format(Locale.US, "%.2f", weight)}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MonoTextStyle.copy(fontSize = 9.5.sp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (hasKey) MaterialTheme.colorScheme.secondaryContainer
                                                            else MaterialTheme.colorScheme.errorContainer
                                                        )
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = if (hasKey) "KEY SET" else "MISSING KEY",
                                                        color = if (hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                                        style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { onRemoveMember(spec) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("council_remove_member_${spec.replace(":", "_")}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.council_remove_member_desc),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CouncilSubTab.LEDGER -> {
                            item(key = "ledger_actions") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onResolveOutcomes,
                                            enabled = !uiState.isResolving && uiState.ledger.any { it.outcome == null },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("council_resolve_button")
                                        ) {
                                            if (uiState.isResolving) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(15.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.size(6.dp))
                                                Text(stringResource(R.string.council_action_resolving))
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.FactCheck,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.size(6.dp))
                                                Text(
                                                    text = stringResource(R.string.council_action_resolve),
                                                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                                )
                                            }
                                        }

                                        if (uiState.ledger.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = onClearLedger,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("council_clear_ledger_button")
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.council_action_clear_ledger),
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CouncilLedgerFilter.entries.forEach { f ->
                                            val selected = f == uiState.ledgerFilter
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onSelectLedgerFilter(f) }
                                                    .testTag("council_ledger_filter_${f.name.lowercase(Locale.US)}")
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = f.label,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredLedger.isEmpty()) {
                                item(key = "empty_ledger") {
                                    EmptyCouncilBox(
                                        title = stringResource(R.string.council_empty_ledger_title),
                                        subtitle = stringResource(R.string.council_empty_ledger_subtitle)
                                    )
                                }
                            } else {
                                items(
                                    items = filteredLedger,
                                    key = { "ledger_${it.id}" }
                                ) { row ->
                                    CouncilLedgerRowCard(decision = row)
                                }
                            }
                        }

                        CouncilSubTab.STATS -> {
                            item(key = "stats_overview") {
                                val st = uiState.stats
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "COUNCIL LEDGER CALIBRATION",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            StatsMetricBox(
                                                label = "LOGGED",
                                                value = st.totalDecisions.toString(),
                                                accent = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            StatsMetricBox(
                                                label = "RESOLVED",
                                                value = st.resolvedCount.toString(),
                                                accent = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            StatsMetricBox(
                                                label = "ALL ACC",
                                                value = st.councilAccuracy?.let { String.format(Locale.US, "%.0f%%", it * 100.0) } ?: "—",
                                                accent = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            StatsMetricBox(
                                                label = "BUY/SELL",
                                                value = st.buySellAccuracy?.let { String.format(Locale.US, "%.0f%%", it * 100.0) } ?: "—",
                                                accent = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (st.resolvedCount > 0) {
                                            Text(
                                                text = "HOLD calls: ${st.holdCount} (${String.format(Locale.US, "%.0f%%", st.holdRate * 100.0)}) · BUY/SELL scored: ${st.buySellCount}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MonoTextStyle.copy(fontSize = 10.sp)
                                            )
                                        }
                                        if (!st.sampleWarning.isNullOrBlank()) {
                                            Text(
                                                text = st.sampleWarning,
                                                color = MarketScanTheme.extraColors.bannerWarnText,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.stats.memberStats.isEmpty()) {
                                item(key = "empty_stats") {
                                    EmptyCouncilBox(
                                        title = stringResource(R.string.council_empty_stats_title),
                                        subtitle = stringResource(R.string.council_empty_stats_subtitle)
                                    )
                                }
                            } else {
                                item(key = "member_stats_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "MEMBER",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "N  ·  ACC  ·  W",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                                items(
                                    items = uiState.stats.memberStats,
                                    key = { "stat_${it.member}" }
                                ) { ms ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ms.member,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = ms.family.uppercase(Locale.US),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MonoTextStyle.copy(fontSize = 9.sp)
                                                )
                                            }
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "%2d · %4.0f%% · %.2fx",
                                                    ms.scoredCount,
                                                    ms.accuracy * 100.0,
                                                    ms.weight
                                                ),
                                                color = if (ms.weight >= 1.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                                style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "council_bottom_spacer") {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CouncilTopBar(
    memberCount: Int,
    familyCount: Int,
    onBack: () -> Unit,
    onOpenAiKeysHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("council_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.council_back_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.council_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$memberCount members · $familyCount distinct families",
                    color = if (familyCount >= 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                )
            }

            IconButton(
                onClick = onOpenAiKeysHub,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("council_keys_hub_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = stringResource(R.string.council_open_keys_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DecideControlCard(
    selectedSymbol: String,
    symbolInput: String,
    quickSymbols: List<String>,
    heldSymbols: Set<String>,
    isDeliberating: Boolean,
    familyCount: Int,
    onSelectSymbol: (String) -> Unit,
    onSymbolInputChange: (String) -> Unit,
    onDeliberate: () -> Unit
) {
    val activeSym = symbolInput.trim().uppercase(Locale.US).ifBlank { selectedSymbol }
    val isLong = heldSymbols.contains(activeSym)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(quickSymbols.take(12)) { sym ->
                    val selected = sym.equals(activeSym, ignoreCase = true)
                    val held = heldSymbols.contains(sym)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectSymbol(sym) }
                            .testTag("council_chip_${sym.lowercase(Locale.US)}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = sym,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            if (held) {
                                Text(
                                    text = "LONG",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MonoTextStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = symbolInput,
                    onValueChange = onSymbolInputChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.council_symbol_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDeliberate() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MonoTextStyle.copy(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("council_symbol_input")
                )
            }

            Button(
                onClick = onDeliberate,
                enabled = !isDeliberating,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("council_convene_button")
            ) {
                if (isDeliberating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "${stringResource(R.string.council_action_deliberating)} ($activeSym)",
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "${stringResource(R.string.council_action_convene)} · $activeSym (${if (isLong) "LONG" else "FLAT"})",
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            if (familyCount >= 2) {
                Text(
                    text = "Quorum & family diversity gate armed ($familyCount distinct families).",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MonoTextStyle.copy(fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
private fun DiversityWarningBanner(onFixClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onFixClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.council_family_warning),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }
    }
}

@Composable
private fun MembersSummaryCard(
    memberCount: Int,
    families: List<String>,
    onAutoBuild: () -> Unit,
    onOpenKeys: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "COMMITTEE ROSTER · $memberCount MEMBERS · ${families.size} FAMILIES",
                color = MaterialTheme.colorScheme.onSurface,
                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = if (families.isEmpty()) "No families configured yet." else "Families: ${families.joinToString(", ")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.sp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAutoBuild,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("council_auto_build_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        text = stringResource(R.string.council_action_auto_panel),
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenKeys,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("council_open_keys_hub_inline")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.council_action_open_keys),
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMemberCard(
    selectedProvider: AiProviderId,
    modelInput: String,
    radarModels: List<RadarModel>,
    onSelectProvider: (AiProviderId) -> Unit,
    onModelInputChange: (String) -> Unit,
    onAddMember: (AiProviderId, String) -> Unit
) {
    val suggestions = remember(selectedProvider, radarModels) {
        val fromRadar = radarModels.filter { it.provider == selectedProvider }.take(5).map { it.id }
        fromRadar.ifEmpty {
            when (selectedProvider) {
                AiProviderId.OPENROUTER -> listOf("qwen/qwen3-235b-a22b:free", "meta-llama/llama-3.3-70b-instruct:free")
                AiProviderId.AIHUBMIX -> listOf("glm-5.3", "glm-4-flash")
                AiProviderId.DEEPSEEK -> listOf("deepseek-chat", "deepseek-reasoner")
                AiProviderId.GROQ -> listOf("llama-3.3-70b-versatile", "qwen-qwq-32b")
                AiProviderId.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-pro")
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AiProviderId.entries) { prov ->
                    val selected = prov == selectedProvider
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectProvider(prov) }
                            .testTag("council_provider_${prov.wire}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = prov.displayName,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = onModelInputChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.council_model_input_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MonoTextStyle.copy(fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("council_model_input")
                )
                Button(
                    onClick = { onAddMember(selectedProvider, modelInput) },
                    enabled = modelInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("council_add_member_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.council_action_add_member),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { modelId ->
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onAddMember(selectedProvider, modelId) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ $modelId (${CouncilEngine.family(modelId)})",
                                color = MaterialTheme.colorScheme.primary,
                                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsMetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = accent,
            style = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp)
        )
    }
}

@Composable
private fun EmptyCouncilBox(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
