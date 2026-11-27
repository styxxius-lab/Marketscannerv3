package com.example.domain.model

enum class SignalAction {
    BUY, SELL, HOLD
}

enum class RegimeLabel {
    RISK_ON, NEUTRAL, RISK_OFF, CRISIS
}

enum class SignalFilter {
    ALL, WATCHLIST, BUY, SELL
}

enum class MarketMode(
    val wire: String,
    val label: String,
    val shortLabel: String,
    val badgeLabel: String
) {
    STOCKS("stocks", "Stock Market", "STOCKS", "US EQUITIES"),
    CRYPTO("crypto", "Crypto 24/7", "CRYPTO", "CRYPTO 24/7");

    val opposite: MarketMode
        get() = if (this == STOCKS) CRYPTO else STOCKS

    companion object {
        fun fromWire(raw: String?): MarketMode =
            entries.firstOrNull {
                it.wire.equals(raw?.trim(), ignoreCase = true) ||
                    it.name.equals(raw?.trim(), ignoreCase = true)
            } ?: STOCKS
    }
}

enum class MarketSector(val label: String) {
    TECHNOLOGY("Tech"),
    CONSUMER("Consumer"),
    FINANCIALS("Financials"),
    ENERGY("Energy"),
    HEALTHCARE("Healthcare"),
    INDUSTRIALS("Industrials"),
    INDEX_ETF("Index / ETF"),
    CRYPTO_L1("Layer 1"),
    CRYPTO_DEFI("DeFi / Infra"),
    CRYPTO_PAYMENTS("Payments / Meme"),
    OTHER("Other")
}

enum class SignalSort(val label: String) {
    CONVICTION("CONV"),
    RR_NET("R:R"),
    CONFIDENCE("CONF"),
    CHANGE_PCT("CHG%")
}

enum class MainTab {
    SIGNALS, PORTFOLIO, TRADE, NEWS, SETUP
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light")
}

data class BarSeries(
    val o: List<Double>,
    val h: List<Double>,
    val l: List<Double>,
    val c: List<Double>,
    val v: List<Double>
)

data class SnapshotQuote(
    val price: Double,
    val changePct: Double,
    val spreadBps: Double,
    val dollarVol: Double
)

data class NewsHeadline(
    val sym: String,
    val title: String,
    val summary: String,
    val publishedIso: String,
    val source: String
)

data class ScoredNewsItem(
    val title: String,
    val direction: Double,
    val materiality: Double,
    val rationale: String,
    val decay: Double
)

data class SymbolNewsSummary(
    val direction: Double,
    val materiality: Double,
    val signed: Double,
    val items: List<ScoredNewsItem>
)

enum class CatalystScoreSource(val badgeLabel: String) {
    AI_GEMINI("GEMINI AI"),
    AI_ADVISOR("LLM ADVISOR"),
    FINNHUB_MODEL("QUANT MODEL")
}

enum class NewsArticleFilter(val label: String) {
    ALL("ALL"),
    BULLISH("BULLISH"),
    BEARISH("BEARISH"),
    HIGH_IMPACT("HIGH IMPACT"),
    WATCHLIST("★ WATCH")
}

data class AnnotatedNewsArticle(
    val id: String,
    val symbol: String,
    val headline: String,
    val summary: String,
    val source: String,
    val category: String,
    val url: String,
    val publishedAtMs: Long,
    val catalystScore: Int,
    val direction: Double,
    val materiality: Double,
    val confidence: Double,
    val decayFactor: Double,
    val catalystTag: String,
    val rationale: String,
    val scoreSource: CatalystScoreSource,
    val isFromFinnhub: Boolean = true
)

sealed class FinnhubNewsFeedState {
    data object Idle : FinnhubNewsFeedState()
    data object Loading : FinnhubNewsFeedState()
    data class Success(
        val articles: List<AnnotatedNewsArticle>,
        val fetchedAtMs: Long,
        val finnhubConnected: Boolean,
        val aiScoringLabel: String
    ) : FinnhubNewsFeedState()
    data class Error(val message: String) : FinnhubNewsFeedState()
}

data class CostEstimate(
    val spreadBps: Double,
    val impactBps: Double,
    val roundTripBps: Double,
    val rrNet: Double,
    val viable: Boolean,
    val reason: String
)

data class EventInfo(
    val daysToEarnings: Int? = null,
    val blocked: Boolean = false
)

data class SignalRow(
    val symbol: String,
    val action: SignalAction,
    val score: Double,
    val confidence: Double,
    val price: Double,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val rr: Double,
    val atrPct: Double,
    val relVolume: Double,
    val size: Double,
    val reasons: List<String>,
    val sub: Map<String, Double>,
    val conviction: Double = 0.0,
    val index: Double = 50.0,
    val rank: Int = 0,
    val keyCatalyst: String = "",
    val newsDirection: Double = 0.0,
    val newsMateriality: Double = 0.0,
    val newsItems: List<ScoredNewsItem> = emptyList(),
    val rs20: Double = 0.0,
    val spreadBps: Double = 10.0,
    val adv: Double = 5e7,
    val cost: CostEstimate? = null,
    val event: EventInfo? = null,
    val series: List<Double> = emptyList(),
    val sector: MarketSector = MarketSector.OTHER,
    val strategyTag: String = "MOMENTUM"
)

data class RegimePoint(
    val dayOffset: Int,
    val label: RegimeLabel,
    val breadth: Double,
    val trend: Double,
    val volPct: Double
)

data class MarketRegime(
    val label: RegimeLabel,
    val breadth: Double,
    val longMult: Double,
    val shortMult: Double,
    val note: String,
    val trendScore: Double = 0.0,
    val volPct: Double = 0.5,
    val timeline: List<RegimePoint> = emptyList(),
    val panicGuardActive: Boolean = false,
    val panicDrawdownPct: Double = 0.0,
    val panicCooldownRemaining: Int = 0
)

enum class TrendBucket(val label: String, val shortLabel: String) {
    BEAR("Bearish", "BEAR"),
    MIXED("Mixed", "MIXED"),
    BULL("Bullish", "BULL")
}

enum class VolatilityBucket(val label: String, val rangeLabel: String) {
    EXTREME("Extreme", ">90p"),
    HIGH("High", "80-90p"),
    NORMAL("Normal", "50-80p"),
    CALM("Calm", "<50p")
}

data class RegimeMatrixCell(
    val row: Int,
    val col: Int,
    val volBucket: VolatilityBucket,
    val trendBucket: TrendBucket,
    val regimeLabel: RegimeLabel,
    val title: String,
    val summary: String,
    val healthScore: Int,
    val isCurrent: Boolean,
    val historyCount: Int
)

data class RegimeHeatmapData(
    val cells: List<RegimeMatrixCell>,
    val activeRow: Int,
    val activeCol: Int,
    val overallHealthScore: Int,
    val healthVerdict: String
)

data class ScanRegimeSnapshot(
    val timestampMs: Long,
    val label: RegimeLabel,
    val breadth: Double,
    val source: String = "demo"
)

data class HighImpactNewsEvent(
    val eventKey: String,
    val symbol: String,
    val title: String,
    val direction: Double,
    val materiality: Double,
    val rationale: String
)

enum class ThresholdAlertKind {
    REGIME_SHIFT,
    PANIC_GUARD,
    AI_SCORE_DELTA,
    AI_SCORE_EXTREME,
    ADVISOR_VETO
}

data class ThresholdAlertSettings(
    val regimeAlertsEnabled: Boolean = true,
    val watchedRegimes: Set<RegimeLabel> = setOf(RegimeLabel.RISK_OFF, RegimeLabel.CRISIS),
    val alertOnPanicGuard: Boolean = true,
    val aiNewsAlertsEnabled: Boolean = true,
    val aiNewsScoreDeltaThreshold: Int = 25,
    val aiNewsMinScoreAbsThreshold: Int = 55,
    val alertOnAdvisorVeto: Boolean = true,
    val aiNewsWatchlistOnly: Boolean = false
)

data class ThresholdAlertEvent(
    val id: String,
    val kind: ThresholdAlertKind,
    val title: String,
    val detail: String,
    val symbol: String? = null,
    val regimeLabel: RegimeLabel? = null,
    val scoreValue: Int? = null,
    val scoreDelta: Int? = null,
    val timestampMs: Long
)

enum class AiProviderId(
    val wire: String,
    val displayName: String,
    val baseUrl: String,
    val isPublicCatalog: Boolean,
    val defaultTier: ModelTier,
    val note: String
) {
    OPENROUTER(
        wire = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        isPublicCatalog = true,
        defaultTier = ModelTier.UNKNOWN,
        note = "authoritative pricing in /models"
    ),
    AIHUBMIX(
        wire = "aihubmix",
        displayName = "AIHubMix",
        baseUrl = "https://aihubmix.com/v1",
        isPublicCatalog = false,
        defaultTier = ModelTier.UNKNOWN,
        note = "aggregator; /models may omit pricing"
    ),
    DEEPSEEK(
        wire = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        isPublicCatalog = false,
        defaultTier = ModelTier.PAID,
        note = "pricing on their site; API list omits it"
    ),
    GROQ(
        wire = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        isPublicCatalog = false,
        defaultTier = ModelTier.FREE,
        note = "ultra-fast Llama / Qwen / Mixtral inference"
    ),
    GEMINI(
        wire = "gemini",
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com",
        isPublicCatalog = false,
        defaultTier = ModelTier.FREE,
        note = "direct Gemini news, threat & council scoring"
    );

    companion object {
        fun fromWire(raw: String?): AiProviderId? =
            entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) || it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class ModelTier(val wire: String, val label: String) {
    FREE("free", "FREE"),
    PAID("paid", "PAID"),
    UNKNOWN("unknown", "UNKNOWN")
}

enum class ModelTierBasis(val wire: String, val label: String) {
    DECLARED("declared", "declared"),
    INFERRED("inferred", "~inferred"),
    PROVIDER("provider", "provider"),
    UNKNOWN("unknown", "unknown")
}

enum class ModelTierFilter(val label: String) {
    ALL("ALL"),
    FREE("FREE"),
    PAID("PAID"),
    UNKNOWN("UNKNOWN")
}

data class RadarModel(
    val provider: AiProviderId,
    val id: String,
    val name: String = id,
    val context: Int = 0,
    val priceIn: Double? = null,
    val priceOut: Double? = null,
    val tier: ModelTier = ModelTier.UNKNOWN,
    val basis: ModelTierBasis = ModelTierBasis.UNKNOWN,
    val created: Long = 0L,
    val modality: String = "",
    val note: String = ""
) {
    val key: String get() = "${provider.wire}:$id"
}

data class AiProviderSettings(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val selectedModelId: String = "",
    val tierPreference: ModelTierFilter = ModelTierFilter.FREE
)

data class AiRoutingConfig(
    val activeProvider: AiProviderId = AiProviderId.OPENROUTER,
    val providers: Map<AiProviderId, AiProviderSettings> = defaultProviders()
) {
    fun settingsFor(provider: AiProviderId): AiProviderSettings =
        providers[provider] ?: defaultProviders()[provider] ?: AiProviderSettings()

    val enabledProviders: Set<AiProviderId>
        get() = AiProviderId.entries.filter { settingsFor(it).enabled }.toSet()

    val activeSettings: AiProviderSettings
        get() = settingsFor(activeProvider)

    val isAnyAiEnabled: Boolean
        get() = enabledProviders.isNotEmpty()

    companion object {
        fun defaultProviders(): Map<AiProviderId, AiProviderSettings> = mapOf(
            AiProviderId.OPENROUTER to AiProviderSettings(
                enabled = true,
                selectedModelId = "",
                tierPreference = ModelTierFilter.FREE
            ),
            AiProviderId.AIHUBMIX to AiProviderSettings(
                enabled = true,
                selectedModelId = "",
                tierPreference = ModelTierFilter.FREE
            ),
            AiProviderId.DEEPSEEK to AiProviderSettings(
                enabled = false,
                selectedModelId = "deepseek-chat",
                tierPreference = ModelTierFilter.PAID
            ),
            AiProviderId.GROQ to AiProviderSettings(
                enabled = false,
                selectedModelId = "llama-3.3-70b-versatile",
                tierPreference = ModelTierFilter.FREE
            ),
            AiProviderId.GEMINI to AiProviderSettings(
                enabled = true,
                selectedModelId = "gemini-2.5-flash",
                tierPreference = ModelTierFilter.FREE
            )
        )
    }
}

data class ModelProbeResult(
    val provider: AiProviderId,
    val modelId: String,
    val ok: Boolean,
    val latencyMs: Long,
    val replyPreview: String = "",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val error: String? = null
)

data class AppConfig(
    val alpacaId: String = "",
    val alpacaSecret: String = "",
    val geminiKey: String = "",
    val finnhubKey: String = "",
    val cryptoPanicToken: String = "",
    val marketMode: MarketMode = MarketMode.STOCKS,
    val demo: Boolean = true,
    val account: Double = 10.0,
    val riskPct: Double = 1.0,
    val watchlist: List<String> = emptyList(),
    val cryptoWatchlist: List<String> = listOf("BTC/USD", "ETH/USD", "SOL/USD"),
    val tradeNotificationsEnabled: Boolean = true,
    val newsNotificationsEnabled: Boolean = true,
    val vixAlertEnabled: Boolean = true,
    val vixSpikeThresholdPct: Double = 10.0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val thresholdAlerts: ThresholdAlertSettings = ThresholdAlertSettings(),
    val aiRouting: AiRoutingConfig = AiRoutingConfig()
) {
    val activeWatchlist: List<String>
        get() = if (marketMode == MarketMode.CRYPTO) cryptoWatchlist else watchlist
}

data class CryptoFundingHistoryPoint(
    val tsMs: Long,
    val symbol: String,
    val funding8h: Double,
    val fundingAnnual: Double,
    val openInterest: Double,
    val basisBps: Double
)

data class CryptoDerivsSnapshot(
    val symbol: String,
    val tsIso: String,
    val funding8h: Double,
    val fundingAnnual: Double,
    val openInterest: Double,
    val mark: Double,
    val index: Double,
    val basisBps: Double,
    val volumeQuote: Double = 0.0,
    val crowdingPct: Double? = null,
    val crowdingMult: Double = 1.0,
    val crowdingNote: String = "insufficient history — neutral",
    val historySampleCount: Int = 0
)

data class CryptoPanicPost(
    val title: String,
    val url: String,
    val source: String,
    val publishedIso: String,
    val ageHours: Double,
    val currencies: List<String> = emptyList(),
    val positive: Int = 0,
    val negative: Int = 0,
    val important: Int = 0,
    val score: Double = 0.0,
    val weight: Double = 0.0,
    val kind: String = "news"
)

data class CryptoGuardVerdict(
    val symbol: String,
    val sizeMult: Double = 1.0,
    val newsScore: Double = 0.0,
    val blocked: Boolean = false,
    val notes: List<String> = emptyList(),
    val headlines: List<String> = emptyList(),
    val fundingAnnual: Double? = null,
    val crowdingPct: Double? = null,
    val crowdingNote: String = "",
    val eventCategory: String = "NONE",
    val eventConfidence: Double = 0.0,
    val eventSpan: String = "",
    val eventWhy: String = ""
)

data class TradePosition(
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

data class TradeLogEntry(
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

data class EquityPoint(
    val timestampMs: Long,
    val equity: Double
)

data class BotState(
    val equity: Double = 10.0,
    val cash: Double = 10.0,
    val initialEquity: Double = 10.0,
    val positions: List<TradePosition> = emptyList(),
    val log: List<TradeLogEntry> = emptyList(),
    val equityHistory: List<EquityPoint> = emptyList(),
    val dayKey: String = "",
    val tradesToday: Int = 0,
    val halted: Boolean = false,
    val running: Boolean = false
) {
    val pos: TradePosition? get() = positions.firstOrNull()
}

enum class AlertCondition(val label: String, val symbolText: String) {
    ABOVE("ABOVE ≥", "≥"),
    BELOW("BELOW ≤", "≤")
}

data class PriceAlert(
    val id: String,
    val symbol: String,
    val targetPrice: Double,
    val condition: AlertCondition,
    val createdAtMs: Long,
    val active: Boolean = true,
    val triggeredAtMs: Long? = null,
    val triggeredPrice: Double? = null
)

enum class SignalAlertMode(val label: String, val shortLabel: String) {
    ANY_CHANGE("Any AI Signal Change", "ANY FLIP"),
    BUY_ONLY("Flip to BUY", "→ BUY"),
    SELL_ONLY("Flip to SELL", "→ SELL")
}

data class SignalChangeAlert(
    val id: String,
    val symbol: String,
    val mode: SignalAlertMode = SignalAlertMode.ANY_CHANGE,
    val lastKnownAction: SignalAction? = null,
    val enabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastTriggeredAtMs: Long? = null,
    val lastTransitionLabel: String? = null
)

data class VixSnapshot(
    val current: Double,
    val open: Double,
    val prevClose: Double,
    val intradayLow: Double,
    val intradayHigh: Double,
    val changeFromClosePct: Double,
    val intradaySpikePct: Double,
    val intradaySeries: List<Double> = emptyList(),
    val source: String = "DEMO",
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class VixSpikeAlertEvent(
    val id: String,
    val vixLevel: Double,
    val baselineLevel: Double,
    val spikePct: Double,
    val changeFromClosePct: Double,
    val thresholdPct: Double,
    val source: String,
    val timestampMs: Long
)

enum class ThreatLevel(val label: String) {
    LOW("LOW"),
    ELEVATED("ELEVATED"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL")
}

enum class ThreatAction(val label: String) {
    HOLD("HOLD"),
    TRIM("TRIM 50%"),
    SELL("SELL ALL"),
    EXIT_NOW("EXIT NOW")
}

enum class ThreatCategory(
    val key: String,
    val severity: Double,
    val persistenceDays: Int,
    val structural: Boolean,
    val label: String
) {
    ACCOUNTING_FRAUD("accounting_fraud", 1.00, 120, true, "Accounting fraud / restatement"),
    GUIDANCE_CUT("guidance_cut", 0.90, 20, true, "Guidance cut"),
    REGULATORY_ACTION("regulatory_action", 0.85, 45, true, "Regulatory action"),
    EARNINGS_MISS("earnings_miss", 0.80, 8, false, "Earnings miss"),
    CUSTOMER_LOSS("customer_loss", 0.80, 40, true, "Lost a major customer"),
    COMPETITION("competition", 0.75, 60, true, "Competitive displacement"),
    DILUTION("dilution", 0.70, 15, false, "Equity dilution / raise"),
    SUPPLY_CHAIN("supply_chain", 0.65, 21, false, "Supply chain disruption"),
    LITIGATION("litigation", 0.60, 30, false, "Material litigation"),
    EXECUTIVE_DEPARTURE("executive_departure", 0.55, 10, false, "Executive departure"),
    MACRO("macro", 0.50, 5, false, "Macro / rates shock"),
    SECTOR_ROTATION("sector_rotation", 0.45, 12, false, "Sector rotation"),
    ANALYST_DOWNGRADE("analyst_downgrade", 0.35, 3, false, "Analyst downgrade"),
    NONE("none", 0.00, 0, false, "No identifiable threat");

    companion object {
        fun fromKey(raw: String?): ThreatCategory {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == normalized } ?: NONE
        }
    }
}

data class ThreatDriver(
    val name: String,
    val value: Double,
    val score: Int,
    val text: String
)

data class ThreatQuantResult(
    val score: Double,
    val drivers: List<ThreatDriver>,
    val gapPct: Double,
    val chgPct: Double,
    val volZ: Double,
    val rangeRatio: Double,
    val clv: Double,
    val dd20Pct: Double,
    val atr: Double,
    val rsi: Double,
    val ema20: Double,
    val swingLow20: Double,
    val relWeakPct: Double,
    val volShift: Double
)

data class ThreatNarrativeResult(
    val category: ThreatCategory,
    val materiality: Double,
    val structural: Boolean,
    val companySpecific: Boolean,
    val persistenceDays: Int,
    val confidence: Double,
    val summary: String,
    val severityPrior: Double,
    val degraded: Boolean
)

data class ContagionResult(
    val sectorWide: Boolean,
    val peerDropAvgPct: Double,
    val peersFalling: Int,
    val peerCount: Int
)

data class ThreatPositionContext(
    val qty: Double,
    val entry: Double,
    val initialStop: Double,
    val stop: Double,
    val upnl: Double
)

data class ThreatAssessment(
    val symbol: String,
    val timestampMs: Long,
    val price: Double,
    val action: ThreatAction,
    val verdict: String,
    val trimFraction: Double,
    val threatScore: Int,
    val level: ThreatLevel,
    val confidence: Double,
    val reasoning: List<String>,
    val priceActionScore: Int,
    val narrativeScore: Int,
    val drivers: List<ThreatDriver>,
    val narrative: ThreatNarrativeResult,
    val contagion: ContagionResult?,
    val suggestedStop: Double?,
    val invalidation: String,
    val isLiveClassified: Boolean
)

