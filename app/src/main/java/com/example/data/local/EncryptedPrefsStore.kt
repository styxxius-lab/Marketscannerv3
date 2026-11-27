package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.engine.BackgroundMonitorConfig
import com.example.domain.engine.BackgroundMonitorRunStatus
import com.example.domain.engine.CatalystImpactLevel
import com.example.domain.engine.CouncilDecision
import com.example.domain.engine.CouncilMemberWeight
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiProviderSettings
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.AlertCondition
import com.example.domain.model.AppConfig
import com.example.domain.model.BotState
import com.example.domain.model.CryptoFundingHistoryPoint
import com.example.domain.model.EquityPoint
import com.example.domain.model.MarketMode
import com.example.domain.model.ModelTierFilter
import com.example.domain.model.PriceAlert
import com.example.domain.model.RegimeLabel
import com.example.domain.model.ScanRegimeSnapshot
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalAlertMode
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThresholdAlertSettings
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradePosition
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.marketDataStore: DataStore<Preferences> by preferencesDataStore(name = "market_scan_prefs")

@JsonClass(generateAdapter = true)
data class BotStateJson(
    val equity: Double = 10.0,
    val cash: Double = 10.0,
    val initialEquity: Double = 10.0,
    val pos: TradePosJson? = null,
    val positions: List<TradePosJson> = emptyList(),
    val log: List<TradeLogJson> = emptyList(),
    val equityHistory: List<EquityPointJson> = emptyList(),
    val dayKey: String = "",
    val tradesToday: Int = 0,
    val halted: Boolean = false,
    val running: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EquityPointJson(
    val timestampMs: Long,
    val equity: Double
)

@JsonClass(generateAdapter = true)
data class TradePosJson(
    val symbol: String,
    val qty: Double,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val openedAtMs: Long,
    val upnl: Double,
    val last: Double,
    val highestPrice: Double = last,
    val initialRisk: Double = 0.0,
    val rMultiple: Double = 0.0,
    val partialTaken: Boolean = false,
    val isTrailing: Boolean = false,
    val sessionAge: Int = 0,
    val llmVerdict: String = "off",
    val entryRegime: String = ""
)

@JsonClass(generateAdapter = true)
data class TradeLogJson(
    val timestampMs: Long,
    val type: String,
    val symbol: String,
    val qty: Double,
    val price: Double,
    val pnl: Double? = null,
    val note: String = "",
    val llmVerdict: String = "off",
    val regime: String = "",
    val rMultiple: Double? = null
)

@JsonClass(generateAdapter = true)
data class PriceAlertJson(
    val id: String,
    val symbol: String,
    val targetPrice: Double,
    val condition: String,
    val createdAtMs: Long,
    val active: Boolean = true,
    val triggeredAtMs: Long? = null,
    val triggeredPrice: Double? = null
)

@JsonClass(generateAdapter = true)
data class PriceAlertsListJson(
    val items: List<PriceAlertJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SignalChangeAlertJson(
    val id: String,
    val symbol: String,
    val mode: String = SignalAlertMode.ANY_CHANGE.name,
    val lastKnownAction: String? = null,
    val enabled: Boolean = true,
    val createdAtMs: Long = 0L,
    val lastTriggeredAtMs: Long? = null,
    val lastTransitionLabel: String? = null
)

@JsonClass(generateAdapter = true)
data class SignalChangeAlertsListJson(
    val items: List<SignalChangeAlertJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ScanRegimeJson(
    val timestampMs: Long,
    val label: String,
    val breadth: Double,
    val source: String = "demo"
)

@JsonClass(generateAdapter = true)
data class ScanRegimeHistoryJson(
    val items: List<ScanRegimeJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CouncilLedgerJson(
    val items: List<CouncilDecision> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CouncilWeightsJson(
    val items: List<CouncilMemberWeight> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CryptoFundingPointJson(
    val timestampMs: Long,
    val symbol: String,
    val funding8h: Double,
    val fundingAnnual: Double,
    val openInterest: Double,
    val mark: Double,
    val index: Double,
    val basisBps: Double
)

@JsonClass(generateAdapter = true)
data class CryptoFundingHistoryJson(
    val items: List<CryptoFundingPointJson> = emptyList()
)

class EncryptedPrefsStore(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val botAdapter = moshi.adapter(BotStateJson::class.java)
    private val alertsAdapter = moshi.adapter(PriceAlertsListJson::class.java)
    private val signalAlertsAdapter = moshi.adapter(SignalChangeAlertsListJson::class.java)
    private val scanRegimeAdapter = moshi.adapter(ScanRegimeHistoryJson::class.java)
    private val councilLedgerAdapter = moshi.adapter(CouncilLedgerJson::class.java)
    private val councilWeightsAdapter = moshi.adapter(CouncilWeightsJson::class.java)
    private val cryptoFundingAdapter = moshi.adapter(CryptoFundingHistoryJson::class.java)

    private object Keys {
        val ALPACA_ID = stringPreferencesKey("enc_alpaca_id")
        val ALPACA_SECRET = stringPreferencesKey("enc_alpaca_secret")
        val GEMINI_KEY = stringPreferencesKey("enc_gemini_key")
        val FINNHUB_KEY = stringPreferencesKey("enc_finnhub_key")
        val CRYPTOPANIC_TOKEN = stringPreferencesKey("enc_cryptopanic_token")
        val MARKET_MODE = stringPreferencesKey("market_mode")
        val DEMO = booleanPreferencesKey("demo")
        val ACCOUNT = doublePreferencesKey("account")
        val RISK = doublePreferencesKey("risk")
        val WATCHLIST = stringPreferencesKey("watchlist")
        val CRYPTO_WATCHLIST = stringPreferencesKey("crypto_watchlist")
        val TRADE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("trade_notifications_enabled")
        val NEWS_NOTIFICATIONS_ENABLED = booleanPreferencesKey("news_notifications_enabled")
        val VIX_ALERT_ENABLED = booleanPreferencesKey("vix_alert_enabled")
        val VIX_SPIKE_THRESHOLD_PCT = doublePreferencesKey("vix_spike_threshold_pct")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BOT_JSON = stringPreferencesKey("bot_state_json")
        val CRYPTO_BOT_JSON = stringPreferencesKey("crypto_bot_state_json")
        val CRYPTO_FUNDING_HISTORY_JSON = stringPreferencesKey("crypto_funding_history_json")
        val ALERTS_JSON = stringPreferencesKey("price_alerts_json")
        val SIGNAL_ALERTS_JSON = stringPreferencesKey("signal_change_alerts_json")
        val SCAN_REGIME_JSON = stringPreferencesKey("scan_regime_history_json")
        val NOTIFIED_NEWS_KEYS = stringPreferencesKey("notified_news_catalyst_keys")
        val REGIME_ALERTS_ENABLED = booleanPreferencesKey("threshold_regime_alerts_enabled")
        val WATCHED_REGIMES = stringPreferencesKey("threshold_watched_regimes")
        val ALERT_ON_PANIC_GUARD = booleanPreferencesKey("threshold_alert_panic_guard")
        val AI_NEWS_ALERTS_ENABLED = booleanPreferencesKey("threshold_ai_news_alerts_enabled")
        val AI_NEWS_DELTA_THRESHOLD = intPreferencesKey("threshold_ai_news_delta")
        val AI_NEWS_EXTREME_THRESHOLD = intPreferencesKey("threshold_ai_news_extreme")
        val ALERT_ON_ADVISOR_VETO = booleanPreferencesKey("threshold_alert_advisor_veto")
        val AI_NEWS_WATCHLIST_ONLY = booleanPreferencesKey("threshold_ai_news_watchlist_only")
        val AI_ACTIVE_PROVIDER = stringPreferencesKey("ai_active_provider")
        val COUNCIL_MEMBERS = stringPreferencesKey("council_members_csv")
        val COUNCIL_LEDGER_JSON = stringPreferencesKey("council_ledger_json")
        val COUNCIL_WEIGHTS_JSON = stringPreferencesKey("council_weights_json")

        val BG_MONITOR_ENABLED = booleanPreferencesKey("bg_catalyst_monitor_enabled")
        val BG_MONITOR_INTERVAL_MIN = intPreferencesKey("bg_catalyst_monitor_interval_min")
        val BG_MONITOR_MIN_SCORE = intPreferencesKey("bg_catalyst_monitor_min_score")
        val BG_MONITOR_MIN_IMPACT = stringPreferencesKey("bg_catalyst_monitor_min_impact")
        val BG_MONITOR_MIN_MOVE_PCT = doublePreferencesKey("bg_catalyst_monitor_min_move_pct")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("simulation_disclaimer_accepted")
        val BG_MONITOR_WATCHLIST_ONLY = booleanPreferencesKey("bg_catalyst_monitor_watchlist_only")
        val BG_MONITOR_NOTIFIED_KEYS = stringPreferencesKey("bg_catalyst_monitor_notified_keys")
        val BG_MONITOR_LAST_RUN_MS = longPreferencesKey("bg_catalyst_monitor_last_run_ms")
        val BG_MONITOR_LAST_CHECKED = intPreferencesKey("bg_catalyst_monitor_last_checked")
        val BG_MONITOR_LAST_ALERTS = intPreferencesKey("bg_catalyst_monitor_last_alerts")
        val BG_MONITOR_LAST_SUMMARY = stringPreferencesKey("bg_catalyst_monitor_last_summary")
        val BG_MONITOR_LAST_SCORER = stringPreferencesKey("bg_catalyst_monitor_last_scorer")
        val BG_MONITOR_LAST_OK = booleanPreferencesKey("bg_catalyst_monitor_last_ok")

        fun aiProviderEnabled(p: AiProviderId) = booleanPreferencesKey("ai_prov_${p.wire}_enabled")
        fun aiProviderModel(p: AiProviderId) = stringPreferencesKey("ai_prov_${p.wire}_model")
        fun aiProviderTier(p: AiProviderId) = stringPreferencesKey("ai_prov_${p.wire}_tier")
        fun aiProviderApiKey(p: AiProviderId) = stringPreferencesKey("enc_ai_prov_${p.wire}_key")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun loadConfig(): AppConfig {
        val prefs = context.marketDataStore.data.first()
        val watchlistRaw = prefs[Keys.WATCHLIST].orEmpty()
        val watchlist = watchlistRaw.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        val watchedRegimes = prefs[Keys.WATCHED_REGIMES]?.let { raw ->
            if (raw.isBlank()) {
                emptySet()
            } else {
                raw.split(",")
                    .mapNotNull { token -> runCatching { RegimeLabel.valueOf(token.trim()) }.getOrNull() }
                    .toSet()
            }
        } ?: setOf(RegimeLabel.RISK_OFF, RegimeLabel.CRISIS)
        val thresholdSettings = ThresholdAlertSettings(
            regimeAlertsEnabled = prefs[Keys.REGIME_ALERTS_ENABLED] ?: true,
            watchedRegimes = watchedRegimes,
            alertOnPanicGuard = prefs[Keys.ALERT_ON_PANIC_GUARD] ?: true,
            aiNewsAlertsEnabled = prefs[Keys.AI_NEWS_ALERTS_ENABLED] ?: true,
            aiNewsScoreDeltaThreshold = (prefs[Keys.AI_NEWS_DELTA_THRESHOLD] ?: 25).coerceIn(10, 80),
            aiNewsMinScoreAbsThreshold = (prefs[Keys.AI_NEWS_EXTREME_THRESHOLD] ?: 55).coerceIn(20, 95),
            alertOnAdvisorVeto = prefs[Keys.ALERT_ON_ADVISOR_VETO] ?: true,
            aiNewsWatchlistOnly = prefs[Keys.AI_NEWS_WATCHLIST_ONLY] ?: false
        )
        val legacyGeminiKey = decrypt(prefs[Keys.GEMINI_KEY])
        val defaults = AiRoutingConfig.defaultProviders()
        val loadedProviders = AiProviderId.entries.associateWith { prov ->
            val def = defaults[prov] ?: AiProviderSettings()
            val storedKey = decrypt(prefs[Keys.aiProviderApiKey(prov)])
            val resolvedKey = if (prov == AiProviderId.GEMINI && storedKey.isBlank()) {
                legacyGeminiKey
            } else {
                storedKey
            }
            val tierPref = prefs[Keys.aiProviderTier(prov)]
                ?.let { raw -> runCatching { ModelTierFilter.valueOf(raw) }.getOrNull() }
                ?: def.tierPreference
            AiProviderSettings(
                enabled = prefs[Keys.aiProviderEnabled(prov)] ?: def.enabled,
                apiKey = resolvedKey,
                selectedModelId = prefs[Keys.aiProviderModel(prov)] ?: def.selectedModelId,
                tierPreference = tierPref
            )
        }
        val activeProv = AiProviderId.fromWire(prefs[Keys.AI_ACTIVE_PROVIDER])
            ?: if (legacyGeminiKey.isNotBlank()) AiProviderId.GEMINI else AiProviderId.OPENROUTER
        val aiRouting = AiRoutingConfig(
            activeProvider = activeProv,
            providers = loadedProviders
        )
        val effectiveGeminiKey = loadedProviders[AiProviderId.GEMINI]?.apiKey.orEmpty().ifBlank { legacyGeminiKey }

        val cryptoWatchlistRaw = prefs[Keys.CRYPTO_WATCHLIST]
        val cryptoWatchlist = if (cryptoWatchlistRaw == null) {
            listOf("BTC/USD", "ETH/USD", "SOL/USD")
        } else {
            cryptoWatchlistRaw.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
        }

        return AppConfig(
            alpacaId = decrypt(prefs[Keys.ALPACA_ID]),
            alpacaSecret = decrypt(prefs[Keys.ALPACA_SECRET]),
            geminiKey = effectiveGeminiKey,
            finnhubKey = decrypt(prefs[Keys.FINNHUB_KEY]),
            cryptoPanicToken = decrypt(prefs[Keys.CRYPTOPANIC_TOKEN]),
            marketMode = MarketMode.fromWire(prefs[Keys.MARKET_MODE]),
            demo = prefs[Keys.DEMO] ?: true,
            account = prefs[Keys.ACCOUNT]?.takeIf { it > 0.0 } ?: 10.0,
            riskPct = prefs[Keys.RISK]?.takeIf { it > 0.0 } ?: 1.0,
            watchlist = watchlist,
            cryptoWatchlist = cryptoWatchlist,
            tradeNotificationsEnabled = prefs[Keys.TRADE_NOTIFICATIONS_ENABLED] ?: true,
            newsNotificationsEnabled = prefs[Keys.NEWS_NOTIFICATIONS_ENABLED] ?: true,
            vixAlertEnabled = prefs[Keys.VIX_ALERT_ENABLED] ?: true,
            vixSpikeThresholdPct = prefs[Keys.VIX_SPIKE_THRESHOLD_PCT]?.takeIf { it in 2.0..100.0 } ?: 10.0,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.DARK,
            thresholdAlerts = thresholdSettings,
            aiRouting = aiRouting
        )
    }

    suspend fun saveConfig(cfg: AppConfig) {
        val syncedGeminiKey = cfg.aiRouting.settingsFor(AiProviderId.GEMINI).apiKey.ifBlank { cfg.geminiKey }
        context.marketDataStore.edit { prefs ->
            prefs[Keys.ALPACA_ID] = encrypt(cfg.alpacaId)
            prefs[Keys.ALPACA_SECRET] = encrypt(cfg.alpacaSecret)
            prefs[Keys.GEMINI_KEY] = encrypt(syncedGeminiKey)
            prefs[Keys.FINNHUB_KEY] = encrypt(cfg.finnhubKey)
            prefs[Keys.CRYPTOPANIC_TOKEN] = encrypt(cfg.cryptoPanicToken)
            prefs[Keys.MARKET_MODE] = cfg.marketMode.wire
            prefs[Keys.DEMO] = cfg.demo
            prefs[Keys.ACCOUNT] = cfg.account
            prefs[Keys.RISK] = cfg.riskPct
            prefs[Keys.WATCHLIST] = cfg.watchlist.joinToString(",")
            prefs[Keys.CRYPTO_WATCHLIST] = cfg.cryptoWatchlist.joinToString(",")
            prefs[Keys.TRADE_NOTIFICATIONS_ENABLED] = cfg.tradeNotificationsEnabled
            prefs[Keys.NEWS_NOTIFICATIONS_ENABLED] = cfg.newsNotificationsEnabled
            prefs[Keys.VIX_ALERT_ENABLED] = cfg.vixAlertEnabled
            prefs[Keys.VIX_SPIKE_THRESHOLD_PCT] = cfg.vixSpikeThresholdPct
            prefs[Keys.THEME_MODE] = cfg.themeMode.name
            val ts = cfg.thresholdAlerts
            prefs[Keys.REGIME_ALERTS_ENABLED] = ts.regimeAlertsEnabled
            prefs[Keys.WATCHED_REGIMES] = ts.watchedRegimes.joinToString(",") { it.name }
            prefs[Keys.ALERT_ON_PANIC_GUARD] = ts.alertOnPanicGuard
            prefs[Keys.AI_NEWS_ALERTS_ENABLED] = ts.aiNewsAlertsEnabled
            prefs[Keys.AI_NEWS_DELTA_THRESHOLD] = ts.aiNewsScoreDeltaThreshold.coerceIn(10, 80)
            prefs[Keys.AI_NEWS_EXTREME_THRESHOLD] = ts.aiNewsMinScoreAbsThreshold.coerceIn(20, 95)
            prefs[Keys.ALERT_ON_ADVISOR_VETO] = ts.alertOnAdvisorVeto
            prefs[Keys.AI_NEWS_WATCHLIST_ONLY] = ts.aiNewsWatchlistOnly
            prefs[Keys.AI_ACTIVE_PROVIDER] = cfg.aiRouting.activeProvider.wire
            AiProviderId.entries.forEach { prov ->
                val ps = cfg.aiRouting.settingsFor(prov)
                val keyToStore = if (prov == AiProviderId.GEMINI) syncedGeminiKey else ps.apiKey
                prefs[Keys.aiProviderEnabled(prov)] = ps.enabled
                prefs[Keys.aiProviderModel(prov)] = ps.selectedModelId.trim()
                prefs[Keys.aiProviderTier(prov)] = ps.tierPreference.name
                prefs[Keys.aiProviderApiKey(prov)] = encrypt(keyToStore.trim())
            }
        }
    }

    suspend fun loadBotState(mode: MarketMode = MarketMode.STOCKS): BotState {
        val prefs = context.marketDataStore.data.first()
        val key = if (mode == MarketMode.CRYPTO) Keys.CRYPTO_BOT_JSON else Keys.BOT_JSON
        val raw = prefs[key] ?: return BotState()
        return try {
            val dto = botAdapter.fromJson(raw) ?: return BotState()
            val loadedPositions = if (dto.positions.isNotEmpty()) {
                dto.positions.map {
                    TradePosition(
                        symbol = it.symbol,
                        qty = it.qty,
                        entry = it.entry,
                        stop = it.stop,
                        target = it.target,
                        openedAtMs = it.openedAtMs,
                        upnl = it.upnl,
                        last = it.last,
                        highestPrice = it.highestPrice,
                        initialRisk = it.initialRisk,
                        rMultiple = it.rMultiple,
                        partialTaken = it.partialTaken,
                        isTrailing = it.isTrailing,
                        sessionAge = it.sessionAge,
                        llmVerdict = it.llmVerdict,
                        entryRegime = it.entryRegime
                    )
                }
            } else {
                listOfNotNull(
                    dto.pos?.let {
                        TradePosition(
                            symbol = it.symbol,
                            qty = it.qty,
                            entry = it.entry,
                            stop = it.stop,
                            target = it.target,
                            openedAtMs = it.openedAtMs,
                            upnl = it.upnl,
                            last = it.last,
                            highestPrice = it.highestPrice,
                            initialRisk = it.initialRisk,
                            rMultiple = it.rMultiple,
                            partialTaken = it.partialTaken,
                            isTrailing = it.isTrailing,
                            sessionAge = it.sessionAge,
                            llmVerdict = it.llmVerdict,
                            entryRegime = it.entryRegime
                        )
                    }
                )
            }
            BotState(
                equity = dto.equity,
                cash = dto.cash,
                initialEquity = dto.initialEquity,
                positions = loadedPositions,
                log = dto.log.map {
                    TradeLogEntry(
                        timestampMs = it.timestampMs,
                        type = it.type,
                        symbol = it.symbol,
                        qty = it.qty,
                        price = it.price,
                        pnl = it.pnl,
                        note = it.note,
                        llmVerdict = it.llmVerdict,
                        regime = it.regime,
                        rMultiple = it.rMultiple
                    )
                },
                equityHistory = dto.equityHistory.map {
                    EquityPoint(it.timestampMs, it.equity)
                },
                dayKey = dto.dayKey,
                tradesToday = dto.tradesToday,
                halted = dto.halted,
                running = dto.running
            )
        } catch (_: Exception) {
            BotState()
        }
    }

    suspend fun saveBotState(state: BotState, mode: MarketMode = MarketMode.STOCKS) {
        val dto = BotStateJson(
            equity = state.equity,
            cash = state.cash,
            initialEquity = state.initialEquity,
            pos = null,
            positions = state.positions.map {
                TradePosJson(
                    symbol = it.symbol,
                    qty = it.qty,
                    entry = it.entry,
                    stop = it.stop,
                    target = it.target,
                    openedAtMs = it.openedAtMs,
                    upnl = it.upnl,
                    last = it.last,
                    highestPrice = it.highestPrice,
                    initialRisk = it.initialRisk,
                    rMultiple = it.rMultiple,
                    partialTaken = it.partialTaken,
                    isTrailing = it.isTrailing,
                    sessionAge = it.sessionAge,
                    llmVerdict = it.llmVerdict,
                    entryRegime = it.entryRegime
                )
            },
            log = state.log.map {
                TradeLogJson(
                    timestampMs = it.timestampMs,
                    type = it.type,
                    symbol = it.symbol,
                    qty = it.qty,
                    price = it.price,
                    pnl = it.pnl,
                    note = it.note,
                    llmVerdict = it.llmVerdict,
                    regime = it.regime,
                    rMultiple = it.rMultiple
                )
            },
            equityHistory = state.equityHistory.map {
                EquityPointJson(it.timestampMs, it.equity)
            },
            dayKey = state.dayKey,
            tradesToday = state.tradesToday,
            halted = state.halted,
            running = state.running
        )
        val json = botAdapter.toJson(dto)
        val key = if (mode == MarketMode.CRYPTO) Keys.CRYPTO_BOT_JSON else Keys.BOT_JSON
        context.marketDataStore.edit { prefs ->
            prefs[key] = json
        }
    }

    suspend fun loadCryptoFundingHistory(): List<CryptoFundingHistoryPoint> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.CRYPTO_FUNDING_HISTORY_JSON] ?: return emptyList()
        return try {
            val dto = cryptoFundingAdapter.fromJson(raw) ?: return emptyList()
            dto.items.map {
                CryptoFundingHistoryPoint(
                    tsMs = it.timestampMs,
                    symbol = it.symbol,
                    funding8h = it.funding8h,
                    fundingAnnual = it.fundingAnnual,
                    openInterest = it.openInterest,
                    basisBps = it.basisBps
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCryptoFundingHistory(points: List<CryptoFundingHistoryPoint>) {
        val dto = CryptoFundingHistoryJson(
            items = points.takeLast(4500).map {
                CryptoFundingPointJson(
                    timestampMs = it.tsMs,
                    symbol = it.symbol,
                    funding8h = it.funding8h,
                    fundingAnnual = it.fundingAnnual,
                    openInterest = it.openInterest,
                    mark = 0.0,
                    index = 0.0,
                    basisBps = it.basisBps
                )
            }
        )
        val json = cryptoFundingAdapter.toJson(dto)
        context.marketDataStore.edit { prefs ->
            prefs[Keys.CRYPTO_FUNDING_HISTORY_JSON] = json
        }
    }

    suspend fun loadAlerts(): List<PriceAlert> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.ALERTS_JSON] ?: return emptyList()
        return try {
            val dto = alertsAdapter.fromJson(raw) ?: return emptyList()
            dto.items.mapNotNull { item ->
                val cond = runCatching { AlertCondition.valueOf(item.condition) }.getOrNull()
                    ?: return@mapNotNull null
                PriceAlert(
                    id = item.id,
                    symbol = item.symbol,
                    targetPrice = item.targetPrice,
                    condition = cond,
                    createdAtMs = item.createdAtMs,
                    active = item.active,
                    triggeredAtMs = item.triggeredAtMs,
                    triggeredPrice = item.triggeredPrice
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAlerts(alerts: List<PriceAlert>) {
        val dto = PriceAlertsListJson(
            items = alerts.map {
                PriceAlertJson(
                    id = it.id,
                    symbol = it.symbol,
                    targetPrice = it.targetPrice,
                    condition = it.condition.name,
                    createdAtMs = it.createdAtMs,
                    active = it.active,
                    triggeredAtMs = it.triggeredAtMs,
                    triggeredPrice = it.triggeredPrice
                )
            }
        )
        val json = alertsAdapter.toJson(dto)
        context.marketDataStore.edit { prefs ->
            prefs[Keys.ALERTS_JSON] = json
        }
    }

    suspend fun loadSignalAlerts(): List<SignalChangeAlert> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.SIGNAL_ALERTS_JSON] ?: return emptyList()
        return try {
            val dto = signalAlertsAdapter.fromJson(raw) ?: return emptyList()
            dto.items.map { item ->
                val mode = runCatching { SignalAlertMode.valueOf(item.mode) }
                    .getOrDefault(SignalAlertMode.ANY_CHANGE)
                val lastAction = item.lastKnownAction?.let {
                    runCatching { SignalAction.valueOf(it) }.getOrNull()
                }
                SignalChangeAlert(
                    id = item.id,
                    symbol = item.symbol,
                    mode = mode,
                    lastKnownAction = lastAction,
                    enabled = item.enabled,
                    createdAtMs = item.createdAtMs,
                    lastTriggeredAtMs = item.lastTriggeredAtMs,
                    lastTransitionLabel = item.lastTransitionLabel
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveSignalAlerts(alerts: List<SignalChangeAlert>) {
        val dto = SignalChangeAlertsListJson(
            items = alerts.map {
                SignalChangeAlertJson(
                    id = it.id,
                    symbol = it.symbol,
                    mode = it.mode.name,
                    lastKnownAction = it.lastKnownAction?.name,
                    enabled = it.enabled,
                    createdAtMs = it.createdAtMs,
                    lastTriggeredAtMs = it.lastTriggeredAtMs,
                    lastTransitionLabel = it.lastTransitionLabel
                )
            }
        )
        val json = signalAlertsAdapter.toJson(dto)
        context.marketDataStore.edit { prefs ->
            prefs[Keys.SIGNAL_ALERTS_JSON] = json
        }
    }

    suspend fun loadDisclaimerAccepted(): Boolean {
        val prefs = context.marketDataStore.data.first()
        return prefs[Keys.DISCLAIMER_ACCEPTED] ?: false
    }

    suspend fun saveDisclaimerAccepted(accepted: Boolean) {
        context.marketDataStore.edit { prefs ->
            prefs[Keys.DISCLAIMER_ACCEPTED] = accepted
        }
    }

    suspend fun loadScanRegimeHistory(): List<ScanRegimeSnapshot> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.SCAN_REGIME_JSON] ?: return emptyList()
        return try {
            val dto = scanRegimeAdapter.fromJson(raw) ?: return emptyList()
            dto.items.mapNotNull { item ->
                val lbl = runCatching { RegimeLabel.valueOf(item.label) }.getOrNull() ?: return@mapNotNull null
                ScanRegimeSnapshot(
                    timestampMs = item.timestampMs,
                    label = lbl,
                    breadth = item.breadth,
                    source = item.source
                )
            }.takeLast(5)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveScanRegimeHistory(items: List<ScanRegimeSnapshot>) {
        val dto = ScanRegimeHistoryJson(
            items = items.takeLast(5).map {
                ScanRegimeJson(
                    timestampMs = it.timestampMs,
                    label = it.label.name,
                    breadth = it.breadth,
                    source = it.source
                )
            }
        )
        val json = scanRegimeAdapter.toJson(dto)
        context.marketDataStore.edit { prefs ->
            prefs[Keys.SCAN_REGIME_JSON] = json
        }
    }

    suspend fun loadNotifiedNewsKeys(): Set<String> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.NOTIFIED_NEWS_KEYS].orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    suspend fun saveNotifiedNewsKeys(keys: Set<String>) {
        val trimmed = keys.toList().takeLast(80).joinToString("\n")
        context.marketDataStore.edit { prefs ->
            prefs[Keys.NOTIFIED_NEWS_KEYS] = trimmed
        }
    }

    suspend fun loadCouncilMembers(): List<String> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.COUNCIL_MEMBERS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.contains(":") }.distinct()
    }

    suspend fun saveCouncilMembers(members: List<String>) {
        val serialized = members.map { it.trim() }.filter { it.contains(":") }.distinct().joinToString(",")
        context.marketDataStore.edit { prefs ->
            prefs[Keys.COUNCIL_MEMBERS] = serialized
        }
    }

    suspend fun loadCouncilLedger(): List<CouncilDecision> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.COUNCIL_LEDGER_JSON] ?: return emptyList()
        return try {
            councilLedgerAdapter.fromJson(raw)?.items.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCouncilLedger(ledger: List<CouncilDecision>) {
        val json = councilLedgerAdapter.toJson(CouncilLedgerJson(items = ledger.take(250)))
        context.marketDataStore.edit { prefs ->
            prefs[Keys.COUNCIL_LEDGER_JSON] = json
        }
    }

    suspend fun loadCouncilWeights(): Map<String, CouncilMemberWeight> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.COUNCIL_WEIGHTS_JSON] ?: return emptyMap()
        return try {
            councilWeightsAdapter.fromJson(raw)?.items.orEmpty().associateBy { it.member }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveCouncilWeights(weights: Map<String, CouncilMemberWeight>) {
        val json = councilWeightsAdapter.toJson(CouncilWeightsJson(items = weights.values.toList()))
        context.marketDataStore.edit { prefs ->
            prefs[Keys.COUNCIL_WEIGHTS_JSON] = json
        }
    }

    suspend fun loadBackgroundMonitorConfig(): BackgroundMonitorConfig {
        val prefs = context.marketDataStore.data.first()
        val impact = prefs[Keys.BG_MONITOR_MIN_IMPACT]?.let { raw ->
            runCatching { CatalystImpactLevel.valueOf(raw) }.getOrNull()
        } ?: CatalystImpactLevel.HIGH
        return BackgroundMonitorConfig(
            enabled = prefs[Keys.BG_MONITOR_ENABLED] ?: true,
            intervalMinutes = (prefs[Keys.BG_MONITOR_INTERVAL_MIN] ?: 15).coerceIn(15, 240),
            minCatalystScoreAbs = (prefs[Keys.BG_MONITOR_MIN_SCORE] ?: 50).coerceIn(20, 95),
            minImpactLevel = impact,
            minPriceMovePct = (prefs[Keys.BG_MONITOR_MIN_MOVE_PCT] ?: 3.0).coerceIn(0.5, 20.0),
            watchlistOnly = prefs[Keys.BG_MONITOR_WATCHLIST_ONLY] ?: false
        )
    }

    suspend fun saveBackgroundMonitorConfig(config: BackgroundMonitorConfig) {
        context.marketDataStore.edit { prefs ->
            prefs[Keys.BG_MONITOR_ENABLED] = config.enabled
            prefs[Keys.BG_MONITOR_INTERVAL_MIN] = config.intervalMinutes.coerceIn(15, 240)
            prefs[Keys.BG_MONITOR_MIN_SCORE] = config.minCatalystScoreAbs.coerceIn(20, 95)
            prefs[Keys.BG_MONITOR_MIN_IMPACT] = config.minImpactLevel.name
            prefs[Keys.BG_MONITOR_MIN_MOVE_PCT] = config.minPriceMovePct.coerceIn(0.5, 20.0)
            prefs[Keys.BG_MONITOR_WATCHLIST_ONLY] = config.watchlistOnly
        }
    }

    suspend fun loadBackgroundMonitorNotifiedKeys(): Set<String> {
        val prefs = context.marketDataStore.data.first()
        val raw = prefs[Keys.BG_MONITOR_NOTIFIED_KEYS].orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    suspend fun saveBackgroundMonitorNotifiedKeys(keys: Set<String>) {
        val trimmed = keys.toList().takeLast(120).joinToString("\n")
        context.marketDataStore.edit { prefs ->
            prefs[Keys.BG_MONITOR_NOTIFIED_KEYS] = trimmed
        }
    }

    suspend fun saveBackgroundMonitorStatus(status: BackgroundMonitorRunStatus) {
        context.marketDataStore.edit { prefs ->
            prefs[Keys.BG_MONITOR_LAST_RUN_MS] = status.lastRunAtMs
            prefs[Keys.BG_MONITOR_LAST_CHECKED] = status.tickersChecked
            prefs[Keys.BG_MONITOR_LAST_ALERTS] = status.alertsTriggered
            prefs[Keys.BG_MONITOR_LAST_SUMMARY] = status.lastSummary
            prefs[Keys.BG_MONITOR_LAST_SCORER] = status.scorerLabel
            prefs[Keys.BG_MONITOR_LAST_OK] = status.succeeded
        }
    }

    fun observeBackgroundMonitorStatus(): Flow<BackgroundMonitorRunStatus> =
        context.marketDataStore.data.map { prefs ->
            BackgroundMonitorRunStatus(
                lastRunAtMs = prefs[Keys.BG_MONITOR_LAST_RUN_MS] ?: 0L,
                tickersChecked = prefs[Keys.BG_MONITOR_LAST_CHECKED] ?: 0,
                alertsTriggered = prefs[Keys.BG_MONITOR_LAST_ALERTS] ?: 0,
                lastSummary = prefs[Keys.BG_MONITOR_LAST_SUMMARY].orEmpty(),
                scorerLabel = prefs[Keys.BG_MONITOR_LAST_SCORER].orEmpty(),
                succeeded = prefs[Keys.BG_MONITOR_LAST_OK] ?: true
            )
        }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "market_scan_api_keys_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
