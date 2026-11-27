package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.example.domain.engine.MarketEnvironmentClassifier
import com.example.domain.model.MarketEnvironment
import com.example.domain.model.MarketEnvironmentClassification
import com.example.domain.model.MarketEnvironmentFactor
import com.example.domain.model.MarketRegime
import com.example.domain.model.SignalRow
import com.example.domain.model.VixSnapshot
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class DashboardTab(val label: String) {
    OVERVIEW("OVERVIEW"),
    FACTORS("FACTORS"),
    PLAYBOOK("PLAYBOOK")
}

/**
 * Overview dashboard component that processes market data inputs (regime trend, breadth,
 * VIX volatility, universe signal bias, and sector dispersion) to classify the current
 * market environment (Bull, Bear, Volatile, Neutral, Crisis) and display high-level
 * tactical rules at the top of the main screen.
 */
@Composable
fun MarketRegimeDashboard(
    regime: MarketRegime,
    rows: List<SignalRow> = emptyList(),
    vixSnapshot: VixSnapshot? = null,
    modifier: Modifier = Modifier
) {
    val classification: MarketEnvironmentClassification = remember(regime, rows, vixSnapshot) {
        MarketEnvironmentClassifier.classify(
            regime = regime,
            rows = rows,
            vixSnapshot = vixSnapshot
        )
    }

    var activeTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    val envColor = environmentColor(classification.environment)

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
            .testTag("market_regime_dashboard")
    ) {
        // Top Header: Label, Live Environment Badge, and Tab Pills
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(envColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = environmentIcon(classification.environment),
                        contentDescription = "Environment icon",
                        tint = envColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column {
                    SectionLabel(text = "MARKET REGIME DASHBOARD")
                    Text(
                        text = classification.environment.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TagChip(
                text = "${classification.environment.shortLabel} · ${classification.confidencePct}% CONF",
                fg = envColor,
                bg = envColor.copy(alpha = 0.16f),
                modifier = Modifier.testTag("regime_env_badge")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs: OVERVIEW / FACTORS / PLAYBOOK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.70f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DashboardTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .clickable { activeTab = tab }
                        .padding(vertical = 6.dp)
                        .testTag("regime_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) envColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content Animated Transition
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "regime_dashboard_tab_content"
        ) { tab ->
            when (tab) {
                DashboardTab.OVERVIEW -> {
                    OverviewSection(
                        classification = classification,
                        envColor = envColor
                    )
                }
                DashboardTab.FACTORS -> {
                    FactorsSection(
                        factors = classification.factors,
                        envColor = envColor
                    )
                }
                DashboardTab.PLAYBOOK -> {
                    PlaybookSection(
                        classification = classification,
                        envColor = envColor
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    classification: MarketEnvironmentClassification,
    envColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hero Barometer Gauge + Posture Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speedometer Barometer Gauge
            EnvironmentBarometerGauge(
                barometer = classification.compositeBarometer,
                environment = classification.environment,
                envColor = envColor,
                modifier = Modifier.size(136.dp)
            )

            // Right Overview Posture Box
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = classification.headlineVerdict,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(envColor.copy(alpha = 0.12f))
                        .border(1.dp, envColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "TACTICAL POSTURE",
                            color = envColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Text(
                            text = classification.tacticalPosture,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (classification.panicGuardActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚠ PANIC GUARD ACTIVE",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    )
                    Text(
                        text = "−${"%.1f".format(java.util.Locale.US, classification.panicDrawdownPct)}% index DD · Sizing cut 50% to prevent bounce trap",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4 Primary Metric Tiles: Trend, Breadth, VIX, Signals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val trendDisplay = when {
                classification.trendScore >= 0.5 -> "+${"%.1f".format(java.util.Locale.US, classification.trendScore)} Bull"
                classification.trendScore <= -0.5 -> "${"%.1f".format(java.util.Locale.US, classification.trendScore)} Bear"
                else -> "${"%.1f".format(java.util.Locale.US, classification.trendScore)} Mixed"
            }
            val trendValColor = when {
                classification.trendScore >= 0.5 -> MaterialTheme.colorScheme.secondary
                classification.trendScore <= -0.5 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }

            val vixDisplay = classification.vixLevel?.let {
                "%.1f".format(java.util.Locale.US, it)
            } ?: "${(classification.volatilityPercentile * 100).toInt()}p"

            val vixValColor = when {
                classification.vixSpikeActive || (classification.vixLevel ?: 0.0) >= 25.0 -> MaterialTheme.colorScheme.error
                (classification.vixLevel ?: 0.0) >= 20.0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            }

            MetricTile(
                label = "SPY TREND",
                value = trendDisplay,
                valueColor = trendValColor,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "BREADTH",
                value = "${classification.breadthPct.toInt()}%",
                valueColor = if (classification.breadthPct >= 50) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "VIX RISK",
                value = vixDisplay,
                valueColor = vixValColor,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "SIGNALS",
                value = "${classification.buySignalCount}B : ${classification.sellSignalCount}S",
                valueColor = if (classification.buySignalCount >= classification.sellSignalCount) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }

        // Tactical Sizing Pill Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.50f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SUGGESTED EXPOSURE:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LONG ${"%.2f".format(java.util.Locale.US, classification.recommendedLongMult)}×",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "SHORT ${"%.2f".format(java.util.Locale.US, classification.recommendedShortMult)}×",
                    color = MaterialTheme.colorScheme.error,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "CASH ${classification.recommendedCashAllocationPct}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun FactorsSection(
    factors: List<MarketEnvironmentFactor>,
    envColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLASSIFICATION TELEMETRY DRIVERS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            )
            Text(
                text = "WEIGHTED 0-100",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 9.sp)
            )
        }

        factors.forEach { factor ->
            val scoreColor = when {
                factor.score >= 65 -> if (factor.isPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                factor.score >= 40 -> MaterialTheme.colorScheme.tertiary
                else -> if (factor.isPositive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            }
            val barFraction = (factor.score / 100f).coerceIn(0.05f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.50f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = factor.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = factor.valueDisplay,
                        color = scoreColor,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = factor.verdict,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp)
                    )
                    Text(
                        text = "${factor.score} pts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 9.sp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(scoreColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybookSection(
    classification: MarketEnvironmentClassification,
    envColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tactical Rules Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.60f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Risk Rules",
                        tint = envColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${classification.environment.title.uppercase()} PLAYBOOK",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Text(
                    text = classification.environment.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                )
            }
        }

        // Stop & Execution Strategy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.60f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "STOP-LOSS & SIZING DISCIPLINE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Text(
                    text = classification.stopStrategyText,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp
                    )
                )
            }
        }

        // Allocation breakdown table
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "LONG SIZING",
                value = "${"%.2f".format(java.util.Locale.US, classification.recommendedLongMult)}×",
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "SHORT SIZING",
                value = "${"%.2f".format(java.util.Locale.US, classification.recommendedShortMult)}×",
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "CASH RESERVE",
                value = "${classification.recommendedCashAllocationPct}%",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Animated 240-degree speedometer-style barometer gauge representing the market environment
 * along the Crisis -> Bear -> Neutral -> Volatile -> Bull spectrum.
 */
@Composable
private fun EnvironmentBarometerGauge(
    barometer: Int,
    environment: MarketEnvironment,
    envColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedBarometer by animateFloatAsState(
        targetValue = barometer.toFloat().coerceIn(0f, 100f),
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.75f),
        label = "barometer_anim"
    )

    val bearCol = MaterialTheme.colorScheme.error
    val neutralCol = MaterialTheme.colorScheme.primary
    val volatileCol = MaterialTheme.colorScheme.tertiary
    val bullCol = MaterialTheme.colorScheme.secondary
    val trackCol = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .semantics {
                contentDescription = "Market environment barometer: $barometer out of 100, classified as ${environment.title}"
            }
            .testTag("regime_barometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 11.dp.toPx()
            val inset = strokePx / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val startAngle = 150f
            val totalSweep = 240f

            // Background segmented track
            // 4 zones: Crisis/Bear (0-30), Neutral (30-60), Volatile/Recovery (60-75), Bull (75-100)
            drawArc(
                color = trackCol,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active gradient arc
            val activeSweep = (animatedBarometer / 100f).coerceIn(0.01f, 1f) * totalSweep
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to bearCol,
                    0.35f to neutralCol,
                    0.65f to volatileCol,
                    0.85f to bullCol,
                    1.0f to bullCol,
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Tip indicator
            val tipRad = (startAngle + activeSweep) * (PI / 180.0)
            val radius = arcSize.width / 2f
            val tipCenter = Offset(
                x = center.x + (radius * cos(tipRad)).toFloat(),
                y = center.y + (radius * sin(tipRad)).toFloat()
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = strokePx * 0.65f,
                center = tipCenter
            )
            drawCircle(
                color = envColor,
                radius = strokePx * 0.45f,
                center = tipCenter
            )
        }

        // Center readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = "${barometer}",
                color = textPrimary,
                style = MonoTextStyle.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Text(
                text = "BAROMETER",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 7.5.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            TagChip(
                text = environment.shortLabel,
                fg = envColor,
                bg = envColor.copy(alpha = 0.16f)
            )
        }

        // Bounds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BEAR",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "BULL",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun environmentColor(env: MarketEnvironment): Color = when (env) {
    MarketEnvironment.BULL -> MaterialTheme.colorScheme.secondary
    MarketEnvironment.BEAR -> MaterialTheme.colorScheme.error
    MarketEnvironment.VOLATILE -> MaterialTheme.colorScheme.tertiary
    MarketEnvironment.NEUTRAL -> MaterialTheme.colorScheme.primary
    MarketEnvironment.CRISIS -> MaterialTheme.colorScheme.error
}

private fun environmentIcon(env: MarketEnvironment) = when (env) {
    MarketEnvironment.BULL -> Icons.Outlined.TrendingUp
    MarketEnvironment.BEAR -> Icons.Outlined.TrendingDown
    MarketEnvironment.VOLATILE -> Icons.Outlined.Bolt
    MarketEnvironment.NEUTRAL -> Icons.Outlined.Speed
    MarketEnvironment.CRISIS -> Icons.Outlined.LocalFireDepartment
}
