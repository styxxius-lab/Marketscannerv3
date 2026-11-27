package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.IndicatorMode
import com.example.domain.engine.TickerDirectionFilter
import com.example.domain.engine.TickerIndicatorEngine
import com.example.domain.engine.TickerSortField
import com.example.ui.TickerFeedStatus
import com.example.ui.TickerIndicatorsUiState
import com.example.ui.components.TickerSignalTableRow
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerIndicatorsScreen(
    uiState: TickerIndicatorsUiState,
    knownAnalysisSymbols: Set<String>,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onTickerInputChange: (String) -> Unit,
    onAddTickers: () -> Unit,
    onRemoveTicker: (String) -> Unit,
    onSelectIndicatorMode: (IndicatorMode) -> Unit,
    onSelectDirectionFilter: (TickerDirectionFilter) -> Unit,
    onSelectSort: (TickerSortField) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleExpandRow: (String) -> Unit,
    onOpenFullAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onStartPolling()
        onDispose { onStopPolling() }
    }

    val filteredQuotes = remember(
        uiState.quotes,
        uiState.indicatorMode,
        uiState.directionFilter,
        uiState.sortField,
        uiState.sortDescending,
        uiState.searchQuery
    ) {
        TickerIndicatorEngine.filterAndSort(
            items = uiState.quotes,
            mode = uiState.indicatorMode,
            filter = uiState.directionFilter,
            sortField = uiState.sortField,
            sortDescending = uiState.sortDescending,
            searchQuery = uiState.searchQuery
        )
    }

    val summary = remember(uiState.quotes, uiState.indicatorMode) {
        TickerIndicatorEngine.summarize(uiState.quotes, uiState.indicatorMode)
    }

    val updatedTimeText = remember(uiState.lastUpdatedMs) {
        if (uiState.lastUpdatedMs > 0L) {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(uiState.lastUpdatedMs))
        } else {
            "—"
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ticker_indicators_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TickerIndicatorsTopBar(
                feedLabel = uiState.feedLabel,
                isLiveApi = uiState.isLiveApi,
                isRefreshing = uiState.isRefreshing,
                updatedTimeText = updatedTimeText,
                autoRefreshEnabled = uiState.autoRefreshEnabled,
                onBack = onBack,
                onToggleAutoRefresh = onToggleAutoRefresh,
                onRefresh = onRefresh
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing && uiState.quotes.isNotEmpty(),
                onRefresh = onRefresh,
                state = pullRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = uiState.isRefreshing && uiState.quotes.isNotEmpty(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("ticker_indicators_pull_refresh")
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ticker_indicators_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Live Indicator Summary Card
                    item(key = "summary_header") {
                        TickerSummaryBanner(
                            total = summary.total,
                            bullish = summary.bullishCount,
                            bearish = summary.bearishCount,
                            extremes = summary.extremeCount,
                            avgRsi = summary.avgRsi,
                            warningMessage = uiState.warningMessage
                        )
                    }

                    // 2. Add Custom Ticker Input + Indicator Mode Switcher
                    item(key = "controls_card") {
                        TickerControlsPanel(
                            tickerInput = uiState.tickerInput,
                            searchQuery = uiState.searchQuery,
                            indicatorMode = uiState.indicatorMode,
                            directionFilter = uiState.directionFilter,
                            onTickerInputChange = onTickerInputChange,
                            onAddTickers = onAddTickers,
                            onSearchQueryChange = onSearchQueryChange,
                            onSelectIndicatorMode = onSelectIndicatorMode,
                            onSelectDirectionFilter = onSelectDirectionFilter
                        )
                    }

                    // 3. Sortable 3-Column Table Header: TICKER | PRICE | SIGNAL
                    item(key = "table_header") {
                        TickerTableHeader(
                            sortField = uiState.sortField,
                            sortDescending = uiState.sortDescending,
                            indicatorMode = uiState.indicatorMode,
                            onSelectSort = onSelectSort
                        )
                    }

                    // 4. Feed States (Loading / Error / Empty / Live Rows)
                    when (val status = uiState.status) {
                        TickerFeedStatus.Loading -> {
                            item(key = "loading_state") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.ticker_loading_label),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        is TickerFeedStatus.Error -> {
                            item(key = "error_state") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.ticker_error_title),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = status.message,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = onRefresh,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("ticker_retry_api_button")
                                        ) {
                                            Text(text = stringResource(R.string.ticker_action_retry))
                                        }
                                    }
                                }
                            }
                        }

                        TickerFeedStatus.Ready -> {
                            if (filteredQuotes.isEmpty()) {
                                item(key = "empty_state") {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.QueryStats,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.ticker_empty_title),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(R.string.ticker_empty_subtitle),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = filteredQuotes,
                                    key = { it.symbol }
                                ) { quote ->
                                    TickerSignalTableRow(
                                        quote = quote,
                                        mode = uiState.indicatorMode,
                                        expanded = uiState.expandedSymbol == quote.symbol,
                                        canOpenFullAnalysis = knownAnalysisSymbols.contains(quote.symbol),
                                        canRemove = uiState.symbols.size > 1,
                                        onToggleExpand = { onToggleExpandRow(quote.symbol) },
                                        onOpenFullAnalysis = { onOpenFullAnalysis(quote.symbol) },
                                        onRemove = { onRemoveTicker(quote.symbol) }
                                    )
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TickerIndicatorsTopBar(
    feedLabel: String,
    isLiveApi: Boolean,
    isRefreshing: Boolean,
    updatedTimeText: String,
    autoRefreshEnabled: Boolean,
    onBack: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_feed_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("ticker_screen_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.ticker_back_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ticker_screen_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val dotColor = if (isLiveApi && autoRefreshEnabled) {
                        MaterialTheme.colorScheme.secondary
                    } else if (isLiveApi) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(if (autoRefreshEnabled) pulseAlpha else 0.7f)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = "$feedLabel · $updatedTimeText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        if (autoRefreshEnabled) R.string.ticker_auto_live else R.string.ticker_auto_paused
                    ),
                    color = if (autoRefreshEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                )
                Switch(
                    checked = autoRefreshEnabled,
                    onCheckedChange = onToggleAutoRefresh,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("ticker_auto_refresh_switch")
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("ticker_manual_refresh_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.ticker_refresh_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TickerSummaryBanner(
    total: Int,
    bullish: Int,
    bearish: Int,
    extremes: Int,
    avgRsi: Double,
    warningMessage: String?
) {
    val extra = MarketScanTheme.extraColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(extra.heroGradStart, extra.heroGradEnd)
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.ticker_screen_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SummaryStatPill(
                label = stringResource(R.string.ticker_metric_tracked),
                value = total.toString(),
                accent = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = stringResource(R.string.ticker_metric_bullish),
                value = bullish.toString(),
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = stringResource(R.string.ticker_metric_bearish),
                value = bearish.toString(),
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = stringResource(R.string.ticker_metric_extremes),
                value = extremes.toString(),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = stringResource(R.string.ticker_metric_avg_rsi),
                value = String.format(Locale.US, "%.0f", avgRsi),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        if (!warningMessage.isNullOrBlank()) {
            Text(
                text = warningMessage,
                color = extra.bannerWarnText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
            )
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = accent,
            style = MonoTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
            maxLines = 1
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TickerControlsPanel(
    tickerInput: String,
    searchQuery: String,
    indicatorMode: IndicatorMode,
    directionFilter: TickerDirectionFilter,
    onTickerInputChange: (String) -> Unit,
    onAddTickers: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectIndicatorMode: (IndicatorMode) -> Unit,
    onSelectDirectionFilter: (TickerDirectionFilter) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add custom ticker via API
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = tickerInput,
                onValueChange = onTickerInputChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.ticker_add_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onAddTickers() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = MonoTextStyle.copy(
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ticker_add_input")
            )

            Button(
                onClick = onAddTickers,
                enabled = tickerInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("ticker_add_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.ticker_add_action),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // Indicator Mode Segmented Selector (controls the 'Signal' column indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IndicatorMode.entries.forEach { mode ->
                val selected = mode == indicatorMode
                val tag = when (mode) {
                    IndicatorMode.RSI_14 -> "indicator_mode_rsi"
                    IndicatorMode.MA_CROSS -> "indicator_mode_ma_cross"
                    IndicatorMode.COMPOSITE -> "indicator_mode_composite"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelectIndicatorMode(mode) }
                        .padding(vertical = 8.dp)
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.shortLabel,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Direction Filter Chips + Compact Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(TickerDirectionFilter.entries) { filter ->
                    val selected = filter == directionFilter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectDirectionFilter(filter) }
                            .testTag("ticker_filter_${filter.name.lowercase(Locale.US)}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ticker_clear_search_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.ticker_search_placeholder),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ticker_search_input")
        )
    }
}

@Composable
private fun TickerTableHeader(
    sortField: TickerSortField,
    sortDescending: Boolean,
    indicatorMode: IndicatorMode,
    onSelectSort: (TickerSortField) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortableHeaderCell(
                label = stringResource(R.string.ticker_col_ticker),
                active = sortField == TickerSortField.SYMBOL,
                descending = sortDescending,
                alignment = Alignment.CenterStart,
                onClick = { onSelectSort(TickerSortField.SYMBOL) },
                modifier = Modifier
                    .weight(0.34f)
                    .testTag("ticker_column_header")
            )
            SortableHeaderCell(
                label = stringResource(R.string.ticker_col_price),
                active = sortField == TickerSortField.PRICE || sortField == TickerSortField.CHANGE_PCT,
                descending = sortDescending,
                alignment = Alignment.CenterEnd,
                onClick = {
                    val nextField = if (sortField == TickerSortField.PRICE) {
                        TickerSortField.CHANGE_PCT
                    } else {
                        TickerSortField.PRICE
                    }
                    onSelectSort(nextField)
                },
                modifier = Modifier
                    .weight(0.28f)
                    .testTag("price_column_header")
            )
            SortableHeaderCell(
                label = "${stringResource(R.string.ticker_col_signal)} (${indicatorMode.shortLabel})",
                active = sortField == TickerSortField.SIGNAL || sortField == TickerSortField.RSI,
                descending = sortDescending,
                alignment = Alignment.CenterEnd,
                onClick = {
                    val target = if (indicatorMode == IndicatorMode.RSI_14 && sortField == TickerSortField.SIGNAL) {
                        TickerSortField.RSI
                    } else {
                        TickerSortField.SIGNAL
                    }
                    onSelectSort(target)
                },
                modifier = Modifier
                    .weight(0.38f)
                    .testTag("signal_column_header")
            )
        }
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    active: Boolean,
    descending: Boolean,
    alignment: Alignment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (active) {
                Icon(
                    imageVector = if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
