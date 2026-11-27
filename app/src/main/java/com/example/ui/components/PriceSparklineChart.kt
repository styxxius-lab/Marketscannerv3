package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp

@Composable
fun PriceSparklineChart(
    series: List<Double>,
    lineColor: Color,
    stopPrice: Double? = null,
    targetPrice: Double? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val buyColor = MaterialTheme.colorScheme.secondary
    val sellColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (series.size < 2) {
            Text(
                text = "Chart unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 12.dp.toPx()

            val allVals = buildList {
                addAll(series)
                if (stopPrice != null && stopPrice.isFinite()) add(stopPrice)
                if (targetPrice != null && targetPrice.isFinite()) add(targetPrice)
            }
            val minVal = allVals.min()
            val maxVal = allVals.max()
            val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0

            fun px(i: Int): Float = pad + (i.toFloat() / (series.size - 1)) * (w - pad * 2f)
            fun py(v: Double): Float =
                h - pad - (((v - minVal) / range).toFloat() * (h - pad * 2f))

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

            targetPrice?.takeIf { it.isFinite() }?.let { tgt ->
                val y = py(tgt)
                drawLine(
                    color = buyColor.copy(alpha = 0.45f),
                    start = Offset(pad, y),
                    end = Offset(w - pad, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            stopPrice?.takeIf { it.isFinite() }?.let { stp ->
                val y = py(stp)
                drawLine(
                    color = sellColor.copy(alpha = 0.45f),
                    start = Offset(pad, y),
                    end = Offset(w - pad, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            val linePath = Path().apply {
                moveTo(px(0), py(series[0]))
                for (i in 1 until series.size) {
                    lineTo(px(i), py(series[i]))
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(px(series.size - 1), h)
                lineTo(px(0), h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        lineColor.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            val lastX = px(series.size - 1)
            val lastY = py(series.last())
            drawCircle(
                color = lineColor.copy(alpha = 0.28f),
                radius = 6.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }
    }
}
