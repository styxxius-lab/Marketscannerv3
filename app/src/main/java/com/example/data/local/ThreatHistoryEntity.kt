package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ThreatAction
import com.example.domain.model.ThreatLevel
import com.example.engine.ThreatCard
import com.example.engine.ThreatDiagnosticDriver
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Room entity storing persisted [ThreatCard] scan outputs for the Simulator Threat History log.
 */
@Entity(
    tableName = "threat_history",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["timestampMs"])
    ]
)
data class ThreatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val timestampMs: Long,
    val price: Double,
    val threatScore: Int,
    val level: String,
    val action: String,
    val verdict: String,
    val priceActionScore: Int,
    val narrativeScore: Int,
    val confidence: Double,
    val categoryKey: String,
    val categoryLabel: String,
    val structural: Boolean,
    val persistenceDays: Int,
    val suggestedStop: Double?,
    val invalidation: String,
    val rsi: Double,
    val sectorWideContagion: Boolean,
    val driversJson: String,
    val reasoningJson: String
)

/**
 * UI/Domain representation of a logged [ThreatCard] scan result.
 */
data class ThreatHistoryEntry(
    val id: Long,
    val symbol: String,
    val timestampMs: Long,
    val price: Double,
    val threatScore: Int,
    val level: ThreatLevel,
    val action: ThreatAction,
    val verdict: String,
    val priceActionScore: Int,
    val narrativeScore: Int,
    val confidence: Double,
    val categoryKey: String,
    val categoryLabel: String,
    val structural: Boolean,
    val persistenceDays: Int,
    val suggestedStop: Double?,
    val invalidation: String,
    val rsi: Double,
    val sectorWideContagion: Boolean,
    val drivers: List<ThreatDiagnosticDriver>,
    val reasoning: List<String>
)

object ThreatHistoryMapper {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val driversAdapter = moshi.adapter<List<ThreatDiagnosticDriver>>(
        Types.newParameterizedType(List::class.java, ThreatDiagnosticDriver::class.java)
    )
    private val reasoningAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun toEntity(card: ThreatCard): ThreatHistoryEntity = ThreatHistoryEntity(
        symbol = card.symbol.uppercase(),
        timestampMs = card.timestampMs,
        price = card.price,
        threatScore = card.threatScore,
        level = card.level.name,
        action = card.action.name,
        verdict = card.verdict,
        priceActionScore = card.priceActionScore,
        narrativeScore = card.narrativeScore,
        confidence = card.confidence,
        categoryKey = card.narrative.categoryKey,
        categoryLabel = card.narrative.categoryLabel,
        structural = card.narrative.structural,
        persistenceDays = card.narrative.persistenceDays,
        suggestedStop = card.suggestedStop,
        invalidation = card.invalidation,
        rsi = card.quant.rsi,
        sectorWideContagion = card.contagion?.sectorWide == true,
        driversJson = runCatching { driversAdapter.toJson(card.quant.drivers) }.getOrDefault("[]"),
        reasoningJson = runCatching { reasoningAdapter.toJson(card.reasoning) }.getOrDefault("[]")
    )

    fun toEntry(entity: ThreatHistoryEntity): ThreatHistoryEntry = ThreatHistoryEntry(
        id = entity.id,
        symbol = entity.symbol,
        timestampMs = entity.timestampMs,
        price = entity.price,
        threatScore = entity.threatScore,
        level = runCatching { ThreatLevel.valueOf(entity.level) }.getOrDefault(ThreatLevel.LOW),
        action = runCatching { ThreatAction.valueOf(entity.action) }.getOrDefault(ThreatAction.HOLD),
        verdict = entity.verdict,
        priceActionScore = entity.priceActionScore,
        narrativeScore = entity.narrativeScore,
        confidence = entity.confidence,
        categoryKey = entity.categoryKey,
        categoryLabel = entity.categoryLabel,
        structural = entity.structural,
        persistenceDays = entity.persistenceDays,
        suggestedStop = entity.suggestedStop,
        invalidation = entity.invalidation,
        rsi = entity.rsi,
        sectorWideContagion = entity.sectorWideContagion,
        drivers = runCatching { driversAdapter.fromJson(entity.driversJson) }.getOrNull().orEmpty(),
        reasoning = runCatching { reasoningAdapter.fromJson(entity.reasoningJson) }.getOrNull().orEmpty()
    )

    fun fromCard(card: ThreatCard, id: Long = 0L): ThreatHistoryEntry = ThreatHistoryEntry(
        id = id,
        symbol = card.symbol.uppercase(),
        timestampMs = card.timestampMs,
        price = card.price,
        threatScore = card.threatScore,
        level = card.level,
        action = card.action,
        verdict = card.verdict,
        priceActionScore = card.priceActionScore,
        narrativeScore = card.narrativeScore,
        confidence = card.confidence,
        categoryKey = card.narrative.categoryKey,
        categoryLabel = card.narrative.categoryLabel,
        structural = card.narrative.structural,
        persistenceDays = card.narrative.persistenceDays,
        suggestedStop = card.suggestedStop,
        invalidation = card.invalidation,
        rsi = card.quant.rsi,
        sectorWideContagion = card.contagion?.sectorWide == true,
        drivers = card.quant.drivers,
        reasoning = card.reasoning
    )
}
