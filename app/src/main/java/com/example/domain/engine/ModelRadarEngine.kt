package com.example.domain.engine

import com.example.BuildConfig
import com.example.domain.model.AiProviderId
import com.example.domain.model.AiRoutingConfig
import com.example.domain.model.ModelProbeResult
import com.example.domain.model.ModelTier
import com.example.domain.model.ModelTierBasis
import com.example.domain.model.ModelTierFilter
import com.example.domain.model.RadarModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class ProviderCatalogStats(
    val provider: AiProviderId,
    val total: Int,
    val freeCount: Int,
    val paidCount: Int,
    val unknownCount: Int
)

data class ModelRadarSyncResult(
    val models: List<RadarModel>,
    val errorsByProvider: Map<AiProviderId, String>,
    val syncedAtMs: Long,
    val fromCache: Boolean = false
)

/**
 * Kotlin port of `modelradar.py` — pulls live model catalogs from OpenRouter,
 * AIHubMix, DeepSeek, and Google Gemini, normalizes into a single schema with
 * honest tier basis (`declared` vs `~inferred` vs `provider` vs `unknown`),
 * ranks best free/paid chat models, and runs live completion probes.
 */
class ModelRadarEngine(
    private val transport: AdvisorHttpTransport = OkHttpAdvisorTransport(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).serializeNulls()
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cacheMutex = Mutex()
    private var cachedModels: List<RadarModel> = emptyList()
    private var cachedErrors: Map<AiProviderId, String> = emptyMap()
    private var cachedAtMs: Long = 0L

    suspend fun syncCatalog(
        routing: AiRoutingConfig,
        forceRefresh: Boolean = false
    ): ModelRadarSyncResult {
        val now = clockMs()
        if (!forceRefresh) {
            cacheMutex.withLock {
                if (cachedModels.isNotEmpty() && (now - cachedAtMs) < CACHE_TTL_MS) {
                    return ModelRadarSyncResult(
                        models = cachedModels,
                        errorsByProvider = cachedErrors,
                        syncedAtMs = cachedAtMs,
                        fromCache = true
                    )
                }
            }
        }

        val enabledProviders = routing.enabledProviders.ifEmpty { AiProviderId.entries.toSet() }
        val errors = LinkedHashMap<AiProviderId, String>()
        val collected = ArrayList<RadarModel>()

        coroutineScope {
            val jobs = enabledProviders.map { prov ->
                async {
                    val key = resolveApiKey(prov, routing)
                    try {
                        prov to fetchProviderModels(prov, key) to null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        prov to emptyList<RadarModel>() to (e.message ?: "sync failed")
                    }
                }
            }
            jobs.awaitAll().forEach { (pair, err) ->
                val (prov, list) = pair
                if (err != null) {
                    errors[prov] = err
                } else {
                    collected.addAll(list)
                }
            }
        }

        val sorted = collected.sortedWith(
            compareBy<RadarModel>(
                { tierSortOrder(it.tier) },
                { it.provider.ordinal },
                { it.id.lowercase(Locale.US) }
            )
        )

        cacheMutex.withLock {
            cachedModels = sorted
            cachedErrors = errors.toMap()
            cachedAtMs = now
        }

        return ModelRadarSyncResult(
            models = sorted,
            errorsByProvider = errors,
            syncedAtMs = now,
            fromCache = false
        )
    }

    private suspend fun fetchProviderModels(
        provider: AiProviderId,
        apiKey: String
    ): List<RadarModel> {
        if (provider == AiProviderId.GEMINI) {
            if (apiKey.isBlank()) {
                return builtInGeminiCatalog()
            }
            val url = "${provider.baseUrl}/v1beta/models?key=$apiKey"
            val body = httpGet(url, emptyMap())
            val parsed = parseGeminiCatalog(body)
            return parsed.ifEmpty { builtInGeminiCatalog() }
        }

        if (apiKey.isBlank() && !provider.isPublicCatalog) {
            throw IllegalStateException("${provider.displayName} API key not set")
        }

        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        headers["User-Agent"] = "modelradar/1.0"
        if (apiKey.isNotBlank()) {
            headers["Authorization"] = "Bearer $apiKey"
        }
        if (provider == AiProviderId.OPENROUTER) {
            headers["HTTP-Referer"] = "http://localhost"
            headers["X-Title"] = "modelradar"
        }

        val url = provider.baseUrl.trimEnd('/') + "/models"
        val body = httpGet(url, headers)
        val models = when (provider) {
            AiProviderId.OPENROUTER -> parseOpenRouter(body)
            else -> parseGeneric(provider, body, provider.defaultTier)
        }
        if (models.isEmpty()) {
            throw IllegalStateException("empty model list returned")
        }
        return models
    }

    private fun httpGet(url: String, headers: Map<String, String>): String {
        val reqBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        okHttp.newCall(reqBuilder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code in 200..299) {
                return body
            }
            if (resp.code == 401 || resp.code == 403) {
                throw IllegalStateException("auth failed — HTTP ${resp.code}")
            }
            throw IllegalStateException("HTTP ${resp.code}: ${body.take(120)}")
        }
    }

    /**
     * Port of `action_probe` from `modelradar.py`: sends one real completion
     * ("Reply with the single word: ok") to verify the model answers live.
     */
    suspend fun probeModel(
        model: RadarModel,
        routing: AiRoutingConfig
    ): ModelProbeResult {
        val key = resolveApiKey(model.provider, routing)
        val started = clockMs()
        if (model.provider == AiProviderId.GEMINI) {
            if (key.isBlank()) {
                return ModelProbeResult(
                    provider = model.provider,
                    modelId = model.id,
                    ok = false,
                    latencyMs = 0L,
                    error = "Gemini API key not set"
                )
            }
            val url = "${model.provider.baseUrl}/v1beta/models/${model.id}:generateContent?key=$key"
            val payload = mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to "Reply with the single word: ok")))
                ),
                "generationConfig" to mapOf("maxOutputTokens" to 8, "temperature" to 0.0)
            )
            return try {
                val resp = transport.postJson(
                    url = url,
                    headers = mapOf("Content-Type" to "application/json"),
                    jsonBody = mapAdapter.toJson(payload),
                    timeoutSec = 20.0
                )
                val ms = (clockMs() - started).coerceAtLeast(1L)
                if (resp.code !in 200..299) {
                    ModelProbeResult(model.provider, model.id, false, ms, error = "HTTP ${resp.code}")
                } else {
                    val map = parseJsonMap(resp.body)
                    val cands = map?.get("candidates") as? List<*>
                    val first = cands?.firstOrNull() as? Map<*, *>
                    val content = first?.get("content") as? Map<*, *>
                    val parts = content?.get("parts") as? List<*>
                    val text = ((parts?.firstOrNull() as? Map<*, *>)?.get("text") as? String)?.trim().orEmpty()
                    ModelProbeResult(model.provider, model.id, true, ms, replyPreview = text.take(40))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ModelProbeResult(model.provider, model.id, false, (clockMs() - started).coerceAtLeast(1L), error = e.message?.take(100))
            }
        }

        if (key.isBlank()) {
            return ModelProbeResult(
                provider = model.provider,
                modelId = model.id,
                ok = false,
                latencyMs = 0L,
                error = "${model.provider.displayName} API key not set"
            )
        }

        val headers = linkedMapOf(
            "Authorization" to "Bearer $key",
            "Content-Type" to "application/json",
            "User-Agent" to "modelradar/1.0"
        )
        if (model.provider == AiProviderId.OPENROUTER) {
            headers["HTTP-Referer"] = "http://localhost"
            headers["X-Title"] = "modelradar"
        }
        val payload = mapOf(
            "model" to model.id,
            "messages" to listOf(mapOf("role" to "user", "content" to "Reply with the single word: ok")),
            "max_tokens" to 8,
            "temperature" to 0
        )
        val url = model.provider.baseUrl.trimEnd('/') + "/chat/completions"
        return try {
            val resp = transport.postJson(url, headers, mapAdapter.toJson(payload), timeoutSec = 25.0)
            val ms = (clockMs() - started).coerceAtLeast(1L)
            if (resp.code !in 200..299) {
                ModelProbeResult(model.provider, model.id, false, ms, error = "HTTP ${resp.code}: ${resp.body.take(80)}")
            } else {
                val map = parseJsonMap(resp.body)
                val choices = map?.get("choices") as? List<*>
                val first = choices?.firstOrNull() as? Map<*, *>
                val message = first?.get("message") as? Map<*, *>
                val text = (message?.get("content") as? String)?.trim().orEmpty()
                val usage = map?.get("usage") as? Map<*, *>
                val pTok = num(usage?.get("prompt_tokens"))?.toInt()
                val cTok = num(usage?.get("completion_tokens"))?.toInt()
                ModelProbeResult(
                    provider = model.provider,
                    modelId = model.id,
                    ok = true,
                    latencyMs = ms,
                    replyPreview = text.take(40),
                    promptTokens = pTok,
                    completionTokens = cTok
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelProbeResult(
                provider = model.provider,
                modelId = model.id,
                ok = false,
                latencyMs = (clockMs() - started).coerceAtLeast(1L),
                error = (e.message ?: e.javaClass.simpleName).take(100)
            )
        }
    }

    private fun parseJsonMap(raw: String): Map<String, Any?>? = try {
        mapAdapter.fromJson(raw)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val CACHE_TTL_MS = 6L * 3_600_000L

        private val FREE_MARKERS = listOf("free", ":free", "-free", "_free", "(free)")
        private val NON_CHAT = listOf(
            "embed", "rerank", "whisper", "tts", "audio", "dall-e", "dalle",
            "stable-diffusion", "moderation", "guard", "ocr", "clip",
            "flux", "sdxl", "kolors", "veo", "sora", "kling"
        )
        private val DEFAULT_PREFER = listOf("glm", "deepseek", "qwen", "llama", "mistral", "gpt", "gemini")

        private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val staticMapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        ).serializeNulls()

        private val PLACEHOLDER_PREFIXES = listOf("YOUR_", "MY_", "REPLACE_", "CHANGE_ME", "PLACEHOLDER")

        private fun sanitizeSecret(raw: String?): String {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty()) return ""
            val upper = t.uppercase(Locale.US)
            if (PLACEHOLDER_PREFIXES.any { upper.startsWith(it) } || upper.startsWith("<")) return ""
            return t
        }

        fun resolveApiKey(provider: AiProviderId, routing: AiRoutingConfig, fallbackGeminiKey: String = ""): String {
            val userKey = routing.settingsFor(provider).apiKey.trim()
            if (userKey.isNotEmpty()) return userKey
            return when (provider) {
                AiProviderId.OPENROUTER -> sanitizeSecret(BuildConfig.OPENROUTER_API_KEY)
                AiProviderId.AIHUBMIX -> sanitizeSecret(BuildConfig.AIHUBMIX_API_KEY).ifBlank {
                    if (BuildConfig.LLM_BASE_URL.contains("aihubmix", ignoreCase = true)) {
                        sanitizeSecret(BuildConfig.LLM_API_KEY)
                    } else ""
                }
                AiProviderId.DEEPSEEK -> sanitizeSecret(BuildConfig.DEEPSEEK_API_KEY).ifBlank {
                    if (BuildConfig.LLM_BASE_URL.contains("deepseek", ignoreCase = true)) {
                        sanitizeSecret(BuildConfig.LLM_API_KEY)
                    } else ""
                }
                AiProviderId.GROQ -> sanitizeSecret(BuildConfig.GROQ_API_KEY).ifBlank {
                    if (BuildConfig.LLM_BASE_URL.contains("groq", ignoreCase = true)) {
                        sanitizeSecret(BuildConfig.LLM_API_KEY)
                    } else ""
                }
                AiProviderId.GEMINI -> fallbackGeminiKey.trim()
            }
        }

        /**
         * Return (tier, basis). Never guesses a price, only classifies a label honestly.
         */
        fun classify(
            modelId: String,
            name: String,
            priceIn: Double?,
            priceOut: Double?,
            defaultTier: ModelTier
        ): Pair<ModelTier, ModelTierBasis> {
            if (priceIn != null && priceOut != null) {
                return if (priceIn == 0.0 && priceOut == 0.0) {
                    ModelTier.FREE to ModelTierBasis.DECLARED
                } else {
                    ModelTier.PAID to ModelTierBasis.DECLARED
                }
            }
            val text = "$modelId $name".lowercase(Locale.US)
            if (FREE_MARKERS.any { text.contains(it) }) {
                return ModelTier.FREE to ModelTierBasis.INFERRED
            }
            if (defaultTier != ModelTier.UNKNOWN) {
                return defaultTier to ModelTierBasis.PROVIDER
            }
            return ModelTier.UNKNOWN to ModelTierBasis.UNKNOWN
        }

        fun parseOpenRouter(rawJson: String): List<RadarModel> {
            val payload = try {
                staticMapAdapter.fromJson(rawJson)
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            val data = payload["data"] as? List<*> ?: return emptyList()
            val out = ArrayList<RadarModel>(data.size)
            for (item in data) {
                val raw = item as? Map<*, *> ?: continue
                val mid = (raw["id"] as? String)?.trim().orEmpty()
                if (mid.isEmpty()) continue
                val name = (raw["name"] as? String)?.trim().takeIf { !it.isNullOrEmpty() } ?: mid
                val pr = raw["pricing"] as? Map<*, *>
                val pin = num(pr?.get("prompt"))?.let { roundPrice(it * 1_000_000.0) }
                val pout = num(pr?.get("completion"))?.let { roundPrice(it * 1_000_000.0) }
                val (tier, basis) = classify(mid, name, pin, pout, ModelTier.UNKNOWN)
                val arch = raw["architecture"] as? Map<*, *>
                val modality = (arch?.get("modality") as? String).orEmpty()
                out.add(
                    RadarModel(
                        provider = AiProviderId.OPENROUTER,
                        id = mid,
                        name = name,
                        context = num(raw["context_length"])?.toInt() ?: 0,
                        priceIn = pin,
                        priceOut = pout,
                        tier = tier,
                        basis = basis,
                        created = num(raw["created"])?.toLong() ?: 0L,
                        modality = modality
                    )
                )
            }
            return out
        }

        fun parseGeneric(
            provider: AiProviderId,
            rawJson: String,
            defaultTier: ModelTier = provider.defaultTier
        ): List<RadarModel> {
            val payload = try {
                staticMapAdapter.fromJson(rawJson)
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            val rows = (payload["data"] as? List<*>) ?: (payload["models"] as? List<*>) ?: return emptyList()
            val out = ArrayList<RadarModel>(rows.size)
            for (entry in rows) {
                val mid: String
                val name: String
                var ctx = 0
                var pin: Double? = null
                var pout: Double? = null
                var created = 0L
                var modality = ""
                when (entry) {
                    is String -> {
                        mid = entry.trim()
                        name = mid
                    }
                    is Map<*, *> -> {
                        mid = ((entry["id"] ?: entry["model"] ?: entry["name"]) as? String)?.trim().orEmpty()
                        name = (entry["name"] as? String)?.trim().takeIf { !it.isNullOrEmpty() } ?: mid
                        ctx = num(entry["context_length"] ?: entry["context_window"] ?: entry["max_context_length"])?.toInt() ?: 0
                        val pr = entry["pricing"] as? Map<*, *>
                        pin = num(pr?.get("prompt"))?.let { roundPrice(it * 1_000_000.0) }
                        pout = num(pr?.get("completion"))?.let { roundPrice(it * 1_000_000.0) }
                        created = num(entry["created"])?.toLong() ?: 0L
                        modality = (entry["modality"] as? String).orEmpty()
                    }
                    else -> continue
                }
                if (mid.isEmpty()) continue
                val (tier, basis) = classify(mid, name, pin, pout, defaultTier)
                out.add(
                    RadarModel(
                        provider = provider,
                        id = mid,
                        name = name,
                        context = ctx,
                        priceIn = pin,
                        priceOut = pout,
                        tier = tier,
                        basis = basis,
                        created = created,
                        modality = modality
                    )
                )
            }
            return out
        }

        fun parseGeminiCatalog(rawJson: String): List<RadarModel> {
            val payload = try {
                staticMapAdapter.fromJson(rawJson)
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            val rows = payload["models"] as? List<*> ?: return emptyList()
            val out = ArrayList<RadarModel>()
            for (entry in rows) {
                val m = entry as? Map<*, *> ?: continue
                val methods = (m["supportedGenerationMethods"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                if (methods.isNotEmpty() && !methods.contains("generateContent")) continue
                val rawName = (m["name"] as? String)?.trim().orEmpty()
                val mid = rawName.removePrefix("models/")
                if (mid.isEmpty() || !mid.contains("gemini", ignoreCase = true)) continue
                val displayName = (m["displayName"] as? String)?.trim().takeIf { !it.isNullOrEmpty() } ?: mid
                val ctx = num(m["inputTokenLimit"])?.toInt() ?: 0
                val isPaid = mid.contains("pro", ignoreCase = true) || mid.contains("ultra", ignoreCase = true)
                out.add(
                    RadarModel(
                        provider = AiProviderId.GEMINI,
                        id = mid,
                        name = displayName,
                        context = ctx,
                        priceIn = null,
                        priceOut = null,
                        tier = if (isPaid) ModelTier.PAID else ModelTier.FREE,
                        basis = ModelTierBasis.PROVIDER,
                        modality = "text->text"
                    )
                )
            }
            return out
        }

        fun builtInGeminiCatalog(): List<RadarModel> = listOf(
            RadarModel(
                provider = AiProviderId.GEMINI,
                id = "gemini-2.5-flash",
                name = "Gemini 2.5 Flash (Free Tier Eligible)",
                context = 1_048_576,
                tier = ModelTier.FREE,
                basis = ModelTierBasis.PROVIDER,
                modality = "text->text",
                note = "Fast free-tier eligible news & threat scorer"
            ),
            RadarModel(
                provider = AiProviderId.GEMINI,
                id = "gemini-2.5-flash-lite",
                name = "Gemini 2.5 Flash-Lite (Free Tier Eligible)",
                context = 1_048_576,
                tier = ModelTier.FREE,
                basis = ModelTierBasis.PROVIDER,
                modality = "text->text",
                note = "Ultra-low latency free-tier scorer"
            ),
            RadarModel(
                provider = AiProviderId.GEMINI,
                id = "gemini-2.5-pro",
                name = "Gemini 2.5 Pro (Paid Tier)",
                context = 1_048_576,
                tier = ModelTier.PAID,
                basis = ModelTierBasis.PROVIDER,
                modality = "text->text",
                note = "Higher reasoning depth for complex filings"
            )
        )

        fun isChat(model: RadarModel): Boolean {
            val mid = model.id.lowercase(Locale.US)
            if (NON_CHAT.any { mid.contains(it) }) return false
            if (model.modality.contains("->")) {
                val parts = model.modality.split("->", limit = 2)
                val left = parts.getOrNull(0).orEmpty().lowercase(Locale.US)
                val right = parts.getOrNull(1).orEmpty().lowercase(Locale.US)
                if (!left.contains("text") || !right.contains("text")) return false
            }
            return true
        }

        fun score(model: RadarModel, prefer: List<String> = DEFAULT_PREFER): Double {
            var s = 0.0
            if (model.tier == ModelTier.FREE) s += 120.0
            if (model.basis == ModelTierBasis.DECLARED) s += 40.0
            s += min(model.context, 200_000) / 2_000.0
            val lower = model.id.lowercase(Locale.US)
            s += prefer.count { lower.contains(it) } * 15.0
            if (model.created > 0L) {
                s += min(max(model.created - 1_600_000_000L, 0L) / 1e8, 30.0)
            }
            if (model.modality.contains("image", ignoreCase = true)) {
                s -= 20.0
            }
            return s
        }

        fun pickBest(
            models: List<RadarModel>,
            tier: ModelTierFilter = ModelTierFilter.FREE,
            provider: AiProviderId? = null,
            enabledProviders: Set<AiProviderId>? = null
        ): RadarModel? {
            val pool = models.filter { m ->
                isChat(m) &&
                    (enabledProviders == null || enabledProviders.contains(m.provider)) &&
                    (provider == null || m.provider == provider) &&
                    when (tier) {
                        ModelTierFilter.ALL -> true
                        ModelTierFilter.FREE -> m.tier == ModelTier.FREE
                        ModelTierFilter.PAID -> m.tier == ModelTier.PAID
                        ModelTierFilter.UNKNOWN -> m.tier == ModelTier.UNKNOWN
                    }
            }
            return pool.maxByOrNull { score(it) }
        }

        fun filterCatalog(
            models: List<RadarModel>,
            enabledProviders: Set<AiProviderId>,
            providerFilter: AiProviderId?,
            tierFilter: ModelTierFilter,
            searchQuery: String = "",
            chatOnly: Boolean = true
        ): List<RadarModel> {
            val q = searchQuery.trim().lowercase(Locale.US)
            return models.filter { m ->
                if (chatOnly && !isChat(m)) return@filter false
                if (!enabledProviders.contains(m.provider)) return@filter false
                if (providerFilter != null && m.provider != providerFilter) return@filter false
                val matchesTier = when (tierFilter) {
                    ModelTierFilter.ALL -> true
                    ModelTierFilter.FREE -> m.tier == ModelTier.FREE
                    ModelTierFilter.PAID -> m.tier == ModelTier.PAID
                    ModelTierFilter.UNKNOWN -> m.tier == ModelTier.UNKNOWN
                }
                if (!matchesTier) return@filter false
                if (q.isEmpty()) return@filter true
                m.id.lowercase(Locale.US).contains(q) ||
                    m.name.lowercase(Locale.US).contains(q) ||
                    m.provider.displayName.lowercase(Locale.US).contains(q)
            }.sortedWith(
                compareBy<RadarModel>(
                    { tierSortOrder(it.tier) },
                    { -score(it) },
                    { it.id.lowercase(Locale.US) }
                )
            )
        }

        fun stats(models: List<RadarModel>): Map<AiProviderId, ProviderCatalogStats> {
            return AiProviderId.entries.associateWith { prov ->
                val list = models.filter { it.provider == prov && isChat(it) }
                ProviderCatalogStats(
                    provider = prov,
                    total = list.size,
                    freeCount = list.count { it.tier == ModelTier.FREE },
                    paidCount = list.count { it.tier == ModelTier.PAID },
                    unknownCount = list.count { it.tier == ModelTier.UNKNOWN }
                )
            }
        }

        fun formatPrice(x: Double?): String {
            if (x == null) return "?"
            if (x == 0.0) return "$0"
            return if (x < 0.01) "$${"%.4f".format(Locale.US, x)}"
            else if (x < 1.0) "$${"%.3f".format(Locale.US, x).trimEnd('0').trimEnd('.')}"
            else "$${"%.2f".format(Locale.US, x).trimEnd('0').trimEnd('.')}"
        }

        fun formatContext(n: Int): String {
            if (n <= 0) return "-"
            return if (n >= 1000) "${n / 1000}k" else n.toString()
        }

        private fun tierSortOrder(tier: ModelTier): Int = when (tier) {
            ModelTier.FREE -> 0
            ModelTier.UNKNOWN -> 1
            ModelTier.PAID -> 2
        }

        private fun num(v: Any?): Double? = when (v) {
            null, is Boolean -> null
            is Number -> v.toDouble().takeIf { it.isFinite() && it >= 0.0 }
            is String -> v.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            else -> null
        }

        private fun roundPrice(x: Double): Double {
            val scale = if (x < 1.0) 10_000.0 else 1_000.0
            return (x * scale).roundToInt() / scale
        }
    }
}
