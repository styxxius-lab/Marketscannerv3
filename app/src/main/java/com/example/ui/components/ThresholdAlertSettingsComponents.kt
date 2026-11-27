package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.example.domain.engine.ThresholdAlertEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.RadarModel
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThresholdAlertEvent
import com.example.domain.model.ThresholdAlertKind
import com.example.domain.model.ThresholdAlertSettings
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdAlertSettingsSheet(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    aiRouting: AiRoutingConfig,
    geminiFallbackKey: String,
    radarModels: List<RadarModel>,
    onToggleAiProvider: (AiProviderId, Boolean) -> Unit,
    onSelectActiveAiProvider: (AiProviderId) -> Unit,
    onOpenAiModelHub: () -> Unit,
    finnhubKey: String,
    finnhubConfigured: Boolean,
    onSaveFinnhubKey: (String) -> Unit,
    settings: ThresholdAlertSettings,
    currentRegime: RegimeLabel,
    watchlistCount: Int,
    hasNotificationPermission: Boolean,
    recentEvents: List<ThresholdAlertEvent>,
    onUpdateSettings: (ThresholdAlertSettings) -> Unit,
    onTestAlert: () -> Unit,
    onClearEvents: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("threshold_settings_sheet")
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
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.threshold_settings_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.threshold_settings_subtitle),
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
                        .testTag("threshold_settings_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.threshold_close_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            GlobalThemeSettingsCard(
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode
            )

            AiModelHubSummaryCard(
                aiRouting = aiRouting,
                geminiFallbackKey = geminiFallbackKey,
                radarModels = radarModels,
                onToggleProvider = onToggleAiProvider,
                onSelectActiveProvider = onSelectActiveAiProvider,
                onOpenModelHub = onOpenAiModelHub
            )

            FinnhubApiKeySettingsCard(
                finnhubKey = finnhubKey,
                finnhubConfigured = finnhubConfigured,
                onSaveFinnhubKey = onSaveFinnhubKey
            )

            ThresholdAlertSettingsCard(
                settings = settings,
                currentRegime = currentRegime,
                watchlistCount = watchlistCount,
                hasNotificationPermission = hasNotificationPermission,
                recentEvents = recentEvents,
                onUpdateSettings = onUpdateSettings,
                onTestAlert = onTestAlert,
                onClearEvents = onClearEvents,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }
    }
}

@Composable
fun FinnhubApiKeySettingsCard(
    finnhubKey: String,
    finnhubConfigured: Boolean,
    onSaveFinnhubKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var keyInput by remember { mutableStateOf(finnhubKey) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(finnhubKey) {
        keyInput = finnhubKey
    }

    val submitKey = {
        onSaveFinnhubKey(keyInput.trim())
    }

    TerminalCard(modifier = modifier.testTag("finnhub_api_key_settings_card")) {
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
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.finnhub_settings_section_title))
            }
            TagChip(
                text = if (finnhubConfigured) stringResource(R.string.finnhub_settings_configured)
                else stringResource(R.string.finnhub_settings_not_set),
                fg = if (finnhubConfigured) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                bg = if (finnhubConfigured) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.finnhub_settings_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.finnhub_settings_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submitKey() }),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showKey) {
                            stringResource(R.string.finnhub_settings_hide_desc)
                        } else {
                            stringResource(R.string.finnhub_settings_show_desc)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
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
                .testTag("settings_finnhub_key_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = submitKey,
                enabled = keyInput.trim() != finnhubKey.trim() && keyInput.trim().isNotEmpty(),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("settings_save_finnhub_key_button")
            ) {
                Text(
                    text = stringResource(R.string.finnhub_settings_save),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            if (finnhubKey.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        keyInput = ""
                        onSaveFinnhubKey("")
                    },
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("settings_clear_finnhub_key_button")
                ) {
                    Text(
                        text = stringResource(R.string.finnhub_settings_clear),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun GlobalThemeSettingsCard(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = themeMode == ThemeMode.DARK
    val toggleDesc = stringResource(R.string.theme_toggle_content_desc)

    TerminalCard(modifier = modifier.testTag("global_theme_settings_card")) {
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
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.theme_section_title))
            }
            TagChip(
                text = if (isDark) stringResource(R.string.theme_badge_dark)
                else stringResource(R.string.theme_badge_light),
                fg = MaterialTheme.colorScheme.primary,
                bg = MaterialTheme.colorScheme.primaryContainer
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Global Dark/Light Material 3 Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .clickable {
                    onSelectThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isDark) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = null,
                        tint = if (isDark) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.theme_toggle_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isDark) stringResource(R.string.theme_toggle_desc_dark)
                        else stringResource(R.string.theme_toggle_desc_light),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Switch(
                checked = isDark,
                onCheckedChange = { darkSelected ->
                    onSelectThemeMode(if (darkSelected) ThemeMode.DARK else ThemeMode.LIGHT)
                },
                thumbContent = {
                    Icon(
                        imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .semantics { contentDescription = toggleDesc }
                    .testTag("global_theme_toggle_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Explicit Light / Dark Segmented Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                .padding(3.dp)
                .testTag("theme_switcher"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                val selected = themeMode == mode
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .clickable { onSelectThemeMode(mode) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("theme_${mode.name.lowercase(Locale.US)}_option"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (mode == ThemeMode.DARK) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (mode == ThemeMode.DARK) stringResource(R.string.theme_option_dark)
                        else stringResource(R.string.theme_option_light),
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThresholdAlertSettingsCard(
    settings: ThresholdAlertSettings,
    currentRegime: RegimeLabel,
    watchlistCount: Int,
    hasNotificationPermission: Boolean,
    recentEvents: List<ThresholdAlertEvent>,
    onUpdateSettings: (ThresholdAlertSettings) -> Unit,
    onTestAlert: () -> Unit,
    onClearEvents: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArmed = (settings.regimeAlertsEnabled && (settings.watchedRegimes.isNotEmpty() || settings.alertOnPanicGuard)) ||
        settings.aiNewsAlertsEnabled
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    TerminalCard(modifier = modifier.testTag("threshold_alert_settings_card")) {
        // Card Header
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
                    imageVector = if (isArmed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = if (isArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.threshold_section_title))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(
                    text = currentRegime.name,
                    fg = regimeForeground(currentRegime),
                    bg = regimeBackground(currentRegime)
                )
                TagChip(
                    text = if (isArmed) stringResource(R.string.threshold_status_active)
                    else stringResource(R.string.threshold_status_paused),
                    fg = if (isArmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    bg = if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Notification permission banner when armed without permission
        if (!hasNotificationPermission && isArmed) {
            Spacer(modifier = Modifier.height(10.dp))
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
                    text = stringResource(R.string.threshold_notif_banner),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("threshold_allow_notif_button")
                ) {
                    Text(
                        text = stringResource(R.string.threshold_notif_allow),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION 1: MARKET REGIME THRESHOLD ALERTS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.threshold_regime_header),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.threshold_regime_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.regimeAlertsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !hasNotificationPermission) onRequestNotificationPermission()
                    onUpdateSettings(settings.copy(regimeAlertsEnabled = enabled))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("regime_alerts_switch")
            )
        }

        if (settings.regimeAlertsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("regime_alert_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RegimeLabel.entries.forEach { label ->
                    val selected = settings.watchedRegimes.contains(label)
                    val fg = regimeForeground(label)
                    val bg = regimeBackground(label)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val nextSet = if (selected) {
                                settings.watchedRegimes - label
                            } else {
                                settings.watchedRegimes + label
                            }
                            onUpdateSettings(settings.copy(watchedRegimes = nextSet))
                        },
                        label = {
                            Text(
                                text = label.name.replace('_', ' '),
                                style = MonoTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = bg,
                            selectedLabelColor = fg,
                            containerColor = MaterialTheme.colorScheme.background,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = fg.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("regime_alert_chip_${label.name.lowercase(Locale.US)}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.threshold_panic_guard_title),
                subtitle = stringResource(R.string.threshold_panic_guard_desc),
                checked = settings.alertOnPanicGuard,
                onCheckedChange = { onUpdateSettings(settings.copy(alertOnPanicGuard = it)) },
                testTag = "panic_guard_alert_switch"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION 2: AI NEWS SCORE CHANGE THRESHOLD ALERTS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.threshold_ai_header),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.threshold_ai_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.aiNewsAlertsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !hasNotificationPermission) onRequestNotificationPermission()
                    onUpdateSettings(settings.copy(aiNewsAlertsEnabled = enabled))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("ai_news_alerts_switch")
            )
        }

        if (settings.aiNewsAlertsEnabled) {
            Spacer(modifier = Modifier.height(10.dp))

            // Score Swing Delta Slider (10..80, step 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.threshold_ai_delta_label),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                TagChip(
                    text = "Δ ≥ ±${settings.aiNewsScoreDeltaThreshold} pts",
                    fg = MaterialTheme.colorScheme.primary,
                    bg = MaterialTheme.colorScheme.primaryContainer
                )
            }
            Slider(
                value = settings.aiNewsScoreDeltaThreshold
                    .coerceIn(ThresholdAlertEngine.MIN_DELTA_THRESHOLD, ThresholdAlertEngine.MAX_DELTA_THRESHOLD)
                    .toFloat(),
                onValueChange = { raw ->
                    val snapped = ((raw / 5f).roundToInt() * 5)
                        .coerceIn(ThresholdAlertEngine.MIN_DELTA_THRESHOLD, ThresholdAlertEngine.MAX_DELTA_THRESHOLD)
                    if (snapped != settings.aiNewsScoreDeltaThreshold) {
                        onUpdateSettings(settings.copy(aiNewsScoreDeltaThreshold = snapped))
                    }
                },
                valueRange = ThresholdAlertEngine.MIN_DELTA_THRESHOLD.toFloat()..ThresholdAlertEngine.MAX_DELTA_THRESHOLD.toFloat(),
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_delta_threshold_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Extreme Score Crossing Slider (20..95, step 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.threshold_ai_extreme_label),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                TagChip(
                    text = "|Score| ≥ ${settings.aiNewsMinScoreAbsThreshold}",
                    fg = MaterialTheme.colorScheme.tertiary,
                    bg = MaterialTheme.colorScheme.tertiaryContainer
                )
            }
            Slider(
                value = settings.aiNewsMinScoreAbsThreshold
                    .coerceIn(ThresholdAlertEngine.MIN_EXTREME_THRESHOLD, ThresholdAlertEngine.MAX_EXTREME_THRESHOLD)
                    .toFloat(),
                onValueChange = { raw ->
                    val snapped = ((raw / 5f).roundToInt() * 5)
                        .coerceIn(ThresholdAlertEngine.MIN_EXTREME_THRESHOLD, ThresholdAlertEngine.MAX_EXTREME_THRESHOLD)
                    if (snapped != settings.aiNewsMinScoreAbsThreshold) {
                        onUpdateSettings(settings.copy(aiNewsMinScoreAbsThreshold = snapped))
                    }
                },
                valueRange = ThresholdAlertEngine.MIN_EXTREME_THRESHOLD.toFloat()..ThresholdAlertEngine.MAX_EXTREME_THRESHOLD.toFloat(),
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_extreme_threshold_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))
            SettingToggleRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.threshold_advisor_veto_title),
                subtitle = stringResource(R.string.threshold_advisor_veto_desc),
                checked = settings.alertOnAdvisorVeto,
                onCheckedChange = { onUpdateSettings(settings.copy(alertOnAdvisorVeto = it)) },
                testTag = "advisor_veto_alert_switch"
            )

            Spacer(modifier = Modifier.height(8.dp))
            val watchSuffix = if (watchlistCount > 0) " ($watchlistCount starred)" else " (star symbols to filter)"
            SettingToggleRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.threshold_watchlist_only_title),
                subtitle = stringResource(R.string.threshold_watchlist_only_desc) + watchSuffix,
                checked = settings.aiNewsWatchlistOnly,
                onCheckedChange = { onUpdateSettings(settings.copy(aiNewsWatchlistOnly = it)) },
                testTag = "ai_watchlist_only_switch"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (!hasNotificationPermission) onRequestNotificationPermission()
                    onTestAlert()
                },
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_threshold_alert_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.threshold_action_test),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            if (recentEvents.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearEvents,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("clear_threshold_alerts_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.threshold_action_clear_log),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Recent Triggered Alerts Log
        if (recentEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel(text = stringResource(R.string.threshold_recent_header))
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_threshold_alerts_list")
            ) {
                recentEvents.take(5).forEach { ev ->
                    val (accentFg, accentBg) = eventColors(ev.kind)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TagChip(
                                    text = eventBadge(ev.kind),
                                    fg = accentFg,
                                    bg = accentBg
                                )
                                Text(
                                    text = timeFormatter.format(Date(ev.timestampMs)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(fontSize = 10.5.sp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ev.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ev.detail,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun regimeForeground(label: RegimeLabel): Color = when (label) {
    RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
    RegimeLabel.NEUTRAL -> SignalHold
    RegimeLabel.RISK_OFF -> MaterialTheme.colorScheme.tertiary
    RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
}

@Composable
private fun regimeBackground(label: RegimeLabel): Color = when (label) {
    RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondaryContainer
    RegimeLabel.NEUTRAL -> SignalHoldContainer
    RegimeLabel.RISK_OFF -> MaterialTheme.colorScheme.tertiaryContainer
    RegimeLabel.CRISIS -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun eventColors(kind: ThresholdAlertKind): Pair<Color, Color> = when (kind) {
    ThresholdAlertKind.REGIME_SHIFT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    ThresholdAlertKind.PANIC_GUARD -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    ThresholdAlertKind.AI_SCORE_DELTA -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    ThresholdAlertKind.AI_SCORE_EXTREME -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
    ThresholdAlertKind.ADVISOR_VETO -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
}

private fun eventBadge(kind: ThresholdAlertKind): String = when (kind) {
    ThresholdAlertKind.REGIME_SHIFT -> "REGIME"
    ThresholdAlertKind.PANIC_GUARD -> "PANIC"
    ThresholdAlertKind.AI_SCORE_DELTA -> "AI DELTA"
    ThresholdAlertKind.AI_SCORE_EXTREME -> "AI SCORE"
    ThresholdAlertKind.ADVISOR_VETO -> "VETO"
}
