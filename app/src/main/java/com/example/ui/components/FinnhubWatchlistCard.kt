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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.WatchlistDataStore
import com.example.data.service.WatchlistQuoteSnippet
import com.example.ui.WatchlistUiState
import com.example.ui.theme.MonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DataStore-persisted Favorite Watchlist component integrated with the Finnhub `/quote` API
 * to display real-time quote snippets (live price, dollar/% delta, open/previous close, and
 * intraday low-to-high range bar).
 */
@Composable
fun FinnhubWatchlistCard(
    uiState: WatchlistUiState,
    onTickerInputChange: (String) -> Unit,
    onAddTickers: () -> Unit,
    onQuickAddTicker: (String) -> Unit,
    onRemoveTicker: (String) -> Unit,
    onRefreshQuotes: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onOpenTicker: (String) -> Unit,
    onOpenFinnhubSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val suggestions = remember(uiState.favorites) {
        val currentSet = uiState.favorites.map { it.uppercase(Locale.US) }.toSet()
        WatchlistDataStore.SUGGESTED_TICKERS.filterNot { currentSet.contains(it) }.take(6)
    }

    TerminalCard(
        modifier = modifier.testTag("finnhub_watchlist_card")
    ) {
        // 1. Header: Favorite Watchlist title, Finnhub feed badge, 15s live poll toggle, Refresh button
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
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    SectionLabel(text = stringResource(R.string.watchlist_card_title))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagChip(
                        text = uiState.feedLabel,
                        fg = if (uiState.finnhubConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        bg = if (uiState.finnhubConnected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    )
                    if (uiState.lastUpdatedMs > 0L) {
                        Text(
                            text = timeFormatter.format(Date(uiState.lastUpdatedMs)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyle.copy(fontSize = 10.sp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = uiState.autoRefreshEnabled,
                    onClick = { onToggleAutoRefresh(!uiState.autoRefreshEnabled) },
                    label = {
                        Text(
                            text = if (uiState.autoRefreshEnabled) {
                                stringResource(R.string.watchlist_poll_live)
                            } else {
                                stringResource(R.string.watchlist_poll_paused)
                            },
                            style = MonoTextStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("watchlist_auto_refresh_chip")
                )

                IconButton(
                    onClick = onRefreshQuotes,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("watchlist_refresh_button")
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.watchlist_refresh_desc),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Add Favorite Ticker Input + ADD Button (persisted to DataStore)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.tickerInput,
                onValueChange = onTickerInputChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.watchlist_input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (uiState.tickerInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onTickerInputChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.watchlist_clear_input_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onAddTickers() }),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("watchlist_ticker_input")
            )

            Button(
                onClick = onAddTickers,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("watchlist_add_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.watchlist_action_add),
                    style = MonoTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Quick-add popular ticker chips
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions, key = { it }) { sym ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickAddTicker(sym) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = {
                            Text(
                                text = sym,
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("watchlist_quick_add_$sym")
                    )
                }
            }
        }

        // Finnhub key prompt strip if user hasn't added a Finnhub key yet
        if (!uiState.finnhubConfigured) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.watchlist_finnhub_key_prompt),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onOpenFinnhubSettings,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("watchlist_open_settings_button")
                ) {
                    Text(
                        text = stringResource(R.string.watchlist_action_add_key),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MonoTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Favorite Tickers Quote Snippet List
        if (uiState.favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.watchlist_empty_state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.favorites.forEach { symbol ->
                    WatchlistQuoteSnippetRow(
                        symbol = symbol,
                        snippet = uiState.quotesBySymbol[symbol],
                        isLoading = uiState.isLoading && uiState.quotesBySymbol[symbol] == null,
                        onOpen = { onOpenTicker(symbol) },
                        onRemove = { onRemoveTicker(symbol) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistQuoteSnippetRow(
    symbol: String,
    snippet: WatchlistQuoteSnippet?,
    isLoading: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val isPositive = (snippet?.changeDollar ?: 0.0) >= 0.0
    val deltaColor = if (isPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val deltaBg = if (isPositive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 4.dp)
            .testTag("watchlist_quote_row_$symbol")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Symbol + Open / Prev Close
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = symbol,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    if (snippet != null && snippet.isFinnhubLive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }
                if (snippet != null && snippet.currentPrice > 0.0) {
                    Text(
                        text = "O ${fmtMoney(snippet.dayOpen)} · PC ${fmtMoney(snippet.prevClose)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyle.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (isLoading) {
                            stringResource(R.string.watchlist_fetching_quote)
                        } else {
                            snippet?.errorMessage ?: stringResource(R.string.watchlist_quote_pending)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                    )
                }
            }

            // Right: Live Price + Change Pill + Remove Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (snippet != null && snippet.currentPrice > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = fmtMoney(snippet.currentPrice),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MonoTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.testTag("watchlist_price_$symbol")
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(deltaBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${fmtSignedMoney(snippet.changeDollar)} (${fmtPctSigned(snippet.changePct)})",
                                color = deltaColor,
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                } else if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("watchlist_remove_$symbol")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.watchlist_remove_desc, symbol),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Intraday Low-to-High Day Range Bar
        if (snippet != null && snippet.currentPrice > 0.0 && snippet.dayHigh > snippet.dayLow) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "L ${fmtMoney(snippet.dayLow)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.sp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(snippet.dayRangePosition.coerceIn(0.05f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(deltaColor.copy(alpha = 0.8f))
                    )
                }
                Text(
                    text = "H ${fmtMoney(snippet.dayHigh)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyle.copy(fontSize = 9.sp)
                )
            }
        }
    }
}
