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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.PaperBotEngine
import com.example.domain.model.BotState
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PerformanceChartMetric(val label: String) {
    EQUITY("$"),
    RETURN_PCT("%")
}

@Composable
fun ThirtyDayEquityCurveCard(
    botState: BotState,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val baseEquity = if (botState.initialEquity > 0.0) botState.initialEquity else 10.0
    val summary = remember(
        botState.equityHistory,
        botState.cash,
        botState.positions,
        botState.log,
        baseEquity
    ) {
        PaperBotEngine.buildDailyThirtyDayPerformance(botState)
    }
    val points = summary.points
    var metricMode by remember { mutableStateOf(PerformanceChartMetric.EQUITY) }
    var inspectedIdx by remember(points.size) { mutableStateOf<Int?>(null) }

    val activeIdx = (inspectedIdx ?: (points.size - 1)).coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val activePoint = points[activeIdx]
    val shortDateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
    val borderColor = MaterialTheme.colorScheme.outline
    val dotHighlightColor = MaterialTheme.colorScheme.onBackground

    val values = remember(points, metricMode) {
        points.map { pt ->
            if (metricMode == PerformanceChartMetric.EQUITY) pt.equity else pt.returnPct
        }
    }
    val baselineValue = if (metricMode == PerformanceChartMetric.EQUITY) baseEquity else 0.0
    val rawMin = minOf(
        values.minOrNull() ?: baselineValue,
        if (metricMode == PerformanceChartMetric.EQUITY) summary.low else baselineValue,
        baselineValue
    )
    val rawMax = maxOf(
        values.maxOrNull() ?: baselineValue,
        if (metricMode == PerformanceChartMetric.EQUITY) summary.high else baselineValue,
        baselineValue
    )
    val minSpan = if (metricMode == PerformanceChartMetric.EQUITY) {
        (baseEquity * 0.04).coerceAtLeast(0.20)
    } else {
        4.0
    }
    val rawRange = (rawMax - rawMin).takeIf { it >= minSpan * 0.25 } ?: minSpan
    val axisLo = rawMin - rawRange * 0.18
    val axisHi = rawMax + rawRange * 0.18
    val axisMid = (axisLo + axisHi) / 2.0

    fun formatAxisVal(v: Double): String = if (metricMode == PerformanceChartMetric.EQUITY) {
        fmtMoney(v)
    } else {
        fmtPctSigned(v)
    }

    val activeDelta = activePoint.equity - baseEquity
    val activeDeltaColor = if (activeDelta >= 0.0) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    TerminalCard(
        modifier = modifier.testTag("trade_equity_curve_card")
    ) {
        // Header Row + Equity($) / Return(%) Mode Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(text = "30-DAY TOTAL EQUITY CURVE")
                Spacer(modifier = Modifier.height(2.dp))
                val dayText = when {
                    inspectedIdx == null -> "Live · ${summary.activeDaysCount}/30d active"
                    activeIdx == points.lastIndex -> "Today"
                    else -> shortDateFormatter.format(Date(activePoint.timestampMs))
                }
                Text(
                    text = "$dayText · ${fmtMoney(activePoint.equity)} (${fmtPctSigned(activePoint.returnPct)})",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PerformanceChartMetric.entries.forEach { mode ->
                    val selected = metricMode == mode
                    val tag = if (mode == PerformanceChartMetric.EQUITY) {
                        "equity_chart_mode_dollar"
                    } else {
                        "equity_chart_mode_pct"
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable { metricMode = mode }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(tag),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) lineColor else mutedColor,
                            style = MonoTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tooltip / Delta Readout Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TagChip(
                    text = "${fmtSignedMoney(activeDelta)} (${fmtPctSigned(activePoint.returnPct)})",
                    fg = activeDeltaColor,
                    bg = if (activeDelta >= 0.0) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
                if (!activePoint.hasActivity) {
                    Text(
                        text = "pre-start baseline",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                    )
                }
            }
            if (inspectedIdx != null) {
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { inspectedIdx = null }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = "Tap or drag to inspect",
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cartesian Line Chart with Left Y-Axis + Grid + Canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatAxisVal(axisHi),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                Text(
                    text = formatAxisVal(axisMid),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                Text(
                    text = formatAxisVal(axisLo),
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .pointerInput(points.size) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && points.isNotEmpty()) {
                                val idx = ((offset.x / size.width) * points.size)
                                    .toInt()
                                    .coerceIn(0, points.size - 1)
                                inspectedIdx = if (inspectedIdx == idx) null else idx
                            }
                        }
                    }
                    .pointerInput(points.size) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0 && points.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * points.size)
                                    .toInt()
                                    .coerceIn(0, points.size - 1)
                                inspectedIdx = idx
                            }
                        }
                    }
                    .semantics {
                        contentDescription =
                            "30-day paper portfolio performance line chart. Current equity ${fmtMoney(points.last().equity)}, return ${fmtPctSigned(summary.periodReturnPct)}."
                    }
                    .testTag("trade_equity_curve_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padX = 10.dp.toPx()
                    val padY = 12.dp.toPx()
                    val span = (axisHi - axisLo).takeIf { it > 0.0001 } ?: 1.0

                    fun px(i: Int): Float = if (points.size <= 1) w - padX
                    else padX + (i.toFloat() / (points.size - 1)) * (w - padX * 2f)

                    fun py(v: Double): Float =
                        h - padY - (((v - axisLo) / span).toFloat() * (h - padY * 2f))

                    // Horizontal Cartesian grid lines (top, mid, bottom)
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    listOf(padY, h / 2f, h - padY).forEach { gy ->
                        drawLine(
                            color = borderColor.copy(alpha = 0.65f),
                            start = Offset(padX, gy),
                            end = Offset(w - padX, gy),
                            strokeWidth = 0.8.dp.toPx(),
                            pathEffect = gridDash
                        )
                    }

                    // Vertical grid markers (D-20, D-10)
                    listOf(9, 19).forEach { idx ->
                        if (idx < points.size) {
                            val gx = px(idx)
                            drawLine(
                                color = borderColor.copy(alpha = 0.4f),
                                start = Offset(gx, padY),
                                end = Offset(gx, h - padY),
                                strokeWidth = 0.8.dp.toPx(),
                                pathEffect = gridDash
                            )
                        }
                    }

                    // Baseline Reference Line (initial equity / 0%)
                    val baseY = py(baselineValue)
                    drawLine(
                        color = mutedColor.copy(alpha = 0.48f),
                        start = Offset(padX, baseY),
                        end = Offset(w - padX, baseY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    val firstActiveIdx = points.indexOfFirst { it.hasActivity }.let {
                        if (it >= 0) it else 0
                    }

                    // Pre-activity baseline dashed segment (days prior to first simulation tick)
                    if (firstActiveIdx > 0) {
                        val prePath = Path().apply {
                            moveTo(px(0), py(values[0]))
                            for (i in 1..firstActiveIdx) {
                                lineTo(px(i), py(values[i]))
                            }
                        }
                        drawPath(
                            path = prePath,
                            color = mutedColor.copy(alpha = 0.55f),
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        )
                    }

                    // Active portfolio curve + area gradient fill
                    if (firstActiveIdx < points.lastIndex) {
                        val activePath = Path().apply {
                            moveTo(px(firstActiveIdx), py(values[firstActiveIdx]))
                            for (i in (firstActiveIdx + 1) until points.size) {
                                lineTo(px(i), py(values[i]))
                            }
                        }
                        val fillPath = Path().apply {
                            addPath(activePath)
                            lineTo(px(points.lastIndex), h - padY)
                            lineTo(px(firstActiveIdx), h - padY)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.26f), Color.Transparent),
                                startY = padY,
                                endY = h - padY
                            )
                        )
                        drawPath(
                            path = activePath,
                            color = lineColor,
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Small vertex dots on active days
                    for (i in firstActiveIdx until points.size) {
                        drawCircle(
                            color = lineColor,
                            radius = 2.2.dp.toPx(),
                            center = Offset(px(i), py(values[i]))
                        )
                    }

                    // Vertical crosshair + highlighted point ring for active/inspected day
                    val selX = px(activeIdx)
                    val selY = py(values[activeIdx])
                    if (inspectedIdx != null) {
                        drawLine(
                            color = mutedColor.copy(alpha = 0.6f),
                            start = Offset(selX, padY * 0.5f),
                            end = Offset(selX, h - padY * 0.5f),
                            strokeWidth = 1.1.dp.toPx()
                        )
                    }
                    drawCircle(
                        color = lineColor.copy(alpha = 0.34f),
                        radius = 7.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    drawCircle(
                        color = dotHighlightColor,
                        radius = 3.2.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-Axis 30-Day Calendar Date Labels aligned under chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tickIndices = listOf(0, 9, 19, points.lastIndex).distinct()
            tickIndices.forEach { idx ->
                val lbl = if (idx == points.lastIndex) {
                    "Today"
                } else {
                    shortDateFormatter.format(Date(points[idx].timestampMs))
                }
                Text(
                    text = lbl,
                    color = mutedColor,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary Performance Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetric(
                label = "30D RETURN",
                value = fmtPctSigned(summary.periodReturnPct),
                valueColor = if (summary.periodReturnPct >= 0.0) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "30D HIGH",
                value = fmtMoney(summary.high),
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "30D LOW",
                value = fmtMoney(summary.low),
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "MAX DD",
                value = "-${fmtNum(summary.maxDrawdownPct, 2)}%",
                valueColor = if (summary.maxDrawdownPct > 2.0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
