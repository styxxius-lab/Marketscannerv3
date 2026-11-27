package com.example.domain.engine

import com.example.BuildConfig
import com.example.domain.model.CryptoEventCategory
import com.example.domain.model.CryptoEventClassification
import com.example.domain.model.TradeLogEntry
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AlphaTrader Advisor Layer — Kotlin port of `advisor.py`.
 *
 * Enforces three strict rules:
 *  1. Never in the entry path: cannot generate trades or add risk.
 *  2. Veto only: every failure mode (timeout, rate limit, malformed JSON, missing key,
 *     low confidence < 0.70, or missing news) returns [AdvisorVetoAction.NONE].
 *  3. Log the verdict: every trade carries `llm = off | clear | veto` so population
 *     reports can compare outcomes quantitatively.
 */
data class AdvisorConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeoutSec: Double = 8.0,
    val maxTokens: Int = 700,
    val finnhubKey: String = "",
    val maxCallsPerHour: Int = 40,
    val minGapSec: Double = 1.2
) {
    val cleanBaseUrl: String get() = baseUrl.trim().trimEnd('/')
    val cleanApiKey: String get() = sanitizeSecret(apiKey)
    val cleanModel: String get() = sanitizeSecret(model)
    val cleanFinnhubKey: String get() = sanitizeSecret(finnhubKey)

    val enabled: Boolean
        get() = cleanBaseUrl.isNotEmpty() && cleanApiKey.isNotEmpty() && cleanModel.isNotEmpty()

    val vetoReady: Boolean
        get() = enabled && cleanFinnhubKey.isNotEmpty()

    companion object {
        private val PLACEHOLDER_PREFIXES = listOf("YOUR_", "MY_", "REPLACE_", "CHANGE_ME", "PLACEHOLDER")

        private fun sanitizeSecret(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            val upper = trimmed.uppercase(Locale.US)
            if (PLACEHOLDER_PREFIXES.any { upper.startsWith(it) } || upper.startsWith("<")) {
                return ""
            }
            return trimmed
        }

        fun fromBuildConfig(): AdvisorConfig = AdvisorConfig(
            baseUrl = BuildConfig.LLM_BASE_URL,
            apiKey = BuildConfig.LLM_API_KEY,
            model = BuildConfig.LLM_MODEL,
            finnhubKey = BuildConfig.FINNHUB_KEY
        )
    }
}

data class AdvisorNewsItem(
    val symbol: String,
    val headline: String,
    val publishedMs: Long,
    val ageHours: Double,
    val source: String = "",
    val url: String = ""
)

enum class AdvisorVetoAction(val wire: String) {
    NONE("none"),
    VETO("veto")
}

data class AdvisorVetoVerdict(
    val symbol: String,
    val action: AdvisorVetoAction = AdvisorVetoAction.NONE,
    val weight: Double = 0.0,
    val category: String = "",
    val why: String = "",
    val headline: String = "",
    val llmTag: String = "off",
    val checkedAtMs: Long = 0L
) {
    val isVeto: Boolean get() = action == AdvisorVetoAction.VETO

    companion object {
        fun none(symbol: String, advisorEnabled: Boolean, nowMs: Long = 0L): AdvisorVetoVerdict =
            AdvisorVetoVerdict(
                symbol = symbol.uppercase(Locale.US),
                action = AdvisorVetoAction.NONE,
                weight = 0.0,
                llmTag = if (advisorEnabled) "clear" else "off",
                checkedAtMs = nowMs
            )
    }
}

data class AdvisorJournalReport(
    val headline: String,
    val observations: List<String>,
    val concerns: List<String>,
    val sampleSize: Int,
    val generatedAtMs: Long
) {
    fun formatText(): String {
        val sb = StringBuilder()
        sb.append("  ").append(headline)
        observations.take(4).forEach { sb.append("\n   · ").append(it) }
        concerns.take(4).forEach { sb.append("\n   ! ").append(it) }
        return sb.toString()
    }
}

data class AdvisorPopulationBucket(
    val llmTag: String,
    val tradeCount: Int,
    val winRatePct: Double,
    val totalPnl: Double,
    val avgRMultiple: Double?
)

data class AdvisorPopulationReport(
    val buckets: List<AdvisorPopulationBucket>,
    val totalClosedTrades: Int,
    val vetoEventsLogged: Int
)

data class AdvisorEvent(
    val timestampMs: Long,
    val event: String,
    val details: Map<String, String>
)

/**
 * HTTP response abstraction for testability.
 */
data class AdvisorHttpResponse(
    val code: Int,
    val body: String
)

interface AdvisorHttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        jsonBody: String,
        timeoutSec: Double
    ): AdvisorHttpResponse

    suspend fun get(
        url: String,
        timeoutSec: Double
    ): AdvisorHttpResponse
}

class OkHttpAdvisorTransport(
    private val baseClient: OkHttpClient = OkHttpClient()
) : AdvisorHttpTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        jsonBody: String,
        timeoutSec: Double
    ): AdvisorHttpResponse {
        val ms = (timeoutSec * 1000.0).toLong().coerceIn(1000L, 60_000L)
        val client = baseClient.newBuilder()
            .connectTimeout(ms, TimeUnit.MILLISECONDS)
            .readTimeout(ms, TimeUnit.MILLISECONDS)
            .writeTimeout(ms, TimeUnit.MILLISECONDS)
            .build()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            return AdvisorHttpResponse(
                code = resp.code,
                body = resp.body?.string().orEmpty()
            )
        }
    }

    override suspend fun get(
        url: String,
        timeoutSec: Double
    ): AdvisorHttpResponse {
        val ms = (timeoutSec * 1000.0).toLong().coerceIn(1000L, 60_000L)
        val client = baseClient.newBuilder()
            .connectTimeout(ms, TimeUnit.MILLISECONDS)
            .readTimeout(ms, TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            return AdvisorHttpResponse(
                code = resp.code,
                body = resp.body?.string().orEmpty()
            )
        }
    }
}

/**
 * Token bucket rate limiter matching `advisor.py`'s `_Budget`:
 * Free tiers rate-limit hard, and a 429 in the trading loop is worse than no advisor.
 */
class AdvisorBudget(
    val perHour: Int = 40,
    val minGapSec: Double = 1.2,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleepFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) {
    private val mutex = Mutex()
    private val stamps = ArrayList<Long>()

    suspend fun allow(): Boolean = mutex.withLock {
        val now = clockMs()
        pruneLocked(now)
        if (stamps.size >= perHour) {
            return@withLock false
        }
        val minGapMs = (minGapSec * 1000.0).toLong().coerceAtLeast(0L)
        val last = stamps.lastOrNull()
        if (last != null && minGapMs > 0L) {
            val gap = now - last
            if (gap in 0 until minGapMs) {
                sleepFn(minGapMs - gap)
            }
        }
        stamps.add(clockMs())
        true
    }

    suspend fun callsUsedLastHour(): Int = mutex.withLock {
        pruneLocked(clockMs())
        stamps.size
    }

    private fun pruneLocked(nowMs: Long) {
        val cutoff = nowMs - 3_600_000L
        stamps.removeAll { it < cutoff }
    }
}

class AdvisorEngine(
    val config: AdvisorConfig = AdvisorConfig.fromBuildConfig(),
    private val transport: AdvisorHttpTransport = OkHttpAdvisorTransport(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    sleepFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) {
    val budget = AdvisorBudget(
        perHour = config.maxCallsPerHour,
        minGapSec = config.minGapSec,
        clockMs = clockMs,
        sleepFn = sleepFn
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()

    private val listAdapter: JsonAdapter<List<Any?>> = moshi.adapter<List<Any?>>(
        Types.newParameterizedType(List::class.java, Any::class.java)
    ).serializeNulls()

    private val cacheMutex = Mutex()
    private val newsCache = HashMap<String, Pair<Long, List<AdvisorNewsItem>>>()
    private val vetoCache = HashMap<String, Pair<Long, AdvisorVetoVerdict>>()
    private val events = ArrayDeque<AdvisorEvent>()
    @Volatile
    private var runtimeFinnhubKey: String = ""
    @Volatile
    private var runtimeBaseUrl: String? = null
    @Volatile
    private var runtimeApiKey: String? = null
    @Volatile
    private var runtimeModel: String? = null
    @Volatile
    private var runtimeIsOpenRouter: Boolean = false
    @Volatile
    private var runtimeForceDisabled: Boolean = false

    val effectiveFinnhubKey: String
        get() = runtimeFinnhubKey.ifBlank { config.cleanFinnhubKey }

    val effectiveBaseUrl: String
        get() = runtimeBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: config.cleanBaseUrl

    val effectiveApiKey: String
        get() = runtimeApiKey?.trim()?.takeIf { it.isNotEmpty() } ?: config.cleanApiKey

    val effectiveModel: String
        get() = runtimeModel?.trim()?.takeIf { it.isNotEmpty() } ?: config.cleanModel

    val enabled: Boolean
        get() = !runtimeForceDisabled &&
            effectiveBaseUrl.isNotEmpty() &&
            effectiveApiKey.isNotEmpty() &&
            effectiveModel.isNotEmpty()

    val vetoReady: Boolean get() = enabled && effectiveFinnhubKey.isNotEmpty()

    fun setRuntimeFinnhubKey(key: String) {
        runtimeFinnhubKey = key.trim()
    }

    fun setRuntimeLlmRouting(
        baseUrl: String?,
        apiKey: String?,
        model: String?,
        isOpenRouter: Boolean = false,
        forceDisabled: Boolean = false
    ) {
        runtimeBaseUrl = baseUrl?.trim()?.trimEnd('/')
        runtimeApiKey = apiKey?.trim()
        runtimeModel = model?.trim()
        runtimeIsOpenRouter = isOpenRouter
        runtimeForceDisabled = forceDisabled
    }

    private fun logEvent(event: String, details: Map<String, String>) {
        synchronized(events) {
            events.addFirst(AdvisorEvent(clockMs(), event, details))
            while (events.size > MAX_EVENTS) {
                events.removeLast()
            }
        }
    }

    fun recentEvents(): List<AdvisorEvent> = synchronized(events) { events.toList() }

    // ── Transport & JSON salvage ──────────────────────────────────────────

    private suspend fun chat(system: String, user: String): String? {
        if (!enabled) return null
        if (!budget.allow()) {
            logEvent("LLM_BUDGET", mapOf("note" to "hourly call budget reached"))
            return null
        }

        val activeUrl = effectiveBaseUrl
        val activeKey = effectiveApiKey
        val activeModel = effectiveModel

        val messages = listOf(
            mapOf("role" to "system", "content" to system),
            mapOf("role" to "user", "content" to user)
        )
        val headers = linkedMapOf(
            "Authorization" to "Bearer $activeKey",
            "Content-Type" to "application/json"
        )
        if (runtimeIsOpenRouter || activeUrl.contains("openrouter.ai", ignoreCase = true)) {
            headers["HTTP-Referer"] = "http://localhost"
            headers["X-Title"] = "MarketScan"
        }
        val url = "$activeUrl/chat/completions"

        // Some aggregators reject response_format; retry once without it and let salvageJsonObject() recover braces.
        for (attempt in 0..1) {
            val payload = linkedMapOf<String, Any?>(
                "model" to activeModel,
                "messages" to messages,
                "temperature" to 0.1,
                "max_tokens" to config.maxTokens
            )
            if (attempt == 0) {
                payload["response_format"] = mapOf("type" to "json_object")
            }
            try {
                val resp = transport.postJson(url, headers, mapAdapter.toJson(payload), config.timeoutSec)
                if (resp.code in 200..299) {
                    val parsed = parseJsonMap(resp.body)
                    val choices = parsed?.get("choices") as? List<*>
                    val firstChoice = choices?.firstOrNull() as? Map<*, *>
                    val message = firstChoice?.get("message") as? Map<*, *>
                    val content = message?.get("content") as? String
                    if (!content.isNullOrBlank()) {
                        return content
                    }
                    logEvent("LLM_FAIL", mapOf("err" to "empty choices payload"))
                    return null
                }
                val errMsg = "HTTP ${resp.code}: ${resp.body.take(120)}"
                if (attempt == 0 && (resp.code == 400 || errMsg.contains("response_format", ignoreCase = true))) {
                    continue
                }
                logEvent("LLM_FAIL", mapOf("err" to errMsg.take(140)))
                return null
            } catch (exc: Exception) {
                val msg = (exc.message ?: exc.javaClass.simpleName).take(140)
                if (attempt == 0 && (msg.contains("400") || msg.contains("response_format", ignoreCase = true))) {
                    continue
                }
                logEvent("LLM_FAIL", mapOf("err" to msg))
                return null
            }
        }
        return null
    }

    private fun parseJsonMap(raw: String): Map<String, Any?>? {
        return try {
            mapAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    internal fun salvageJsonObject(raw: String?): Map<String, Any?>? {
        if (raw.isNullOrBlank()) return null
        parseJsonMap(raw)?.let { return it }
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a >= 0 && b > a) {
            return parseJsonMap(raw.substring(a, b + 1))
        }
        return null
    }

    private suspend fun chatJson(system: String, user: String): Map<String, Any?>? =
        salvageJsonObject(chat(system, user))

    // ── Finnhub company news (18h decay window) ───────────────────────────

    suspend fun headlines(
        symbol: String,
        hours: Double = DEFAULT_NEWS_WINDOW_HOURS,
        forceRefresh: Boolean = false
    ): List<AdvisorNewsItem> {
        val sym = symbol.trim().uppercase(Locale.US)
        val activeFinnhubKey = effectiveFinnhubKey
        if (sym.isEmpty() || activeFinnhubKey.isEmpty()) {
            return emptyList()
        }
        val nowMs = clockMs()
        if (!forceRefresh) {
            cacheMutex.withLock {
                val hit = newsCache[sym]
                if (hit != null && (nowMs - hit.first) < CACHE_TTL_MS) {
                    return hit.second
                }
            }
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val toDate = dateFmt.format(Date(nowMs))
        val fromDate = dateFmt.format(Date(nowMs - 3L * 24L * 3_600_000L))
        val encodedSym = URLEncoder.encode(sym, "UTF-8")
        val encodedToken = URLEncoder.encode(activeFinnhubKey, "UTF-8")
        val url = "https://finnhub.io/api/v1/company-news?symbol=$encodedSym&from=$fromDate&to=$toDate&token=$encodedToken"

        val resp = try {
            transport.get(url, FINNHUB_TIMEOUT_SEC)
        } catch (exc: Exception) {
            logEvent("NEWS_FAIL", mapOf("symbol" to sym, "err" to (exc.message ?: exc.javaClass.simpleName).take(110)))
            return emptyList()
        }
        if (resp.code !in 200..299) {
            logEvent("NEWS_FAIL", mapOf("symbol" to sym, "err" to "HTTP ${resp.code}"))
            return emptyList()
        }

        val rawList = try {
            listAdapter.fromJson(resp.body)
        } catch (_: Exception) {
            logEvent("NEWS_FAIL", mapOf("symbol" to sym, "err" to "invalid news json"))
            return emptyList()
        } ?: return emptyList()

        val out = ArrayList<AdvisorNewsItem>()
        val limit = min(rawList.size, 30)
        for (i in 0 until limit) {
            val item = rawList[i] as? Map<*, *> ?: continue
            val dtSec = extractDouble(item["datetime"]) ?: continue
            if (!dtSec.isFinite() || dtSec <= 0.0) continue
            val pubMs = (dtSec * 1000.0).toLong()
            val ageHours = (nowMs - pubMs).toDouble() / 3_600_000.0
            if (ageHours > hours || ageHours < -1.0) continue
            val head = extractString(item["headline"]).trim()
            if (head.isEmpty()) continue
            out.add(
                AdvisorNewsItem(
                    symbol = sym,
                    headline = head,
                    publishedMs = pubMs,
                    ageHours = round2(ageHours),
                    source = extractString(item["source"]).take(24),
                    url = extractString(item["url"])
                )
            )
        }

        val freshest = out.sortedBy { it.ageHours }.take(4)
        cacheMutex.withLock {
            newsCache[sym] = nowMs to freshest
        }
        return freshest
    }

    // ── Veto: reduce risk only ────────────────────────────────────────────

    suspend fun newsVeto(
        symbol: String,
        hours: Double = DEFAULT_NEWS_WINDOW_HOURS,
        forceRefresh: Boolean = false
    ): AdvisorVetoVerdict {
        val sym = symbol.trim().uppercase(Locale.US)
        val nowMs = clockMs()
        val none = AdvisorVetoVerdict.none(sym, advisorEnabled = enabled, nowMs = nowMs)
        if (!enabled || sym.isEmpty()) {
            return none
        }

        if (!forceRefresh) {
            cacheMutex.withLock {
                val hit = vetoCache[sym]
                if (hit != null && (nowMs - hit.first) < CACHE_TTL_MS) {
                    return hit.second
                }
            }
        }

        val items = headlines(sym, hours, forceRefresh = forceRefresh)
        if (items.isEmpty()) {
            cacheMutex.withLock { vetoCache[sym] = nowMs to none }
            return none
        }

        val body = items.joinToString("\n") { n ->
            "- [${"%.1f".format(Locale.US, n.ageHours)}h ago, ${n.source}] ${n.headline}"
        }

        val out = chatJson(
            system = VETO_SYSTEM_PROMPT,
            user = "Symbol: $sym\nHeadlines:\n$body"
        )

        if (out == null || !extractString(out["action"]).equals("veto", ignoreCase = true)) {
            cacheMutex.withLock { vetoCache[sym] = nowMs to none }
            return none
        }

        val conf = extractDouble(out["confidence"])?.takeIf { it.isFinite() } ?: 0.0
        if (conf < MIN_VETO_CONFIDENCE) {
            cacheMutex.withLock { vetoCache[sym] = nowMs to none }
            return none
        }

        val weight = round3(min(conf, 1.0))
        val category = extractString(out["category"]).ifBlank { "?" }.take(40)
        val why = extractString(out["why"]).take(160)
        val verdict = AdvisorVetoVerdict(
            symbol = sym,
            action = AdvisorVetoAction.VETO,
            weight = weight,
            category = category,
            why = why,
            headline = items.first().headline.take(120),
            llmTag = "veto",
            checkedAtMs = nowMs
        )
        cacheMutex.withLock { vetoCache[sym] = nowMs to verdict }
        logEvent(
            "LLM_VETO",
            mapOf(
                "symbol" to sym,
                "weight" to "%.3f".format(Locale.US, weight),
                "category" to category
            )
        )
        return verdict
    }

    // ── Journal: zero risk, immediate value ───────────────────────────────

    suspend fun journal(entries: List<TradeLogEntry>): AdvisorJournalReport? {
        val closed = entries
            .filter { it.pnl != null }
            .sortedBy { it.timestampMs }
            .takeLast(MAX_JOURNAL_TRADES)
        if (closed.isEmpty() || !enabled) {
            return null
        }

        val slim = closed.map { r ->
            linkedMapOf<String, Any?>(
                "s" to r.symbol,
                "why" to r.note,
                "pnl" to r.pnl,
                "r" to r.rMultiple,
                "slip" to 0.0,
                "regime" to r.regime.ifBlank { "UNKNOWN" },
                "llm" to r.llmVerdict.ifBlank { "off" }
            )
        }

        val out = chatJson(
            system = JOURNAL_SYSTEM_PROMPT,
            user = listAdapter.toJson(slim)
        ) ?: return null

        val headline = extractString(out["headline"]).trim()
            .ifBlank { "Systematic book review (${closed.size} trades)" }
        val obs = extractStringList(out["observations"]).take(4)
        val concerns = extractStringList(out["concerns"]).take(4)

        logEvent("LLM_JOURNAL", mapOf("trades" to closed.size.toString()))
        return AdvisorJournalReport(
            headline = headline,
            observations = obs,
            concerns = concerns,
            sampleSize = closed.size,
            generatedAtMs = clockMs()
        )
    }

    /**
     * Batch-scores Finnhub financial news articles into directional catalyst assessments
     * using the configured OpenAI-compatible LLM endpoint. Fail-soft: returns empty map on error.
     */
    suspend fun scoreFinnhubArticles(
        articles: List<RawFinnhubArticle>
    ): Map<Int, AiCatalystAssessment> {
        if (!enabled || articles.isEmpty()) return emptyMap()
        val batch = articles.take(18)
        val lines = batch.mapIndexed { idx, art ->
            val cleanSummary = art.summary.replace('\n', ' ').trim().take(160)
            "$idx | ${art.symbol} | ${art.source} | ${art.headline}${if (cleanSummary.isNotBlank()) " — $cleanSummary" else ""}"
        }.joinToString("\n")
        val prompt = "Score each financial news article for market catalyst impact:\n$lines"
        val res = chatJson(CATALYST_SCORE_SYSTEM_PROMPT, prompt) ?: return emptyMap()
        val rawItems = res["items"] as? List<*> ?: return emptyMap()
        val out = LinkedHashMap<Int, AiCatalystAssessment>()
        rawItems.forEach { entry ->
            val m = entry as? Map<*, *> ?: return@forEach
            val idx = extractDouble(m["id"])?.toInt() ?: return@forEach
            if (idx !in batch.indices) return@forEach
            val dir = (extractDouble(m["direction"]) ?: 0.0).coerceIn(-1.0, 1.0)
            val mat = (extractDouble(m["materiality"]) ?: 0.35).coerceIn(0.0, 1.0)
            val conf = (extractDouble(m["confidence"]) ?: 0.75).coerceIn(0.25, 1.0)
            val tag = extractString(m["tag"]).trim().uppercase(Locale.US).take(14)
            val rationale = extractString(m["rationale"]).trim().take(160)
            out[idx] = AiCatalystAssessment(
                direction = dir,
                materiality = mat,
                confidence = conf,
                catalystTag = tag,
                rationale = rationale
            )
        }
        return out
    }

    /**
     * Exact `crypto_events.py` / `cryptotrader.py 2.0` categorical crypto headline classifier.
     * Pre-filters with [CryptoTraderEngine.RISK_WORDS] so zero LLM tokens are spent on routine
     * headlines. Fails open to [CryptoEventCategory.NONE] on any error or low confidence (< 0.65).
     */
    suspend fun classifyCryptoHeadline(headline: String): CryptoEventClassification {
        val clean = headline.trim()
        if (clean.isEmpty() || !CryptoTraderEngine.hasRiskKeyword(clean)) {
            return CryptoEventClassification.none(clean)
        }
        if (!enabled) {
            return CryptoTraderEngine.classifyHeadlineDeterministic(clean)
        }
        val parsed = runCatching {
            chatJson(
                system = CryptoTraderEngine.EVENT_PROMPT,
                user = clean.take(400)
            )
        }.getOrNull() ?: return CryptoTraderEngine.classifyHeadlineDeterministic(clean)

        val cat = CryptoEventCategory.fromWire(extractString(parsed["category"]))
        val conf = (extractDouble(parsed["confidence"]) ?: 0.0).coerceIn(0.0, 1.0)
        if (cat != CryptoEventCategory.NONE && conf < 0.65) {
            return CryptoEventClassification.none(clean)
        }
        val span = extractString(parsed["span"]).trim().take(80)
        if (cat != CryptoEventCategory.NONE) {
            logEvent(
                "CRYPTO_EVENT_GATE",
                mapOf(
                    "category" to cat.name,
                    "confidence" to "%.2f".format(Locale.US, conf),
                    "mult" to "%.2f".format(Locale.US, cat.mult),
                    "block" to cat.block.toString()
                )
            )
        }
        return CryptoEventClassification(
            category = cat,
            mult = cat.mult,
            block = cat.block,
            confidence = (conf * 100.0).roundToInt() / 100.0,
            span = span,
            headline = clean.take(220),
            viaLlm = true
        )
    }

    private fun extractString(value: Any?): String = when (value) {
        is String -> value
        is Number -> value.toString()
        else -> ""
    }

    private fun extractDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    private fun extractStringList(value: Any?): List<String> {
        val raw = value as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            extractString(item).trim().takeIf { it.isNotEmpty() }
        }
    }

    companion object {
        const val DEFAULT_NEWS_WINDOW_HOURS = 18.0
        const val CACHE_TTL_MS = 1800L * 1000L
        const val MIN_VETO_CONFIDENCE = 0.70
        const val MAX_JOURNAL_TRADES = 25
        private const val FINNHUB_TIMEOUT_SEC = 8.0
        private const val MAX_EVENTS = 60

        private const val VETO_SYSTEM_PROMPT =
            "You screen headlines for a long-only momentum book. Return JSON only: " +
                "{\"action\":\"none\"|\"veto\",\"confidence\":0.0-1.0,\"category\":str,\"why\":str}. " +
                "veto ONLY for a material adverse event likely to gap the stock down: " +
                "fraud or accounting restatement, guidance cut or profit warning, trading halt, " +
                "delisting or going-concern notice, major regulatory or criminal action, " +
                "sudden CEO/CFO departure, failed pivotal trial, catastrophic litigation, " +
                "credit downgrade to junk. " +
                "NEVER veto for: analyst upgrades or downgrades, price-target changes, " +
                "initiation of coverage, 'shares rise/fall' recaps, index or ETF flows, " +
                "insider sales under a pre-arranged plan, sector-wide moves, or anything routine. " +
                "If the headlines are ordinary, action must be none. Do not infer facts that are " +
                "not in the text. Do not use outside knowledge about the company."

        private const val JOURNAL_SYSTEM_PROMPT =
            "You review a systematic long-only momentum book. Reply as JSON: " +
                "{\"headline\":str,\"observations\":[str],\"concerns\":[str]}. " +
                "Be quantitative and specific — name symbols, cite the numbers given, " +
                "compare wins against losses. Never recommend changing position size, " +
                "leverage, or the strategy's rules. Max 4 items per list. " +
                "If the sample is too small to say anything, say so."

        private const val CATALYST_SCORE_SYSTEM_PROMPT =
            "You are a systematic equity news analyst. Output JSON only: " +
                "{\"items\":[{\"id\":int,\"direction\":float(-1..1),\"materiality\":float(0..1)," +
                "\"confidence\":float(0.25..1),\"tag\":str,\"rationale\":str}]}. " +
                "Score ONLY what is stated; never invent facts. " +
                "materiality 0=noise, 1=thesis-changing. Analyst target tweaks <=0.30. " +
                "tag must be one of EARNINGS, GUIDANCE, REGULATORY, M&A, ANALYST, MACRO, PRODUCT, MARKET. " +
                "rationale <=14 words."

        private fun round2(x: Double): Double = (x * 100.0).roundToInt() / 100.0
        private fun round3(x: Double): Double = (x * 1000.0).roundToInt() / 1000.0

        /**
         * Rule #3 population audit: compares `clear`, `off`, and `veto` closed-trade
         * populations so the trader can falsify whether the LLM advisor actually helped.
         */
        fun buildPopulationReport(entries: List<TradeLogEntry>): AdvisorPopulationReport {
            val closed = entries.filter { it.pnl != null }
            val vetoEvents = entries.count { it.type.equals("VETO", ignoreCase = true) }
            val order = listOf("clear", "off", "veto")
            val grouped = closed.groupBy { it.llmVerdict.trim().lowercase(Locale.US).ifBlank { "off" } }
            val buckets = order.mapNotNull { tag ->
                val list = grouped[tag].orEmpty()
                if (list.isEmpty() && tag == "veto") return@mapNotNull null
                val wins = list.count { (it.pnl ?: 0.0) > 0.0 }
                val winRate = if (list.isNotEmpty()) round2((wins.toDouble() / list.size) * 100.0) else 0.0
                val pnlSum = round2(list.sumOf { it.pnl ?: 0.0 })
                val rVals = list.mapNotNull { it.rMultiple }
                val avgR = if (rVals.isNotEmpty()) round2(rVals.average()) else null
                AdvisorPopulationBucket(
                    llmTag = tag,
                    tradeCount = list.size,
                    winRatePct = winRate,
                    totalPnl = pnlSum,
                    avgRMultiple = avgR
                )
            }
            return AdvisorPopulationReport(
                buckets = buckets,
                totalClosedTrades = closed.size,
                vetoEventsLogged = vetoEvents
            )
        }
    }
}
