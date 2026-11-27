package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ScanRegimeSnapshot
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun regimeShortLabel(label: RegimeLabel): String = when (label) {
    RegimeLabel.RISK_ON -> "RISK ON"
    RegimeLabel.NEUTRAL -> "NEUTRAL"
    RegimeLabel.RISK_OFF -> "RISK OFF"
    RegimeLabel.CRISIS -> "CRISIS"
}

@Composable
fun SignalsHeaderRegimeBar(
    history: List<ScanRegimeSnapshot>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    var inspectedIdx by remember(history) { mutableStateOf<Int?>(null) }
    val activeIdx = (inspectedIdx ?: (history.size - 1)).coerceIn(0, history.lastIndex)
    val active = history[activeIdx]
    val activeColor = regimeColor(active.label)

    val riskOnCol = regimeColor(RegimeLabel.RISK_ON)
    val neutralCol = regimeColor(RegimeLabel.NEUTRAL)
    val riskOffCol = regimeColor(RegimeLabel.RISK_OFF)
    val crisisCol = regimeColor(RegimeLabel.CRISIS)
    val trackCol = MaterialTheme.colorScheme.surfaceVariant
    val trackStroke = MaterialTheme.colorScheme.outline
    val focusRingCol = MaterialTheme.colorScheme.onBackground

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val scanSummaryText = remember(history) {
        history.mapIndexed { i, s -> "Scan ${i + 1}: ${regimeShortLabel(s.label)}" }.joinToString(", ")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .pointerInput(history.size) {
                detectTapGestures { offset ->
                    if (size.width > 0 && history.isNotEmpty()) {
                        val slot = ((offset.x / size.width) * 5f).toInt().coerceIn(0, 4)
                        inspectedIdx = if (slot < history.size) {
                            if (inspectedIdx == slot) null else slot
                        } else {
                            null
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics {
                contentDescription = "Historical market regime over the last ${history.size} scans: $scanSummaryText"
            }
            .testTag("signals_header_regime_timeline")
    ) {
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
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
                Text(
                    text = "LAST 5 SCANS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "#${activeIdx + 1} ${regimeShortLabel(active.label)} ${(active.breadth * 100).roundToInt()}% (${timeFmt.format(Date(active.timestampMs))})",
                    color = activeColor,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "${history.size}/5",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalSlots = 5
                val gapPx = 5.dp.toPx()
                val slotW = ((size.width - gapPx * (totalSlots - 1)) / totalSlots).coerceAtLeast(4f)
                val barH = size.height
                val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                for (i in 0 until totalSlots) {
                    val left = i * (slotW + gapPx)
                    val snap = history.getOrNull(i)
                    if (snap == null) {
                        drawRoundRect(
                            color = trackCol.copy(alpha = 0.45f),
                            topLeft = Offset(left, 0f),
                            size = Size(slotW, barH),
                            cornerRadius = corner
                        )
                        drawRoundRect(
                            color = trackStroke.copy(alpha = 0.7f),
                            topLeft = Offset(left, 0f),
                            size = Size(slotW, barH),
                            cornerRadius = corner,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    } else {
                        val barCol = when (snap.label) {
                            RegimeLabel.RISK_ON -> riskOnCol
                            RegimeLabel.NEUTRAL -> neutralCol
                            RegimeLabel.RISK_OFF -> riskOffCol
                            RegimeLabel.CRISIS -> crisisCol
                        }
                        val isActive = i == activeIdx
                        drawRoundRect(
                            color = barCol.copy(alpha = if (isActive) 1f else 0.72f),
                            topLeft = Offset(left, 0f),
                            size = Size(slotW, barH),
                            cornerRadius = corner
                        )
                        if (isActive) {
                            drawRoundRect(
                                color = focusRingCol.copy(alpha = 0.9f),
                                topLeft = Offset(left, 0f),
                                size = Size(slotW, barH),
                                cornerRadius = corner,
                                style = Stroke(width = 1.4.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}
