package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.MarketMode
import com.example.ui.theme.MonoTextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Compact 48dp-touch-target toggle chip placed in the top app bar so the user
 * can swap between Stock Market and Crypto 24/7 from any tab.
 */
@Composable
fun TopBarMarketModeChip(
    marketMode: MarketMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCrypto = marketMode == MarketMode.CRYPTO
    val fg = if (isCrypto) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val bg = if (isCrypto) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val border = fg.copy(alpha = 0.55f)
    val accessibilityDesc = stringResource(R.string.market_mode_toggle_desc, marketMode.opposite.label)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = accessibilityDesc }
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("topbar_market_mode_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isCrypto) Icons.Outlined.CurrencyBitcoin else Icons.Outlined.ShowChart,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isCrypto) stringResource(R.string.market_mode_crypto_short)
                else stringResource(R.string.market_mode_stocks_short),
                color = fg,
                style = MonoTextStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Shared segmented market switcher displayed at the top of every primary page.
 * Shows both Stock Market and Crypto 24/7 live paper portfolio equities so
 * swapping immediately reflects the active portfolio and market universe.
 */
@Composable
fun MarketModeSwapBar(
    marketMode: MarketMode,
    stockEquity: Double,
    cryptoEquity: Double,
    onSelectMarketMode: (MarketMode) -> Unit,
    testTagPrefix: String = "market_swap",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarketModeSegment(
                title = stringResource(R.string.market_mode_stocks),
                equityText = fmtMoney(stockEquity),
                badgeText = "REG HRS",
                icon = Icons.Outlined.ShowChart,
                selected = marketMode == MarketMode.STOCKS,
                accentFg = MaterialTheme.colorScheme.primary,
                accentBg = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onSelectMarketMode(MarketMode.STOCKS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_stocks")
            )
            MarketModeSegment(
                title = stringResource(R.string.market_mode_crypto),
                equityText = fmtMoney(cryptoEquity),
                badgeText = "24/7 LIVE",
                icon = Icons.Outlined.CurrencyBitcoin,
                selected = marketMode == MarketMode.CRYPTO,
                accentFg = MaterialTheme.colorScheme.tertiary,
                accentBg = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = { onSelectMarketMode(MarketMode.CRYPTO) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_crypto")
            )
        }
    }
}

@Composable
private fun MarketModeSegment(
    title: String,
    equityText: String,
    badgeText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentFg: Color,
    accentBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) accentBg else Color.Transparent
    val borderColor = if (selected) accentFg.copy(alpha = 0.6f) else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) accentFg.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentFg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = equityText,
                    color = if (selected) accentFg else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = badgeText,
                    color = if (selected) accentFg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MonoTextStyle.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Prominent Swap Action button for the Autotrader screen that explicitly swaps
 * between 24/7 Crypto day-trading mode and Stock Market mode and updates the
 * active paper portfolio immediately.
 */
@Composable
fun AutotraderMarketSwapButton(
    marketMode: MarketMode,
    oppositeEquity: Double,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCrypto = marketMode == MarketMode.CRYPTO
    val fg = if (isCrypto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val bg = if (isCrypto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val label = if (isCrypto) {
        stringResource(R.string.market_mode_swap_to_stocks)
    } else {
        stringResource(R.string.market_mode_swap_to_crypto)
    }

    Surface(
        onClick = onSwap,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("autotrader_swap_market_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = fmtMoney(oppositeEquity),
                color = fg,
                style = MonoTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Kraken Futures Perpetual Funding / Crowding & CryptoPanic Guard overview card
 * shown when MarketMode.CRYPTO is active.
 */
@Composable
fun CryptoDerivsGuardCard(
    derivsBySymbol: Map<String, CryptoDerivsSnapshot>,
    guardsBySymbol: Map<String, CryptoGuardVerdict>,
    fundingSampleCount: Int,
    modifier: Modifier = Modifier
) {
    val orderedSymbols = remember(derivsBySymbol, guardsBySymbol) {
        val preferred = listOf("BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "LINK/USD", "DOGE/USD")
        val allKeys = (derivsBySymbol.keys + guardsBySymbol.keys).toSet()
        (preferred.filter { it in allKeys } + allKeys.filterNot { it in preferred }.sorted()).take(6)
    }

    val blockedCount = remember(guardsBySymbol) { guardsBySymbol.values.count { it.blocked } }
    val shrunkCount = remember(guardsBySymbol) {
        guardsBySymbol.values.count { !it.blocked && it.sizeMult < 0.99 }
    }
    val hasPercentileHistory = fundingSampleCount >= 30

    TerminalCard(modifier = modifier.testTag("crypto_derivs_guard_card")) {
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
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                SectionLabel(text = stringResource(R.string.crypto_guard_title))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                TagChip(
                    text = stringResource(R.string.crypto_247_badge),
                    fg = MaterialTheme.colorScheme.tertiary,
                    bg = MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasPercentileHistory) {
                stringResource(R.string.crypto_guard_subtitle_ready, fundingSampleCount, blockedCount, shrunkCount)
            } else {
                stringResource(R.string.crypto_guard_subtitle_warming, fundingSampleCount)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (orderedSymbols.isEmpty()) {
            Text(
                text = stringResource(R.string.crypto_guard_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // Compact table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAIR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "FUND/YR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "BASIS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(0.9f)
                )
                Text(
                    text = "GUARD",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1.25f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            orderedSymbols.forEachIndexed { idx, sym ->
                val deriv = derivsBySymbol[sym]
                val guard = guardsBySymbol[sym]
                val fundAnnPct = deriv?.fundingAnnual?.times(100.0)
                val fundColor = when {
                    fundAnnPct == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    fundAnnPct >= 30.0 -> MaterialTheme.colorScheme.error
                    fundAnnPct >= 15.0 -> MaterialTheme.colorScheme.tertiary
                    fundAnnPct < 0.0 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onBackground
                }
                val basisBps = deriv?.basisBps
                val sizeMult = guard?.sizeMult ?: deriv?.crowdingMult ?: 1.0
                val isBlocked = guard?.blocked == true

                val guardText = when {
                    isBlocked -> "BLOCKED"
                    sizeMult < 0.99 -> "${String.format(Locale.US, "%.2f", sizeMult)}× SIZE"
                    else -> "1.00× OK"
                }
                val guardFg = when {
                    isBlocked -> MaterialTheme.colorScheme.error
                    sizeMult < 0.99 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }
                val guardBg = when {
                    isBlocked -> MaterialTheme.colorScheme.errorContainer
                    sizeMult < 0.99 -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = sym,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                            val oiText = deriv?.openInterest?.takeIf { it > 0.0 }?.let { formatCompactOi(it) }
                            if (oiText != null) {
                                Text(
                                    text = "OI $oiText",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(fontSize = 9.sp),
                                    maxLines = 1
                                )
                            }
                        }

                        Text(
                            text = if (fundAnnPct != null) {
                                "${if (fundAnnPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", fundAnnPct)}%"
                            } else "—",
                            color = fundColor,
                            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        Text(
                            text = if (basisBps != null) {
                                "${if (basisBps >= 0) "+" else ""}${String.format(Locale.US, "%.1f", basisBps)}bp"
                            } else "—",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.5.sp),
                            modifier = Modifier.weight(0.9f),
                            maxLines = 1
                        )

                        Box(modifier = Modifier.weight(1.25f)) {
                            TagChip(
                                text = guardText,
                                fg = guardFg,
                                bg = guardBg
                            )
                        }
                    }

                    val hasAdverseEvent = guard != null &&
                        guard.eventCategory.isNotBlank() &&
                        !guard.eventCategory.equals("NONE", ignoreCase = true)
                    val noteLine = buildString {
                        if (hasAdverseEvent && guard != null) {
                            append("[${guard.eventCategory}] ")
                            if (guard.eventWhy.isNotBlank()) append(guard.eventWhy)
                        }
                        if (guard != null && abs(guard.newsScore) >= 0.05) {
                            if (isNotEmpty()) append(" · ")
                            append("News ${if (guard.newsScore >= 0) "+" else ""}${String.format(Locale.US, "%.2f", guard.newsScore)}")
                        }
                        val firstNote = guard?.notes?.firstOrNull() ?: deriv?.crowdingNote
                        if (!firstNote.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(firstNote)
                        }
                    }
                    if (noteLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = noteLine,
                            color = if (hasAdverseEvent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (hasAdverseEvent) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (idx < orderedSymbols.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun formatCompactOi(value: Double): String {
    val absVal = abs(value)
    return when {
        absVal >= 1_000_000_000.0 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        absVal >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        absVal >= 1_000.0 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "%.0f", value)
    }
}
