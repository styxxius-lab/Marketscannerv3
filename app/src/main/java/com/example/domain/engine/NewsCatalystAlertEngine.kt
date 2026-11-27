package com.example.domain.engine

import com.example.domain.model.HighImpactNewsEvent
import com.example.domain.model.ScoredNewsItem
import com.example.domain.model.SignalRow

object NewsCatalystAlertEngine {
    const val HIGH_IMPACT_MATERIALITY = 0.65

    fun eventKey(symbol: String, title: String): String =
        "${symbol.trim().uppercase()}|${title.trim()}"

    fun isHighImpact(item: ScoredNewsItem): Boolean =
        item.materiality >= HIGH_IMPACT_MATERIALITY

    fun detectHighImpactWatchlistEvents(
        rows: List<SignalRow>,
        watchlist: List<String>,
        alreadyNotifiedKeys: Set<String>
    ): List<HighImpactNewsEvent> {
        val watchSet = watchlist.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        if (watchSet.isEmpty() || rows.isEmpty()) return emptyList()

        val events = ArrayList<HighImpactNewsEvent>()
        val seenInPass = HashSet<String>()

        for (row in rows) {
            val sym = row.symbol.trim().uppercase()
            if (!watchSet.contains(sym)) continue

            val highItems = row.newsItems.filter { isHighImpact(it) }
            if (highItems.isNotEmpty()) {
                val topItem = highItems.maxByOrNull { it.materiality } ?: continue
                val key = eventKey(sym, topItem.title)
                if (!alreadyNotifiedKeys.contains(key) && seenInPass.add(key)) {
                    events.add(
                        HighImpactNewsEvent(
                            eventKey = key,
                            symbol = sym,
                            title = topItem.title,
                            direction = topItem.direction,
                            materiality = topItem.materiality,
                            rationale = topItem.rationale
                        )
                    )
                }
            } else if (row.newsMateriality >= HIGH_IMPACT_MATERIALITY && row.keyCatalyst.isNotBlank()) {
                val key = eventKey(sym, row.keyCatalyst)
                if (!alreadyNotifiedKeys.contains(key) && seenInPass.add(key)) {
                    events.add(
                        HighImpactNewsEvent(
                            eventKey = key,
                            symbol = sym,
                            title = row.keyCatalyst,
                            direction = row.newsDirection,
                            materiality = row.newsMateriality,
                            rationale = ""
                        )
                    )
                }
            }
        }

        return events.sortedByDescending { it.materiality }
    }
}
