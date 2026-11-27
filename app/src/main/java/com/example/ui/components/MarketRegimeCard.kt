package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.RegimePoint
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class RegimeVizMode(val label: String) {
    RADIAL("RADIAL"),
    TIMELINE("TIMELINE")
}

@Composable
fun regimeColor(label: RegimeLabel): Color = when (label) {
    RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
    RegimeLabel.NEUTRAL -> MaterialTheme.colorScheme.primary
    RegimeLabel.RISK_OFF -> MaterialTheme.colorScheme.tertiary
    RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
}

private fun regimeDisplayName(label: RegimeLabel): String = when (label) {
    RegimeLabel.RISK_ON -> "RISK ON"
    RegimeLabel.NEUTRAL -> "NEUTRAL"
    RegimeLabel.RISK_OFF -> "RISK OFF"
    RegimeLabel.CRISIS -> "CRISIS"
}

@Composable
fun MarketRegimeCard(
    regime: MarketRegime,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(RegimeVizMode.RADIAL) }
    var inspectedIdx by remember(regime.timeline) { mutableStateOf<Int?>(null) }

    val activeColor by animateColorAsState(
        targetValue = regimeColor(regime.label),
        label = "regime_active_color"
    )

    val timeline = remember(regime) {
        regime.timeline.ifEmpty {
            listOf(
                RegimePoint(
                    dayOffset = 0,
                    label = regime.label,
                    breadth = regime.breadth,
                    trend = regime.trendScore,
                    volPct = regime.volPct
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MarketScanTheme.extraColors.heroGradStart,
                        MarketScanTheme.extraColors.heroGradEnd
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("market_regime_card")
    ) {
        // Header + Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
                Column {
                    SectionLabel(text = "MARKET REGIME")
                    Text(
                        text = "${regimeDisplayName(regime.label)} · ${regime.note.ifBlank { "scanning" }}",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Segmented Toggle: RADIAL / TIMELINE
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                RegimeVizMode.entries.forEach { item ->
                    val selected = mode == item
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable {
                                mode = item
                                inspectedIdx = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("regime_mode_${item.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.label,
                            color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "regime_viz_transition"
        ) { currentMode ->
            when (currentMode) {
                RegimeVizMode.RADIAL -> {
                    RadialRegimeSection(
                        regime = regime,
                        timeline = timeline,
                        activeColor = activeColor,
                        onOpenTimeline = { mode = RegimeVizMode.TIMELINE }
                    )
                }
                RegimeVizMode.TIMELINE -> {
                    TimelineRegimeSection(
                        regime = regime,
                        timeline = timeline,
                        inspectedIdx = inspectedIdx,
                        onInspectIndex = { inspectedIdx = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun RadialRegimeSection(
    regime: MarketRegime,
    timeline: List<RegimePoint>,
    activeColor: Color,
    onOpenTimeline: () -> Unit
) {
    val targetNeedleFraction = remember(regime) {
        val b = regime.breadth.coerceIn(0.0, 1.0)
        val v = regime.volPct.coerceIn(0.0, 1.0)
        val tNorm = ((regime.trendScore + 1.0) / 2.0).coerceIn(0.0, 1.0)
        when (regime.label) {
            RegimeLabel.CRISIS -> (0.04 + (1.0 - b) * 0.10 + v * 0.07).toFloat().coerceIn(0.03f, 0.22f)
            RegimeLabel.RISK_OFF -> (0.28 + b * 0.18).toFloat().coerceIn(0.27f, 0.47f)
            RegimeLabel.NEUTRAL -> (0.53 + (b * 0.6 + tNorm * 0.4) * 0.18).toFloat().coerceIn(0.52f, 0.72f)
            RegimeLabel.RISK_ON -> (0.78 + (b * 0.7 + (1.0 - v) * 0.3) * 0.18).toFloat().coerceIn(0.77f, 0.97f)
        }
    }

    val animatedNeedle by animateFloatAsState(
        targetValue = targetNeedleFraction,
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.78f),
        label = "regime_needle_anim"
    )

    val crisisColor = regimeColor(RegimeLabel.CRISIS)
    val riskOffColor = regimeColor(RegimeLabel.RISK_OFF)
    val neutralColor = regimeColor(RegimeLabel.NEUTRAL)
    val riskOnColor = regimeColor(RegimeLabel.RISK_ON)
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radial Arc Gauge
            Box(
                modifier = Modifier
                    .size(154.dp)
                    .semantics {
                        contentDescription =
                            "Market regime radial gauge: ${regimeDisplayName(regime.label)}, breadth ${(regime.breadth * 100).roundToInt()} percent"
                    }
                    .testTag("regime_radial_chart"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 14.dp.toPx()
                    val inset = strokePx / 2f + 8.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)
                    val startBase = 150f
                    val totalSweep = 240f
                    val gap = 5f
                    val segSweep = (totalSweep - gap * 3f) / 4f

                    val segments = listOf(
                        RegimeLabel.CRISIS to crisisColor,
                        RegimeLabel.RISK_OFF to riskOffColor,
                        RegimeLabel.NEUTRAL to neutralColor,
                        RegimeLabel.RISK_ON to riskOnColor
                    )

                    segments.forEachIndexed { idx, (lbl, col) ->
                        val isCurrent = lbl == regime.label
                        val segStart = startBase + idx * (segSweep + gap)
                        drawArc(
                            color = trackBg,
                            startAngle = segStart,
                            sweepAngle = segSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = col.copy(alpha = if (isCurrent) 1.0f else 0.28f),
                            startAngle = segStart,
                            sweepAngle = segSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = if (isCurrent) strokePx else strokePx * 0.78f,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    // Needle indicator & glow dot on arc
                    val angleDeg = startBase + animatedNeedle * totalSweep
                    val angleRad = (angleDeg * PI / 180.0)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = arcSize.width / 2f
                    val dotX = center.x + (radius * cos(angleRad)).toFloat()
                    val dotY = center.y + (radius * sin(angleRad)).toFloat()

                    val innerR = radius - strokePx * 0.9f
                    val innerX = center.x + (innerR * cos(angleRad)).toFloat()
                    val innerY = center.y + (innerR * sin(angleRad)).toFloat()

                    drawLine(
                        color = activeColor.copy(alpha = 0.75f),
                        start = Offset(innerX, innerY),
                        end = Offset(dotX, dotY),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.28f),
                        radius = 10.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                    drawCircle(
                        color = textPrimary,
                        radius = 4.5.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(regime.breadth * 100).roundToInt()}%",
                        color = textPrimary,
                        style = MonoTextStyle.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        text = "BREADTH",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TagChip(
                        text = regimeDisplayName(regime.label),
                        fg = activeColor,
                        bg = activeColor.copy(alpha = 0.16f)
                    )
                }
            }

            // Right Metrics Grid
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val trendText = when {
                    regime.trendScore >= 0.75 -> "+${fmtNum(regime.trendScore, 1)} Bull"
                    regime.trendScore <= -0.75 -> "${fmtNum(regime.trendScore, 1)} Bear"
                    else -> "${fmtNum(regime.trendScore, 1)} Mixed"
                }
                val trendColor = when {
                    regime.trendScore >= 0.75 -> riskOnColor
                    regime.trendScore <= -0.75 -> crisisColor
                    else -> neutralColor
                }
                val volColor = when {
                    regime.volPct >= 0.85 -> crisisColor
                    regime.volPct >= 0.65 -> riskOffColor
                    else -> riskOnColor
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        label = "SPY TREND",
                        value = trendText,
                        valueColor = trendColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "VOL PCT",
                        value = "${(regime.volPct * 100).roundToInt()}p",
                        valueColor = volColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        label = "LONG SIZE",
                        value = "${fmtNum(regime.longMult, 2)}x",
                        valueColor = if (regime.longMult >= 1.0) riskOnColor else riskOffColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "SHORT SIZE",
                        value = "${fmtNum(regime.shortMult, 2)}x",
                        valueColor = if (regime.shortMult > 1.0) crisisColor else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4-State Regime Spectrum Indicator (CRISIS / RISK_OFF / NEUTRAL / RISK_ON)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                RegimeLabel.CRISIS,
                RegimeLabel.RISK_OFF,
                RegimeLabel.NEUTRAL,
                RegimeLabel.RISK_ON
            ).forEach { lbl ->
                val isActive = lbl == regime.label
                val col = regimeColor(lbl)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) col.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                        )
                        .border(
                            1.dp,
                            if (isActive) col.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(col.copy(alpha = if (isActive) 1f else 0.45f))
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = regimeDisplayName(lbl),
                        color = if (isActive) col else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Miniature Color-Coded Timeline Tape Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenTimeline)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("regime_mini_timeline_strip"),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${timeline.size}-DAY REGIME TIMELINE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Tap for chart →",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                timeline.forEach { pt ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(regimeColor(pt.label))
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRegimeSection(
    regime: MarketRegime,
    timeline: List<RegimePoint>,
    inspectedIdx: Int?,
    onInspectIndex: (Int?) -> Unit
) {
    val activeIdx = (inspectedIdx ?: (timeline.size - 1)).coerceIn(0, (timeline.size - 1).coerceAtLeast(0))
    val activePt = timeline[activeIdx]
    val activeColor = regimeColor(activePt.label)

    val riskOnCol = regimeColor(RegimeLabel.RISK_ON)
    val neutralCol = regimeColor(RegimeLabel.NEUTRAL)
    val riskOffCol = regimeColor(RegimeLabel.RISK_OFF)
    val crisisCol = regimeColor(RegimeLabel.CRISIS)
    val mutedCol = MaterialTheme.colorScheme.onSurfaceVariant
    val lineOverlayCol = MaterialTheme.colorScheme.onBackground

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active / Scrubbed Session Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dayLabel = if (activePt.dayOffset == 0) "Today" else "T${activePt.dayOffset}d"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(
                    text = regimeDisplayName(activePt.label),
                    fg = activeColor,
                    bg = activeColor.copy(alpha = 0.16f)
                )
                Text(
                    text = "$dayLabel · Breadth ${(activePt.breadth * 100).roundToInt()}% · Vol ${(activePt.volPct * 100).roundToInt()}p",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                )
            }
            if (inspectedIdx != null) {
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onInspectIndex(null) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Color-Coded Bar + Breadth Overlay Timeline Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .pointerInput(timeline.size) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && timeline.isNotEmpty()) {
                            val idx = ((offset.x / size.width) * timeline.size)
                                .toInt()
                                .coerceIn(0, timeline.size - 1)
                            onInspectIndex(idx)
                        }
                    }
                }
                .pointerInput(timeline.size) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0 && timeline.isNotEmpty()) {
                            val idx = ((change.position.x / size.width) * timeline.size)
                                .toInt()
                                .coerceIn(0, timeline.size - 1)
                            onInspectIndex(idx)
                        }
                    }
                }
                .semantics {
                    contentDescription =
                        "Color-coded market regime timeline for the last ${timeline.size} trading sessions. Current state is ${regimeDisplayName(regime.label)}."
                }
                .testTag("regime_timeline_chart")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = 10.dp.toPx()
                val padY = 12.dp.toPx()
                val chartW = (size.width - padX * 2f).coerceAtLeast(1f)
                val chartH = (size.height - padY * 2f).coerceAtLeast(1f)

                fun yForBreadth(b: Double): Float {
                    val clamped = b.coerceIn(0.0, 1.0).toFloat()
                    return padY + (1f - clamped) * chartH
                }

                // Dashed Threshold Lines: 55% Risk-On, 40% Risk-Off
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                val y55 = yForBreadth(0.55)
                val y40 = yForBreadth(0.40)
                drawLine(
                    color = riskOnCol.copy(alpha = 0.35f),
                    start = Offset(padX, y55),
                    end = Offset(size.width - padX, y55),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
                drawLine(
                    color = riskOffCol.copy(alpha = 0.35f),
                    start = Offset(padX, y40),
                    end = Offset(size.width - padX, y40),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )

                val n = timeline.size.coerceAtLeast(1)
                val slotW = chartW / n
                val barW = (slotW * 0.72f).coerceAtLeast(2f)
                val barGapOffset = (slotW - barW) / 2f

                val breadthPath = Path()
                timeline.forEachIndexed { idx, pt ->
                    val col = when (pt.label) {
                        RegimeLabel.RISK_ON -> riskOnCol
                        RegimeLabel.NEUTRAL -> neutralCol
                        RegimeLabel.RISK_OFF -> riskOffCol
                        RegimeLabel.CRISIS -> crisisCol
                    }
                    val xLeft = padX + idx * slotW + barGapOffset
                    val barTop = yForBreadth(pt.breadth.coerceAtLeast(0.08))
                    val barH = (padY + chartH - barTop).coerceAtLeast(4.dp.toPx())
                    val isSelected = idx == activeIdx

                    drawRoundRect(
                        color = col.copy(alpha = if (isSelected) 0.95f else 0.58f),
                        topLeft = Offset(xLeft, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )

                    val cx = xLeft + barW / 2f
                    if (idx == 0) {
                        breadthPath.moveTo(cx, barTop)
                    } else {
                        breadthPath.lineTo(cx, barTop)
                    }
                }

                if (timeline.size >= 2) {
                    drawPath(
                        path = breadthPath,
                        color = lineOverlayCol.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Active / Scrubbed Marker
                val selX = padX + activeIdx * slotW + slotW / 2f
                val selY = yForBreadth(activePt.breadth.coerceAtLeast(0.08))
                drawLine(
                    color = mutedCol.copy(alpha = 0.45f),
                    start = Offset(selX, padY * 0.4f),
                    end = Offset(selX, size.height - padY * 0.4f),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = activeColor.copy(alpha = 0.35f),
                    radius = 6.dp.toPx(),
                    center = Offset(selX, selY)
                )
                drawCircle(
                    color = lineOverlayCol,
                    radius = 3.dp.toPx(),
                    center = Offset(selX, selY)
                )
            }
        }

        // Color-Coded Legend with Window Session Counts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                RegimeLabel.RISK_ON to "Risk On",
                RegimeLabel.NEUTRAL to "Neutral",
                RegimeLabel.RISK_OFF to "Risk Off",
                RegimeLabel.CRISIS to "Crisis"
            ).forEach { (lbl, name) ->
                val count = timeline.count { it.label == lbl }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(regimeColor(lbl))
                    )
                    Text(
                        text = "$name ($count)",
                        color = if (lbl == regime.label) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (lbl == regime.label) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
