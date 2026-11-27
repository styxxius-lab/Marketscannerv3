package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatCategory
import com.example.domain.model.ThreatLevel
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldContainer
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatAssessmentCard(
    symbol: String,
    assessment: ThreatAssessment?,
    isAssessing: Boolean,
    isHolding: Boolean,
    currentStop: Double? = null,
    onRefresh: () -> Unit,
    onExitPosition: (() -> Unit)? = null,
    onTrimPosition: (() -> Unit)? = null,
    onApplySuggestedStop: ((Double) -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember(symbol) { mutableStateOf(initiallyExpanded) }

    val (levelFg, levelBg) = when (assessment?.level) {
        ThreatLevel.CRITICAL, ThreatLevel.HIGH ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        ThreatLevel.ELEVATED ->
            SignalHold to SignalHoldContainer
        ThreatLevel.LOW, null ->
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
    }

    val (actionFg, actionBg) = when (assessment?.action) {
        ThreatAction.EXIT_NOW, ThreatAction.SELL ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        ThreatAction.TRIM ->
            SignalHold to SignalHoldContainer
        ThreatAction.HOLD, null ->
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
    }

    TerminalCard(
        modifier = modifier.testTag("threat_assessment_card_$symbol")
    ) {
        // Header Row
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
                    imageVector = if (assessment?.level == ThreatLevel.HIGH || assessment?.level == ThreatLevel.CRITICAL) {
                        Icons.Outlined.WarningAmber
                    } else {
                        Icons.Outlined.Shield
                    },
                    contentDescription = "Threat assessment shield",
                    tint = levelFg,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = "RISK THREAT ENGINE")
                if (assessment != null) {
                    TagChip(
                        text = "${assessment.level.label} ${assessment.threatScore}",
                        fg = levelFg,
                        bg = levelBg,
                        modifier = Modifier.testTag("threat_level_chip_$symbol")
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAssessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("threat_refresh_button_$symbol")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh threat assessment for $symbol",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (assessment != null) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("threat_expand_button_$symbol")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Collapse threat details" else "Expand threat details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (assessment == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAssessing) "Evaluating tape shock & news taxonomy for $symbol…"
                else "Tap refresh to run quant shock + narrative risk assessment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            return@TerminalCard
        }

        // Action + Verdict Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagChip(
                text = assessment.action.label,
                fg = actionFg,
                bg = actionBg,
                modifier = Modifier.testTag("threat_action_chip_$symbol")
            )
            Text(
                text = assessment.verdict,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quant (60%) vs Narrative (40%) Dual Progress Meters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreatSubMeter(
                label = "QUANT SHOCK",
                score = assessment.priceActionScore,
                modifier = Modifier.weight(1f)
            )
            ThreatSubMeter(
                label = if (assessment.isLiveClassified) "GEMINI NARRATIVE"
                else if (assessment.narrative.degraded) "TAPE-ONLY"
                else "NARRATIVE",
                score = assessment.narrativeScore,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Taxonomy + Contagion Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val cat = assessment.narrative.category
            if (cat != ThreatCategory.NONE) {
                val structuralFg = if (assessment.narrative.structural) {
                    MaterialTheme.colorScheme.error
                } else {
                    SignalHold
                }
                val structuralBg = if (assessment.narrative.structural) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    SignalHoldContainer
                }
                TagChip(
                    text = cat.label.uppercase(Locale.US),
                    fg = structuralFg,
                    bg = structuralBg
                )
                if (assessment.narrative.structural) {
                    TagChip(
                        text = "STRUCTURAL · ${assessment.narrative.persistenceDays}D",
                        fg = MaterialTheme.colorScheme.error,
                        bg = MaterialTheme.colorScheme.errorContainer
                    )
                }
            } else {
                TagChip(
                    text = "NO THESIS BREAK",
                    fg = MaterialTheme.colorScheme.secondary,
                    bg = MaterialTheme.colorScheme.secondaryContainer
                )
            }

            if (assessment.contagion?.sectorWide == true) {
                val c = assessment.contagion
                TagChip(
                    text = "SECTOR CONTAGION ${c.peersFalling}/${c.peerCount} (${"%.1f".format(Locale.US, c.peerDropAvgPct)}%)",
                    fg = SignalHold,
                    bg = SignalHoldContainer
                )
            }

            TagChip(
                text = "CONF ${(assessment.confidence * 100).toInt()}%",
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
                bg = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Expanded breakdown: Narrative summary, drivers, reasoning, stop/invalidation
        if (expanded || assessment.level >= ThreatLevel.ELEVATED) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))

            if (assessment.narrative.summary.isNotBlank()) {
                Text(
                    text = assessment.narrative.summary,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (assessment.drivers.isNotEmpty()) {
                Text(
                    text = "QUANT DRIVERS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                assessment.drivers.take(if (expanded) 9 else 3).forEach { driver ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "· ${driver.text}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${driver.score}",
                            color = if (driver.score >= 60) MaterialTheme.colorScheme.error else SignalHold,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            if (expanded && assessment.reasoning.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "DECISION RATIONALE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                assessment.reasoning.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                    )
                }
            }

            // Suggested Stop & Invalidation
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (assessment.suggestedStop != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SUGGESTED PROTECTIVE STOP",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = fmtMoney(assessment.suggestedStop),
                                color = MaterialTheme.colorScheme.error,
                                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                    Text(
                        text = "Invalidation: ${assessment.invalidation}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                    )
                }
            }
        }

        // Actionable Risk Execution Buttons when position is open
        if (isHolding) {
            val stopCandidate = assessment.suggestedStop
            val canTightenStop = stopCandidate != null &&
                onApplySuggestedStop != null &&
                (currentStop == null || stopCandidate > currentStop + 0.01)
            val showExit = (assessment.action == ThreatAction.EXIT_NOW || assessment.action == ThreatAction.SELL) &&
                onExitPosition != null
            val showTrim = onTrimPosition != null &&
                (assessment.action == ThreatAction.TRIM || assessment.level >= ThreatLevel.ELEVATED)

            if (showExit || showTrim || canTightenStop) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showExit) {
                        Button(
                            onClick = { onExitPosition?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("threat_exit_button_$symbol"),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                text = assessment.action.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                    if (showTrim) {
                        OutlinedButton(
                            onClick = { onTrimPosition?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("threat_trim_button_$symbol"),
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, SignalHold),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalHold)
                        ) {
                            Text(
                                text = "TRIM 50%",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                    if (canTightenStop && stopCandidate != null) {
                        OutlinedButton(
                            onClick = { onApplySuggestedStop?.invoke(stopCandidate) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("threat_tighten_stop_button_$symbol"),
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "STOP ${fmtMoney(stopCandidate)}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatSubMeter(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val barColor: Color = when {
        score >= 65 -> MaterialTheme.colorScheme.error
        score >= 40 -> SignalHold
        else -> MaterialTheme.colorScheme.secondary
    }
    val fraction = (score / 100f).coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "$score/100",
                color = barColor,
                style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}
