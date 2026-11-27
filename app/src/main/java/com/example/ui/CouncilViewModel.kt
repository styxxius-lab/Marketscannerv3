package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EncryptedPrefsStore
import com.example.data.repository.MarketRepository
import com.example.domain.engine.CouncilDecision
import com.example.domain.engine.CouncilEngine
import com.example.domain.engine.CouncilFeatures
import com.example.domain.engine.CouncilMemberWeight
import com.example.domain.engine.CouncilStats
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AppConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.BotState
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.RadarModel
import com.example.domain.model.RegimeLabel
import com.example.domain.model.SignalRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class CouncilSubTab(val label: String) {
    DECIDE("DECIDE"),
    MEMBERS("MEMBERS"),
    LEDGER("LEDGER"),
    STATS("STATS")
}

enum class CouncilLedgerFilter(val label: String) {
    ALL("ALL"),
    PENDING("PENDING"),
    RESOLVED("RESOLVED")
}

data class CouncilUiState(
    val activeTab: CouncilSubTab = CouncilSubTab.DECIDE,
    val selectedSymbol: String = "AAPL",
    val symbolInput: String = "",
    val members: List<String> = CouncilEngine.DEFAULT_MEMBERS,
    val memberProviderInput: AiProviderId = AiProviderId.OPENROUTER,
    val memberModelInput: String = "",
    val latestFeatures: CouncilFeatures? = null,
    val latestDecision: CouncilDecision? = null,
    val isDeliberating: Boolean = false,
    val isResolving: Boolean = false,
    val ledger: List<CouncilDecision> = emptyList(),
    val ledgerFilter: CouncilLedgerFilter = CouncilLedgerFilter.ALL,
    val weights: Map<String, CouncilMemberWeight> = emptyMap(),
    val stats: CouncilStats = CouncilEngine.computeStats(emptyList(), emptyMap()),
    val statusNotice: String? = null
)

class CouncilViewModel(application: Application) : AndroidViewModel(application) {

    private val store = EncryptedPrefsStore(application.applicationContext)
    private val repository = MarketRepository()
    private val engine = CouncilEngine()

    private val _uiState = MutableStateFlow(CouncilUiState())
    val uiState: StateFlow<CouncilUiState> = _uiState.asStateFlow()

    private var config: AppConfig = AppConfig()
    private var rows: List<SignalRow> = emptyList()
    private var barsBySymbol: Map<String, BarSeries> = emptyMap()
    private var regime: MarketRegime = MarketRegime(
        label = RegimeLabel.NEUTRAL,
        breadth = 0.5,
        longMult = 1.0,
        shortMult = 1.0,
        note = ""
    )
    private var botState: BotState = BotState()
    private var headlines: List<NewsHeadline> = emptyList()
    private var radarModels: List<RadarModel> = emptyList()
    private var initializedFromStore: Boolean = false

    init {
        viewModelScope.launch {
            val savedMembers = withContext(Dispatchers.IO) { store.loadCouncilMembers() }
            val savedLedger = withContext(Dispatchers.IO) { store.loadCouncilLedger() }
            val savedWeights = withContext(Dispatchers.IO) { store.loadCouncilWeights() }
            val resolvedMembers = savedMembers.ifEmpty {
                CouncilEngine.defaultMembersFromEnvironment(
                    routing = config.aiRouting,
                    radarModels = radarModels,
                    fallbackGeminiKey = config.geminiKey
                )
            }
            initializedFromStore = true
            _uiState.update { state ->
                state.copy(
                    members = resolvedMembers,
                    ledger = savedLedger,
                    weights = savedWeights,
                    latestDecision = savedLedger.firstOrNull(),
                    stats = CouncilEngine.computeStats(savedLedger, savedWeights)
                )
            }
        }
    }

    fun syncMarketContext(
        config: AppConfig,
        rows: List<SignalRow>,
        barsBySymbol: Map<String, BarSeries>,
        regime: MarketRegime,
        botState: BotState,
        headlines: List<NewsHeadline>,
        radarModels: List<RadarModel>
    ) {
        this.config = config
        this.rows = rows
        this.barsBySymbol = barsBySymbol
        this.regime = regime
        this.botState = botState
        this.headlines = headlines
        this.radarModels = radarModels

        if (initializedFromStore && _uiState.value.members.isEmpty()) {
            val defaults = CouncilEngine.defaultMembersFromEnvironment(
                routing = config.aiRouting,
                radarModels = radarModels,
                fallbackGeminiKey = config.geminiKey
            )
            _uiState.update { it.copy(members = defaults) }
        }
    }

    fun selectTab(tab: CouncilSubTab) {
        _uiState.update { it.copy(activeTab = tab, statusNotice = null) }
    }

    fun selectSymbol(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isEmpty()) return
        _uiState.update { it.copy(selectedSymbol = clean, statusNotice = null) }
    }

    fun updateSymbolInput(text: String) {
        _uiState.update { it.copy(symbolInput = text.uppercase(Locale.US)) }
    }

    fun updateMemberProvider(provider: AiProviderId) {
        _uiState.update { it.copy(memberProviderInput = provider) }
    }

    fun updateMemberModelInput(model: String) {
        _uiState.update { it.copy(memberModelInput = model) }
    }

    fun selectLedgerFilter(filter: CouncilLedgerFilter) {
        _uiState.update { it.copy(ledgerFilter = filter) }
    }

    fun addMember(provider: AiProviderId = _uiState.value.memberProviderInput, rawModel: String = _uiState.value.memberModelInput) {
        val cleanModel = rawModel.trim()
        if (cleanModel.isEmpty()) {
            _uiState.update { it.copy(statusNotice = "Enter a model ID (e.g. deepseek-chat, glm-5.3, gemini-2.5-flash).") }
            return
        }
        val spec = "${provider.wire}:$cleanModel"
        val next = (_uiState.value.members + spec).distinct()
        _uiState.update {
            it.copy(
                members = next,
                memberModelInput = "",
                statusNotice = "Added $spec (${CouncilEngine.family(cleanModel)} family)"
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.saveCouncilMembers(next) }
    }

    fun removeMember(spec: String) {
        val next = _uiState.value.members.filterNot { it == spec }
        _uiState.update { it.copy(members = next, statusNotice = "Removed $spec") }
        viewModelScope.launch(Dispatchers.IO) { store.saveCouncilMembers(next) }
    }

    fun autoBuildDiversePanel() {
        val picked = LinkedHashSet<String>()
        val seenFamilies = LinkedHashSet<String>()

        // Prefer providers that have keys configured
        AiProviderId.entries.forEach { prov ->
            val key = ModelRadarEngine.resolveApiKey(prov, config.aiRouting, config.geminiKey)
            if (key.isNotBlank()) {
                val settings = config.aiRouting.settingsFor(prov)
                val modelId = settings.selectedModelId.trim().ifBlank {
                    when (prov) {
                        AiProviderId.GEMINI -> "gemini-2.5-flash"
                        AiProviderId.DEEPSEEK -> "deepseek-chat"
                        AiProviderId.GROQ -> "llama-3.3-70b-versatile"
                        AiProviderId.OPENROUTER -> radarModels.firstOrNull { it.provider == AiProviderId.OPENROUTER }?.id
                            ?: "qwen/qwen3-235b-a22b:free"
                        AiProviderId.AIHUBMIX -> radarModels.firstOrNull { it.provider == AiProviderId.AIHUBMIX }?.id
                            ?: "glm-4-flash"
                    }
                }
                val fam = CouncilEngine.family(modelId)
                if (seenFamilies.add(fam)) {
                    picked.add("${prov.wire}:$modelId")
                }
            }
        }

        // If OpenRouter has a key, we can add distinct families from OpenRouter too
        val orKey = ModelRadarEngine.resolveApiKey(AiProviderId.OPENROUTER, config.aiRouting, config.geminiKey)
        if (orKey.isNotBlank() && seenFamilies.size < 3) {
            val candidates = radarModels
                .filter { it.provider == AiProviderId.OPENROUTER }
                .map { it.id }
                .ifEmpty {
                    listOf(
                        "qwen/qwen3-235b-a22b:free",
                        "meta-llama/llama-3.3-70b-instruct:free",
                        "deepseek/deepseek-chat-v3-0324:free"
                    )
                }
            for (m in candidates) {
                if (seenFamilies.size >= 3) break
                val fam = CouncilEngine.family(m)
                if (seenFamilies.add(fam)) {
                    picked.add("openrouter:$m")
                }
            }
        }

        if (picked.isEmpty()) {
            picked.addAll(CouncilEngine.DEFAULT_MEMBERS)
        }

        val next = picked.toList()
        _uiState.update {
            it.copy(
                members = next,
                statusNotice = "Panel rebuilt: ${next.size} members across ${next.map { m -> CouncilEngine.family(m.substringAfter(":")) }.toSet().size} families."
            )
        }
        viewModelScope.launch(Dispatchers.IO) { store.saveCouncilMembers(next) }
    }

    fun deliberate(symbolOverride: String? = null) {
        val rawSym = (symbolOverride ?: _uiState.value.symbolInput.ifBlank { _uiState.value.selectedSymbol })
            .trim()
            .uppercase(Locale.US)
        if (rawSym.isEmpty()) {
            _uiState.update { it.copy(statusNotice = "Enter a valid ticker symbol first.") }
            return
        }
        val activeMembers = _uiState.value.members
        if (activeMembers.isEmpty()) {
            _uiState.update {
                it.copy(
                    activeTab = CouncilSubTab.MEMBERS,
                    statusNotice = "Configure at least 2 members from 2 distinct model families."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSymbol = rawSym,
                    symbolInput = "",
                    activeTab = CouncilSubTab.DECIDE,
                    isDeliberating = true,
                    statusNotice = null
                )
            }
            try {
                val bars = barsBySymbol[rawSym] ?: withContext(Dispatchers.IO) {
                    repository.resolveBarsForSymbol(rawSym, config)
                }
                if (bars == null || bars.c.size < 25) {
                    _uiState.update {
                        it.copy(
                            isDeliberating = false,
                            statusNotice = "$rawSym: not enough daily bars to compute council snapshot."
                        )
                    }
                    return@launch
                }

                val liveRow = rows.firstOrNull { it.symbol.equals(rawSym, ignoreCase = true) }
                val openPos = botState.positions.firstOrNull { it.symbol.equals(rawSym, ignoreCase = true) }
                val features = CouncilEngine.featuresFromBars(
                    symbol = rawSym,
                    bars = bars,
                    regime = regime,
                    livePrice = liveRow?.price,
                    position = openPos,
                    headlines = headlines
                )

                val decision = withContext(Dispatchers.IO) {
                    engine.deliberate(
                        members = activeMembers,
                        features = features,
                        routing = config.aiRouting,
                        fallbackGeminiKey = config.geminiKey,
                        weights = _uiState.value.weights
                    )
                }

                val nextLedger = (listOf(decision) + _uiState.value.ledger).take(250)
                val nextStats = CouncilEngine.computeStats(nextLedger, _uiState.value.weights)

                _uiState.update {
                    it.copy(
                        latestFeatures = features,
                        latestDecision = decision,
                        ledger = nextLedger,
                        stats = nextStats,
                        isDeliberating = false,
                        statusNotice = decision.brief()
                    )
                }
                withContext(Dispatchers.IO) { store.saveCouncilLedger(nextLedger) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeliberating = false,
                        statusNotice = "Council failed: ${e.message ?: "unexpected error"}"
                    )
                }
            }
        }
    }

    fun resolveLedgerOutcomes() {
        val currentLedger = _uiState.value.ledger
        val pendingSymbols = currentLedger
            .filter { it.outcome == null }
            .map { it.symbol.uppercase(Locale.US) }
            .distinct()

        if (pendingSymbols.isEmpty()) {
            _uiState.update { it.copy(statusNotice = "Nothing to resolve — every logged decision already has an outcome.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, statusNotice = null) }
            try {
                val priceMap = LinkedHashMap<String, Double>()
                rows.forEach { priceMap[it.symbol.uppercase(Locale.US)] = it.price }

                val missing = pendingSymbols.filterNot { priceMap.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val fetched = withContext(Dispatchers.IO) {
                        repository.fetchTickerIndicators(
                            symbols = missing,
                            cfg = config,
                            cachedBars = barsBySymbol
                        )
                    }
                    fetched.quotes.forEach { q ->
                        if (q.price > 0.0) priceMap[q.symbol.uppercase(Locale.US)] = q.price
                    }
                }

                val (resolvedLedger, count) = CouncilEngine.resolveLedger(
                    ledger = currentLedger,
                    latestPriceBySymbol = priceMap
                )
                val updatedWeights = CouncilEngine.updateWeights(resolvedLedger)
                val nextStats = CouncilEngine.computeStats(resolvedLedger, updatedWeights)

                _uiState.update {
                    it.copy(
                        ledger = resolvedLedger,
                        weights = updatedWeights,
                        stats = nextStats,
                        isResolving = false,
                        statusNotice = if (count > 0) {
                            "Resolved $count decision(s) and updated member weights."
                        } else {
                            "Could not fetch live prices for pending symbols."
                        }
                    )
                }
                if (count > 0) {
                    withContext(Dispatchers.IO) {
                        store.saveCouncilLedger(resolvedLedger)
                        store.saveCouncilWeights(updatedWeights)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        statusNotice = "Resolve failed: ${e.message ?: "error"}"
                    )
                }
            }
        }
    }

    fun clearLedger() {
        _uiState.update {
            it.copy(
                ledger = emptyList(),
                weights = emptyMap(),
                latestDecision = null,
                stats = CouncilEngine.computeStats(emptyList(), emptyMap()),
                statusNotice = "Council ledger and weights cleared."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            store.saveCouncilLedger(emptyList())
            store.saveCouncilWeights(emptyMap())
        }
    }
}
