package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.CouncilDecision
import com.example.domain.engine.CouncilFeatures
import com.example.domain.engine.CouncilVote
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

@Composable
fun CouncilLauncherCard(
    memberCount: Int,
    familyCount: Int,
    ledgerCount: Int,
    lastDecision: CouncilDecision?,
    onOpenCouncil: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = MarketScanTheme.extraColors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(extra.heroGradStart, extra.heroGradEnd)
                )
            )
            .clickable(onClick = onOpenCouncil)
            .testTag("council_launcher_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.council_launcher_badge),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        Text(
                            text = stringResource(R.string.council_launcher_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.council_launcher_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.council_launcher_cta),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.council_title),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$memberCount members · $familyCount families · $ledgerCount logged",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 10.sp),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastDecision != null) {
                    val (fg, bg) = councilActionColors(lastDecision.action, lastDecision.degraded)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${lastDecision.symbol} ${lastDecision.action} · ${String.format(Locale.US, "%.2fx", lastDecision.sizeMult)}",
                            color = fg,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CouncilDecisionCard(
    decision: CouncilDecision,
    features: CouncilFeatures?,
    modifier: Modifier = Modifier
) {
    val (fg, bg) = councilActionColors(decision.action, decision.degraded)
    val extra = MarketScanTheme.extraColors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, fg.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("council_verdict_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = decision.symbol,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        )
                        if (decision.price > 0.0) {
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", decision.price)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        if (decision.degraded) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.council_degraded_badge),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${decision.regime} · horizon ${decision.horizon}d · quorum ${decision.quorum}/${decision.votes.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = decision.action,
                        color = fg,
                        style = MonoTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CouncilStatTile(
                    label = stringResource(R.string.council_metric_score),
                    value = String.format(Locale.US, "%+.2f", decision.score),
                    color = fg,
                    modifier = Modifier.weight(1f)
                )
                CouncilStatTile(
                    label = stringResource(R.string.council_metric_size),
                    value = String.format(Locale.US, "%.2fx", decision.sizeMult),
                    color = if (decision.sizeMult > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                CouncilStatTile(
                    label = stringResource(R.string.council_metric_agree),
                    value = String.format(Locale.US, "%.0f%%", decision.agreement * 100.0),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                CouncilStatTile(
                    label = stringResource(R.string.council_metric_families),
                    value = decision.families.toString(),
                    color = if (decision.families >= 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(extra.metricTileBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (decision.degraded) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Verdict: ${decision.reason} (disp ${String.format(Locale.US, "%.2f", decision.dispersion)})",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MonoTextStyle.copy(fontSize = 11.sp)
                )
            }

            if (features != null && features.symbol == decision.symbol) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "Snapshot · ATR ${String.format(Locale.US, "%.2f%%", features.atrPct)} · " +
                        "MOM20 ${String.format(Locale.US, "%+.1f%%", features.mom20d)} · " +
                        "RSI ${String.format(Locale.US, "%.0f", features.rsi14)} · " +
                        "ADX ${String.format(Locale.US, "%.0f", features.adx14)} · " +
                        "RVOL ${String.format(Locale.US, "%.2fx", features.rvol)} · " +
                        "POS ${features.position.uppercase(Locale.US)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.8.sp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CouncilVoteCard(
    vote: CouncilVote,
    weight: Double,
    modifier: Modifier = Modifier
) {
    val hasError = vote.error.isNotBlank()
    val (fg, bg) = if (hasError) {
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    } else {
        councilActionColors(vote.action, false)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("council_vote_card_${vote.member.replace(":", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vote.member,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = vote.family.uppercase(Locale.US),
                                color = MaterialTheme.colorScheme.primary,
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "w=${String.format(Locale.US, "%.2f", weight)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp)
                        )
                        if (vote.latencyMs > 0L) {
                            Text(
                                text = "${vote.latencyMs}ms",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(fontSize = 9.5.sp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasError) "ERR" else "${vote.action} ${String.format(Locale.US, "%.0f%%", vote.confidence * 100.0)}",
                        color = fg,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            if (hasError) {
                Text(
                    text = vote.error,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MonoTextStyle.copy(fontSize = 10.5.sp)
                )
            } else {
                if (vote.thesis.isNotBlank()) {
                    Text(
                        text = vote.thesis,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Horizon: ${vote.horizon}d",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp)
                    )
                    if (vote.invalidation != null) {
                        Text(
                            text = "Invalidation: $${String.format(Locale.US, "%.2f", vote.invalidation)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp)
                        )
                    }
                }
                if (vote.flags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        vote.flags.forEach { flag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚑ $flag",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp)
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
fun CouncilLedgerRowCard(
    decision: CouncilDecision,
    modifier: Modifier = Modifier
) {
    val (fg, bg) = councilActionColors(decision.action, decision.degraded)
    val outcome = decision.outcome
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("council_ledger_row_${decision.symbol.lowercase(Locale.US)}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = decision.symbol,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = decision.action,
                            color = fg,
                            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    if (decision.degraded) {
                        Text(
                            text = "DEG",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (outcome != null) {
                    val hitColor = when (outcome.hit) {
                        true -> MaterialTheme.colorScheme.secondary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val hitText = when (outcome.hit) {
                        true -> "HIT"
                        false -> "MISS"
                        null -> "N/A"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%+.2f%% (%dd)", outcome.ret * 100.0, outcome.sessions),
                            color = hitColor,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(hitColor.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = hitText,
                                color = hitColor,
                                style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "PENDING",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Text(
                text = "${decision.asof} · $${String.format(Locale.US, "%.2f", decision.price)} · " +
                    "score ${String.format(Locale.US, "%+.2f", decision.score)} · " +
                    "size ${String.format(Locale.US, "%.2fx", decision.sizeMult)} · " +
                    "${decision.families} fam · ${decision.reason}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CouncilStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = color,
            style = MonoTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold),
            maxLines = 1
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            maxLines = 1
        )
    }
}

@Composable
fun councilActionColors(action: String, degraded: Boolean): Pair<Color, Color> {
    if (degraded) {
        return MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    }
    return when (action.uppercase(Locale.US)) {
        "BUY" -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        "SELL" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
}
