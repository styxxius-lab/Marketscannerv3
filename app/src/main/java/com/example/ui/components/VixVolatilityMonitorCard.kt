package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.VixAlertEngine
import com.example.domain.engine.VixMonitorState
import com.example.domain.model.VixSnapshot
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val VIX_SPIKE_PRESETS = listOf(5.0, 8.0, 10.0, 15.0, 20.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VixVolatilityMonitorCard(
    vixSnapshot: VixSnapshot?,
    monitorState: VixMonitorState,
    alertEnabled: Boolean,
    thresholdPct: Double,
    hasNotificationPermission: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateThreshold: (Double) -> Unit,
    onRearm: () -> Unit,
    onSimulateSpike: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thresholdInput by remember { mutableStateOf(formatThreshold(thresholdPct)) }

    LaunchedEffect(thresholdPct) {
        thresholdInput = formatThreshold(thresholdPct)
    }

    val spikePct = vixSnapshot?.intradaySpikePct ?: 0.0
    val isBreached = spikePct >= thresholdPct
    val isElevated = spikePct >= thresholdPct * 0.7

    val statusColor = when {
        !alertEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isBreached || !monitorState.armed -> MaterialTheme.colorScheme.error
        isElevated -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusText = when {
        !alertEnabled -> "PAUSED"
        isBreached || !monitorState.armed -> "SPIKE ALERT"
        isElevated -> "ELEVATED"
        else -> "ARMED"
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    TerminalCard(
        modifier = modifier.testTag("vix_monitor_card")
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SectionLabel(text = "CBOE VIX VOLATILITY")
                        TagChip(
                            text = vixSnapshot?.source ?: "SYNCING",
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    Text(
                        text = "Intraday spike push monitor",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (alertEnabled) Icons.Outlined.NotificationsActive
                    else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = if (alertEnabled) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Switch(
                    checked = alertEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = statusColor
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vix_alert_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live VIX Price & Status Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (vixSnapshot != null) fmtNum(vixSnapshot.current, 2) else "—",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.testTag("vix_live_value")
                    )
                    if (vixSnapshot != null) {
                        val chgColor = if (vixSnapshot.changeFromClosePct >= 0.0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                        Text(
                            text = "${fmtPctSigned(vixSnapshot.changeFromClosePct)} vs close",
                            color = chgColor,
                            style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            TagChip(
                text = statusText,
                fg = statusColor,
                bg = statusColor.copy(alpha = 0.16f)
            )
        }

        // Intraday Sparkline + User Threshold Trigger Line
        if (vixSnapshot != null && vixSnapshot.intradaySeries.size >= 2) {
            Spacer(modifier = Modifier.height(10.dp))
            val triggerLevel = remember(vixSnapshot, thresholdPct) {
                val base = min(vixSnapshot.intradayLow, vixSnapshot.open).coerceAtLeast(5.0)
                base * (1.0 + thresholdPct / 100.0)
            }
            val lineColor = when {
                isBreached -> MaterialTheme.colorScheme.error
                isElevated -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            val thresholdColor = MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
            val outlineColor = MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .border(1.dp, outlineColor, RoundedCornerShape(10.dp))
                    .semantics {
                        contentDescription =
                            "Intraday CBOE VIX chart at ${fmtNum(vixSnapshot.current, 2)}, spike ${fmtNum(spikePct, 1)} percent, trigger level ${fmtNum(triggerLevel, 2)}"
                    }
                    .testTag("vix_intraday_chart")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pts = vixSnapshot.intradaySeries
                    val padX = 8.dp.toPx()
                    val padY = 8.dp.toPx()
                    val chartW = (size.width - padX * 2f).coerceAtLeast(1f)
                    val chartH = (size.height - padY * 2f).coerceAtLeast(1f)

                    val rawMin = pts.minOrNull() ?: vixSnapshot.current
                    val rawMax = max(pts.maxOrNull() ?: vixSnapshot.current, triggerLevel * 1.01)
                    val span = (rawMax - rawMin).coerceAtLeast(0.5)

                    fun yFor(v: Double): Float {
                        val frac = ((v - rawMin) / span).toFloat().coerceIn(0f, 1f)
                        return padY + (1f - frac) * chartH
                    }

                    // Dashed Threshold Trigger Line
                    val yThresh = yFor(triggerLevel)
                    drawLine(
                        color = thresholdColor,
                        start = Offset(padX, yThresh),
                        end = Offset(size.width - padX, yThresh),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )

                    val linePath = Path()
                    val areaPath = Path()
                    val stepX = chartW / (pts.size - 1).coerceAtLeast(1)
                    var lastX = padX
                    var lastY = yFor(pts.last())

                    pts.forEachIndexed { idx, v ->
                        val x = padX + idx * stepX
                        val y = yFor(v)
                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            areaPath.moveTo(x, padY + chartH)
                            areaPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }
                        if (idx == pts.lastIndex) {
                            lastX = x
                            lastY = y
                        }
                    }
                    areaPath.lineTo(lastX, padY + chartH)
                    areaPath.close()

                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
                            startY = padY,
                            endY = padY + chartH
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawCircle(
                        color = lineColor.copy(alpha = 0.3f),
                        radius = 5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }

                Text(
                    text = "TRIGGER ≥ ${fmtNum(triggerLevel, 2)} (+${formatThreshold(thresholdPct)}%)",
                    color = thresholdColor,
                    style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3 Metric Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = "LOW → HIGH",
                value = if (vixSnapshot != null) {
                    "${fmtNum(vixSnapshot.intradayLow, 1)}–${fmtNum(vixSnapshot.intradayHigh, 1)}"
                } else "—",
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "SPIKE",
                value = "+${fmtNum(spikePct, 1)}%",
                valueColor = statusColor,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "THRESHOLD",
                value = "+${formatThreshold(thresholdPct)}%",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Threshold Presets + Custom Input
        Text(
            text = "SPIKE TRIGGER THRESHOLD (%)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VIX_SPIKE_PRESETS.forEach { preset ->
                val isSelected = abs(thresholdPct - preset) < 0.05
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            thresholdInput = formatThreshold(preset)
                            onUpdateThreshold(preset)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("vix_preset_${preset.roundToInt()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${formatThreshold(preset)}%",
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val parsedInput = thresholdInput.toDoubleOrNull()
        val validInput = parsedInput != null &&
            parsedInput in VixAlertEngine.MIN_THRESHOLD_PCT..VixAlertEngine.MAX_THRESHOLD_PCT
        val inputDirty = validInput && abs((parsedInput ?: thresholdPct) - thresholdPct) >= 0.05

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = thresholdInput,
                onValueChange = { thresholdInput = it },
                label = { Text("Custom spike % (2–100%)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .weight(1f)
                    .testTag("vix_threshold_input")
            )
            OutlinedButton(
                onClick = {
                    val v = thresholdInput.toDoubleOrNull()
                    if (v != null && v in VixAlertEngine.MIN_THRESHOLD_PCT..VixAlertEngine.MAX_THRESHOLD_PCT) {
                        onRequestNotificationPermission()
                        onUpdateThreshold(v)
                    }
                },
                enabled = validInput && inputDirty,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("vix_threshold_save_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (inputDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = "SET",
                    color = if (inputDirty) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
            OutlinedButton(
                onClick = {
                    onRequestNotificationPermission()
                    onSimulateSpike()
                },
                enabled = alertEnabled && vixSnapshot != null,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("vix_test_spike_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = "Simulate VIX spike alert",
                    tint = if (alertEnabled) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = "TEST",
                    color = if (alertEnabled) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }

        // Latest Alert Banner & Re-arm Control
        val latestAlert = monitorState.recentAlerts.firstOrNull()
        if (latestAlert != null || !monitorState.armed) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (latestAlert != null) {
                        Text(
                            text = "Last spike: ${fmtNum(latestAlert.vixLevel, 2)} (+${fmtNum(latestAlert.spikePct, 1)}%) at ${timeFormatter.format(Date(latestAlert.timestampMs))}",
                            color = MaterialTheme.colorScheme.error,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = if (monitorState.armed) "Monitor armed for next spike."
                        else "Cooldown active (15m or resets at +${fmtNum(thresholdPct * 0.75, 1)}%).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                    )
                }
                if (!monitorState.armed) {
                    OutlinedButton(
                        onClick = onRearm,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("vix_rearm_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Re-arm VIX alert",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "RE-ARM",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        if (alertEnabled && !hasNotificationPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enable Android notification permission to receive VIX spike alerts.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("vix_enable_notifications_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "ALLOW",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }
    }
}

private fun formatThreshold(v: Double): String {
    return if (abs(v - v.roundToInt()) < 0.05) {
        v.roundToInt().toString()
    } else {
        "%.1f".format(Locale.US, v)
    }
}
