package com.example.domain.engine

import com.example.BuildConfig
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.BarSeries
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsHeadline
import com.example.domain.model.RadarModel
import com.example.domain.model.TradePosition
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Multi-model investment committee that votes BUY / SELL / HOLD.
 * Kotlin port of `council.py`.
 *
 * THE THREE RULES, ENFORCED IN CODE:
 *  1. `sizeMult <= 1.0` always. The council can shrink or block a trade, never enlarge one.
 *  2. Quorum (`minMembers`) and family diversity (`minFamilies >= 2`) are hard gates.
 *     Failing either returns HOLD with `sizeMult = 0.0` and `degraded = true`.
 *  3. Every decision is logged with full vote breakdown so `resolve` and `stats` can score
 *     past decisions against actual subsequent prices and update bounded member weights (0.5..2.0).
 */
@JsonClass(generateAdapter = true)
data class CouncilFeatures(
    val symbol: String,
    val asof: String,
    val price: Double,
    val atrPct: Double,
    val mom20d: Double,
    val mom60d: Double,
    val rsi14: Double,
    val adx14: Double,
    val rvol: Double,
    val aboveEma20: Boolean,
    val aboveEma50: Boolean,
    val aboveEma200: Boolean,
    val distEma20Pct: Double,
    val regime: String = "NEUTRAL",
    val breadth: Double = 0.5,
    val volPctile: Double = 0.5,
    val sector: String = "other",
    val position: String = "flat",
    val entry: Double = 0.0,
    val pnlR: Double = 0.0,
    val daysHeld: Int = 0,
    val headlines: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CouncilVote(
    val member: String,
    val family: String,
    val action: String = "HOLD",
    val confidence: Double = 0.0,
    val horizon: Int = 5,
    val invalidation: Double? = null,
    val thesis: String = "",
    val flags: List<String> = emptyList(),
    val latencyMs: Long = 0L,
    val error: String = "",
    val raw: String = "",
    val hit: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CouncilOutcome(
    val ret: Double,
    val sessions: Int,
    val hit: Boolean?
)

@JsonClass(generateAdapter = true)
data class CouncilDecision(
    val id: String = UUID.randomUUID().toString(),
    val symbol: String,
    val asof: String,
    val action: String,
    val score: Double,
    val sizeMult: Double,
    val agreement: Double,
    val dispersion: Double,
    val quorum: Int,
    val families: Int,
    val votes: List<CouncilVote>,
    val reason: String,
    val degraded: Boolean = false,
    val ts: String = "",
    val price: Double = 0.0,
    val atrPct: Double = 0.0,
    val regime: String = "NEUTRAL",
    val horizon: Int = 5,
    val outcome: CouncilOutcome? = null
) {
    fun brief(): String {
        val degTag = if (degraded) "DEGRADED " else ""
        return String.format(
            Locale.US,
            "%s %s score=%+.2f size=%.2f agree=%.0f%% fam=%d %s%s",
            symbol, action, score, sizeMult, agreement * 100.0, families, degTag, reason
        )
    }
}

@JsonClass(generateAdapter = true)
data class CouncilMemberWeight(
    val member: String,
    val weight: Double = 1.0,
    val n: Int = 0,
    val accuracy: Double? = null,
    val note: String = ""
)

data class CouncilMemberStats(
    val member: String,
    val family: String,
    val scoredCount: Int,
    val hits: Int,
    val accuracy: Double,
    val weight: Double
)

data class CouncilStats(
    val totalDecisions: Int,
    val resolvedCount: Int,
    val councilAccuracy: Double?,
    val buySellAccuracy: Double?,
    val buySellCount: Int,
    val holdCount: Int,
    val holdRate: Double,
    val memberStats: List<CouncilMemberStats>,
    val sampleWarning: String?
)

data class CouncilConfig(
    val minMembers: Int = 2,
    val minFamilies: Int = 2,
    val buyThreshold: Double = 0.45,
    val sellThreshold: Double = -0.40,
    val sellVeto: Double = 0.70,
    val dispPenalty: Double = 0.45,
    val temperature: Double = 0.2,
    val timeoutSec: Double = 25.0
)

class CouncilEngine(
    private val transport: AdvisorHttpTransport = OkHttpAdvisorTransport(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()

    private val responseCache = LinkedHashMap<String, Pair<Long, CouncilDecision>>()

    suspend fun deliberate(
        members: List<String>,
        features: CouncilFeatures,
        routing: AiRoutingConfig,
        fallbackGeminiKey: String = "",
        weights: Map<String, CouncilMemberWeight> = emptyMap(),
        config: CouncilConfig = CouncilConfig(),
        ttlSec: Long = 0L
    ): CouncilDecision {
        val cacheKey = "${features.symbol}|${features.position}|${members.joinToString(",")}"
        val now = clockMs()
        if (ttlSec > 0L) {
            val hit = responseCache[cacheKey]
            if (hit != null && (now - hit.first) < ttlSec * 1000L) {
                return hit.second
            }
        }

        val votes = coroutineScope {
            members.map { spec ->
                async {
                    try {
                        askMember(
                            spec = spec,
                            features = features,
                            routing = routing,
                            fallbackGeminiKey = fallbackGeminiKey,
                            config = config
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val modelId = spec.substringAfter(":", spec)
                        CouncilVote(
                            member = spec,
                            family = family(modelId),
                            error = (e.message ?: e.javaClass.simpleName).take(140)
                        )
                    }
                }
            }.awaitAll().sortedBy { it.member }
        }

        val decision = aggregate(
            votes = votes,
            features = features,
            weights = weights,
            config = config,
            nowMs = clockMs()
        )
        if (ttlSec > 0L) {
            responseCache[cacheKey] = clockMs() to decision
        }
        return decision
    }

    suspend fun askMember(
        spec: String,
        features: CouncilFeatures,
        routing: AiRoutingConfig,
        fallbackGeminiKey: String = "",
        config: CouncilConfig = CouncilConfig()
    ): CouncilVote {
        val providerWire = spec.substringBefore(":").trim().lowercase(Locale.US)
        val modelId = spec.substringAfter(":", "").trim()
        val fam = family(modelId)
        val started = clockMs()

        val baseUrl = COUNCIL_BASES[providerWire]
        val providerEnum = AiProviderId.fromWire(providerWire)
        val apiKey = if (providerEnum != null) {
            ModelRadarEngine.resolveApiKey(providerEnum, routing, fallbackGeminiKey)
        } else {
            ""
        }

        if (baseUrl.isNullOrBlank() || modelId.isBlank() || apiKey.isBlank()) {
            val keyHint = KEY_ENV_NAMES[providerWire] ?: "API key"
            return CouncilVote(
                member = spec,
                family = fam,
                error = "$providerWire: missing $keyHint or model ID"
            )
        }

        val headers = LinkedHashMap<String, String>().apply {
            put("Authorization", "Bearer $apiKey")
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            put("User-Agent", "council/1.0")
            if (providerWire == "openrouter") {
                put("HTTP-Referer", "http://localhost")
                put("X-Title", "council")
            }
        }

        val basePayload = linkedMapOf<String, Any?>(
            "model" to modelId,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to userPrompt(features))
            ),
            "temperature" to config.temperature,
            "max_tokens" to 400
        )

        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        var responseMap: Map<String, Any?>? = null
        var lastError = "no response"

        variantLoop@ for (variant in 0..1) {
            val payload = LinkedHashMap(basePayload)
            if (variant == 0) {
                payload["response_format"] = mapOf("type" to "json_object")
            }
            val bodyJson = mapAdapter.toJson(payload)

            for (attempt in 0..2) {
                try {
                    val resp = transport.postJson(
                        url = endpoint,
                        headers = headers,
                        jsonBody = bodyJson,
                        timeoutSec = config.timeoutSec
                    )
                    if (resp.code in 200..299) {
                        responseMap = parseJsonMap(resp.body)
                        break@variantLoop
                    }
                    val detail = resp.body.take(200)
                    lastError = "HTTP ${resp.code} $detail".trim()
                    if (resp.code == 401 || resp.code == 403) {
                        lastError = "auth rejected — $lastError"
                        break@variantLoop
                    }
                    if (resp.code == 400 && detail.contains("response_format", ignoreCase = true) && variant == 0) {
                        continue@variantLoop
                    }
                    if (resp.code == 429 && attempt < 2) {
                        delay(2000L * (attempt + 1))
                        continue
                    }
                    if (attempt < 2) {
                        delay(1200L * (attempt + 1))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = "${e.javaClass.simpleName}: ${e.message}".take(160)
                    if (attempt < 2) delay(1200L * (attempt + 1))
                }
            }
            break@variantLoop
        }

        val latencyMs = (clockMs() - started).coerceAtLeast(1L)
        if (responseMap == null) {
            return CouncilVote(
                member = spec,
                family = fam,
                latencyMs = latencyMs,
                error = lastError.take(160)
            )
        }

        val choices = responseMap["choices"] as? List<*>
        val firstChoice = choices?.firstOrNull() as? Map<*, *>
        val message = firstChoice?.get("message") as? Map<*, *>
        val content = message?.get("content") as? String
        if (content.isNullOrBlank()) {
            return CouncilVote(
                member = spec,
                family = fam,
                latencyMs = latencyMs,
                error = "unexpected response shape"
            )
        }

        val parsed = salvageJson(content)
            ?: return CouncilVote(
                member = spec,
                family = fam,
                latencyMs = latencyMs,
                error = "unparseable JSON",
                raw = content.take(600)
            )

        val action = (parsed["action"] as? String)?.trim()?.uppercase(Locale.US).orEmpty()
        if (action !in VALID_ACTIONS) {
            return CouncilVote(
                member = spec,
                family = fam,
                latencyMs = latencyMs,
                error = "bad action '$action'",
                raw = content.take(600)
            )
        }

        val confidence = toDouble(parsed["confidence"], 0.0).coerceIn(0.0, 1.0)
        val horizon = toDouble(parsed["horizon_days"], 5.0).roundToInt().coerceIn(1, 20)
        val invalidation = parsed["invalidation"]?.let { rawInv ->
            val v = toDouble(rawInv, Double.NaN)
            if (v.isFinite() && v > 0.0) roundTo(v, 2) else null
        }
        val thesis = (parsed["thesis"] as? String)?.trim().orEmpty().take(200)
        val flags = (parsed["red_flags"] as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.take(60)?.takeIf { s -> s.isNotEmpty() } }
            ?.take(3)
            .orEmpty()

        return CouncilVote(
            member = spec,
            family = fam,
            action = action,
            confidence = roundTo(confidence, 3),
            horizon = horizon,
            invalidation = invalidation,
            thesis = thesis,
            flags = flags,
            latencyMs = latencyMs,
            raw = content.take(600)
        )
    }

    private fun parseJsonMap(raw: String): Map<String, Any?>? = try {
        mapAdapter.fromJson(raw)
    } catch (_: Exception) {
        null
    }

    fun salvageJson(text: String): Map<String, Any?>? {
        if (text.isBlank()) return null
        parseJsonMap(text.trim())?.let { return it }
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a >= 0 && b > a) {
            parseJsonMap(text.substring(a, b + 1))?.let { return it }
        }
        return null
    }

    companion object {
        val COUNCIL_BASES: Map<String, String> = mapOf(
            "aihubmix" to "https://aihubmix.com/v1",
            "openrouter" to "https://openrouter.ai/api/v1",
            "deepseek" to "https://api.deepseek.com",
            "groq" to "https://api.groq.com/openai/v1",
            "gemini" to "https://generativelanguage.googleapis.com/v1beta/openai"
        )

        val KEY_ENV_NAMES: Map<String, String> = mapOf(
            "aihubmix" to "AIHUBMIX_API_KEY",
            "openrouter" to "OPENROUTER_API_KEY",
            "deepseek" to "DEEPSEEK_API_KEY",
            "groq" to "GROQ_API_KEY",
            "gemini" to "GEMINI_API_KEY"
        )

        val VALID_ACTIONS: Set<String> = setOf("BUY", "SELL", "HOLD")

        private val DIR_MAP: Map<String, Double> = mapOf(
            "BUY" to 1.0,
            "SELL" to -1.0,
            "HOLD" to 0.0
        )

        val FAMILIES: List<String> = listOf(
            "glm", "qwen", "llama", "deepseek", "gpt", "gemini", "mistral",
            "kimi", "moonshot", "claude", "grok", "phi", "yi", "ernie",
            "minimax", "doubao", "step", "hunyuan"
        )

        val DEFAULT_MEMBERS: List<String> = listOf(
            "gemini:gemini-2.5-flash",
            "openrouter:qwen/qwen3-235b-a22b:free",
            "groq:llama-3.3-70b-versatile",
            "deepseek:deepseek-chat"
        )

        val SYSTEM_PROMPT: String = """
You are one member of an investment committee evaluating ONE US
equity for a long-only systematic momentum book. You return JSON only.

Schema — every key required:
{"action":"BUY"|"SELL"|"HOLD",
 "confidence":<0.0-1.0>,
 "horizon_days":<1-20>,
 "invalidation":<price level that proves you wrong, or null>,
 "thesis":"<=25 words, cite the numbers you were given",
 "red_flags":["<=3 short strings"]}

Decision rules:
- HOLD is the correct answer whenever evidence is mixed, thin, or conflicting.
  HOLD is a decision, not a failure to decide.
- BUY means open a long. SELL means close an existing long — never a short.
- confidence above 0.75 requires at least three independent confirming
  indicators in the supplied data. Do not inflate confidence to seem decisive.
- Never state a target or invalidation further than 4x ATR from the last price.
- You have no knowledge of future events, earnings dates, deals, or guidance
  that is not present in the data. Do not speculate about them.
- If a field you need is missing or zero, answer HOLD with confidence <= 0.30.
- A position already up more than 2R with deteriorating momentum is a SELL,
  not a HOLD-for-more.

Answer with JSON and nothing else.
        """.trimIndent()

        fun family(modelId: String): String {
            val m = modelId.trim().lowercase(Locale.US)
            for (fam in FAMILIES) {
                if (m.contains(fam)) return fam
            }
            return m.substringBefore("/").substringBefore("-").take(12).ifBlank { "unknown" }
        }

        fun parseMembers(spec: String?): List<String> {
            if (spec.isNullOrBlank()) return emptyList()
            return spec.split(",")
                .mapNotNull { chunk ->
                    val trimmed = chunk.trim()
                    if (!trimmed.contains(":")) return@mapNotNull null
                    val prov = trimmed.substringBefore(":").trim().lowercase(Locale.US)
                    val model = trimmed.substringAfter(":").trim()
                    if (prov in COUNCIL_BASES && model.isNotEmpty()) "$prov:$model" else null
                }
                .distinct()
        }

        fun defaultMembersFromEnvironment(
            routing: AiRoutingConfig,
            radarModels: List<RadarModel> = emptyList(),
            fallbackGeminiKey: String = ""
        ): List<String> {
            val fromBuild = parseMembers(BuildConfig.COUNCIL_MODELS)
            if (fromBuild.isNotEmpty()) return fromBuild

            val picked = LinkedHashSet<String>()
            val seenFamilies = LinkedHashSet<String>()

            // 1. Prefer explicitly enabled providers that have a key + selected model
            AiProviderId.entries.forEach { prov ->
                val settings = routing.settingsFor(prov)
                val key = ModelRadarEngine.resolveApiKey(prov, routing, fallbackGeminiKey)
                val model = settings.selectedModelId.trim().ifBlank {
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
                if (settings.enabled && key.isNotBlank() && model.isNotBlank()) {
                    val fam = family(model)
                    if (seenFamilies.add(fam) || picked.size < 3) {
                        picked.add("${prov.wire}:$model")
                    }
                }
            }

            // 2. Ensure at least 3 distinct-family starter slots are shown so users see the diversity gate clearly
            if (picked.size < 2) {
                DEFAULT_MEMBERS.forEach { spec ->
                    val fam = family(spec.substringAfter(":"))
                    if (seenFamilies.add(fam) && picked.size < 3) {
                        picked.add(spec)
                    }
                }
            }
            return picked.toList()
        }

        fun userPrompt(f: CouncilFeatures): String {
            val lines = mutableListOf(
                "symbol: ${f.symbol}",
                "as_of: ${f.asof}",
                "last_price: ${roundTo(f.price, 4)}",
                "atr_pct_of_price: ${roundTo(f.atrPct, 4)}",
                "momentum_20d_pct: ${roundTo(f.mom20d, 4)}",
                "momentum_60d_pct: ${roundTo(f.mom60d, 4)}",
                "rsi_14: ${roundTo(f.rsi14, 4)}",
                "adx_14: ${roundTo(f.adx14, 4)}",
                "relative_volume_vs_20d: ${roundTo(f.rvol, 4)}",
                "above_ema20: ${f.aboveEma20}",
                "above_ema50: ${f.aboveEma50}",
                "above_ema200: ${f.aboveEma200}",
                "distance_from_ema20_pct: ${roundTo(f.distEma20Pct, 4)}",
                "market_regime: ${f.regime}",
                "breadth_above_50d: ${roundTo(f.breadth, 4)}",
                "spy_vol_percentile: ${roundTo(f.volPctile, 4)}",
                "sector: ${f.sector}",
                "position: ${f.position}"
            )
            if (f.position == "long") {
                lines += "entry_price: ${roundTo(f.entry, 4)}"
                lines += "open_pnl_r: ${roundTo(f.pnlR, 4)}"
                lines += "days_held: ${f.daysHeld}"
            }
            if (f.headlines.isNotEmpty()) {
                lines += "recent_headlines:"
                f.headlines.take(4).forEach { h -> lines += "  - $h" }
            } else {
                lines += "recent_headlines: none supplied"
            }
            return lines.joinToString("\n")
        }

        /**
         * Deterministic aggregator enforcing the Three Rules from `council.py`.
         */
        fun aggregate(
            votes: List<CouncilVote>,
            features: CouncilFeatures,
            weights: Map<String, CouncilMemberWeight> = emptyMap(),
            config: CouncilConfig = CouncilConfig(),
            nowMs: Long = System.currentTimeMillis()
        ): CouncilDecision {
            val valid = votes.filter { it.error.isBlank() }
            val ts = isoUtc(nowMs)
            val medianHorizon = medianInt(valid.map { it.horizon }.ifEmpty { listOf(5) })

            if (valid.size < config.minMembers) {
                return CouncilDecision(
                    symbol = features.symbol,
                    asof = features.asof,
                    action = "HOLD",
                    score = 0.0,
                    sizeMult = 0.0,
                    agreement = 0.0,
                    dispersion = 1.0,
                    quorum = valid.size,
                    families = valid.map { it.family }.toSet().size,
                    votes = votes,
                    reason = "quorum ${valid.size}/${config.minMembers}",
                    degraded = true,
                    ts = ts,
                    price = roundTo(features.price, 4),
                    atrPct = roundTo(features.atrPct, 4),
                    regime = features.regime,
                    horizon = medianHorizon
                )
            }

            val fams = valid.map { it.family }.toSet()
            if (fams.size < config.minFamilies) {
                return CouncilDecision(
                    symbol = features.symbol,
                    asof = features.asof,
                    action = "HOLD",
                    score = 0.0,
                    sizeMult = 0.0,
                    agreement = 0.0,
                    dispersion = 1.0,
                    quorum = valid.size,
                    families = fams.size,
                    votes = votes,
                    reason = "only ${fams.size} model family — correlated, not independent",
                    degraded = true,
                    ts = ts,
                    price = roundTo(features.price, 4),
                    atrPct = roundTo(features.atrPct, 4),
                    regime = features.regime,
                    horizon = medianHorizon
                )
            }

            val memberWeights = valid.associate { v ->
                v.member to (weights[v.member]?.weight ?: 1.0).coerceIn(0.5, 2.0)
            }
            val wsum = memberWeights.values.sum().takeIf { it > 0.0 } ?: 1.0

            val dirs = valid.associate { v ->
                v.member to ((DIR_MAP[v.action] ?: 0.0) * v.confidence)
            }
            val score = valid.sumOf { v -> (memberWeights[v.member] ?: 1.0) * (dirs[v.member] ?: 0.0) } / wsum
            val variance = valid.sumOf { v ->
                val d = (dirs[v.member] ?: 0.0) - score
                (memberWeights[v.member] ?: 1.0) * d * d
            } / wsum
            val disp = sqrt(max(variance, 0.0))
            val agreement = max(0.0, 1.0 - disp)

            val hardSell = valid.filter { it.action == "SELL" && it.confidence >= config.sellVeto }

            // ---- Open long ----
            if (features.position.equals("long", ignoreCase = true)) {
                if (hardSell.isNotEmpty()) {
                    val top = hardSell.maxBy { it.confidence }
                    return CouncilDecision(
                        symbol = features.symbol,
                        asof = features.asof,
                        action = "SELL",
                        score = roundTo(score, 3),
                        sizeMult = 1.0,
                        agreement = roundTo(agreement, 3),
                        dispersion = roundTo(disp, 3),
                        quorum = valid.size,
                        families = fams.size,
                        votes = votes,
                        reason = "veto by ${top.member} (conf ${String.format(Locale.US, "%.2f", top.confidence)})",
                        degraded = false,
                        ts = ts,
                        price = roundTo(features.price, 4),
                        atrPct = roundTo(features.atrPct, 4),
                        regime = features.regime,
                        horizon = medianHorizon
                    )
                }
                if (score <= config.sellThreshold) {
                    val size = min(1.0, 0.5 + 0.5 * abs(score))
                    return CouncilDecision(
                        symbol = features.symbol,
                        asof = features.asof,
                        action = "SELL",
                        score = roundTo(score, 3),
                        sizeMult = roundTo(size.coerceIn(0.0, 1.0), 3),
                        agreement = roundTo(agreement, 3),
                        dispersion = roundTo(disp, 3),
                        quorum = valid.size,
                        families = fams.size,
                        votes = votes,
                        reason = "bearish score ${String.format(Locale.US, "%+.2f", score)}",
                        degraded = false,
                        ts = ts,
                        price = roundTo(features.price, 4),
                        atrPct = roundTo(features.atrPct, 4),
                        regime = features.regime,
                        horizon = medianHorizon
                    )
                }
                return CouncilDecision(
                    symbol = features.symbol,
                    asof = features.asof,
                    action = "HOLD",
                    score = roundTo(score, 3),
                    sizeMult = 0.0,
                    agreement = roundTo(agreement, 3),
                    dispersion = roundTo(disp, 3),
                    quorum = valid.size,
                    families = fams.size,
                    votes = votes,
                    reason = "score ${String.format(Locale.US, "%+.2f", score)} inside hold band",
                    degraded = false,
                    ts = ts,
                    price = roundTo(features.price, 4),
                    atrPct = roundTo(features.atrPct, 4),
                    regime = features.regime,
                    horizon = medianHorizon
                )
            }

            // ---- Flat ----
            if (hardSell.isNotEmpty()) {
                val top = hardSell.maxBy { it.confidence }
                return CouncilDecision(
                    symbol = features.symbol,
                    asof = features.asof,
                    action = "HOLD",
                    score = roundTo(score, 3),
                    sizeMult = 0.0,
                    agreement = roundTo(agreement, 3),
                    dispersion = roundTo(disp, 3),
                    quorum = valid.size,
                    families = fams.size,
                    votes = votes,
                    reason = "blocked by ${top.member} (conf ${String.format(Locale.US, "%.2f", top.confidence)})",
                    degraded = false,
                    ts = ts,
                    price = roundTo(features.price, 4),
                    atrPct = roundTo(features.atrPct, 4),
                    regime = features.regime,
                    horizon = medianHorizon
                )
            }

            if (score >= config.buyThreshold) {
                val span = max(1.0 - config.buyThreshold, 1e-6)
                val base = 0.4 + 0.6 * min((score - config.buyThreshold) / span, 1.0)
                val size = (base * (1.0 - config.dispPenalty * disp)).coerceIn(0.0, 1.0)
                return CouncilDecision(
                    symbol = features.symbol,
                    asof = features.asof,
                    action = "BUY",
                    score = roundTo(score, 3),
                    sizeMult = roundTo(size, 3),
                    agreement = roundTo(agreement, 3),
                    dispersion = roundTo(disp, 3),
                    quorum = valid.size,
                    families = fams.size,
                    votes = votes,
                    reason = "score ${String.format(Locale.US, "%+.2f", score)} >= ${config.buyThreshold}",
                    degraded = false,
                    ts = ts,
                    price = roundTo(features.price, 4),
                    atrPct = roundTo(features.atrPct, 4),
                    regime = features.regime,
                    horizon = medianHorizon
                )
            }

            return CouncilDecision(
                symbol = features.symbol,
                asof = features.asof,
                action = "HOLD",
                score = roundTo(score, 3),
                sizeMult = 0.0,
                agreement = roundTo(agreement, 3),
                dispersion = roundTo(disp, 3),
                quorum = valid.size,
                families = fams.size,
                votes = votes,
                reason = "score ${String.format(Locale.US, "%+.2f", score)} below buy threshold",
                degraded = false,
                ts = ts,
                price = roundTo(features.price, 4),
                atrPct = roundTo(features.atrPct, 4),
                regime = features.regime,
                horizon = medianHorizon
            )
        }

        fun featuresFromBars(
            symbol: String,
            bars: BarSeries,
            regime: MarketRegime,
            livePrice: Double? = null,
            position: TradePosition? = null,
            headlines: List<NewsHeadline> = emptyList(),
            nowMs: Long = System.currentTimeMillis()
        ): CouncilFeatures {
            val cleanSym = symbol.trim().uppercase(Locale.US)
            val closes = bars.c.toMutableList()
            if (livePrice != null && livePrice.isFinite() && livePrice > 0.0 && closes.isNotEmpty()) {
                closes[closes.lastIndex] = livePrice
            }
            val px = closes.lastOrNull() ?: livePrice ?: 0.0
            val atr = computeAtr14(bars)
            val atrPct = if (px > 0.0) (atr / px) * 100.0 else 0.0
            val e20 = QuantEngine.ema(closes, 20).lastOrNull() ?: px
            val e50 = QuantEngine.ema(closes, 50).lastOrNull() ?: px
            val e200 = QuantEngine.ema(closes, 200).lastOrNull() ?: px

            val mom20 = if (closes.size > 21 && closes[closes.size - 21] > 0.0) {
                (closes.last() / closes[closes.size - 21] - 1.0) * 100.0
            } else 0.0
            val mom60 = if (closes.size > 61 && closes[closes.size - 61] > 0.0) {
                (closes.last() / closes[closes.size - 61] - 1.0) * 100.0
            } else 0.0

            val vols = bars.v
            val baseVol = if (vols.size > 21) vols.subList(vols.size - 21, vols.size - 1).filter { it > 0.0 } else emptyList()
            val rvol = if (baseVol.isNotEmpty() && vols.isNotEmpty()) {
                vols.last() / baseVol.average()
            } else 1.0

            val rsi14 = TickerIndicatorEngine.computeRsi14(closes, 14)
            val adx14 = computeAdx14(bars)

            val posStr = if (position != null) "long" else "flat"
            val entryPx = position?.entry ?: 0.0
            val pnlR = if (position != null && entryPx > 0.0) {
                val riskUnit = if (position.initialRisk > 0.0) position.initialRisk else max(1.5 * atr, 1e-6)
                roundTo((px - entryPx) / riskUnit, 2)
            } else 0.0
            val daysHeld = if (position != null && position.openedAtMs > 0L) {
                max(0, ((nowMs - position.openedAtMs) / 86_400_000L).toInt())
            } else 0

            val symHeadlines = headlines
                .filter { it.sym.equals(cleanSym, ignoreCase = true) }
                .map { it.title.trim() }
                .filter { it.isNotEmpty() }
                .take(4)

            return CouncilFeatures(
                symbol = cleanSym,
                asof = dateUtc(nowMs),
                price = roundTo(px, 4),
                atrPct = roundTo(atrPct, 4),
                mom20d = roundTo(mom20, 4),
                mom60d = roundTo(mom60, 4),
                rsi14 = roundTo(rsi14, 2),
                adx14 = roundTo(adx14, 2),
                rvol = roundTo(rvol, 3),
                aboveEma20 = px > e20,
                aboveEma50 = px > e50,
                aboveEma200 = px > e200,
                distEma20Pct = if (e20 > 0.0) roundTo(((px / e20) - 1.0) * 100.0, 4) else 0.0,
                regime = regime.label.name,
                breadth = roundTo(regime.breadth, 4),
                volPctile = roundTo(regime.volPct, 4),
                sector = QuantEngine.classifySector(cleanSym).label,
                position = posStr,
                entry = roundTo(entryPx, 4),
                pnlR = pnlR,
                daysHeld = daysHeld,
                headlines = symHeadlines
            )
        }

        /**
         * Scores whether an action was right given realized fractional return `ret`.
         * HOLD is scored as "nothing dramatic happened" (|ret| < max(0.5 * ATR_frac, 0.5%)).
         */
        fun hitOf(action: String, ret: Double, atrPct: Double): Boolean? {
            val atrFrac = if (atrPct > 0.5) atrPct / 100.0 else atrPct
            val band = max(atrFrac * 0.5, 0.005)
            return when (action.uppercase(Locale.US)) {
                "BUY" -> ret > 0.0
                "SELL" -> ret < 0.0
                "HOLD" -> abs(ret) < band
                else -> null
            }
        }

        /**
         * Resolves pending ledger entries against latest market prices.
         */
        fun resolveLedger(
            ledger: List<CouncilDecision>,
            latestPriceBySymbol: Map<String, Double>,
            nowMs: Long = System.currentTimeMillis()
        ): Pair<List<CouncilDecision>, Int> {
            var resolvedNow = 0
            val updated = ledger.map { row ->
                if (row.outcome != null) return@map row
                val currentPx = latestPriceBySymbol[row.symbol.uppercase(Locale.US)]
                if (currentPx == null || currentPx <= 0.0 || row.price <= 0.0) return@map row

                val ret = currentPx / row.price - 1.0
                val elapsedDays = max(1, daysSinceIso(row.asof, nowMs))
                val sessions = min(elapsedDays, max(1, row.horizon))
                val decisionHit = hitOf(row.action, ret, if (row.atrPct > 0.0) row.atrPct else 1.0)
                val updatedVotes = row.votes.map { v ->
                    if (v.error.isNotBlank()) v
                    else v.copy(hit = hitOf(v.action, ret, if (row.atrPct > 0.0) row.atrPct else 1.0))
                }
                resolvedNow++
                row.copy(
                    votes = updatedVotes,
                    outcome = CouncilOutcome(
                        ret = roundTo(ret, 5),
                        sessions = sessions,
                        hit = decisionHit
                    )
                )
            }
            return updated to resolvedNow
        }

        /**
         * Bounded, sample-size aware member weights (`0.5..2.0`).
         * Fewer than 10 scored outcomes stays at `1.0`.
         */
        fun updateWeights(
            ledger: List<CouncilDecision>,
            lo: Double = 0.5,
            hi: Double = 2.0
        ): Map<String, CouncilMemberWeight> {
            data class Acc(var n: Int = 0, var hits: Int = 0)
            val book = LinkedHashMap<String, Acc>()
            for (r in ledger) {
                if (r.outcome == null) continue
                for (v in r.votes) {
                    if (v.error.isNotBlank() || v.hit == null) continue
                    val slot = book.getOrPut(v.member) { Acc() }
                    slot.n += 1
                    if (v.hit) slot.hits += 1
                }
            }
            return book.mapValues { (member, s) ->
                if (s.n < 10) {
                    CouncilMemberWeight(
                        member = member,
                        weight = 1.0,
                        n = s.n,
                        accuracy = null,
                        note = "not enough history"
                    )
                } else {
                    val acc = s.hits.toDouble() / s.n.toDouble()
                    var w = 1.0 + (acc - 0.5) * 2.0
                    w *= min(1.0, s.n / 40.0).pow(0.5)
                    CouncilMemberWeight(
                        member = member,
                        weight = roundTo(w.coerceIn(lo, hi), 3),
                        n = s.n,
                        accuracy = roundTo(acc, 3)
                    )
                }
            }
        }

        fun computeStats(
            ledger: List<CouncilDecision>,
            weights: Map<String, CouncilMemberWeight>
        ): CouncilStats {
            val done = ledger.filter { it.outcome != null }
            val hits = done.mapNotNull { it.outcome?.hit }
            val councilAcc = if (hits.isNotEmpty()) hits.count { it }.toDouble() / hits.size else null

            val actionable = done.filter { it.action == "BUY" || it.action == "SELL" }
            val actHits = actionable.mapNotNull { it.outcome?.hit }
            val buySellAcc = if (actHits.isNotEmpty()) actHits.count { it }.toDouble() / actHits.size else null

            val holds = done.count { it.action == "HOLD" }
            val holdRate = if (done.isNotEmpty()) holds.toDouble() / done.size else 0.0

            data class BookEntry(var n: Int = 0, var hits: Int = 0, var fam: String = "")
            val book = LinkedHashMap<String, BookEntry>()
            for (r in done) {
                for (v in r.votes) {
                    if (v.error.isNotBlank() || v.hit == null) continue
                    val slot = book.getOrPut(v.member) { BookEntry(fam = v.family) }
                    slot.n += 1
                    if (v.hit) slot.hits += 1
                }
            }

            val memberStats = book.keys.sorted().map { member ->
                val b = book.getValue(member)
                CouncilMemberStats(
                    member = member,
                    family = b.fam.ifBlank { family(member.substringAfter(":")) },
                    scoredCount = b.n,
                    hits = b.hits,
                    accuracy = if (b.n > 0) b.hits.toDouble() / b.n else 0.0,
                    weight = weights[member]?.weight ?: 1.0
                )
            }

            val warning = when {
                done.isEmpty() -> "No resolved outcomes yet — run Resolve after prices move to calibrate member weights."
                done.size < 30 -> "${done.size} resolved decisions is not a sample. Below 100 you are reading noise."
                done.size < 100 -> "${done.size} resolved decisions — directional signal emerging, 100+ needed for full confidence."
                else -> null
            }

            return CouncilStats(
                totalDecisions = ledger.size,
                resolvedCount = done.size,
                councilAccuracy = councilAcc,
                buySellAccuracy = buySellAcc,
                buySellCount = actHits.size,
                holdCount = holds,
                holdRate = holdRate,
                memberStats = memberStats,
                sampleWarning = warning
            )
        }

        private fun computeAtr14(bars: BarSeries, n: Int = 14): Double {
            val size = minOf(bars.h.size, bars.l.size, bars.c.size)
            if (size < n + 1) return 0.0
            val trs = ArrayList<Double>(size - 1)
            for (i in 1 until size) {
                val h = bars.h[i]
                val l = bars.l[i]
                val pc = bars.c[i - 1]
                trs.add(maxOf(h - l, abs(h - pc), abs(l - pc)))
            }
            return trs.takeLast(n).average()
        }

        private fun computeAdx14(bars: BarSeries, n: Int = 14): Double {
            val size = minOf(bars.h.size, bars.l.size, bars.c.size)
            if (size < n * 2 + 1) return 0.0
            val trs = ArrayList<Double>(size - 1)
            val plusDm = ArrayList<Double>(size - 1)
            val minusDm = ArrayList<Double>(size - 1)
            for (i in 1 until size) {
                val upMove = bars.h[i] - bars.h[i - 1]
                val downMove = bars.l[i - 1] - bars.l[i]
                plusDm.add(if (upMove > downMove && upMove > 0.0) upMove else 0.0)
                minusDm.add(if (downMove > upMove && downMove > 0.0) downMove else 0.0)
                val tr = maxOf(
                    bars.h[i] - bars.l[i],
                    abs(bars.h[i] - bars.c[i - 1]),
                    abs(bars.l[i] - bars.c[i - 1])
                )
                trs.add(tr)
            }
            var trSm = trs.take(n).sum()
            var pSm = plusDm.take(n).sum()
            var mSm = minusDm.take(n).sum()
            val dxs = ArrayList<Double>()
            for (i in n until trs.size) {
                trSm = trSm - (trSm / n) + trs[i]
                pSm = pSm - (pSm / n) + plusDm[i]
                mSm = mSm - (mSm / n) + minusDm[i]
                val pDi = if (trSm > 0.0) 100.0 * (pSm / trSm) else 0.0
                val mDi = if (trSm > 0.0) 100.0 * (mSm / trSm) else 0.0
                val denom = pDi + mDi
                dxs.add(if (denom > 0.0) 100.0 * abs(pDi - mDi) / denom else 0.0)
            }
            if (dxs.size < n) return dxs.average().takeIf { it.isFinite() } ?: 0.0
            var adx = dxs.take(n).average()
            for (i in n until dxs.size) {
                adx = ((adx * (n - 1)) + dxs[i]) / n
            }
            return adx.coerceIn(0.0, 100.0)
        }

        private fun medianInt(values: List<Int>): Int {
            if (values.isEmpty()) return 5
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }

        private fun toDouble(value: Any?, default: Double): Double = when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() } ?: default
            is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
            else -> default
        }

        private fun roundTo(value: Double, decimals: Int): Double {
            if (!value.isFinite()) return 0.0
            val factor = 10.0.pow(decimals)
            return (value * factor).roundToInt() / factor
        }

        private fun isoUtc(ms: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(ms))
        }

        private fun dateUtc(ms: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(ms))
        }

        private fun daysSinceIso(asof: String, nowMs: Long): Int {
            return try {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(asof) ?: return 1
                max(1, ((nowMs - parsed.time) / 86_400_000L).toInt())
            } catch (_: Exception) {
                1
            }
        }
    }
}
