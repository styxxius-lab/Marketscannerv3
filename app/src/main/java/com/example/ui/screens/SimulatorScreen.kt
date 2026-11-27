package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BotState
import com.example.domain.model.SignalRow
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatLevel
import com.example.engine.ThreatCard
import com.example.engine.ThreatDiagnosticDriver
import com.example.ui.SimulatorUiState
import com.example.ui.components.MiniMetric
import com.example.ui.components.SimulatorThreatScannerCard
import com.example.ui.components.TagChip
import com.example.ui.components.ThreatHistoryCard
import com.example.ui.components.fmtMoney
import com.example.ui.components.fmtNum
import com.example.ui.theme.MonoTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simulator screen dedicated to scanning symbols with [com.example.engine.ThreatEngine]
 * and rendering the [ThreatCard] gauge, decision verdict, and active shock drivers.
 */
@Composable
fun SimulatorScreen(
    uiState: SimulatorUiState,
    botState: BotState,
    rows: List<SignalRow>,
    watchlist: List<String>,
    onSymbolInputChange: (String) -> Unit,
    onScanSymbol: (String) -> Unit,
    onToggleLiveMonitoring: (Boolean) -> Unit,
    onSelectRecentCard: (ThreatCard) -> Unit,
    onExitPosition: (String) -> Unit,
    onTrimPosition: (String, Double) -> Unit,
    onApplySuggestedStop: (String, Double) -> Unit,
    onSelectHistoryFilter: (String?) -> Unit = {},
    onDeleteHistoryEntry: (Long) -> Unit = {},
    onClearThreatHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("simulator_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "simulator_scanner_header") {
            SimulatorThreatScannerCard(
                uiState = uiState,
                botState = botState,
                rows = rows,
                watchlist = watchlist,
                onSymbolInputChange = onSymbolInputChange,
                onScanSymbol = onScanSymbol,
                onToggleLiveMonitoring = onToggleLiveMonitoring,
                onSelectRecentCard = onSelectRecentCard,
                onExitPosition = onExitPosition,
                onTrimPosition = onTrimPosition,
                onApplySuggestedStop = onApplySuggestedStop
            )
        }

        item(key = "simulator_threat_history") {
            ThreatHistoryCard(
                entries = uiState.threatHistory,
                activeSymbolFilter = uiState.historySymbolFilter,
                watchlist = watchlist,
                heldSymbols = botState.positions.map { it.symbol },
                onSelectSymbolFilter = onSelectHistoryFilter,
                onRescanSymbol = onScanSymbol,
                onDeleteEntry = onDeleteHistoryEntry,
                onClearHistory = onClearThreatHistory
            )
        }
    }
}

/**
 * Renders the complete [ThreatCard] output from [com.example.engine.ThreatEngine],
 * displaying `threatScore` in an animated [ThreatScoreGauge] and listing active [ThreatDiagnosticDriver]s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatCardOutputComponent(
    card: ThreatCard,
    currentStop: Double? = null,
    isHolding: Boolean = false,
    onExitPosition: () -> Unit = {},
    onTrimPosition: () -> Unit = {},
    onApplySuggestedStop: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (levelFg, levelBg) = threatLevelColors(card.level)
    val (actionFg, actionBg) = threatActionColors(card.action)

    Column(
        modifier = modifier.fillMaxWidth().testTag("simulator_threat_result_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Symbol, Live Price, ThreatLevel & ThreatAction
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.level >= ThreatLevel.HIGH) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "High threat warning",
                        tint = levelFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = card.symbol,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = fmtMoney(card.price),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("${card.level.label} ${card.threatScore}", levelFg, levelBg, Modifier.testTag("simulator_threat_level_badge"))
                TagChip(card.action.label, actionFg, actionBg, Modifier.testTag("simulator_threat_action_badge"))
            }
        }

        // Radial Threat Score Gauge + Quant/Narrative Breakdown Panel
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 340.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThreatScoreGauge(threatScore = card.threatScore, level = card.level)
                    ThreatBreakdownPanel(card = card)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThreatScoreGauge(threatScore = card.threatScore, level = card.level)
                    ThreatBreakdownPanel(card = card, modifier = Modifier.weight(1f))
                }
            }
        }

        // Catalyst Taxonomy & Peer Contagion Badges
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (card.narrative.categoryKey != "none") {
                val catFg = if (card.narrative.structural) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                val catBg = if (card.narrative.structural) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                TagChip(card.narrative.categoryLabel.uppercase(Locale.US), catFg, catBg)
                TagChip(
                    text = if (card.narrative.structural) "STRUCTURAL · ${card.narrative.persistenceDays}D" else "TRANSIENT · ${card.narrative.persistenceDays}D",
                    fg = MaterialTheme.colorScheme.onSurfaceVariant,
                    bg = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                TagChip("NO ADVERSE CATALYST", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
            }

            TagChip(
                text = when {
                    card.narrative.isLiveClassified -> "GEMINI CLASSIFIED"
                    card.narrative.degraded -> "TAPE-ONLY"
                    else -> "LOCAL RULES"
                },
                fg = MaterialTheme.colorScheme.primary,
                bg = MaterialTheme.colorScheme.primaryContainer
            )

            card.contagion?.let { cont ->
                if (cont.sectorWide) {
                    TagChip(
                        text = "CONTAGION ${cont.peersFalling}/${cont.peerCount} (${"%.1f".format(Locale.US, cont.peerDropAvgPct)}%)",
                        fg = MaterialTheme.colorScheme.tertiary,
                        bg = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
            }
        }

        if (card.narrative.summary.isNotBlank()) {
            Text(
                text = card.narrative.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Active Shock Drivers List
        ThreatDriversList(drivers = card.quant.drivers)

        // Protective Stop, Reasoning & Invalidation Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (card.suggestedStop != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECOMMENDED PROTECTIVE STOP",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = fmtMoney(card.suggestedStop),
                        color = MaterialTheme.colorScheme.error,
                        style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
            card.reasoning.take(3).forEach { line ->
                Text(
                    text = "• $line",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                )
            }
            Text(
                text = "Invalidation: ${card.invalidation}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
            )
        }

        // Open-Position Action Controls
        if (isHolding) {
            val stopCandidate = card.suggestedStop
            val canTighten = stopCandidate != null && (currentStop == null || stopCandidate > currentStop + 0.01)
            val showExit = card.action == ThreatAction.EXIT_NOW || card.action == ThreatAction.SELL
            val showTrim = card.action == ThreatAction.TRIM || card.level >= ThreatLevel.ELEVATED
            if (showExit || showTrim || canTighten) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showExit) {
                        Button(
                            onClick = onExitPosition,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("simulator_threat_exit_button"),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                        ) {
                            Text(card.action.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold))
                        }
                    }
                    if (showTrim) {
                        OutlinedButton(
                            onClick = onTrimPosition,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("simulator_threat_trim_button"),
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("TRIM 50%", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold))
                        }
                    }
                    if (canTighten && stopCandidate != null) {
                        OutlinedButton(
                            onClick = { onApplySuggestedStop(stopCandidate) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("simulator_threat_stop_button"),
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("STOP ${fmtMoney(stopCandidate)}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Radial 240-degree Threat Score Gauge rendering `threatScore` (0..100) with
 * risk-zone threshold bands, animated sweep arc, and perimeter tip indicator.
 */
@Composable
fun ThreatScoreGauge(
    threatScore: Int,
    level: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val clampedScore = threatScore.coerceIn(0, 100)
    val animatedScore by animateFloatAsState(
        targetValue = clampedScore.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "threat_gauge_sweep"
    )
    val (gaugeColor, _) = threatLevelColors(level)
    val trackBase = MaterialTheme.colorScheme.surfaceVariant
    val lowZoneColor = MaterialTheme.colorScheme.secondary
    val elevatedZoneColor = MaterialTheme.colorScheme.tertiary
    val highZoneColor = MaterialTheme.colorScheme.error
    val centerBg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .size(134.dp)
            .semantics { contentDescription = "Threat score $clampedScore out of 100, risk level ${level.label}" }
            .testTag("threat_score_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 11.dp.toPx()
            val outerBandPx = 3.dp.toPx()
            val inset = strokePx / 2f + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val startAngle = 150f
            val totalSweep = 240f

            val bandInset = 2.5.dp.toPx()
            val bandSize = Size(size.width - bandInset * 2f, size.height - bandInset * 2f)
            val bandTopLeft = Offset(bandInset, bandInset)
            drawArc(lowZoneColor.copy(alpha = 0.28f), startAngle, totalSweep * 0.39f, false, bandTopLeft, bandSize, style = Stroke(outerBandPx, cap = StrokeCap.Round))
            drawArc(elevatedZoneColor.copy(alpha = 0.32f), startAngle + totalSweep * 0.405f, totalSweep * 0.235f, false, bandTopLeft, bandSize, style = Stroke(outerBandPx, cap = StrokeCap.Round))
            drawArc(highZoneColor.copy(alpha = 0.36f), startAngle + totalSweep * 0.655f, totalSweep * 0.345f, false, bandTopLeft, bandSize, style = Stroke(outerBandPx, cap = StrokeCap.Round))

            drawArc(trackBase, startAngle, totalSweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))

            val activeSweep = (animatedScore / 100f).coerceIn(0f, 1f) * totalSweep
            if (activeSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(0.0f to lowZoneColor, 0.40f to elevatedZoneColor, 0.72f to gaugeColor, 1.0f to gaugeColor, center = center),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            val tipRad = (startAngle + activeSweep) * (PI / 180.0)
            val radius = arcSize.width / 2f
            val tipCenter = Offset(center.x + (radius * cos(tipRad)).toFloat(), center.y + (radius * sin(tipRad)).toFloat())
            drawCircle(color = centerBg, radius = strokePx * 0.55f, center = tipCenter)
            drawCircle(color = gaugeColor, radius = strokePx * 0.36f, center = tipCenter)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
            Text(text = "$clampedScore", color = gaugeColor, style = MonoTextStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold))
            Text(
                text = "THREAT / 100",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = level.label,
                color = gaugeColor,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MonoTextStyle.copy(fontSize = 9.sp))
            Text("100", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MonoTextStyle.copy(fontSize = 9.sp))
        }
    }
}

@Composable
private fun ThreatBreakdownPanel(card: ThreatCard, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = card.verdict,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("QUANT (60%)", "${card.priceActionScore}/100", scoreSeverityColor(card.priceActionScore), Modifier.weight(1f))
            MiniMetric("NARRATIVE (40%)", "${card.narrativeScore}/100", scoreSeverityColor(card.narrativeScore), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric(
                label = "RSI(14)",
                value = fmtNum(card.quant.rsi, 1),
                valueColor = if (card.quant.rsi < 30.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            MiniMetric("CONF", "${(card.confidence * 100).toInt()}%", modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Lists active quantitative shock [ThreatDiagnosticDriver] items detected by [com.example.engine.ThreatEngine].
 */
@Composable
fun ThreatDriversList(
    drivers: List<ThreatDiagnosticDriver>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("threat_active_drivers_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = "Active threat drivers",
                    tint = if (drivers.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "ACTIVE DRIVERS (${drivers.size})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                )
            }
            if (drivers.isNotEmpty()) {
                Text("SEVERITY 0–100", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MonoTextStyle.copy(fontSize = 9.5.sp))
            }
        }

        if (drivers.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, "No active shock drivers", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Text(
                    text = "No active technical shock drivers detected — price & volume structure intact.",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                drivers.forEach { driver -> ThreatDriverRow(driver = driver) }
            }
        }
    }
}

@Composable
private fun ThreatDriverRow(driver: ThreatDiagnosticDriver, modifier: Modifier = Modifier) {
    val barColor = scoreSeverityColor(driver.score)
    val fraction = (driver.score / 100f).coerceIn(0f, 1f)
    val metricDisplay = formatDriverMetric(driver)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("threat_driver_${driver.name}"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(driverDisplayBadge(driver.name), barColor, barColor.copy(alpha = 0.14f))
                Text(
                    text = driver.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${driver.score}", color = barColor, style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold))
                if (metricDisplay.isNotEmpty()) {
                    Text(metricDisplay, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MonoTextStyle.copy(fontSize = 9.5.sp))
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor)
            )
        }
    }
}

private fun driverDisplayBadge(name: String): String = when (name) {
    "gap" -> "GAP"
    "day_change" -> "DAY CHG"
    "volume" -> "VOLUME"
    "range" -> "RANGE"
    "close_location" -> "CLV"
    "drawdown" -> "20D DD"
    "structure" -> "EMA/SWING"
    "relative" -> "REL WEAK"
    "vol_regime" -> "VOL SHIFT"
    else -> name.uppercase(Locale.US).take(10)
}

private fun formatDriverMetric(driver: ThreatDiagnosticDriver): String = when (driver.name) {
    "gap", "day_change", "drawdown" -> "${"%.1f".format(Locale.US, driver.value)}%"
    "relative" -> "${"%.1f".format(Locale.US, driver.value)}pp"
    "volume" -> "${"%.1f".format(Locale.US, driver.value)}σ"
    "range", "vol_regime" -> "${"%.1f".format(Locale.US, driver.value)}×"
    "close_location" -> "${"%.2f".format(Locale.US, driver.value)}"
    "structure" -> "${driver.value.toInt()} pts"
    else -> ""
}

@Composable
private fun threatLevelColors(level: ThreatLevel): Pair<Color, Color> = when (level) {
    ThreatLevel.CRITICAL, ThreatLevel.HIGH -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    ThreatLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    ThreatLevel.LOW -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun threatActionColors(action: ThreatAction): Pair<Color, Color> = when (action) {
    ThreatAction.EXIT_NOW, ThreatAction.SELL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    ThreatAction.TRIM -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    ThreatAction.HOLD -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun scoreSeverityColor(score: Int): Color = when {
    score >= 65 -> MaterialTheme.colorScheme.error
    score >= 40 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}
