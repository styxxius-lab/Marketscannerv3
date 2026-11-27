package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalAction
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldContainer
import java.util.Locale
import kotlin.math.abs

fun fmtNum(n: Double?, decimals: Int = 2): String {
    if (n == null || !n.isFinite()) return "—"
    return "%.${decimals}f".format(Locale.US, n)
}

fun fmtMoney(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    return "$" + "%.2f".format(Locale.US, n)
}

fun fmtSignedMoney(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    val prefix = if (n >= 0) "+" else "-"
    return "$prefix$" + "%.2f".format(Locale.US, abs(n))
}

fun fmtPctSigned(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    val prefix = if (n >= 0) "+" else ""
    return "$prefix${"%.2f".format(Locale.US, n)}%"
}

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun ActionChip(action: SignalAction, modifier: Modifier = Modifier) {
    val (fg, bg) = when (action) {
        SignalAction.BUY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        SignalAction.SELL -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        SignalAction.HOLD -> SignalHold to SignalHoldContainer
    }
    TagChip(text = action.name, fg = fg, bg = bg, modifier = modifier)
}

@Composable
fun TagChip(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        )
    }
}

@Composable
fun BannerMessage(
    text: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bg = accent.copy(alpha = 0.10f)
    val border = accent.copy(alpha = 0.30f)
    val textColor = if (isWarning) MarketScanTheme.extraColors.bannerWarnText else MarketScanTheme.extraColors.bannerInfoText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp
            )
        )
    }
}

@Composable
fun RegimeStatusRow(regime: MarketRegime, modifier: Modifier = Modifier) {
    val dotColor = when (regime.label) {
        RegimeLabel.RISK_ON -> MaterialTheme.colorScheme.secondary
        RegimeLabel.RISK_OFF, RegimeLabel.CRISIS -> MaterialTheme.colorScheme.error
        RegimeLabel.NEUTRAL -> SignalHold
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = regime.label.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
        if (regime.note.isNotBlank()) {
            Text(
                text = "·",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = regime.note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MiniMetric(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(10.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier
    )
}
