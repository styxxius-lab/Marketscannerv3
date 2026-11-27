package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.QuantEngine
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.RegimeMatrixCell
import com.example.domain.model.TrendBucket
import com.example.domain.model.VolatilityBucket
import com.example.ui.theme.MonoTextStyle
import kotlin.math.roundToInt

@Composable
fun MarketRegimeHeatmapCard(
    regime: MarketRegime,
    modifier: Modifier = Modifier
) {
    val heatmap = remember(regime) { QuantEngine.buildRegimeHeatmap(regime) }
    var selectedCellCoords by remember(regime.label, regime.volPct, regime.trendScore) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    val activeCell = remember(heatmap, selectedCellCoords) {
        val target = selectedCellCoords ?: (heatmap.activeRow to heatmap.activeCol)
        heatmap.cells.firstOrNull { it.row == target.first && it.col == target.second }
            ?: heatmap.cells.first()
    }

    val healthColor = when {
        heatmap.overallHealthScore >= 75 -> MaterialTheme.colorScheme.secondary
        heatmap.overallHealthScore >= 58 -> MaterialTheme.colorScheme.primary
        heatmap.overallHealthScore >= 42 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    TerminalCard(
        modifier = modifier.testTag("regime_heatmap_card")
    ) {
        // Header: Title + Overall Market Health Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(text = "REGIME HEALTH MATRIX")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Volatility vs. Trend · ${heatmap.healthVerdict}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            TagChip(
                text = "HEALTH ${heatmap.overallHealthScore}/100",
                fg = healthColor,
                bg = healthColor.copy(alpha = 0.16f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Column Headers (Trend Axis)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VOL \\ TREND",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                modifier = Modifier.width(62.dp)
            )
            TrendBucket.entries.forEach { trendBucket ->
                val isActiveCol = heatmap.activeCol == trendBucket.ordinal
                Text(
                    text = trendBucket.shortLabel,
                    color = if (isActiveCol) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(
                        fontSize = 9.5.sp,
                        fontWeight = if (isActiveCol) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4 x 3 Color-Coded Heatmap Matrix (Rows = Volatility, Cols = Trend)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("regime_heatmap_grid"),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VolatilityBucket.entries.forEachIndexed { rowIdx, volBucket ->
                val isActiveRow = heatmap.activeRow == rowIdx
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Column(
                        modifier = Modifier.width(57.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = volBucket.label.uppercase(),
                            color = if (isActiveRow) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(
                                fontSize = 9.sp,
                                fontWeight = if (isActiveRow) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = volBucket.rangeLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            style = MonoTextStyle.copy(fontSize = 8.5.sp),
                            maxLines = 1
                        )
                    }

                    TrendBucket.entries.forEachIndexed { colIdx, _ ->
                        val cell = heatmap.cells.first { it.row == rowIdx && it.col == colIdx }
                        val isInspected = selectedCellCoords == (rowIdx to colIdx)
                        HeatmapMatrixCellBox(
                            cell = cell,
                            isInspected = isInspected,
                            onClick = {
                                selectedCellCoords = if (isInspected) null else (rowIdx to colIdx)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active / Inspected Cell Readout Banner
        val cellColor = heatmapCellAccentColor(cell = activeCell)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(cellColor.copy(alpha = 0.12f))
                .border(1.dp, cellColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("regime_heatmap_readout"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(cellColor)
                    )
                    Text(
                        text = buildString {
                            append(activeCell.title)
                            if (activeCell.isCurrent) append(" · NOW")
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${activeCell.summary} · ${activeCell.historyCount}d in 45d window",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectedCellCoords != null) {
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selectedCellCoords = null }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Three Market Health Indicator Bars (Trend / Breadth / Volatility Calmness)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val trendNorm = ((regime.trendScore.coerceIn(-1.0, 1.0) + 1.0) / 2.0).toFloat()
            val trendBarColor = when {
                regime.trendScore >= 0.5 -> MaterialTheme.colorScheme.secondary
                regime.trendScore <= -0.5 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            HealthIndicatorBar(
                label = "TREND",
                valueText = when {
                    regime.trendScore >= 0.75 -> "Bull"
                    regime.trendScore <= -0.75 -> "Bear"
                    else -> "Mixed"
                },
                fraction = trendNorm.coerceIn(0.08f, 1f),
                barColor = trendBarColor,
                modifier = Modifier.weight(1f)
            )

            val breadthPct = (regime.breadth * 100.0).roundToInt()
            val breadthColor = when {
                regime.breadth > 0.55 -> MaterialTheme.colorScheme.secondary
                regime.breadth < 0.40 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            HealthIndicatorBar(
                label = "BREADTH",
                valueText = "$breadthPct%",
                fraction = regime.breadth.toFloat().coerceIn(0.08f, 1f),
                barColor = breadthColor,
                modifier = Modifier.weight(1f)
            )

            val volPctInt = (regime.volPct * 100.0).roundToInt()
            val volColor = when {
                regime.volPct > 0.90 -> MaterialTheme.colorScheme.error
                regime.volPct >= 0.70 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            }
            HealthIndicatorBar(
                label = "VOL PCT",
                valueText = "${volPctInt}p",
                fraction = regime.volPct.toFloat().coerceIn(0.08f, 1f),
                barColor = volColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun heatmapCellAccentColor(cell: RegimeMatrixCell): Color {
    return when (cell.regimeLabel) {
        RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
        RegimeLabel.NEUTRAL -> MaterialTheme.colorScheme.primary
        RegimeLabel.RISK_OFF -> MaterialTheme.colorScheme.tertiary
        RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun HeatmapMatrixCellBox(
    cell: RegimeMatrixCell,
    isInspected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = heatmapCellAccentColor(cell)
    val heatAlpha = when {
        cell.isCurrent -> 0.34f
        isInspected -> 0.28f
        cell.historyCount > 0 -> 0.20f
        else -> 0.11f
    }
    val borderColor = when {
        cell.isCurrent -> accent
        isInspected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        else -> accent.copy(alpha = 0.30f)
    }
    val borderWidth = if (cell.isCurrent || isInspected) 1.6.dp else 1.dp

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(accent.copy(alpha = heatAlpha))
            .border(borderWidth, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .semantics {
                contentDescription =
                    "${cell.volBucket.label} volatility, ${cell.trendBucket.label} trend: ${cell.title}, health ${cell.healthScore} out of 100"
            }
            .testTag("heatmap_cell_${cell.row}_${cell.col}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (cell.isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                Text(
                    text = cell.title,
                    color = if (cell.isCurrent || isInspected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = if (cell.isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val subText = when {
                cell.isCurrent -> "NOW · ${cell.healthScore}"
                cell.historyCount > 0 -> "${cell.healthScore} · ${cell.historyCount}d"
                else -> "${cell.healthScore}"
            }
            Text(
                text = subText,
                color = if (cell.isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(
                    fontSize = 8.5.sp,
                    fontWeight = if (cell.isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HealthIndicatorBar(
    label: String,
    valueText: String,
    fraction: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = valueText,
                color = barColor,
                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}
