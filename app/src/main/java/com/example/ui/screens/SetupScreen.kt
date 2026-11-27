package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AppConfig
import com.example.domain.model.MarketMode
import com.example.domain.model.RadarModel
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThresholdAlertEvent
import com.example.domain.model.ThresholdAlertSettings
import com.example.ui.components.AiModelHubSummaryCard
import com.example.ui.components.BannerMessage
import com.example.ui.components.GlobalThemeSettingsCard
import com.example.ui.components.MarketModeSwapBar
import com.example.ui.components.SectionLabel
import com.example.ui.components.SetupReadinessChecklistCard
import com.example.ui.components.TagChip
import com.example.ui.components.TerminalCard
import com.example.ui.components.ThresholdAlertSettingsCard
import com.example.ui.theme.MonoTextStyle

private val POPULAR_SUGGESTIONS = listOf("NVDA", "AAPL", "MSFT", "TSLA", "SPY", "QQQ", "AMD", "PLTR")
private val POPULAR_CRYPTO_SUGGESTIONS = listOf("BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "LINK/USD", "AVAX/USD", "DOGE/USD", "ADA/USD")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    config: AppConfig,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onAddWatchlistTickers: (String) -> Unit,
    onRemoveWatchlistTicker: (String) -> Unit,
    onSaveAndRescan: (AppConfig) -> Unit,
    radarModels: List<RadarModel> = emptyList(),
    onToggleAiProvider: (AiProviderId, Boolean) -> Unit = { _, _ -> },
    onSelectActiveAiProvider: (AiProviderId) -> Unit = {},
    onOpenAiModelHub: () -> Unit = {},
    currentRegime: RegimeLabel = RegimeLabel.NEUTRAL,
    hasNotificationPermission: Boolean = true,
    recentThresholdAlerts: List<ThresholdAlertEvent> = emptyList(),
    onUpdateThresholdAlerts: (ThresholdAlertSettings) -> Unit = {},
    onTestThresholdAlert: () -> Unit = {},
    onClearThresholdAlerts: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    stockEquity: Double = 10.0,
    cryptoEquity: Double = 10.0,
    onSelectMarketMode: (MarketMode) -> Unit = {},
    finnhubConfigured: Boolean = false,
    advisorEnabled: Boolean = false,
    onShowDisclaimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var alpacaId by remember { mutableStateOf(config.alpacaId) }
    var alpacaSecret by remember { mutableStateOf(config.alpacaSecret) }
    var geminiKey by remember { mutableStateOf(config.geminiKey) }
    var finnhubKey by remember { mutableStateOf(config.finnhubKey) }
    var cryptoPanicToken by remember { mutableStateOf(config.cryptoPanicToken) }
    var demo by remember { mutableStateOf(config.demo) }
    var accountStr by remember { mutableStateOf(config.account.toString()) }
    var riskStr by remember { mutableStateOf(config.riskPct.toString()) }
    var newTickerInput by remember { mutableStateOf("") }

    var showSecret by remember { mutableStateOf(false) }
    var showGemini by remember { mutableStateOf(false) }
    var showFinnhub by remember { mutableStateOf(false) }
    var showCryptoPanic by remember { mutableStateOf(false) }

    val isCrypto = config.marketMode == MarketMode.CRYPTO
    val activeWatchlist = config.activeWatchlist

    LaunchedEffect(config) {
        alpacaId = config.alpacaId
        alpacaSecret = config.alpacaSecret
        geminiKey = config.geminiKey
        finnhubKey = config.finnhubKey
        cryptoPanicToken = config.cryptoPanicToken
        demo = config.demo
        accountStr = config.account.toString()
        riskStr = config.riskPct.toString()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("setup_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "setup_market_mode_bar") {
            MarketModeSwapBar(
                marketMode = config.marketMode,
                stockEquity = stockEquity,
                cryptoEquity = cryptoEquity,
                onSelectMarketMode = onSelectMarketMode,
                testTagPrefix = "setup_market_swap"
            )
        }

        if (isCrypto) {
            item(key = "setup_crypto_public_banner") {
                BannerMessage(
                    text = stringResource(R.string.banner_crypto_public_feed),
                    isWarning = false
                )
            }
        }

        item(key = "setup_readiness_checklist") {
            SetupReadinessChecklistCard(
                config = config,
                finnhubConfigured = finnhubConfigured,
                advisorEnabled = advisorEnabled,
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onShowDisclaimer = onShowDisclaimer
            )
        }

        item(key = "appearance_card") {
            GlobalThemeSettingsCard(
                themeMode = config.themeMode,
                onSelectThemeMode = onSelectThemeMode
            )
        }

        item(key = "ai_model_hub_card") {
            AiModelHubSummaryCard(
                aiRouting = config.aiRouting,
                geminiFallbackKey = config.geminiKey,
                radarModels = radarModels,
                onToggleProvider = onToggleAiProvider,
                onSelectActiveProvider = onSelectActiveAiProvider,
                onOpenModelHub = onOpenAiModelHub
            )
        }

        item(key = "threshold_alert_settings") {
            ThresholdAlertSettingsCard(
                settings = config.thresholdAlerts,
                currentRegime = currentRegime,
                watchlistCount = activeWatchlist.size,
                hasNotificationPermission = hasNotificationPermission,
                recentEvents = recentThresholdAlerts,
                onUpdateSettings = onUpdateThresholdAlerts,
                onTestAlert = onTestThresholdAlert,
                onClearEvents = onClearThresholdAlerts,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }

        item(key = "config_form") {
            TerminalCard {
                FieldLabel(text = stringResource(R.string.label_alpaca_id))
                TerminalTextField(
                    value = alpacaId,
                    onValueChange = { alpacaId = it },
                    placeholder = "PK...",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.testTag("alpaca_id_input")
                )

                FieldLabel(text = stringResource(R.string.label_alpaca_secret))
                TerminalTextField(
                    value = alpacaSecret,
                    onValueChange = { alpacaSecret = it },
                    placeholder = "secret",
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showSecret) "Hide Alpaca secret" else "Show Alpaca secret",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.testTag("alpaca_secret_input")
                )

                FieldLabel(text = stringResource(R.string.label_gemini_key))
                TerminalTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    placeholder = "AIza...",
                    visualTransformation = if (showGemini) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGemini = !showGemini }) {
                            Icon(
                                imageVector = if (showGemini) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showGemini) "Hide Gemini key" else "Show Gemini key",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.testTag("gemini_key_input")
                )

                FieldLabel(text = stringResource(R.string.label_finnhub_key))
                TerminalTextField(
                    value = finnhubKey,
                    onValueChange = { finnhubKey = it },
                    placeholder = "finnhub.io API key",
                    visualTransformation = if (showFinnhub) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showFinnhub = !showFinnhub }) {
                            Icon(
                                imageVector = if (showFinnhub) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showFinnhub) {
                                    stringResource(R.string.finnhub_settings_hide_desc)
                                } else {
                                    stringResource(R.string.finnhub_settings_show_desc)
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.testTag("finnhub_key_input")
                )

                FieldLabel(text = stringResource(R.string.label_cryptopanic_token))
                TerminalTextField(
                    value = cryptoPanicToken,
                    onValueChange = { cryptoPanicToken = it },
                    placeholder = "cryptopanic.com auth token (optional)",
                    visualTransformation = if (showCryptoPanic) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCryptoPanic = !showCryptoPanic }) {
                            Icon(
                                imageVector = if (showCryptoPanic) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCryptoPanic) {
                                    stringResource(R.string.cryptopanic_token_hide_desc)
                                } else {
                                    stringResource(R.string.cryptopanic_token_show_desc)
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.testTag("cryptopanic_token_input")
                )

                FieldLabel(text = stringResource(R.string.label_data_mode))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                        .padding(3.dp)
                        .testTag("data_mode_selector"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModeTogglePill(
                        text = stringResource(R.string.mode_demo),
                        selected = demo,
                        onClick = { demo = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_demo_option")
                    )
                    ModeTogglePill(
                        text = stringResource(R.string.mode_live),
                        selected = !demo,
                        onClick = { demo = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_live_option")
                    )
                }

                FieldLabel(text = stringResource(R.string.label_paper_account))
                TerminalTextField(
                    value = accountStr,
                    onValueChange = { accountStr = it },
                    placeholder = "10",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("account_size_input")
                )

                FieldLabel(text = stringResource(R.string.label_risk_pct))
                TerminalTextField(
                    value = riskStr,
                    onValueChange = { riskStr = it },
                    placeholder = "1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("risk_pct_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Inline Watchlist Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCrypto) "CRYPTO WATCHLIST (INLINE EDIT)" else "STOCK WATCHLIST (INLINE EDIT)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        )
                    )
                    TagChip(
                        text = "${activeWatchlist.size} STARRED",
                        fg = if (activeWatchlist.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        bg = if (activeWatchlist.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeWatchlist.isEmpty()) {
                    Text(
                        text = if (isCrypto) {
                            "No crypto pairs starred yet. Tap ★ on any pair or add below (e.g. BTC/USD)."
                        } else {
                            "No symbols starred yet. Tap ★ on any signal or add a ticker below."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_watchlist_chips"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeWatchlist.forEach { sym ->
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .clickable { onRemoveWatchlistTicker(sym) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("setup_watchlist_chip_$sym"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = sym,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove $sym from watchlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val submitInlineAdd = {
                    val trimmed = newTickerInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddWatchlistTickers(trimmed)
                        newTickerInput = ""
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTickerInput,
                        onValueChange = { newTickerInput = it.uppercase() },
                        placeholder = {
                            Text(
                                text = if (isCrypto) "Add pair (e.g. BTC/USD, SOL/USD)"
                                else "Add symbol (e.g. NVDA, PLTR)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitInlineAdd() }),
                        shape = RoundedCornerShape(11.dp),
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
                            .testTag("watchlist_input")
                    )

                    Button(
                        onClick = submitInlineAdd,
                        enabled = newTickerInput.trim().isNotEmpty(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("setup_watchlist_add_button"),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add ticker to watchlist",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "ADD",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }

                val availableSuggestions = remember(activeWatchlist, isCrypto) {
                    val existing = activeWatchlist.map { it.uppercase() }.toSet()
                    val pool = if (isCrypto) POPULAR_CRYPTO_SUGGESTIONS else POPULAR_SUGGESTIONS
                    pool.filterNot { existing.contains(it) }
                }
                if (availableSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSuggestions.take(6).forEach { sym ->
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { onAddWatchlistTickers(sym) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("setup_watchlist_suggest_$sym"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = sym,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val acct = accountStr.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 10.0
                        val rsk = riskStr.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                        val extraTyped = if (isCrypto) {
                            newTickerInput.split(",")
                                .map { CryptoFeedsEngine.normalizeCryptoSymbol(it) }
                                .filter { it.isNotEmpty() }
                        } else {
                            newTickerInput.split(",")
                                .map { it.trim().uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '.' } }
                                .filter { it.isNotEmpty() }
                        }
                        newTickerInput = ""
                        val updatedConfig = if (isCrypto) {
                            config.copy(
                                alpacaId = alpacaId,
                                alpacaSecret = alpacaSecret,
                                geminiKey = geminiKey,
                                finnhubKey = finnhubKey,
                                cryptoPanicToken = cryptoPanicToken,
                                demo = demo,
                                account = acct,
                                riskPct = rsk,
                                cryptoWatchlist = (config.cryptoWatchlist + extraTyped).distinct()
                            )
                        } else {
                            config.copy(
                                alpacaId = alpacaId,
                                alpacaSecret = alpacaSecret,
                                geminiKey = geminiKey,
                                finnhubKey = finnhubKey,
                                cryptoPanicToken = cryptoPanicToken,
                                demo = demo,
                                account = acct,
                                riskPct = rsk,
                                watchlist = (config.watchlist + extraTyped).distinct()
                            )
                        }
                        onSaveAndRescan(updatedConfig)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_config_button"),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_save_rescan),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }

        item(key = "how_to_keys") {
            TerminalCard {
                SectionLabel(text = stringResource(R.string.label_how_to_keys))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                            append("Alpaca")
                        }
                        append(" — app.alpaca.markets → Paper account → API Keys. Free.\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                            append("Gemini")
                        }
                        append(" — aistudio.google.com → Get API key. Free tier is enough.\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                            append("Finnhub")
                        }
                        append(" — finnhub.io → Dashboard → Free API key for live stock financial news.\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                            append("Kraken 24/7 & CryptoPanic")
                        }
                        append(" — Kraken Spot & Futures public market feeds work out-of-the-box with zero API keys. CryptoPanic public feed works automatically; add an optional Auth Token above for higher limits.")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
                )
            }
        }

        item(key = "what_this_is") {
            TerminalCard {
                SectionLabel(text = stringResource(R.string.label_what_this_is))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A scanner that ranks names on six independent factors, then tilts by market regime and catalyst quality. Scores measure agreement between indicators — they are not probabilities. The autotrader is paper-only. Keys are stored in Android Keystore encrypted preferences and never leave your device except to call Alpaca and Google directly.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
                )
            }
        }

        item(key = "pdt_explainer") {
            TerminalCard {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                            append("Why \$10 cannot day trade for real\n")
                        }
                        append("The US pattern-day-trader rule blocks a 4th day trade in 5 sessions under \$25,000 of margin equity. Spread and slippage on a \$10 notional is 1–3% per round trip. Fractional shares cannot hold exchange-side stop orders. This app is for learning the mechanics cheaply, not for promising an edge.")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    )
}

@Composable
private fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ModeTogglePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}
