package com.example.data.service

import com.example.BuildConfig
import com.example.domain.engine.AdvisorHttpTransport
import com.example.domain.engine.AiProviderCatalystRun
import com.example.domain.engine.AiProviderRunStatus
import com.example.domain.engine.LlmHeadlineCatalyst
import com.example.domain.engine.ModelRadarEngine
import com.example.domain.engine.OkHttpAdvisorTransport
import com.example.domain.engine.RawTickerHeadline
import com.example.domain.engine.TickerCatalystBatchResult
import com.example.domain.engine.TickerCatalystEngine
import com.example.domain.engine.TickerCatalystReport
import com.example.domain.engine.CryptoFeedsEngine
import com.example.domain.model.AiProviderId
import com.example.domain.model.AppConfig
import com.example.domain.model.MarketMode
import com.example.domain.model.NewsHeadline
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Service that fetches the latest market headlines for individual tickers and uses the
 * configured LLM (Google Gemini or any enabled OpenRouter / AIHubMix / DeepSeek / Groq model)
 * to calculate a per-ticker Catalyst Score (-100..+100) and Impact Level (CRITICAL / HIGH /
 * MODERATE / LOW).
 *
 * Headline feed priority per ticker:
 * 1. Finnhub Company News API (when a Finnhub API key is configured)
 * 2. Alpaca News API (when live Alpaca credentials are configured)
 * 3. Yahoo Finance Ticker RSS Feed (universal live public market headlines, no key required)
 * 4. Cached scanned headlines (only if live network feeds fail)
 */
class MarketCatalystService(
    private val transport: AdvisorHttpTransport = OkHttpAdvisorTransport(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()
    private val listAdapter: JsonAdapter<List<Map<String, Any?>>> = moshi.adapter(
        Types.newParameterizedType(
            List::class.java,
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
    )

    private data class ResolvedLlmTarget(
        val provider: AiProviderId,
        val modelId: String,
        val apiKey: String
    ) {
        val label: String get() = "${provider.displayName.uppercase(Locale.US)} · ${shortModelName(modelId)}"
    }

    private data class TickerHeadlineFetch(
        val symbol: String,
        val headlines: List<RawTickerHeadline>,
        val sourceLabel: String
    )

    /**
     * Fetches the latest market headlines for [tickers] in parallel, runs LLM catalyst
     * scoring across all fetched headlines, and returns ranked [TickerCatalystReport] entries.
     */
    suspend fun analyzeTickers(
        tickers: List<String>,
        config: AppConfig,
        fallbackHeadlines: List<NewsHeadline> = emptyList(),
        maxHeadlinesPerTicker: Int = 4
    ): TickerCatalystBatchResult = withContext(Dispatchers.IO) {
        val nowMs = clockMs()
        val cleanTickers = tickers
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .ifEmpty { TickerCatalystEngine.DEFAULT_TICKERS }

        val finnhubKey = resolveFinnhubKey(config)
        val hasAlpacaLive = !config.demo && config.alpacaId.isNotBlank() && config.alpacaSecret.isNotBlank()

        // 1. Fetch latest headlines per ticker in parallel
        val fetchedByTicker: List<TickerHeadlineFetch> = coroutineScope {
            cleanTickers.map { sym ->
                async {
                    fetchTickerHeadlines(
                        symbol = sym,
                        finnhubKey = finnhubKey,
                        hasAlpacaLive = hasAlpacaLive,
                        config = config,
                        fallbackHeadlines = fallbackHeadlines,
                        maxItems = maxHeadlinesPerTicker,
                        nowMs = nowMs
                    )
                }
            }.awaitAll()
        }

        val allHeadlines = fetchedByTicker.flatMap { it.headlines }
        val dominantFeed = fetchedByTicker
            .filter { it.headlines.isNotEmpty() }
            .groupingBy { it.sourceLabel }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "LIVE FEED"

        // 2. Dispatch the catalyst scoring prompt in parallel across ALL enabled & configured AIs
        val resolution = resolveAllLlmTargets(config)
        val readyTargets = resolution.readyTargets
        val providerRunsById = LinkedHashMap<AiProviderId, AiProviderCatalystRun>()
        resolution.skippedRuns.forEach { providerRunsById[it.provider] = it }

        val scoresByProvider = LinkedHashMap<AiProviderId, Map<String, LlmHeadlineCatalyst>>()
        val modelIdByProvider = LinkedHashMap<AiProviderId, String>()
        val failedMessages = mutableListOf<String>()

        if (allHeadlines.isNotEmpty() && readyTargets.isNotEmpty()) {
            val headlinesToScore = allHeadlines.take(32)
            val outcomes = coroutineScope {
                readyTargets.map { target ->
                    async {
                        val startedMs = clockMs()
                        try {
                            val rawResponse = requestLlmCatalystScores(
                                target = target,
                                headlines = headlinesToScore
                            )
                            val parsed = TickerCatalystEngine.parseLlmCatalystJson(rawResponse)
                            val elapsed = (clockMs() - startedMs).coerceAtLeast(0L)
                            if (parsed.isNotEmpty()) {
                                Triple(
                                    target,
                                    parsed,
                                    AiProviderCatalystRun(
                                        provider = target.provider,
                                        providerName = target.provider.displayName,
                                        modelId = target.modelId,
                                        status = AiProviderRunStatus.SCORED,
                                        scoredHeadlineCount = parsed.size,
                                        latencyMs = elapsed
                                    )
                                )
                            } else {
                                Triple(
                                    target,
                                    emptyMap<String, LlmHeadlineCatalyst>(),
                                    AiProviderCatalystRun(
                                        provider = target.provider,
                                        providerName = target.provider.displayName,
                                        modelId = target.modelId,
                                        status = AiProviderRunStatus.FAILED,
                                        latencyMs = elapsed,
                                        errorMessage = "Unparseable JSON response"
                                    )
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val elapsed = (clockMs() - startedMs).coerceAtLeast(0L)
                            Triple(
                                target,
                                emptyMap<String, LlmHeadlineCatalyst>(),
                                AiProviderCatalystRun(
                                    provider = target.provider,
                                    providerName = target.provider.displayName,
                                    modelId = target.modelId,
                                    status = AiProviderRunStatus.FAILED,
                                    latencyMs = elapsed,
                                    errorMessage = e.message?.take(60) ?: "Request failed"
                                )
                            )
                        }
                    }
                }.awaitAll()
            }

            outcomes.forEach { (target, parsed, run) ->
                providerRunsById[target.provider] = run
                if (parsed.isNotEmpty()) {
                    scoresByProvider[target.provider] = parsed
                    modelIdByProvider[target.provider] = target.modelId
                } else {
                    failedMessages.add("${target.provider.displayName}: ${run.errorMessage ?: "error"}")
                }
            }
        } else {
            // Preserve READY status when there were no headlines to score
            readyTargets.forEach { target ->
                providerRunsById[target.provider] = AiProviderCatalystRun(
                    provider = target.provider,
                    providerName = target.provider.displayName,
                    modelId = target.modelId,
                    status = AiProviderRunStatus.READY
                )
            }
        }

        val orderedRuns = AiProviderId.entries.map { provider ->
            providerRunsById[provider] ?: AiProviderCatalystRun(
                provider = provider,
                providerName = provider.displayName,
                modelId = TickerCatalystEngine.defaultModelIdFor(provider),
                status = AiProviderRunStatus.NO_KEY
            )
        }

        val activeAiCount = scoresByProvider.size
        val usedLlm = activeAiCount > 0
        val scorerLabel = when {
            activeAiCount > 1 -> "$activeAiCount/${AiProviderId.entries.size} AIs CONSENSUS"
            activeAiCount == 1 -> {
                val singleProvider = scoresByProvider.keys.first()
                val singleModel = modelIdByProvider[singleProvider].orEmpty()
                "${singleProvider.displayName.uppercase(Locale.US)} · ${shortModelName(singleModel)}"
            }
            else -> "QUANT FALLBACK"
        }

        val warning = when {
            readyTargets.isEmpty() && allHeadlines.isNotEmpty() ->
                "No enabled AI provider has an API key yet — open AI Hub to add keys for Gemini, OpenRouter, DeepSeek, Groq, or AIHubMix."
            !usedLlm && failedMessages.isNotEmpty() ->
                "AI scoring unavailable (${failedMessages.first()}) — showing quantitative fallback."
            usedLlm && failedMessages.isNotEmpty() ->
                "Consensus from $activeAiCount/${readyTargets.size} enabled AIs (${failedMessages.size} errored: ${failedMessages.first()})."
            else -> null
        }

        // 3. Aggregate per-ticker Multi-AI Consensus Catalyst Scores & Impact Ratings
        val reports: List<TickerCatalystReport> = fetchedByTicker.map { fetch ->
            TickerCatalystEngine.aggregateTickerMultiAi(
                symbol = fetch.symbol,
                rawHeadlines = fetch.headlines,
                scoresByProvider = scoresByProvider,
                modelIdByProvider = modelIdByProvider,
                scorerLabel = scorerLabel,
                headlineSource = fetch.sourceLabel,
                nowMs = nowMs
            )
        }.sortedWith(
            compareByDescending<TickerCatalystReport> { it.impactLevel.rank }
                .thenByDescending { it.impactScore }
                .thenByDescending { kotlin.math.abs(it.catalystScore) }
        )

        TickerCatalystBatchResult(
            reports = reports,
            scorerLabel = scorerLabel,
            usedLlm = usedLlm,
            headlineSourceLabel = dominantFeed,
            fetchedAtMs = nowMs,
            warning = warning,
            providerRuns = orderedRuns,
            activeAiCount = activeAiCount,
            totalImplementedAiCount = AiProviderId.entries.size
        )
    }

    private suspend fun fetchTickerHeadlines(
        symbol: String,
        finnhubKey: String,
        hasAlpacaLive: Boolean,
        config: AppConfig,
        fallbackHeadlines: List<NewsHeadline>,
        maxItems: Int,
        nowMs: Long
    ): TickerHeadlineFetch {
        // Tier 0 (Crypto): CryptoPanic public/token news feed
        if (symbol.contains("/") || config.marketMode == MarketMode.CRYPTO) {
            val baseCurrency = CryptoFeedsEngine.normalizeCryptoSymbol(symbol).substringBefore("/")
            val cryptoItems = runCatching {
                val cryptoSvc = CryptoMarketService(transport = transport, clockMs = clockMs)
                val posts = cryptoSvc.fetchCryptoPanicPosts(
                    currencies = baseCurrency,
                    token = config.cryptoPanicToken,
                    limit = maxItems,
                    nowMs = nowMs
                )
                posts.mapIndexed { idx, p ->
                    val pubMs = (nowMs - (p.ageHours * 3_600_000.0).toLong()).coerceAtLeast(0L)
                    RawTickerHeadline(
                        id = "CP_${symbol}_${idx}_${p.title.hashCode()}",
                        symbol = symbol,
                        headline = p.title,
                        summary = "Community votes: ${p.positive}+ / ${p.negative}- · ${p.important} important",
                        source = p.source.ifBlank { "CryptoPanic" },
                        url = p.url,
                        publishedAtMs = pubMs
                    )
                }
            }.getOrDefault(emptyList())
            if (cryptoItems.isNotEmpty()) {
                return TickerHeadlineFetch(symbol, cryptoItems, "CRYPTOPANIC")
            }
        }

        // Tier 1: Finnhub Company News (equities)
        if (finnhubKey.isNotBlank() && !symbol.contains("/")) {
            val finnhubItems = runCatching {
                fetchFinnhubCompanyNews(symbol, finnhubKey, maxItems, nowMs)
            }.getOrDefault(emptyList())
            if (finnhubItems.isNotEmpty()) {
                return TickerHeadlineFetch(symbol, finnhubItems, "FINNHUB")
            }
        }

        // Tier 2: Alpaca Live News (equities)
        if (hasAlpacaLive && !symbol.contains("/")) {
            val alpacaItems = runCatching {
                fetchAlpacaTickerNews(symbol, config.alpacaId, config.alpacaSecret, maxItems, nowMs)
            }.getOrDefault(emptyList())
            if (alpacaItems.isNotEmpty()) {
                return TickerHeadlineFetch(symbol, alpacaItems, "ALPACA NEWS")
            }
        }

        // Tier 3: Yahoo Finance Ticker RSS (no API key required; BTC/USD -> BTC-USD)
        val yahooItems = runCatching {
            fetchYahooRssNews(symbol, maxItems, nowMs)
        }.getOrDefault(emptyList())
        if (yahooItems.isNotEmpty()) {
            return TickerHeadlineFetch(symbol, yahooItems, "YAHOO RSS")
        }

        // Tier 4: Cached scan headlines fallback
        val cached = fallbackHeadlines
            .filter { it.sym.equals(symbol, ignoreCase = true) && it.title.isNotBlank() }
            .take(maxItems)
            .mapIndexed { idx, h ->
                val pubMs = runCatching { Instant.parse(h.publishedIso).toEpochMilli() }
                    .getOrDefault(nowMs - idx * 3_600_000L)
                RawTickerHeadline(
                    id = "CACHE_${symbol}_${idx}_${h.title.hashCode()}",
                    symbol = symbol,
                    headline = h.title,
                    summary = h.summary,
                    source = h.source.ifBlank { "Market Scan" },
                    url = "",
                    publishedAtMs = pubMs
                )
            }
        return TickerHeadlineFetch(
            symbol = symbol,
            headlines = cached,
            sourceLabel = if (cached.isNotEmpty()) "SCAN CACHE" else "NO FEED"
        )
    }

    private suspend fun fetchFinnhubCompanyNews(
        symbol: String,
        finnhubKey: String,
        maxItems: Int,
        nowMs: Long
    ): List<RawTickerHeadline> {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val toDate = df.format(Date(nowMs))
        val fromDate = df.format(Date(nowMs - 7L * 24L * 3_600_000L))
        val encodedSym = URLEncoder.encode(symbol, "UTF-8")
        val encodedKey = URLEncoder.encode(finnhubKey, "UTF-8")
        val url = "https://finnhub.io/api/v1/company-news?symbol=$encodedSym&from=$fromDate&to=$toDate&token=$encodedKey"
        val resp = transport.get(url, timeoutSec = 10.0)
        if (resp.code !in 200..299 || resp.body.isBlank()) return emptyList()

        val rows = runCatching { listAdapter.fromJson(resp.body) }.getOrNull() ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<RawTickerHeadline>()
        for (row in rows) {
            if (out.size >= maxItems) break
            val title = row["headline"]?.toString()?.trim().orEmpty()
            if (title.isBlank() || !seen.add(title.lowercase(Locale.US))) continue
            val idRaw = row["id"]?.toString()?.trim().orEmpty().ifBlank { "${title.hashCode()}" }
            val dtSec = (row["datetime"] as? Number)?.toLong() ?: 0L
            out.add(
                RawTickerHeadline(
                    id = "FH_${symbol}_$idRaw",
                    symbol = symbol,
                    headline = title,
                    summary = row["summary"]?.toString()?.trim().orEmpty().take(280),
                    source = row["source"]?.toString()?.trim().orEmpty().ifBlank { "Finnhub" },
                    url = row["url"]?.toString()?.trim().orEmpty(),
                    publishedAtMs = if (dtSec > 0L) dtSec * 1000L else nowMs
                )
            )
        }
        return out
    }

    private suspend fun fetchAlpacaTickerNews(
        symbol: String,
        apiKey: String,
        apiSecret: String,
        maxItems: Int,
        nowMs: Long
    ): List<RawTickerHeadline> {
        // Use OkHttp directly only when Alpaca headers are needed via standard client
        val encodedSym = URLEncoder.encode(symbol, "UTF-8")
        val url = "https://data.alpaca.markets/v1beta1/news?symbols=$encodedSym&limit=$maxItems&sort=desc"
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val req = okhttp3.Request.Builder()
            .url(url)
            .addHeader("APCA-API-KEY-ID", apiKey.trim())
            .addHeader("APCA-API-SECRET-KEY", apiSecret.trim())
            .get()
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string().orEmpty()
        }
        val payload = runCatching { mapAdapter.fromJson(body) }.getOrNull() ?: return emptyList()
        val newsList = payload["news"] as? List<*> ?: return emptyList()
        val out = ArrayList<RawTickerHeadline>()
        for ((idx, item) in newsList.withIndex()) {
            if (out.size >= maxItems) break
            val row = item as? Map<*, *> ?: continue
            val title = row["headline"]?.toString()?.trim().orEmpty()
            if (title.isBlank()) continue
            val iso = row["created_at"]?.toString().orEmpty()
            val pubMs = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(nowMs - idx * 3_600_000L)
            out.add(
                RawTickerHeadline(
                    id = "ALP_${symbol}_${row["id"] ?: idx}",
                    symbol = symbol,
                    headline = title,
                    summary = row["summary"]?.toString()?.trim().orEmpty().take(280),
                    source = row["source"]?.toString()?.trim().orEmpty().ifBlank { "Alpaca" },
                    url = row["url"]?.toString()?.trim().orEmpty(),
                    publishedAtMs = pubMs
                )
            )
        }
        return out
    }

    private suspend fun fetchYahooRssNews(
        symbol: String,
        maxItems: Int,
        nowMs: Long
    ): List<RawTickerHeadline> {
        val yahooSym = if (symbol.contains("/")) symbol.replace("/", "-") else symbol
        val encodedSym = URLEncoder.encode(yahooSym, "UTF-8")
        val url = "https://feeds.finance.yahoo.com/rss/2.0/headline?s=$encodedSym&region=US&lang=en-US"
        val resp = transport.get(url, timeoutSec = 10.0)
        if (resp.code !in 200..299 || resp.body.isBlank()) return emptyList()
        return TickerCatalystEngine.parseYahooRssXml(resp.body, symbol, nowMs, maxItems)
    }

    private suspend fun requestLlmCatalystScores(
        target: ResolvedLlmTarget,
        headlines: List<RawTickerHeadline>
    ): String {
        val systemPrompt = """
            You are an institutional equity event-catalyst analyst.
            Evaluate each numbered market headline for its specific ticker using ONLY the supplied headline and summary.
            Return STRICT JSON only (no markdown fences, no commentary) matching:
            {"items":[{"id":"<exact_id>","direction":<-1.0..1.0>,"materiality":<0.0..1.0>,"confidence":<0.25..1.0>,"impact_level":"CRITICAL|HIGH|MODERATE|LOW","catalyst_tag":"EARNINGS|GUIDANCE|REGULATORY|M&A|ANALYST|PRODUCT|MACRO|MARKET","rationale":"<=16 words factual summary"}]}
            Rules:
            - CRITICAL: earnings shock, guidance revision, FDA/SEC/DOJ enforcement, M&A buyout, accounting/fraud.
            - HIGH: major analyst rating/target shift, flagship product/contract win or loss, executive change.
            - MODERATE: sector read-through, incremental commentary, minor upgrades/downgrades.
            - LOW: routine market wrap, recycled commentary, non-material noise.
            - Return every headline id you received.
        """.trimIndent()

        val lines = headlines.joinToString("\n") { h ->
            val summaryClean = h.summary.replace("\n", " ").take(140)
            val headlineClean = h.headline.replace("\n", " ").take(180)
            "id=${h.id} | ticker=${h.symbol} | source=${h.source} | headline=$headlineClean" +
                if (summaryClean.isNotBlank()) " | summary=$summaryClean" else ""
        }
        val userPrompt = "Score these latest market headlines and assign impact_level for each ticker:\n$lines"

        return if (target.provider == AiProviderId.GEMINI) {
            callGeminiJson(target, systemPrompt, userPrompt)
        } else {
            callOpenAiCompatibleJson(target, systemPrompt, userPrompt)
        }
    }

    private suspend fun callGeminiJson(
        target: ResolvedLlmTarget,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val cleanModel = target.modelId.removePrefix("models/").ifBlank { "gemini-2.5-flash" }
        val encodedKey = URLEncoder.encode(target.apiKey, "UTF-8")
        val url = "${AiProviderId.GEMINI.baseUrl}/v1beta/models/$cleanModel:generateContent?key=$encodedKey"
        val bodyMap = mapOf(
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to userPrompt))
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,
                "maxOutputTokens" to 1800,
                "responseMimeType" to "application/json"
            )
        )
        val resp = transport.postJson(
            url = url,
            headers = mapOf("Accept" to "application/json"),
            jsonBody = mapAdapter.toJson(bodyMap),
            timeoutSec = 20.0
        )
        if (resp.code !in 200..299) {
            throw IllegalStateException("Gemini HTTP ${resp.code}")
        }
        val parsed = mapAdapter.fromJson(resp.body) ?: throw IllegalStateException("Empty Gemini reply")
        val candidates = parsed["candidates"] as? List<*>
        val first = candidates?.firstOrNull() as? Map<*, *>
        val content = first?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val firstPart = parts?.firstOrNull() as? Map<*, *>
        return firstPart?.get("text")?.toString().orEmpty()
    }

    private suspend fun callOpenAiCompatibleJson(
        target: ResolvedLlmTarget,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val url = "${target.provider.baseUrl.trimEnd('/')}/chat/completions"
        val headers = LinkedHashMap<String, String>()
        headers["Authorization"] = "Bearer ${target.apiKey}"
        headers["Accept"] = "application/json"
        if (target.provider == AiProviderId.OPENROUTER) {
            headers["HTTP-Referer"] = "https://marketscan.app"
            headers["X-Title"] = "Market Scan Catalyst Service"
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        val bodyWithFormat = mapOf(
            "model" to target.modelId,
            "messages" to messages,
            "temperature" to 0.1,
            "max_tokens" to 1800,
            "response_format" to mapOf("type" to "json_object")
        )
        var resp = transport.postJson(url, headers, mapAdapter.toJson(bodyWithFormat), timeoutSec = 22.0)
        // Some models reject response_format=json_object; retry cleanly without it on 400
        if (resp.code == 400) {
            val bodyPlain = mapOf(
                "model" to target.modelId,
                "messages" to messages,
                "temperature" to 0.1,
                "max_tokens" to 1800
            )
            resp = transport.postJson(url, headers, mapAdapter.toJson(bodyPlain), timeoutSec = 22.0)
        }
        if (resp.code !in 200..299) {
            throw IllegalStateException("${target.provider.displayName} HTTP ${resp.code}")
        }
        val parsed = mapAdapter.fromJson(resp.body) ?: throw IllegalStateException("Empty LLM reply")
        val choices = parsed["choices"] as? List<*>
        val first = choices?.firstOrNull() as? Map<*, *>
        val msg = first?.get("message") as? Map<*, *>
        return msg?.get("content")?.toString().orEmpty()
    }

    private data class ResolvedAllTargets(
        val readyTargets: List<ResolvedLlmTarget>,
        val skippedRuns: List<AiProviderCatalystRun>
    )

    private fun resolveAllLlmTargets(config: AppConfig): ResolvedAllTargets {
        val routing = config.aiRouting
        val fallbackGeminiKey = config.geminiKey.trim()

        val orderedProviders = buildList {
            add(routing.activeProvider)
            AiProviderId.entries.forEach { if (it != routing.activeProvider) add(it) }
        }

        val ready = ArrayList<ResolvedLlmTarget>()
        val skipped = ArrayList<AiProviderCatalystRun>()

        for (provider in orderedProviders) {
            val settings = routing.settingsFor(provider)
            val modelId = settings.selectedModelId.trim().ifBlank { TickerCatalystEngine.defaultModelIdFor(provider) }
            if (!settings.enabled) {
                skipped.add(
                    AiProviderCatalystRun(
                        provider = provider,
                        providerName = provider.displayName,
                        modelId = modelId,
                        status = AiProviderRunStatus.DISABLED
                    )
                )
                continue
            }
            val key = ModelRadarEngine.resolveApiKey(provider, routing, fallbackGeminiKey)
            if (key.isBlank()) {
                skipped.add(
                    AiProviderCatalystRun(
                        provider = provider,
                        providerName = provider.displayName,
                        modelId = modelId,
                        status = AiProviderRunStatus.NO_KEY
                    )
                )
                continue
            }
            ready.add(ResolvedLlmTarget(provider = provider, modelId = modelId, apiKey = key))
        }

        return ResolvedAllTargets(readyTargets = ready, skippedRuns = skipped)
    }

    private fun resolveFinnhubKey(config: AppConfig): String {
        val userKey = config.finnhubKey.trim()
        if (userKey.isNotEmpty()) return userKey
        return sanitizeSecret(BuildConfig.FINNHUB_KEY)
    }

    private fun sanitizeSecret(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val up = t.uppercase(Locale.US)
        if (up.startsWith("YOUR_") || up.startsWith("MY_") || up.startsWith("REPLACE_") || up.startsWith("<")) {
            return ""
        }
        return t
    }

    companion object {
        private fun shortModelName(modelId: String): String {
            val tail = modelId.substringAfterLast('/')
            return if (tail.length > 22) tail.take(20) + "…" else tail
        }
    }
}
