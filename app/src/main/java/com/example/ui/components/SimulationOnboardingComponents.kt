package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.model.AppConfig
import com.example.ui.theme.MonoTextStyle

data class SetupReadinessItem(
    val id: String,
    val title: String,
    val detail: String,
    val ready: Boolean,
    val badge: String,
    val actionLabel: String? = null
)

fun buildSetupReadinessChecklist(
    config: AppConfig,
    finnhubConfigured: Boolean,
    advisorEnabled: Boolean,
    hasNotificationPermission: Boolean
): List<SetupReadinessItem> {
    val alpacaConnected = !config.demo && config.alpacaId.isNotBlank() && config.alpacaSecret.isNotBlank()
    val enabledAiCount = config.aiRouting.providers.values.count { it.enabled && it.apiKey.isNotBlank() } +
        (if (config.geminiKey.isNotBlank()) 1 else 0)
    val aiReady = advisorEnabled || enabledAiCount > 0

    return listOf(
        SetupReadinessItem(
            id = "kraken_crypto",
            title = "Kraken 24/7 Crypto Spot & Perps",
            detail = "Live public spot quotes, funding, OI & CryptoPanic Guard (no key needed).",
            ready = true,
            badge = "LIVE PUBLIC"
        ),
        SetupReadinessItem(
            id = "equities_feed",
            title = "US Equities Market Feed",
            detail = if (alpacaConnected) "Alpaca API keys configured for live bars & quotes."
            else "Yahoo public quotes & simulated fallback active. Add Alpaca keys in Setup for live feed.",
            ready = true,
            badge = if (alpacaConnected) "ALPACA LIVE" else "PUBLIC / SIM"
        ),
        SetupReadinessItem(
            id = "finnhub_feed",
            title = "Finnhub Quotes & News Feed",
            detail = if (finnhubConfigured) "Connected for live equity quote snippets & headlines."
            else "Optional — add a free Finnhub key in Setup for real-time quotes & news.",
            ready = finnhubConfigured,
            badge = if (finnhubConfigured) "CONNECTED" else "OPTIONAL"
        ),
        SetupReadinessItem(
            id = "ai_providers",
            title = "AI News Catalyst & Council",
            detail = if (aiReady) "AI provider configured for headline scoring & council review."
            else "Heuristic scorer active. Add an AI key in Setup or AI Model Hub for LLM scoring.",
            ready = aiReady,
            badge = if (aiReady) "AI ACTIVE" else "HEURISTIC"
        ),
        SetupReadinessItem(
            id = "push_notifications",
            title = "Background Push Alerts",
            detail = if (hasNotificationPermission) "Enabled for price thresholds, AI signal flips & news catalysts."
            else "Notifications off — enable to receive background price & AI signal alerts.",
            ready = hasNotificationPermission,
            badge = if (hasNotificationPermission) "ENABLED" else "OFF",
            actionLabel = if (hasNotificationPermission) null else "ENABLE"
        )
    )
}

@Composable
fun SimulationDisclaimerDialog(
    config: AppConfig,
    finnhubConfigured: Boolean,
    advisorEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenSetup: () -> Unit,
    onContinue: () -> Unit
) {
    val items = remember(config, finnhubConfigured, advisorEnabled, hasNotificationPermission) {
        buildSetupReadinessChecklist(
            config = config,
            finnhubConfigured = finnhubConfigured,
            advisorEnabled = advisorEnabled,
            hasNotificationPermission = hasNotificationPermission
        )
    }
    val readyCount = remember(items) { items.count { it.ready } }

    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("simulation_disclaimer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TagChip(
                            text = stringResource(R.string.sim_disclaimer_badge),
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primaryContainer
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.sim_disclaimer_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sim_disclaimer_body),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = stringResource(R.string.sim_checklist_header))
                    TagChip(
                        text = stringResource(R.string.sim_checklist_ready, readyCount, items.size),
                        fg = MaterialTheme.colorScheme.secondary,
                        bg = MaterialTheme.colorScheme.secondaryContainer
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEachIndexed { idx, item ->
                        SetupReadinessRow(
                            item = item,
                            onAction = if (item.id == "push_notifications") onRequestNotificationPermission else null
                        )
                        if (idx < items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSetup,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sim_disclaimer_open_setup_button")
                    ) {
                        Text(
                            text = stringResource(R.string.sim_disclaimer_open_setup),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sim_disclaimer_continue_button")
                    ) {
                        Text(
                            text = stringResource(R.string.sim_disclaimer_continue),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupReadinessChecklistCard(
    config: AppConfig,
    finnhubConfigured: Boolean,
    advisorEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onShowDisclaimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(config, finnhubConfigured, advisorEnabled, hasNotificationPermission) {
        buildSetupReadinessChecklist(
            config = config,
            finnhubConfigured = finnhubConfigured,
            advisorEnabled = advisorEnabled,
            hasNotificationPermission = hasNotificationPermission
        )
    }
    val readyCount = remember(items) { items.count { it.ready } }

    TerminalCard(modifier = modifier.testTag("setup_readiness_checklist_card")) {
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
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.sim_checklist_header))
            }
            TagChip(
                text = stringResource(R.string.sim_checklist_ready, readyCount, items.size),
                fg = if (readyCount == items.size) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                bg = if (readyCount == items.size) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { idx, item ->
                SetupReadinessRow(
                    item = item,
                    onAction = if (item.id == "push_notifications") onRequestNotificationPermission else null
                )
                if (idx < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .clickable(onClick = onShowDisclaimer)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("setup_view_disclaimer_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.sim_footer_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SetupReadinessRow(
    item: SetupReadinessItem,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (item.ready) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = item.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }
        if (item.actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("checklist_action_${item.id}")
            ) {
                Text(
                    text = item.actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        } else {
            TagChip(
                text = item.badge,
                fg = if (item.ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                bg = if (item.ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            )
        }
    }
}

@Composable
fun SimulationFooterNotice(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(R.string.sim_footer_notice),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        style = MonoTextStyle.copy(fontSize = 9.5.sp),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(vertical = 8.dp)
            .testTag("simulation_footer_notice")
    )
}
