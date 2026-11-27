package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.domain.engine.BackgroundMonitorConfig
import com.example.domain.engine.BackgroundMonitorRunStatus
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 control panel for the WorkManager [com.example.data.worker.CatalystMonitorWorker]
 * that periodically scans high-impact catalyst scores and significant market movements
 * in the background and dispatches local notifications.
 */
@Composable
fun BackgroundCatalystMonitorCard(
    config: BackgroundMonitorConfig,
    status: BackgroundMonitorRunStatus,
    isRunningNow: Boolean,
    hasNotificationPermission: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectIntervalMinutes: (Int) -> Unit,
    onSelectMinCatalystScore: (Int) -> Unit,
    onSelectMinMovePct: (Double) -> Unit,
    onToggleWatchlistOnly: (Boolean) -> Unit,
    onRunCheckNow: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    TerminalCard(
        modifier = modifier.testTag("background_catalyst_monitor_card")
    ) {
        // Header + Master Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (config.enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = if (config.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SectionLabel(text = stringResource(R.string.bg_monitor_title))
                    TagChip(
                        text = when {
                            isRunningNow -> stringResource(R.string.bg_monitor_status_running)
                            config.enabled -> stringResource(R.string.bg_monitor_status_active, config.intervalMinutes)
                            else -> stringResource(R.string.bg_monitor_status_paused)
                        },
                        fg = when {
                            isRunningNow -> MaterialTheme.colorScheme.primary
                            config.enabled -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        bg = when {
                            isRunningNow -> MaterialTheme.colorScheme.primaryContainer
                            config.enabled -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.bg_monitor_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.5.sp)
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bg_monitor_enable_switch")
            )
        }

        // Notification permission prompt when missing on Android 13+
        if (config.enabled && !hasNotificationPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.bg_monitor_permission_needed),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bg_monitor_allow_notifications_button")
                ) {
                    Text(
                        text = stringResource(R.string.threshold_notif_allow),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(10.dp))

        // Check Interval Row (15m / 30m / 60m)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.bg_monitor_interval_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    val selected = config.intervalMinutes == minutes
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectIntervalMinutes(minutes) },
                        label = {
                            Text(
                                text = "${minutes}M",
                                style = MonoTextStyle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bg_monitor_interval_${minutes}m")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Threshold Chips Row: Min Catalyst Score + Min Market Move %
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.bg_monitor_score_threshold_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(45, 55, 70).forEach { threshold ->
                        val selected = config.minCatalystScoreAbs == threshold
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMinCatalystScore(threshold) },
                            label = {
                                Text(
                                    text = "≥$threshold",
                                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bg_monitor_score_$threshold")
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.bg_monitor_move_threshold_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2.0, 3.0, 5.0).forEach { movePct ->
                        val selected = kotlin.math.abs(config.minPriceMovePct - movePct) < 0.05
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMinMovePct(movePct) },
                            label = {
                                Text(
                                    text = "${movePct.toInt()}%",
                                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bg_monitor_move_${movePct.toInt()}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Watchlist-only scope toggle + Run Check Now button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = config.watchlistOnly,
                onClick = { onToggleWatchlistOnly(!config.watchlistOnly) },
                label = {
                    Text(
                        text = stringResource(R.string.bg_monitor_watchlist_only),
                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bg_monitor_watchlist_only_chip")
            )

            OutlinedButton(
                onClick = onRunCheckNow,
                enabled = !isRunningNow,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("bg_monitor_run_now_button")
            ) {
                if (isRunningNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (isRunningNow) {
                        stringResource(R.string.bg_monitor_checking_now)
                    } else {
                        stringResource(R.string.bg_monitor_run_now)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // Last background sweep telemetry strip
        if (status.lastRunAtMs > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val timeStr = timeFormatter.format(Date(status.lastRunAtMs))
                Text(
                    text = stringResource(
                        R.string.bg_monitor_last_run_meta,
                        timeStr,
                        status.tickersChecked,
                        status.alertsTriggered
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                )
                if (status.lastSummary.isNotBlank()) {
                    Text(
                        text = status.lastSummary,
                        color = if (status.succeeded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.5.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
