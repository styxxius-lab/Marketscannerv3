package com.example.data.repository

import com.example.data.local.ThreatHistoryDao
import com.example.data.local.ThreatHistoryEntry
import com.example.data.local.ThreatHistoryMapper
import com.example.engine.ThreatCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * Abstraction for storing and observing local [ThreatCard] scan history logs.
 */
interface ThreatHistoryStore {
    fun observeHistory(limit: Int = 100): Flow<List<ThreatHistoryEntry>>
    suspend fun recordScan(card: ThreatCard, forceRecord: Boolean = false)
    suspend fun deleteEntry(id: Long)
    suspend fun deleteSymbolHistory(symbol: String)
    suspend fun clearAll()
}

/**
 * Room-backed implementation persisting [ThreatCard] scan logs in SQLite.
 * Smart deduplication avoids flooding the log on 2.5s background ticks while recording
 * every explicit scan and any meaningful score/level/action shift.
 */
class RoomThreatHistoryRepository(
    private val dao: ThreatHistoryDao
) : ThreatHistoryStore {

    override fun observeHistory(limit: Int): Flow<List<ThreatHistoryEntry>> =
        dao.observeHistory(limit).map { entities ->
            entities.map(ThreatHistoryMapper::toEntry)
        }

    override suspend fun recordScan(card: ThreatCard, forceRecord: Boolean) {
        val sym = card.symbol.uppercase()
        if (!forceRecord) {
            val latest = dao.getLatestForSymbol(sym)
            if (latest != null && !isMaterialChange(latest, card)) {
                return
            }
        }
        dao.insert(ThreatHistoryMapper.toEntity(card))
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteSymbolHistory(symbol: String) {
        dao.deleteForSymbol(symbol.uppercase())
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }

    private fun isMaterialChange(
        previous: com.example.data.local.ThreatHistoryEntity,
        next: ThreatCard
    ): Boolean {
        if (previous.level != next.level.name || previous.action != next.action.name) return true
        if (previous.categoryKey != next.narrative.categoryKey) return true
        if (abs(previous.threatScore - next.threatScore) >= SCORE_DELTA_THRESHOLD) return true
        if (next.timestampMs - previous.timestampMs >= MIN_AUTO_LOG_INTERVAL_MS) return true
        return false
    }

    companion object {
        private const val SCORE_DELTA_THRESHOLD = 5
        private const val MIN_AUTO_LOG_INTERVAL_MS = 5 * 60_000L
    }
}

/**
 * Lightweight in-memory [ThreatHistoryStore] fallback used in unit tests.
 */
class InMemoryThreatHistoryStore : ThreatHistoryStore {
    private var nextId = 1L
    private val entries = MutableStateFlow<List<ThreatHistoryEntry>>(emptyList())

    override fun observeHistory(limit: Int): Flow<List<ThreatHistoryEntry>> =
        entries.asStateFlow().map { list -> list.take(limit) }

    override suspend fun recordScan(card: ThreatCard, forceRecord: Boolean) {
        val sym = card.symbol.uppercase()
        entries.update { current ->
            val latestForSym = current.firstOrNull { it.symbol == sym }
            if (!forceRecord && latestForSym != null) {
                val unchanged = latestForSym.level == card.level &&
                    latestForSym.action == card.action &&
                    latestForSym.categoryKey == card.narrative.categoryKey &&
                    abs(latestForSym.threatScore - card.threatScore) < 5 &&
                    (card.timestampMs - latestForSym.timestampMs) < 5 * 60_000L
                if (unchanged) return@update current
            }
            val item = ThreatHistoryMapper.fromCard(card, id = nextId++)
            (listOf(item) + current).sortedByDescending { it.timestampMs }.take(100)
        }
    }

    override suspend fun deleteEntry(id: Long) {
        entries.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun deleteSymbolHistory(symbol: String) {
        val sym = symbol.uppercase()
        entries.update { current -> current.filterNot { it.symbol == sym } }
    }

    override suspend fun clearAll() {
        entries.value = emptyList()
    }
}
