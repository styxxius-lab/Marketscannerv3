package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
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
import com.example.domain.engine.EquityCurveAnalyticsEngine
import com.example.domain.engine.EquityCurveChartMode
import com.example.domain.engine.EquityCurveSample
import com.example.domain.engine.EquityTimeframe
import com.example.domain.model.BotState
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Interactive Paper Trading Account Equity Curve Dashboard built on a native Jetpack Compose
 * Canvas chart engine with monotone cubic spline rendering, gradient area fill, high-water
 * mark envelope, underwater drawdown visualization, trade fill markers, touch-scrubbing
 * crosshair inspection, timeframe switching (1D / 7D / 30D / ALL), and performance KPIs.
 */
@Composable
fun PaperEquityCurveDashboard(
    botState: BotState,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "equity_dashboard"
) {
    var timeframe by remember { mutableStateOf(EquityTimeframe.ALL) }
    var chartMode by remember { mutableStateOf(EquityCurveChartMode.EQUITY) }
    var showHighWaterMark by remember { mutableStateOf(true) }
    var showTradeMarkers by remember { mutableStateOf(true) }
    var inspectedIdx by remember(timeframe, chartMode) { mutableStateOf<Int?>(null) }

    val analytics = remember(
        botState.equityHistory,
        botState.cash,
        botState.positions,
        botState.log,
        botState.initialEquity,
        timeframe
    ) {
        EquityCurveAnalyticsEngine.buildAnalytics(botState, timeframe)
    }

    val samples = analytics.samples
    val activeIdx = (inspectedIdx ?: samples.lastIndex).coerceIn(0, (samples.lastIndex).coerceAtLeast(0))
    val activeSample = samples[activeIdx]

    val timeOnlyFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateShortFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val tooltipFormatter = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    val positiveColor = MaterialTheme.colorScheme.secondary
    val negativeColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val hwmColor = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val canvasBg = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    val highlightRingCenter = MaterialTheme.colorScheme.onBackground

    val curveColor = when (chartMode) {
        EquityCurveChartMode.DRAWDOWN -> negativeColor
        else -> if (analytics.periodReturnDollar >= 0.0) positiveColor else negativeColor
    }

    val seriesValues = remember(samples, chartMode) {
        samples.map { s -> sampleValueForMode(s, chartMode) }
    }
    val hwmValues = remember(samples, chartMode, analytics.startEquity) {
        samples.map { s ->
            when (chartMode) {
                EquityCurveChartMode.EQUITY -> s.highWaterMark
                EquityCurveChartMode.RETURN_PCT ->
                    if (analytics.startEquity > 0.0) ((s.highWaterMark - analytics.startEquity) / analytics.startEquity) * 100.0
                    else 0.0
                EquityCurveChartMode.DRAWDOWN -> 0.0
            }
        }
    }

    val baselineValue = when (chartMode) {
        EquityCurveChartMode.EQUITY -> analytics.startEquity
        EquityCurveChartMode.RETURN_PCT -> 0.0
        EquityCurveChartMode.DRAWDOWN -> 0.0
    }

    val (axisLo, axisHi) = remember(seriesValues, hwmValues, showHighWaterMark, baselineValue, chartMode) {
        val candidates = ArrayList<Double>(seriesValues.size * 2 + 1)
        candidates.addAll(seriesValues)
        candidates.add(baselineValue)
        if (showHighWaterMark && chartMode != EquityCurveChartMode.DRAWDOWN) {
            candidates.addAll(hwmValues)
        }
        val rawMin = candidates.minOrNull() ?: baselineValue
        val rawMax = candidates.maxOrNull() ?: baselineValue
        if (chartMode == EquityCurveChartMode.DRAWDOWN) {
            val floor = minOf(rawMin, -1.0)
            val pad = abs(floor) * 0.18
            (floor - pad) to 0.0
        } else {
            val minSpan = if (chartMode == EquityCurveChartMode.EQUITY) {
                (analytics.baselineEquity * 0.03).coerceAtLeast(0.20)
            } else {
                3.0
            }
            val span = (rawMax - rawMin).takeIf { it >= minSpan * 0.25 } ?: minSpan
            (rawMin - span * 0.16) to (rawMax + span * 0.16)
        }
    }
    val axisTick1 = axisHi - (axisHi - axisLo) * 0.333
    val axisTick2 = axisHi - (axisHi - axisLo) * 0.667

    fun formatAxis(v: Double): String = when (chartMode) {
        EquityCurveChartMode.EQUITY -> fmtMoney(v)
        EquityCurveChartMode.RETURN_PCT -> fmtPctSigned(v)
        EquityCurveChartMode.DRAWDOWN -> "${fmtNum(v, 1)}%"
    }

    val activeDeltaDollar = activeSample.equity - analytics.startEquity
    val activeDeltaColor = if (activeDeltaDollar >= 0.0) positiveColor else negativeColor

    TerminalCard(
        modifier = modifier.testTag("${testTagPrefix}_card")
    ) {
        // 1. Header Row: Title + Live Status Pill
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
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.equity_dashboard_title))
            }
            TagChip(
                text = if (botState.running) {
                    stringResource(R.string.equity_dashboard_status_running)
                } else {
                    stringResource(R.string.equity_dashboard_status_tracking)
                },
                fg = if (botState.running) positiveColor else primaryColor,
                bg = if (botState.running) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Interactive Scrubbed Value Readout Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (chartMode) {
                        EquityCurveChartMode.EQUITY -> fmtMoney(activeSample.equity)
                        EquityCurveChartMode.RETURN_PCT -> fmtPctSigned(activeSample.returnPct)
                        EquityCurveChartMode.DRAWDOWN -> "${fmtNum(activeSample.drawdownPct, 2)}%"
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.testTag("${testTagPrefix}_headline_value")
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagChip(
                        text = "${fmtSignedMoney(activeDeltaDollar)} (${fmtPctSigned(activeSample.returnPct)})",
                        fg = activeDeltaColor,
                        bg = if (activeDeltaDollar >= 0.0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                    Text(
                        text = if (inspectedIdx == null) {
                            stringResource(R.string.equity_dashboard_live_now)
                        } else {
                            tooltipFormatter.format(Date(activeSample.timestampMs))
                        },
                        color = mutedColor,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (inspectedIdx != null) {
                Text(
                    text = stringResource(R.string.equity_dashboard_reset_inspect),
                    color = primaryColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { inspectedIdx = null }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("${testTagPrefix}_reset_inspect")
                )
            } else {
                Text(
                    text = stringResource(R.string.equity_dashboard_scrub_hint),
                    color = mutedColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                )
            }
        }

        // Trade execution annotation strip when inspecting a point with a trade fill
        val inspectedTrade = activeSample.tradeMarker
        if (inspectedTrade != null && showTradeMarkers) {
            Spacer(modifier = Modifier.height(8.dp))
            val pnlSuffix = inspectedTrade.pnl?.let { " · ${fmtSignedMoney(it)}" }.orEmpty()
            val markerColor = when {
                inspectedTrade.type == "OPEN" -> primaryColor
                (inspectedTrade.pnl ?: 0.0) >= 0.0 -> positiveColor
                else -> negativeColor
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(1.dp, markerColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(markerColor)
                )
                Text(
                    text = "${inspectedTrade.type} ${inspectedTrade.symbol} @ ${fmtMoney(inspectedTrade.price)}$pnlSuffix",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Timeframe Selector (1D / 7D / 30D / ALL) + Chart Mode Segmented Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EquityTimeframe.entries.forEach { tf ->
                    val selected = timeframe == tf
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { timeframe = tf }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("${testTagPrefix}_tf_${tf.label.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tf.label,
                            color = if (selected) primaryColor else mutedColor,
                            style = MonoTextStyle.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Overlay toggles (High-Water Mark & Trade Markers)
            FilterChip(
                selected = showHighWaterMark && chartMode != EquityCurveChartMode.DRAWDOWN,
                onClick = { showHighWaterMark = !showHighWaterMark },
                enabled = chartMode != EquityCurveChartMode.DRAWDOWN,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.equity_dashboard_overlay_hwm),
                        style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = hwmColor,
                    selectedLeadingIconColor = hwmColor
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_toggle_hwm")
            )
        }

        // Chart Mode Selector (EQUITY $ | RETURN % | DRAWDOWN) + Trade Fill Markers Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EquityCurveChartMode.entries.forEach { mode ->
                val selected = chartMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { chartMode = mode },
                    label = {
                        Text(
                            text = mode.label,
                            style = MonoTextStyle.copy(
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (mode == EquityCurveChartMode.DRAWDOWN) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        selectedLabelColor = if (mode == EquityCurveChartMode.DRAWDOWN) {
                            negativeColor
                        } else {
                            primaryColor
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = mutedColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = borderColor,
                        selectedBorderColor = if (mode == EquityCurveChartMode.DRAWDOWN) {
                            negativeColor.copy(alpha = 0.6f)
                        } else {
                            primaryColor.copy(alpha = 0.6f)
                        }
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_mode_${mode.name.lowercase()}")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (analytics.tradeMarkersCount > 0) {
                FilterChip(
                    selected = showTradeMarkers,
                    onClick = { showTradeMarkers = !showTradeMarkers },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.equity_dashboard_overlay_fills),
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_toggle_fills")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Custom Compose Canvas Interactive Equity Curve (Monotone Spline + Area + Crosshair)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(192.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Labels
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf(axisHi, axisTick1, axisTick2, axisLo).forEach { tick ->
                    Text(
                        text = formatAxis(tick),
                        color = mutedColor,
                        style = MonoTextStyle.copy(fontSize = 9.sp),
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Main Interactive Canvas Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(canvasBg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .pointerInput(samples.size) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && samples.isNotEmpty()) {
                                val idx = ((offset.x / size.width) * samples.size)
                                    .toInt()
                                    .coerceIn(0, samples.lastIndex)
                                inspectedIdx = if (inspectedIdx == idx) null else idx
                            }
                        }
                    }
                    .pointerInput(samples.size) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0 && samples.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * samples.size)
                                    .toInt()
                                    .coerceIn(0, samples.lastIndex)
                                inspectedIdx = idx
                            }
                        }
                    }
                    .semantics {
                        contentDescription =
                            "Paper trading equity curve chart. Current equity ${fmtMoney(analytics.currentEquity)}, period return ${fmtPctSigned(analytics.periodReturnPct)}, max drawdown ${fmtNum(analytics.maxDrawdownPct, 2)} percent."
                    }
                    .testTag("${testTagPrefix}_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padX = 12.dp.toPx()
                    val padY = 14.dp.toPx()
                    val usableW = (w - padX * 2f).coerceAtLeast(1f)
                    val usableH = (h - padY * 2f).coerceAtLeast(1f)
                    val span = (axisHi - axisLo).takeIf { it > 1e-6 } ?: 1.0

                    fun px(index: Int): Float = if (samples.size <= 1) {
                        w - padX
                    } else {
                        padX + (index.toFloat() / samples.lastIndex.toFloat()) * usableW
                    }

                    fun py(value: Double): Float =
                        (h - padY - (((value - axisLo) / span).toFloat() * usableH))
                            .coerceIn(padY * 0.6f, h - padY * 0.6f)

                    // Cartesian horizontal dashed gridlines (4 levels)
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    for (step in 0..3) {
                        val gy = padY + (step / 3f) * usableH
                        drawLine(
                            color = borderColor.copy(alpha = 0.55f),
                            start = Offset(padX, gy),
                            end = Offset(w - padX, gy),
                            strokeWidth = 0.8.dp.toPx(),
                            pathEffect = gridDash
                        )
                    }

                    // Vertical gridlines (quarter marks)
                    listOf(0.25f, 0.50f, 0.75f).forEach { frac ->
                        val gx = padX + frac * usableW
                        drawLine(
                            color = borderColor.copy(alpha = 0.35f),
                            start = Offset(gx, padY),
                            end = Offset(gx, h - padY),
                            strokeWidth = 0.8.dp.toPx(),
                            pathEffect = gridDash
                        )
                    }

                    // Reference Baseline (Start Equity or 0%)
                    val baseY = py(baselineValue)
                    drawLine(
                        color = mutedColor.copy(alpha = 0.52f),
                        start = Offset(padX, baseY),
                        end = Offset(w - padX, baseY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    // Optional High-Water Mark dashed envelope
                    if (showHighWaterMark && chartMode != EquityCurveChartMode.DRAWDOWN && samples.size >= 2) {
                        val hwmPath = Path().apply {
                            moveTo(px(0), py(hwmValues[0]))
                            for (i in 1 until samples.size) {
                                // Stepped-horizontal high-water mark visual
                                lineTo(px(i), py(hwmValues[i - 1]))
                                lineTo(px(i), py(hwmValues[i]))
                            }
                        }
                        drawPath(
                            path = hwmPath,
                            color = hwmColor.copy(alpha = 0.60f),
                            style = Stroke(
                                width = 1.3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                            )
                        )
                    }

                    // Main Smooth Cubic Spline Curve + Gradient Area Fill
                    if (samples.size >= 2) {
                        val pts = samples.indices.map { i -> Offset(px(i), py(seriesValues[i])) }
                        val curvePath = buildSmoothCurvePath(pts)
                        val fillAnchorY = if (chartMode == EquityCurveChartMode.DRAWDOWN) {
                            py(0.0)
                        } else {
                            h - padY
                        }
                        val areaPath = Path().apply {
                            addPath(curvePath)
                            lineTo(pts.last().x, fillAnchorY)
                            lineTo(pts.first().x, fillAnchorY)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = if (chartMode == EquityCurveChartMode.DRAWDOWN) {
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, curveColor.copy(alpha = 0.32f)),
                                    startY = padY,
                                    endY = h - padY
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(curveColor.copy(alpha = 0.30f), Color.Transparent),
                                    startY = padY,
                                    endY = h - padY
                                )
                            }
                        )

                        drawPath(
                            path = curvePath,
                            color = curveColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Trade execution markers along the curve
                    if (showTradeMarkers) {
                        samples.forEachIndexed { idx, sample ->
                            val marker = sample.tradeMarker ?: return@forEachIndexed
                            val mx = px(idx)
                            val my = py(seriesValues[idx])
                            val markerCol = when {
                                marker.type == "OPEN" -> primaryColor
                                (marker.pnl ?: 0.0) >= 0.0 -> positiveColor
                                else -> negativeColor
                            }
                            drawCircle(
                                color = canvasBg,
                                radius = 4.6.dp.toPx(),
                                center = Offset(mx, my)
                            )
                            drawCircle(
                                color = markerCol,
                                radius = 3.2.dp.toPx(),
                                center = Offset(mx, my)
                            )
                        }
                    }

                    // Active / Inspected Crosshair + Glowing Indicator Dot
                    val selX = px(activeIdx)
                    val selY = py(seriesValues[activeIdx])
                    if (inspectedIdx != null) {
                        drawLine(
                            color = mutedColor.copy(alpha = 0.7f),
                            start = Offset(selX, padY * 0.4f),
                            end = Offset(selX, h - padY * 0.4f),
                            strokeWidth = 1.1.dp.toPx()
                        )
                        drawLine(
                            color = mutedColor.copy(alpha = 0.35f),
                            start = Offset(padX, selY),
                            end = Offset(w - padX, selY),
                            strokeWidth = 0.9.dp.toPx(),
                            pathEffect = gridDash
                        )
                    }
                    drawCircle(
                        color = curveColor.copy(alpha = 0.28f),
                        radius = 8.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    drawCircle(
                        color = curveColor,
                        radius = 4.2.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    drawCircle(
                        color = highlightRingCenter,
                        radius = 2.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // 5. X-Axis Time Labels aligned under plot area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 62.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tickIndices = buildList {
                if (samples.isNotEmpty()) {
                    add(0)
                    if (samples.size >= 4) add(samples.lastIndex / 3)
                    if (samples.size >= 4) add((samples.lastIndex * 2) / 3)
                    add(samples.lastIndex)
                }
            }.distinct()

            tickIndices.forEach { idx ->
                val label = when {
                    idx == samples.lastIndex -> stringResource(R.string.equity_dashboard_axis_now)
                    analytics.usesIntradayAxis -> timeOnlyFormatter.format(Date(samples[idx].timestampMs))
                    else -> dateShortFormatter.format(Date(samples[idx].timestampMs))
                }
                Text(
                    text = label,
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Institutional Performance KPI Grid (2 rows × 3 tiles)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("${testTagPrefix}_kpi_grid")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_return),
                    value = fmtPctSigned(analytics.periodReturnPct),
                    valueColor = if (analytics.periodReturnPct >= 0.0) positiveColor else negativeColor,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_peak),
                    value = fmtMoney(analytics.peakEquity),
                    valueColor = positiveColor,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_max_dd),
                    value = "-${fmtNum(analytics.maxDrawdownPct, 2)}%",
                    valueColor = if (analytics.maxDrawdownPct >= 2.0) negativeColor else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_win_rate),
                    value = analytics.winRatePct?.let { "${fmtNum(it, 0)}%" } ?: "—",
                    valueColor = when {
                        analytics.winRatePct == null -> mutedColor
                        analytics.winRatePct >= 50.0 -> positiveColor
                        else -> negativeColor
                    },
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_profit_factor),
                    value = analytics.profitFactor?.let { if (it >= 99.0) "∞" else fmtNum(it, 2) } ?: "—",
                    valueColor = when {
                        analytics.profitFactor == null -> mutedColor
                        analytics.profitFactor >= 1.2 -> positiveColor
                        analytics.profitFactor < 1.0 -> negativeColor
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = stringResource(R.string.equity_dashboard_kpi_sharpe),
                    value = analytics.sharpeEstimate?.let { fmtNum(it, 2) } ?: "—",
                    valueColor = when {
                        analytics.sharpeEstimate == null -> mutedColor
                        analytics.sharpeEstimate >= 1.0 -> positiveColor
                        analytics.sharpeEstimate < 0.0 -> negativeColor
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun sampleValueForMode(sample: EquityCurveSample, mode: EquityCurveChartMode): Double =
    when (mode) {
        EquityCurveChartMode.EQUITY -> sample.equity
        EquityCurveChartMode.RETURN_PCT -> sample.returnPct
        EquityCurveChartMode.DRAWDOWN -> sample.drawdownPct
    }

/**
 * Builds a monotone-clamped cubic Bézier path (analogous to Recharts `type="monotone"` /
 * D3 `curveMonotoneX`) so the equity curve renders smoothly without vertical overshoot.
 */
private fun buildSmoothCurvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        path.cubicTo(
            midX, p0.y,
            midX, p1.y,
            p1.x, p1.y
        )
    }
    return path
}
