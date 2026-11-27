package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.IndicatorMode
import com.example.domain.engine.IndicatorPolarity
import com.example.domain.engine.TickerIndicatorEngine
import com.example.domain.engine.TickerIndicatorQuote
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

@Composable
fun TickerSignalTableRow(
    quote: TickerIndicatorQuote,
    mode: IndicatorMode,
    expanded: Boolean,
    canOpenFullAnalysis: Boolean,
    canRemove: Boolean,
    onToggleExpand: () -> Unit,
    onOpenFullAnalysis: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val polarity = TickerIndicatorEngine.signalPolarity(quote, mode)
    val label = TickerIndicatorEngine.signalLabel(quote, mode)
    val (sigFg, sigBg) = polarityColors(polarity)
    val changePositive = quote.changePct >= 0.0
    val changeColor = if (changePositive) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ticker_signal_row_${quote.symbol.lowercase(Locale.US)}"),
        shape = RoundedCornerShape(14.dp),
        color = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) sigFg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Column 1: TICKER + sector + live mini sparkline
                Column(
                    modifier = Modifier.weight(0.34f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = quote.symbol,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyle.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (quote.freshGoldenCross || quote.freshDeathCross) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(sigFg)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = quote.sectorLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (quote.sparkline.size >= 3) {
                            TickerMiniSparkline(
                                points = quote.sparkline,
                                lineColor = changeColor,
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(13.dp)
                            )
                        }
                    }
                }

                // Column 2: CURRENT PRICE + daily % change
                Column(
                    modifier = Modifier.weight(0.28f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", quote.price)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyle.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = if (changePositive) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", quote.changePct),
                            color = changeColor,
                            style = MonoTextStyle.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Column 3: SIGNAL (real-time quantitative indicator)
                Column(
                    modifier = Modifier.weight(0.38f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(sigBg)
                            .border(0.8.dp, sigFg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = label,
                            color = sigFg,
                            style = MonoTextStyle.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    when (mode) {
                        IndicatorMode.RSI_14 -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "RSI ${String.format(Locale.US, "%.1f", quote.rsi14)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                                RsiGaugeBar(
                                    rsi = quote.rsi14,
                                    accentColor = sigFg,
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(6.dp)
                                )
                            }
                        }

                        IndicatorMode.MA_CROSS -> {
                            val spreadColor = when {
                                quote.maSpreadPct > 0.15 -> MaterialTheme.colorScheme.secondary
                                quote.maSpreadPct < -0.15 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = "9/21 ${String.format(Locale.US, "%+.2f%%", quote.maSpreadPct)}",
                                color = spreadColor,
                                style = MonoTextStyle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                        }

                        IndicatorMode.COMPOSITE -> {
                            Text(
                                text = "R ${String.format(Locale.US, "%.0f", quote.rsi14)} · MA ${String.format(Locale.US, "%+.1f%%", quote.maSpreadPct)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(
                                    fontSize = 9.8.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IndicatorMetricTile(
                            label = "RSI (14)",
                            value = String.format(Locale.US, "%.1f", quote.rsi14),
                            subtext = quote.rsiLabel,
                            valueColor = polarityColors(quote.rsiPolarity).first,
                            modifier = Modifier.weight(1f)
                        )
                        IndicatorMetricTile(
                            label = "EMA 9 / 21",
                            value = "${String.format(Locale.US, "%.2f", quote.ema9)} / ${String.format(Locale.US, "%.2f", quote.ema21)}",
                            subtext = String.format(Locale.US, "%+.2f%% · %s", quote.maSpreadPct, quote.maCrossLabel),
                            valueColor = polarityColors(quote.maCrossPolarity).first,
                            modifier = Modifier.weight(1.35f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val smaBull = quote.sma20 >= quote.sma50
                        IndicatorMetricTile(
                            label = "SMA 20 / 50",
                            value = "${String.format(Locale.US, "%.2f", quote.sma20)} / ${String.format(Locale.US, "%.2f", quote.sma50)}",
                            subtext = if (smaBull) "20 ABOVE 50 (BULL)" else "20 BELOW 50 (BEAR)",
                            valueColor = if (smaBull) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1.2f)
                        )
                        IndicatorMetricTile(
                            label = "DAY RANGE",
                            value = "$${String.format(Locale.US, "%.2f", quote.dayLow)}–$${String.format(Locale.US, "%.2f", quote.dayHigh)}",
                            subtext = quote.sourceLabel,
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canRemove) {
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRemove)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("ticker_remove_${quote.symbol.lowercase(Locale.US)}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.ticker_action_remove),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ticker_action_remove),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        if (canOpenFullAnalysis) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable(onClick = onOpenFullAnalysis)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("ticker_open_detail_${quote.symbol.lowercase(Locale.US)}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ticker_action_open_detail),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.ticker_action_open_detail),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorMetricTile(
    label: String,
    value: String,
    subtext: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtext,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RsiGaugeBar(
    rsi: Double,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val clamped = rsi.coerceIn(0.0, 100.0).toFloat() / 100f

    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        val radius = h / 2f

        // Background track
        drawRoundRect(
            color = trackBg,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(radius, radius)
        )

        // Active fill up to RSI value
        drawRoundRect(
            color = accentColor.copy(alpha = 0.72f),
            topLeft = Offset.Zero,
            size = Size((w * clamped).coerceAtLeast(h), h),
            cornerRadius = CornerRadius(radius, radius)
        )

        // 30 and 70 threshold ticks
        drawLine(
            color = markerColor,
            start = Offset(w * 0.30f, 0f),
            end = Offset(w * 0.30f, h),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = markerColor,
            start = Offset(w * 0.70f, 0f),
            end = Offset(w * 0.70f, h),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun TickerMiniSparkline(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: return@Canvas
        val maxVal = points.maxOrNull() ?: return@Canvas
        val span = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
        val stepX = size.width / (points.size - 1).toFloat()
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val normY = ((value - minVal) / span).toFloat()
            val y = size.height - (normY * (size.height - 2f)) - 1f
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 1.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun polarityColors(polarity: IndicatorPolarity): Pair<Color, Color> = when (polarity) {
    IndicatorPolarity.STRONG_BUY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
    IndicatorPolarity.BUY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
    IndicatorPolarity.STRONG_SELL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    IndicatorPolarity.SELL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
    IndicatorPolarity.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
}
