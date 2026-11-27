package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.DailyPnlSnapshotEntity
import com.example.domain.engine.DailyPnlChartAnalytics
import com.example.domain.engine.DailyPnlChartMode
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Interactive 30-Day Daily P&L Performance Chart backed directly by Room's `daily_pnl_snapshots`
 * and `holdings` tables. Implements a Recharts-style ComposedChart / BarChart / AreaChart
 * visualization natively in Jetpack Compose Canvas with dashed Cartesian grid, $0 reference line,
 * daily green/red bars, cumulative P&L spline overlay, interactive drag/tap crosshair tooltip,
 * and 30-day performance KPIs.
 */
@Composable
fun PaperDailyPnlChartCard(
    analytics: DailyPnlChartAnalytics,
    isSyncing: Boolean,
    onRefreshHistory: () -> Unit,
    onSeedDummyHoldings: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "daily_pnl_30d"
) {
    var chartMode by remember { mutableStateOf(DailyPnlChartMode.COMBO) }
    var inspectedIndex by remember(analytics.snapshots.size, chartMode) { mutableStateOf<Int?>(null) }

    val snapshots = analytics.snapshots
    val positiveColor = MaterialTheme.colorScheme.secondary
    val negativeColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val canvasBg = MaterialTheme.colorScheme.background.copy(alpha = 0.62f)
    val highlightDotInner = MaterialTheme.colorScheme.onBackground

    val dateShortFormatter = remember {
        SimpleDateFormat("MMM d", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
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
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    SectionLabel(text = stringResource(R.string.daily_pnl_chart_title))
                    Text(
                        text = if (snapshots.isNotEmpty()) {
                            stringResource(R.string.daily_pnl_chart_subtitle_active, snapshots.size)
                        } else {
                            stringResource(R.string.daily_pnl_chart_subtitle_empty)
                        },
                        color = mutedColor,
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
                TagChip(
                    text = stringResource(R.string.daily_pnl_chart_badge_30d),
                    fg = primaryColor,
                    bg = MaterialTheme.colorScheme.primaryContainer
                )
                if (snapshots.isNotEmpty()) {
                    IconButton(
                        onClick = onRefreshHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_refresh_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = primaryColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.daily_pnl_chart_refresh_desc),
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (snapshots.isEmpty()) {
            // Empty state when no Room paper holdings exist yet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(canvasBg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(vertical = 20.dp, horizontal = 14.dp)
                    .testTag("${testTagPrefix}_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.daily_pnl_chart_empty_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.daily_pnl_chart_empty_subtitle),
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onSeedDummyHoldings,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_empty_seed_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, positiveColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = stringResource(R.string.paper_holdings_action_seed),
                        color = positiveColor,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
            return@TerminalCard
        }

        val activeIdx = (inspectedIndex ?: snapshots.lastIndex).coerceIn(0, snapshots.lastIndex)
        val activeDay = snapshots[activeIdx]
        val activeDailyColor = if (activeDay.dailyPnl >= 0.0) positiveColor else negativeColor
        val activeCumulColor = if (activeDay.cumulativePnl >= 0.0) positiveColor else negativeColor

        // Mode Switcher + Interactive Recharts-style Tooltip Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Segmented Control (COMBO / DAILY / CUMUL)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(canvasBg)
                    .border(1.dp, borderColor, RoundedCornerShape(9.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DailyPnlChartMode.entries.forEach { mode ->
                    val selected = chartMode == mode
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { chartMode = mode }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("${testTagPrefix}_mode_${mode.name.lowercase(Locale.US)}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            color = if (selected) primaryColor else mutedColor,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            if (inspectedIndex != null) {
                Text(
                    text = stringResource(R.string.equity_dashboard_reset_inspect),
                    color = primaryColor,
                    style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { inspectedIndex = null }
                        .padding(horizontal = 6.dp)
                        .testTag("${testTagPrefix}_reset_inspect")
                )
            } else {
                Text(
                    text = stringResource(R.string.daily_pnl_chart_scrub_hint),
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recharts-Style Pinned Tooltip Banner for Active / Scrubbed Day
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(canvasBg)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("${testTagPrefix}_tooltip_banner"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeIdx == snapshots.lastIndex) {
                        "${dateShortFormatter.format(Date(activeDay.epochDayMs))} · Today"
                    } else {
                        dateShortFormatter.format(Date(activeDay.epochDayMs))
                    },
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = fmtSignedMoney(activeDay.dailyPnl),
                        color = activeDailyColor,
                        style = MonoTextStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.testTag("${testTagPrefix}_active_daily_pnl")
                    )
                    TagChip(
                        text = fmtPctSigned(activeDay.dailyPnlPct),
                        fg = activeDailyColor,
                        bg = if (activeDay.dailyPnl >= 0.0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.daily_pnl_chart_cumul_label),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp)
                )
                Text(
                    text = fmtSignedMoney(activeDay.cumulativePnl),
                    color = activeCumulColor,
                    style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "Val ${fmtMoney(activeDay.portfolioValue)}",
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 10.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Interactive Canvas Chart + Right Y-Axis
        val (barAxisLo, barAxisHi) = remember(snapshots) {
            val minD = minOf(snapshots.minOf { it.dailyPnl }, 0.0)
            val maxD = maxOf(snapshots.maxOf { it.dailyPnl }, 0.0)
            val span = (maxD - minD).takeIf { it > 0.01 } ?: 20.0
            (minD - span * 0.16) to (maxD + span * 0.16)
        }
        val (cumulAxisLo, cumulAxisHi) = remember(snapshots) {
            val minC = minOf(snapshots.minOf { it.cumulativePnl }, 0.0)
            val maxC = maxOf(snapshots.maxOf { it.cumulativePnl }, 0.0)
            val span = (maxC - minC).takeIf { it > 0.01 } ?: 50.0
            (minC - span * 0.16) to (maxC + span * 0.16)
        }

        val yAxisLo = if (chartMode == DailyPnlChartMode.CUMULATIVE) cumulAxisLo else barAxisLo
        val yAxisHi = if (chartMode == DailyPnlChartMode.CUMULATIVE) cumulAxisHi else barAxisHi

        val chartSemanticDesc = stringResource(
            R.string.daily_pnl_chart_accessibility_desc,
            snapshots.size,
            fmtSignedMoney(analytics.net30dPnl)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(canvasBg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .semantics { contentDescription = chartSemanticDesc }
                    .testTag("${testTagPrefix}_canvas")
                    .pointerInput(snapshots.size) {
                        detectTapGestures { offset ->
                            if (snapshots.isNotEmpty() && size.width > 0) {
                                val padPx = 10.dp.toPx()
                                val usable = (size.width - padPx * 2f).coerceAtLeast(1f)
                                val frac = ((offset.x - padPx) / usable).coerceIn(0f, 1f)
                                inspectedIndex = (frac * (snapshots.size - 1)).toInt()
                                    .coerceIn(0, snapshots.lastIndex)
                            }
                        }
                    }
                    .pointerInput(snapshots.size) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (snapshots.isNotEmpty() && size.width > 0) {
                                val padPx = 10.dp.toPx()
                                val usable = (size.width - padPx * 2f).coerceAtLeast(1f)
                                val frac = ((change.position.x - padPx) / usable).coerceIn(0f, 1f)
                                inspectedIndex = (frac * (snapshots.size - 1)).toInt()
                                    .coerceIn(0, snapshots.lastIndex)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padH = 10.dp.toPx()
                    val padV = 14.dp.toPx()
                    val plotW = (w - padH * 2f).coerceAtLeast(1f)
                    val plotH = (h - padV * 2f).coerceAtLeast(1f)

                    fun mapY(v: Double, lo: Double, hi: Double): Float {
                        val span = (hi - lo).takeIf { it > 1e-6 } ?: 1.0
                        val norm = ((v - lo) / span).toFloat().coerceIn(0f, 1f)
                        return padV + plotH * (1f - norm)
                    }

                    val count = snapshots.size
                    val slotW = plotW / count.coerceAtLeast(1)
                    fun centerX(i: Int): Float = padH + slotW * (i + 0.5f)

                    // 1. Dashed Horizontal Cartesian Grid (Recharts CartesianGrid)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    for (g in 0..3) {
                        val gy = padV + plotH * (g / 3f)
                        drawLine(
                            color = mutedColor.copy(alpha = 0.18f),
                            start = Offset(padH, gy),
                            end = Offset(w - padH, gy),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // 2. Zero ReferenceLine
                    val zeroY = mapY(0.0, yAxisLo, yAxisHi)
                    drawLine(
                        color = mutedColor.copy(alpha = 0.52f),
                        start = Offset(padH, zeroY),
                        end = Offset(w - padH, zeroY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
                    )

                    // 3. Daily P&L Bars (COMBO and DAILY_BARS modes)
                    if (chartMode == DailyPnlChartMode.COMBO || chartMode == DailyPnlChartMode.DAILY_BARS) {
                        val barWidth = (slotW * 0.64f).coerceIn(2.5.dp.toPx(), 12.dp.toPx())
                        val cornerPx = 2.5.dp.toPx()
                        val baseZeroY = mapY(0.0, barAxisLo, barAxisHi)

                        snapshots.forEachIndexed { idx, snap ->
                            val cx = centerX(idx)
                            val yVal = mapY(snap.dailyPnl, barAxisLo, barAxisHi)
                            val top = minOf(baseZeroY, yVal)
                            val barH = abs(yVal - baseZeroY).coerceAtLeast(1.5.dp.toPx())
                            val isSelected = idx == activeIdx
                            val baseColor = if (snap.dailyPnl >= 0.0) positiveColor else negativeColor
                            val alpha = when {
                                isSelected -> 1f
                                chartMode == DailyPnlChartMode.COMBO -> 0.58f
                                else -> 0.85f
                            }
                            drawRoundRect(
                                color = baseColor.copy(alpha = alpha),
                                topLeft = Offset(cx - barWidth / 2f, top),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(cornerPx, cornerPx)
                            )
                        }
                    }

                    // 4. Cumulative P&L Line / Area (COMBO overlay and CUMULATIVE AreaChart mode)
                    if (chartMode == DailyPnlChartMode.COMBO || chartMode == DailyPnlChartMode.CUMULATIVE) {
                        val linePoints = snapshots.mapIndexed { idx, snap ->
                            Offset(centerX(idx), mapY(snap.cumulativePnl, cumulAxisLo, cumulAxisHi))
                        }
                        if (linePoints.size >= 2) {
                            val linePath = buildSmoothPath(linePoints)
                            val lineColor = if (chartMode == DailyPnlChartMode.COMBO) {
                                primaryColor
                            } else {
                                if (analytics.latestCumulativePnl >= 0.0) positiveColor else negativeColor
                            }

                            if (chartMode == DailyPnlChartMode.CUMULATIVE) {
                                val cumulZeroY = mapY(0.0, cumulAxisLo, cumulAxisHi)
                                val fillPath = Path().apply {
                                    addPath(linePath)
                                    lineTo(linePoints.last().x, cumulZeroY)
                                    lineTo(linePoints.first().x, cumulZeroY)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            lineColor.copy(alpha = 0.28f),
                                            lineColor.copy(alpha = 0.03f)
                                        ),
                                        startY = padV,
                                        endY = h - padV
                                    )
                                )
                            }

                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = Stroke(
                                    width = if (chartMode == DailyPnlChartMode.COMBO) 2.dp.toPx() else 2.4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // 5. Interactive Vertical Crosshair & Active Marker
                    val activeX = centerX(activeIdx)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.45f),
                        start = Offset(activeX, padV),
                        end = Offset(activeX, h - padV),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )

                    val markerY = when (chartMode) {
                        DailyPnlChartMode.CUMULATIVE -> mapY(activeDay.cumulativePnl, cumulAxisLo, cumulAxisHi)
                        DailyPnlChartMode.COMBO -> mapY(activeDay.cumulativePnl, cumulAxisLo, cumulAxisHi)
                        DailyPnlChartMode.DAILY_BARS -> mapY(activeDay.dailyPnl, barAxisLo, barAxisHi)
                    }
                    val markerColor = when (chartMode) {
                        DailyPnlChartMode.COMBO -> primaryColor
                        DailyPnlChartMode.CUMULATIVE -> activeCumulColor
                        DailyPnlChartMode.DAILY_BARS -> activeDailyColor
                    }
                    drawCircle(
                        color = markerColor.copy(alpha = 0.28f),
                        radius = 7.dp.toPx(),
                        center = Offset(activeX, markerY)
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 4.dp.toPx(),
                        center = Offset(activeX, markerY)
                    )
                    drawCircle(
                        color = highlightDotInner,
                        radius = 1.6.dp.toPx(),
                        center = Offset(activeX, markerY)
                    )
                }
            }

            // Right Y-Axis Tick Labels
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatCompactSignedDollar(yAxisHi),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1
                )
                Text(
                    text = "$0",
                    color = mutedColor.copy(alpha = 0.85f),
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = formatCompactSignedDollar(yAxisLo),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    maxLines = 1
                )
            }
        }

        // X-Axis Date Labels Row
        Spacer(modifier = Modifier.height(5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 62.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val mid1Idx = (snapshots.size / 3).coerceIn(0, snapshots.lastIndex)
            val mid2Idx = ((snapshots.size * 2) / 3).coerceIn(0, snapshots.lastIndex)
            Text(
                text = dateShortFormatter.format(Date(snapshots.first().epochDayMs)),
                color = mutedColor,
                style = MonoTextStyle.copy(fontSize = 9.5.sp)
            )
            Text(
                text = dateShortFormatter.format(Date(snapshots[mid1Idx].epochDayMs)),
                color = mutedColor,
                style = MonoTextStyle.copy(fontSize = 9.5.sp)
            )
            Text(
                text = dateShortFormatter.format(Date(snapshots[mid2Idx].epochDayMs)),
                color = mutedColor,
                style = MonoTextStyle.copy(fontSize = 9.5.sp)
            )
            Text(
                text = stringResource(R.string.equity_dashboard_axis_now),
                color = primaryColor,
                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Legend Row
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (chartMode != DailyPnlChartMode.CUMULATIVE) {
                LegendSwatch(color = positiveColor, label = stringResource(R.string.daily_pnl_legend_gain))
                LegendSwatch(color = negativeColor, label = stringResource(R.string.daily_pnl_legend_loss))
            }
            if (chartMode != DailyPnlChartMode.DAILY_BARS) {
                LegendSwatch(
                    color = if (chartMode == DailyPnlChartMode.COMBO) primaryColor else activeCumulColor,
                    label = stringResource(R.string.daily_pnl_legend_cumul)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 30-Day Performance KPI Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val net30dColor = if (analytics.net30dPnl >= 0.0) positiveColor else negativeColor
            MiniMetric(
                label = stringResource(R.string.daily_pnl_kpi_net_30d),
                value = fmtSignedMoney(analytics.net30dPnl),
                valueColor = net30dColor,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_kpi_net30d")
            )
            MiniMetric(
                label = stringResource(R.string.daily_pnl_kpi_best_day),
                value = analytics.bestDay?.let { fmtSignedMoney(it.dailyPnl) } ?: "—",
                valueColor = positiveColor,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.daily_pnl_kpi_worst_day),
                value = analytics.worstDay?.let { fmtSignedMoney(it.dailyPnl) } ?: "—",
                valueColor = negativeColor,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = stringResource(R.string.daily_pnl_kpi_green_days),
                value = "${analytics.greenDays}/${snapshots.size}",
                valueColor = if (analytics.greenDayRatePct >= 50.0) positiveColor else mutedColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 9.5.sp)
        )
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
    return path
}

private fun formatCompactSignedDollar(value: Double): String {
    val absV = abs(value)
    val sign = if (value > 0.005) "+" else if (value < -0.005) "−" else ""
    return when {
        absV >= 100_000.0 -> "${sign}$${String.format(Locale.US, "%.0fk", absV / 1000.0)}"
        absV >= 1_000.0 -> "${sign}$${String.format(Locale.US, "%.1fk", absV / 1000.0)}"
        absV >= 100.0 -> "${sign}$${String.format(Locale.US, "%.0f", absV)}"
        else -> "${sign}$${String.format(Locale.US, "%.1f", absV)}"
    }
}
