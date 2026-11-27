package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class MarketScanExtraColors(
    val heroGradStart: Color,
    val heroGradEnd: Color,
    val metricTileBg: Color,
    val bannerInfoText: Color,
    val bannerWarnText: Color
)

private val DarkExtraColors = MarketScanExtraColors(
    heroGradStart = HeroGradStart,
    heroGradEnd = HeroGradEnd,
    metricTileBg = Color.White.copy(alpha = 0.04f),
    bannerInfoText = Color(0xFF9DC0FF),
    bannerWarnText = Color(0xFFFF8F94)
)

private val LightExtraColors = MarketScanExtraColors(
    heroGradStart = LightHeroGradStart,
    heroGradEnd = LightHeroGradEnd,
    metricTileBg = Color(0xFFF0F4FA),
    bannerInfoText = Color(0xFF1D4ED8),
    bannerWarnText = Color(0xFFB91C1C)
)

private val LocalExtraColors = staticCompositionLocalOf { DarkExtraColors }

private val DarkColorScheme = darkColorScheme(
    primary = AccentCobalt,
    onPrimary = TerminalBg,
    primaryContainer = AccentCobaltContainer,
    onPrimaryContainer = AccentCobalt,
    secondary = SignalBuy,
    onSecondary = Color(0xFF08120D),
    secondaryContainer = SignalBuyContainer,
    onSecondaryContainer = SignalBuy,
    tertiary = WarnAmber,
    onTertiary = TerminalBg,
    tertiaryContainer = WarnAmberContainer,
    onTertiaryContainer = WarnAmber,
    error = SignalSell,
    onError = TerminalTextPrimary,
    errorContainer = SignalSellContainer,
    onErrorContainer = SignalSell,
    background = TerminalBg,
    onBackground = TerminalTextPrimary,
    surface = TerminalSurface,
    onSurface = TerminalTextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TerminalTextMuted,
    surfaceContainerLowest = TerminalBg,
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = TerminalSurface,
    surfaceContainerHigh = TerminalSurfaceElevated,
    surfaceContainerHighest = Color(0xFF232B36),
    outline = TerminalBorder,
    outlineVariant = TerminalBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccentCobalt,
    onPrimary = Color.White,
    primaryContainer = LightAccentCobaltContainer,
    onPrimaryContainer = LightAccentCobalt,
    secondary = LightSignalBuy,
    onSecondary = Color.White,
    secondaryContainer = LightSignalBuyContainer,
    onSecondaryContainer = LightSignalBuy,
    tertiary = LightWarnAmber,
    onTertiary = Color.White,
    tertiaryContainer = LightWarnAmberContainer,
    onTertiaryContainer = LightWarnAmber,
    error = LightSignalSell,
    onError = Color.White,
    errorContainer = LightSignalSellContainer,
    onErrorContainer = LightSignalSell,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = Color(0xFFDCE4EF),
    outline = LightBorder,
    outlineVariant = LightBorder
)

val MarketScanShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

object MarketScanTheme {
    val extraColors: MarketScanExtraColors
        @Composable get() = LocalExtraColors.current
}

@Composable
fun MarketScanTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = MarketScanShapes,
            content = content
        )
    }
}
