package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.CatalystImpactFilter
import com.example.domain.engine.FinnhubCatalystEngine
import com.example.domain.model.AnnotatedNewsArticle
import com.example.domain.model.CatalystScoreSource
import com.example.domain.model.CryptoDerivsSnapshot
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.FinnhubNewsFeedState
import com.example.domain.model.MarketMode
import com.example.domain.model.NewsArticleFilter
import com.example.domain.model.NewsHeadline
import com.example.domain.model.SignalRow
import com.example.ui.MarketCatalystUiState
import com.example.ui.components.BackgroundCatalystMonitorCard
import com.example.ui.components.BannerMessage
import com.example.ui.components.CryptoDerivsGuardCard
import com.example.ui.components.MarketModeSwapBar
import com.example.ui.components.SectionLabel
import com.example.ui.components.TagChip
import com.example.ui.components.TerminalCard
import com.example.ui.components.TickerCatalystPanel
import com.example.ui.theme.MarketScanTheme
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldContainer
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    rows: List<SignalRow>,
    headlines: List<NewsHeadline>,
    source: String,
    watchlist: List<String>,
    newsNotificationsEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onToggleNewsNotifications: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    finnhubConfigured: Boolean = false,
    finnhubNewsState: FinnhubNewsFeedState = FinnhubNewsFeedState.Idle,
    isRefreshingFinnhubNews: Boolean = false,
    newsFilter: NewsArticleFilter = NewsArticleFilter.ALL,
    newsSearchQuery: String = "",
    onSelectNewsFilter: (NewsArticleFilter) -> Unit = {},
    onNewsSearchQueryChange: (String) -> Unit = {},
    onRefreshFinnhubNews: () -> Unit = {},
    catalystUiState: MarketCatalystUiState = MarketCatalystUiState(),
    onCatalystTickerInputChange: (String) -> Unit = {},
    onSubmitCatalystTickers: () -> Unit = {},
    onRefreshCatalystService: () -> Unit = {},
    onSelectCatalystImpactFilter: (CatalystImpactFilter) -> Unit = {},
    onToggleExpandCatalystTicker: (String) -> Unit = {},
    onOpenAiHub: () -> Unit = {},
    onToggleBackgroundMonitor: (Boolean) -> Unit = {},
    onSelectBackgroundMonitorInterval: (Int) -> Unit = {},
    onSelectBackgroundMonitorScoreThreshold: (Int) -> Unit = {},
    onSelectBackgroundMonitorMoveThreshold: (Double) -> Unit = {},
    onToggleBackgroundMonitorWatchlistOnly: (Boolean) -> Unit = {},
    onRunBackgroundCheckNow: () -> Unit = {},
    marketMode: MarketMode = MarketMode.STOCKS,
    stockEquity: Double = 10.0,
    cryptoEquity: Double = 10.0,
    onSelectMarketMode: (MarketMode) -> Unit = {},
    cryptoDerivsBySymbol: Map<String, CryptoDerivsSnapshot> = emptyMap(),
    cryptoGuardsBySymbol: Map<String, CryptoGuardVerdict> = emptyMap(),
    cryptoFundingSampleCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pullState = rememberPullToRefreshState()
    val watchSet = remember(watchlist) { watchlist.map { it.uppercase() }.toSet() }

    val finnhubArticles = remember(finnhubNewsState) {
        (finnhubNewsState as? FinnhubNewsFeedState.Success)?.articles.orEmpty()
    }
    val scannedArticles = remember(rows, headlines) {
        FinnhubCatalystEngine.buildFromScannedHeadlines(rows, headlines)
    }
    val combinedArticles = remember(finnhubArticles, scannedArticles) {
        if (finnhubArticles.isNotEmpty()) finnhubArticles else scannedArticles
    }
    val filteredArticles = remember(combinedArticles, newsFilter, watchlist, newsSearchQuery) {
        FinnhubCatalystEngine.filterArticles(
            articles = combinedArticles,
            filter = newsFilter,
            watchlist = watchlist,
            searchQuery = newsSearchQuery
        )
    }

    val bullishCount = remember(combinedArticles) { combinedArticles.count { it.catalystScore >= 10 } }
    val bearishCount = remember(combinedArticles) { combinedArticles.count { it.catalystScore <= -10 } }
    val highImpactCount = remember(combinedArticles) {
        combinedArticles.count {
            abs(it.catalystScore) >= FinnhubCatalystEngine.HIGH_IMPACT_SCORE_ABS || it.materiality >= 0.65
        }
    }
    val scoringLabel = remember(finnhubNewsState, finnhubArticles) {
        when (finnhubNewsState) {
            is FinnhubNewsFeedState.Success -> finnhubNewsState.aiScoringLabel
            is FinnhubNewsFeedState.Loading -> "SCORING…"
            is FinnhubNewsFeedState.Error -> "ERROR"
            FinnhubNewsFeedState.Idle -> if (finnhubConfigured) "FINNHUB" else "SETUP"
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshingFinnhubNews,
        onRefresh = onRefreshFinnhubNews,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshingFinnhubNews,
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("news_pull_refresh_indicator")
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("news_pull_refresh_box")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("news_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "news_market_mode_bar") {
                MarketModeSwapBar(
                    marketMode = marketMode,
                    stockEquity = stockEquity,
                    cryptoEquity = cryptoEquity,
                    onSelectMarketMode = onSelectMarketMode,
                    testTagPrefix = "news_market_swap"
                )
            }

            if (marketMode == MarketMode.CRYPTO) {
                item(key = "news_crypto_derivs_guard") {
                    CryptoDerivsGuardCard(
                        derivsBySymbol = cryptoDerivsBySymbol,
                        guardsBySymbol = cryptoGuardsBySymbol,
                        fundingSampleCount = cryptoFundingSampleCount
                    )
                }
            }

            // 0. Multi-AI News Catalyst Scorer Panel (runs across all implemented AIs)
            item(key = "ticker_catalyst_service_panel") {
                TickerCatalystPanel(
                    uiState = catalystUiState,
                    onTickerInputChange = onCatalystTickerInputChange,
                    onSubmitTickers = onSubmitCatalystTickers,
                    onRefresh = onRefreshCatalystService,
                    onSelectImpactFilter = onSelectCatalystImpactFilter,
                    onToggleExpandTicker = onToggleExpandCatalystTicker,
                    onOpenAiHub = onOpenAiHub,
                    onOpenUrl = { url ->
                        if (url.startsWith("https://") || url.startsWith("http://")) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }
                )
            }

            // 0b. Background WorkManager Catalyst & Market Movement Alert Worker
            item(key = "background_catalyst_monitor_card") {
                BackgroundCatalystMonitorCard(
                    config = catalystUiState.monitorConfig,
                    status = catalystUiState.monitorStatus,
                    isRunningNow = catalystUiState.isBackgroundCheckRunning,
                    hasNotificationPermission = hasNotificationPermission,
                    onToggleEnabled = onToggleBackgroundMonitor,
                    onSelectIntervalMinutes = onSelectBackgroundMonitorInterval,
                    onSelectMinCatalystScore = onSelectBackgroundMonitorScoreThreshold,
                    onSelectMinMovePct = onSelectBackgroundMonitorMoveThreshold,
                    onToggleWatchlistOnly = onToggleBackgroundMonitorWatchlistOnly,
                    onRunCheckNow = onRunBackgroundCheckNow,
                    onRequestNotificationPermission = onRequestNotificationPermission
                )
            }

            // 1. Finnhub AI Catalyst Feed Overview Card
            item(key = "finnhub_overview_card") {
                TerminalCard(modifier = Modifier.testTag("finnhub_feed_header_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                SectionLabel(
                                    text = if (marketMode == MarketMode.CRYPTO) {
                                        stringResource(R.string.crypto_news_feed_title)
                                    } else {
                                        stringResource(R.string.finnhub_feed_title)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isCrypto = marketMode == MarketMode.CRYPTO
                                val liveConnected = (finnhubNewsState as? FinnhubNewsFeedState.Success)?.finnhubConnected == true
                                TagChip(
                                    text = when {
                                        isCrypto -> stringResource(R.string.crypto_news_status_live)
                                        liveConnected -> stringResource(R.string.finnhub_status_live)
                                        else -> stringResource(R.string.finnhub_status_key_needed)
                                    },
                                    fg = when {
                                        isCrypto -> MaterialTheme.colorScheme.tertiary
                                        liveConnected -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    },
                                    bg = when {
                                        isCrypto -> MaterialTheme.colorScheme.tertiaryContainer
                                        liveConnected -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                )
                                TagChip(
                                    text = scoringLabel,
                                    fg = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefreshFinnhubNews,
                            enabled = !isRefreshingFinnhubNews,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("finnhub_refresh_button")
                        ) {
                            if (isRefreshingFinnhubNews) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.finnhub_refresh_desc),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metrics summary row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NewsMetricTile(
                            label = stringResource(R.string.finnhub_metric_articles),
                            value = combinedArticles.size.toString(),
                            valueColor = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        NewsMetricTile(
                            label = stringResource(R.string.finnhub_metric_bull),
                            value = bullishCount.toString(),
                            valueColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        NewsMetricTile(
                            label = stringResource(R.string.finnhub_metric_bear),
                            value = bearishCount.toString(),
                            valueColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        NewsMetricTile(
                            label = stringResource(R.string.finnhub_metric_high),
                            value = highImpactCount.toString(),
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (marketMode == MarketMode.CRYPTO) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.crypto_news_setup_note),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                            )
                        }
                    } else if (!finnhubConfigured) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.finnhub_setup_prompt),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                            )
                        }
                    }
                }
            }

            if (marketMode == MarketMode.STOCKS && source == "demo" && finnhubArticles.isEmpty()) {
                item(key = "demo_news_banner") {
                    BannerMessage(
                        text = stringResource(R.string.banner_demo_news),
                        isWarning = true
                    )
                }
            }

            // 2. Filter Chips & Search Bar
            item(key = "news_filters_and_search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("news_filter_row")
                    ) {
                        items(NewsArticleFilter.entries, key = { it.name }) { filterOption ->
                            val selected = newsFilter == filterOption
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectNewsFilter(filterOption) },
                                label = {
                                    Text(
                                        text = filterOption.label,
                                        style = MonoTextStyle.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("news_filter_${filterOption.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newsSearchQuery,
                        onValueChange = onNewsSearchQueryChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.finnhub_search_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (newsSearchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onNewsSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear news filter",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("news_search_input")
                    )
                }
            }

            // 3. Loading or Error Banner for Finnhub API
            if (finnhubNewsState is FinnhubNewsFeedState.Loading && combinedArticles.isEmpty()) {
                item(key = "finnhub_loading_card") {
                    TerminalCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.finnhub_loading_text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else if (finnhubNewsState is FinnhubNewsFeedState.Error) {
                item(key = "finnhub_error_card") {
                    TerminalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = finnhubNewsState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onRefreshFinnhubNews,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("finnhub_retry_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.finnhub_retry_action),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Annotated Financial News Article List
            if (filteredArticles.isEmpty()) {
                item(key = "empty_filtered_articles") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Newspaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = if (combinedArticles.isEmpty()) {
                                stringResource(R.string.empty_news_catalysts)
                            } else {
                                stringResource(R.string.finnhub_empty_filtered_title)
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center
                        )
                        if (combinedArticles.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.finnhub_empty_filtered_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                if (finnhubArticles.isEmpty()) {
                    item(key = "scanned_fallback_label") {
                        SectionLabel(
                            text = stringResource(R.string.finnhub_section_scanned_fallback),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                items(filteredArticles, key = { it.id }) { article ->
                    val isWatchlisted = watchSet.contains(article.symbol.uppercase())
                    AnnotatedNewsArticleCard(
                        article = article,
                        isWatchlisted = isWatchlisted,
                        onOpenUrl = { url ->
                            if (url.startsWith("https://") || url.startsWith("http://")) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        }
                    )
                }
            }

            // 5. Watchlist AI Catalyst Push Alerts Toggle Card
            item(key = "watchlist_news_alerts") {
                TerminalCard(modifier = Modifier.testTag("news_alerts_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (newsNotificationsEnabled) Icons.Outlined.NotificationsActive
                            else Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = if (newsNotificationsEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watchlist AI Catalyst Alerts",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (watchSet.isEmpty()) {
                                    "Star symbols in Signals or Setup to get notified on ≥65% materiality AI catalysts."
                                } else {
                                    "Monitoring ${watchSet.size} watchlisted symbol(s) for ≥65% materiality AI catalysts."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = newsNotificationsEnabled,
                            onCheckedChange = onToggleNewsNotifications,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("news_notifications_switch")
                        )
                    }

                    if (newsNotificationsEnabled && !hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Android notification permission is currently off.",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("news_enable_notifications_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "ALLOW",
                                    color = MaterialTheme.colorScheme.tertiary,
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

            // 6. Exponential Decay Note
            item(key = "decay_note") {
                TerminalCard {
                    Text(
                        text = stringResource(R.string.news_decay_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NewsMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MarketScanTheme.extraColors.metricTileBg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnnotatedNewsArticleCard(
    article: AnnotatedNewsArticle,
    isWatchlisted: Boolean,
    onOpenUrl: (String) -> Unit
) {
    val score = article.catalystScore
    val (scoreFg, scoreBg) = when {
        score >= 10 -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        score <= -10 -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> SignalHold to SignalHoldContainer
    }
    val scoreText = when {
        score > 0 -> "+$score"
        else -> "$score"
    }
    val hasValidUrl = article.url.startsWith("https://") || article.url.startsWith("http://")
    val ageLabel = remember(article.publishedAtMs) { formatRelativeAge(article.publishedAtMs) }

    TerminalCard(
        modifier = Modifier.testTag("news_article_card_${article.symbol}"),
        onClick = if (hasValidUrl) ({ onOpenUrl(article.url) }) else null
    ) {
        // Top meta row: Symbol + Tag + Watchlist star | AI Catalyst Score pill
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
                TagChip(
                    text = if (isWatchlisted) "★ ${article.symbol}" else article.symbol,
                    fg = if (isWatchlisted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
                    bg = if (isWatchlisted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
                TagChip(
                    text = article.catalystTag,
                    fg = MaterialTheme.colorScheme.primary,
                    bg = MaterialTheme.colorScheme.primaryContainer
                )
                if (article.isFromFinnhub) {
                    TagChip(
                        text = "FINNHUB",
                        fg = MaterialTheme.colorScheme.onSurfaceVariant,
                        bg = MaterialTheme.colorScheme.background
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Prominent AI Catalyst Score Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreBg)
                    .border(1.dp, scoreFg.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("article_catalyst_score_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = scoreFg,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = scoreText,
                    color = scoreFg,
                    style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Headline
        Text(
            text = article.headline,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (article.summary.isNotBlank() && !article.summary.equals(article.headline, ignoreCase = true)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bidirectional Catalyst Score Bar (-100..+100)
        CatalystScoreBar(
            score = score,
            materialityPct = (article.materiality * 100.0).roundToInt(),
            decayPct = (article.decayFactor * 100.0).roundToInt(),
            barColor = scoreFg
        )

        Spacer(modifier = Modifier.height(8.dp))

        // AI Rationale Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = scoreFg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = article.rationale,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TagChip(
                text = when (article.scoreSource) {
                    CatalystScoreSource.AI_GEMINI -> "GEMINI"
                    CatalystScoreSource.AI_ADVISOR -> "LLM"
                    CatalystScoreSource.FINNHUB_MODEL -> "QUANT"
                },
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
                bg = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer: Source · Relative Time | Read Article CTA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${article.source} · $ageLabel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasValidUrl) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenUrl(article.url) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("open_article_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.finnhub_read_article),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.finnhub_open_article_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalystScoreBar(
    score: Int,
    materialityPct: Int,
    decayPct: Int,
    barColor: Color
) {
    val magnitudeFraction = (abs(score).coerceIn(0, 100) / 100f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI CATALYST SCORE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = 0.5.sp)
            )
            Text(
                text = "MAT $materialityPct% · DECAY $decayPct%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyle.copy(fontSize = 10.sp)
            )
        }
        // Bidirectional track centered at 0 (-100 left, +100 right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (score < 0 && magnitudeFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(magnitudeFraction)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (score > 0 && magnitudeFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(magnitudeFraction)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
        }
    }
}

private fun formatRelativeAge(publishedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (publishedAtMs <= 0L) return "recent"
    val diffMin = ((nowMs - publishedAtMs) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMin < 1L -> "just now"
        diffMin < 60L -> "${diffMin}m ago"
        diffMin < 24L * 60L -> "${diffMin / 60L}h ago"
        else -> "${diffMin / (24L * 60L)}d ago"
    }
}
