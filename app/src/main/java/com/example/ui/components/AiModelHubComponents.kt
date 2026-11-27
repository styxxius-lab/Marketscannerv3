package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.engine.ProviderCatalogStats
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.ModelProbeResult
import com.example.domain.model.ModelTier
import com.example.domain.model.ModelTierBasis
import com.example.domain.model.ModelTierFilter
import com.example.domain.model.RadarModel
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiModelHubSummaryCard(
    aiRouting: AiRoutingConfig,
    geminiFallbackKey: String,
    radarModels: List<RadarModel>,
    onToggleProvider: (AiProviderId, Boolean) -> Unit,
    onSelectActiveProvider: (AiProviderId) -> Unit,
    onOpenModelHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProv = aiRouting.activeProvider
    val activeSettings = aiRouting.settingsFor(activeProv)
    val activeModelId = activeSettings.selectedModelId.trim()
    val matchedModel = remember(radarModels, activeProv, activeModelId) {
        radarModels.firstOrNull { it.provider == activeProv && it.id == activeModelId }
    }
    val activeTier = matchedModel?.tier ?: when {
        activeSettings.tierPreference == ModelTierFilter.FREE -> ModelTier.FREE
        activeSettings.tierPreference == ModelTierFilter.PAID -> ModelTier.PAID
        else -> activeProv.defaultTier
    }
    val enabledCount = aiRouting.enabledProviders.size

    TerminalCard(modifier = modifier.testTag("ai_model_hub_summary_card")) {
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
                SectionLabel(text = stringResource(R.string.ai_hub_section_title))
            }
            TagChip(
                text = if (activeSettings.enabled) "${activeProv.displayName.uppercase(Locale.US)} · ${activeTier.label}"
                else "${enabledCount}/${AiProviderId.entries.size} ON",
                fg = if (activeSettings.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                bg = if (activeSettings.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Model Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeSettings.enabled) "ACTIVE AI · ${activeProv.displayName}" else "ACTIVE AI PAUSED (${activeProv.displayName} OFF)",
                    color = if (activeSettings.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
                TierPill(tier = activeTier, basis = matchedModel?.basis)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = activeModelId.ifBlank { stringResource(R.string.ai_hub_no_model_selected) },
                color = if (activeModelId.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!aiRouting.isAnyAiEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_hub_all_off_banner),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact per-AI ON/OFF switchboard
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AiProviderId.entries.forEach { prov ->
                val ps = aiRouting.settingsFor(prov)
                val isActive = activeProv == prov && ps.enabled
                val hasKey = ModelRadarEngine.resolveApiKey(prov, aiRouting, geminiFallbackKey).isNotBlank()
                val toggleDesc = stringResource(R.string.ai_hub_toggle_provider_desc, prov.displayName)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            if (!ps.enabled) {
                                onToggleProvider(prov, true)
                            }
                            onSelectActiveProvider(prov)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("ai_summary_row_${prov.wire}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = prov.displayName,
                                color = if (ps.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isActive) {
                                TagChip(
                                    text = stringResource(R.string.ai_hub_active_badge),
                                    fg = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else if (ps.enabled) {
                                TagChip(
                                    text = if (hasKey) stringResource(R.string.ai_hub_key_set)
                                    else if (prov.isPublicCatalog) stringResource(R.string.ai_hub_key_public)
                                    else stringResource(R.string.ai_hub_key_needed),
                                    fg = if (hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    bg = if (hasKey) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (ps.enabled) {
                                ps.selectedModelId.ifBlank { "Tap to select model (${ps.tierPreference.label})" }
                            } else {
                                "Disabled — will not be called"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = ps.enabled,
                        onCheckedChange = { onToggleProvider(prov, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .semantics { contentDescription = toggleDesc }
                            .testTag("ai_summary_switch_${prov.wire}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onOpenModelHub,
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_ai_model_hub_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.ai_hub_manage_models),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiModelHubSheet(
    aiRouting: AiRoutingConfig,
    geminiFallbackKey: String,
    radarModels: List<RadarModel>,
    radarSyncing: Boolean,
    radarLastSyncedMs: Long,
    radarErrors: Map<AiProviderId, String>,
    tierFilter: ModelTierFilter,
    providerFilter: AiProviderId?,
    searchQuery: String,
    probingModelKey: String?,
    probeResultsByKey: Map<String, ModelProbeResult>,
    onToggleProvider: (AiProviderId, Boolean) -> Unit,
    onSelectActiveProvider: (AiProviderId) -> Unit,
    onUpdateProviderApiKey: (AiProviderId, String) -> Unit,
    onSyncCatalog: (Boolean) -> Unit,
    onSelectTierFilter: (ModelTierFilter) -> Unit,
    onSelectProviderFilter: (AiProviderId?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPickBestTier: (ModelTierFilter) -> Unit,
    onSelectModel: (RadarModel) -> Unit,
    onProbeModel: (RadarModel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statsByProvider = remember(radarModels) { ModelRadarEngine.stats(radarModels) }
    val filteredModels = remember(
        radarModels,
        aiRouting.providers,
        providerFilter,
        tierFilter,
        searchQuery
    ) {
        ModelRadarEngine.filterCatalog(
            models = radarModels,
            enabledProviders = aiRouting.enabledProviders,
            providerFilter = providerFilter,
            tierFilter = tierFilter,
            searchQuery = searchQuery,
            chatOnly = true
        )
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("ai_model_hub_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item(key = "hub_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ai_hub_sheet_title),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.ai_hub_sheet_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_hub_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.ai_hub_close_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Provider Switchboard Cards
            item(key = "provider_switchboard_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProviderId.entries.forEach { prov ->
                        ProviderControlCard(
                            provider = prov,
                            aiRouting = aiRouting,
                            geminiFallbackKey = geminiFallbackKey,
                            stats = statsByProvider[prov],
                            syncError = radarErrors[prov],
                            onToggle = { enabled -> onToggleProvider(prov, enabled) },
                            onActivate = { onSelectActiveProvider(prov) },
                            onSaveApiKey = { key -> onUpdateProviderApiKey(prov, key) }
                        )
                    }
                }
            }

            // Sync & Quick Best Pick Controls
            item(key = "sync_and_best_controls") {
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel(text = "LIVE MODEL CATALOG")
                        if (radarLastSyncedMs > 0L) {
                            Text(
                                text = "Synced ${timeFmt.format(Date(radarLastSyncedMs))}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 10.5.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSyncCatalog(true) },
                            enabled = !radarSyncing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("ai_hub_sync_button")
                        ) {
                            if (radarSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(5.dp))
                            Text(
                                text = if (radarSyncing) stringResource(R.string.ai_hub_syncing)
                                else stringResource(R.string.ai_hub_sync_catalog),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { onPickBestTier(ModelTierFilter.FREE) },
                            enabled = radarModels.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_hub_pick_best_free_button")
                        ) {
                            Text(
                                text = stringResource(R.string.ai_hub_best_free),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { onPickBestTier(ModelTierFilter.PAID) },
                            enabled = radarModels.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_hub_pick_best_paid_button")
                        ) {
                            Text(
                                text = stringResource(R.string.ai_hub_best_paid),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Free / Paid / Unknown / All Tier Filter Chips
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_hub_tier_filter_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModelTierFilter.entries.forEach { tf ->
                            val selected = tierFilter == tf
                            val (selBg, selFg) = when (tf) {
                                ModelTierFilter.FREE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
                                ModelTierFilter.PAID -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
                                ModelTierFilter.ALL -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                                ModelTierFilter.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTierFilter(tf) },
                                label = {
                                    Text(
                                        text = tf.label,
                                        style = MonoTextStyle.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = selBg,
                                    selectedLabelColor = selFg,
                                    containerColor = MaterialTheme.colorScheme.background,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_hub_tier_${tf.name.lowercase(Locale.US)}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Provider Filter Chips
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_hub_provider_filter_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = providerFilter == null,
                            onClick = { onSelectProviderFilter(null) },
                            label = {
                                Text(
                                    text = "ALL PROVIDERS",
                                    style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_hub_prov_filter_all")
                        )
                        AiProviderId.entries.forEach { prov ->
                            val isOn = aiRouting.settingsFor(prov).enabled
                            FilterChip(
                                selected = providerFilter == prov,
                                enabled = isOn,
                                onClick = { onSelectProviderFilter(prov) },
                                label = {
                                    Text(
                                        text = prov.displayName,
                                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_hub_prov_filter_${prov.wire}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Model Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.ai_hub_search_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.ai_hub_clear_search_desc),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
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
                            .fillMaxWidth()
                            .testTag("ai_hub_search_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${filteredModels.size} chat models · ${stringResource(R.string.ai_hub_price_honesty_note)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Models List or Empty State
            if (filteredModels.isEmpty()) {
                item(key = "empty_models_state") {
                    TerminalCard {
                        Text(
                            text = stringResource(R.string.ai_hub_empty_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ai_hub_empty_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(
                    items = filteredModels.take(80),
                    key = { it.key }
                ) { model ->
                    val isSelected = aiRouting.activeProvider == model.provider &&
                        aiRouting.settingsFor(model.provider).selectedModelId == model.id &&
                        aiRouting.settingsFor(model.provider).enabled
                    RadarModelRowCard(
                        model = model,
                        isSelected = isSelected,
                        isProbing = probingModelKey == model.key,
                        probeResult = probeResultsByKey[model.key],
                        onSelect = { onSelectModel(model) },
                        onProbe = { onProbeModel(model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderControlCard(
    provider: AiProviderId,
    aiRouting: AiRoutingConfig,
    geminiFallbackKey: String,
    stats: ProviderCatalogStats?,
    syncError: String?,
    onToggle: (Boolean) -> Unit,
    onActivate: () -> Unit,
    onSaveApiKey: (String) -> Unit
) {
    val ps = aiRouting.settingsFor(provider)
    val isActive = aiRouting.activeProvider == provider && ps.enabled
    val resolvedKey = ModelRadarEngine.resolveApiKey(provider, aiRouting, geminiFallbackKey)
    val hasKey = resolvedKey.isNotBlank()
    val toggleDesc = stringResource(R.string.ai_hub_toggle_provider_desc, provider.displayName)

    var keyExpanded by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf(ps.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(ps.apiKey) {
        keyDraft = ps.apiKey
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .testTag("ai_provider_card_${provider.wire}")
    ) {
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
                    Text(
                        text = provider.displayName,
                        color = if (ps.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isActive) {
                        TagChip(
                            text = stringResource(R.string.ai_hub_active_badge),
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else if (!ps.enabled) {
                        TagChip(
                            text = stringResource(R.string.ai_hub_off_badge),
                            fg = MaterialTheme.colorScheme.onSurfaceVariant,
                            bg = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                Text(
                    text = provider.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = ps.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .semantics { contentDescription = toggleDesc }
                    .testTag("ai_provider_switch_${provider.wire}")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statsText = if (stats != null && stats.total > 0) {
                "${stats.freeCount} free · ${stats.paidCount} paid"
            } else {
                ps.selectedModelId.ifBlank { "No model selected" }
            }
            Text(
                text = statsText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { keyExpanded = !keyExpanded },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ai_provider_key_toggle_${provider.wire}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = if (hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = if (hasKey) stringResource(R.string.ai_hub_key_set) else stringResource(R.string.ai_hub_api_key_button),
                        color = if (hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }

                if (ps.enabled && !isActive) {
                    OutlinedButton(
                        onClick = onActivate,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_provider_use_${provider.wire}")
                    ) {
                        Text(
                            text = stringResource(R.string.ai_hub_set_active),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }
        }

        if (!syncError.isNullOrBlank() && ps.enabled) {
            Text(
                text = syncError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(visible = keyExpanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    placeholder = {
                        Text(
                            text = "${provider.displayName} API key",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onSaveApiKey(keyDraft.trim())
                        keyExpanded = false
                    }),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) stringResource(R.string.ai_hub_hide_key_desc)
                                else stringResource(R.string.ai_hub_show_key_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_provider_key_input_${provider.wire}")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveApiKey(keyDraft.trim())
                            keyExpanded = false
                        },
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ai_provider_key_save_${provider.wire}")
                    ) {
                        Text(
                            text = stringResource(R.string.ai_hub_save_key),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    if (ps.apiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                keyDraft = ""
                                onSaveApiKey("")
                                keyExpanded = false
                            },
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ai_provider_key_clear_${provider.wire}")
                        ) {
                            Text(
                                text = stringResource(R.string.ai_hub_clear_key),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarModelRowCard(
    model: RadarModel,
    isSelected: Boolean,
    isProbing: Boolean,
    probeResult: ModelProbeResult?,
    onSelect: () -> Unit,
    onProbe: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .testTag("radar_model_card_${model.provider.wire}_${model.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = model.id,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (model.name.isNotBlank() && model.name != model.id) {
                    Text(
                        text = model.name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TierPill(tier = model.tier, basis = model.basis)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagChip(
                text = model.provider.displayName.uppercase(Locale.US),
                fg = MaterialTheme.colorScheme.primary,
                bg = MaterialTheme.colorScheme.primaryContainer
            )
            val ctxLabel = if (model.context > 0) "${ModelRadarEngine.formatContext(model.context)} ctx" else "ctx -"
            val priceLabel = "${ModelRadarEngine.formatPrice(model.priceIn)} / ${ModelRadarEngine.formatPrice(model.priceOut)} per 1M"
            Text(
                text = "$ctxLabel · $priceLabel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (probeResult != null) {
            Spacer(modifier = Modifier.height(6.dp))
            val probeText = if (probeResult.ok) {
                val tokSuffix = if (probeResult.promptTokens != null && probeResult.completionTokens != null) {
                    " · ${probeResult.promptTokens}→${probeResult.completionTokens} tok"
                } else ""
                "PROBE OK · ${probeResult.latencyMs}ms · \"${probeResult.replyPreview}\"$tokSuffix"
            } else {
                "PROBE FAIL · ${probeResult.error ?: "unreachable"}"
            }
            Text(
                text = probeText,
                color = if (probeResult.ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onProbe,
                enabled = !isProbing,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("probe_model_${model.provider.wire}_${model.id}")
            ) {
                if (isProbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (isProbing) stringResource(R.string.ai_hub_probing)
                    else stringResource(R.string.ai_hub_probe),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("select_model_${model.provider.wire}_${model.id}")
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                }
                Text(
                    text = if (isSelected) stringResource(R.string.ai_hub_selected_model)
                    else stringResource(R.string.ai_hub_use_model),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }
    }
}

@Composable
private fun TierPill(
    tier: ModelTier,
    basis: ModelTierBasis?
) {
    val label = buildString {
        append(tier.label)
        if (basis == ModelTierBasis.INFERRED) append(" ~")
    }
    val (fg, bg) = when (tier) {
        ModelTier.FREE -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        ModelTier.PAID -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
        ModelTier.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    TagChip(text = label, fg = fg, bg = bg)
}
