package com.example.domain.engine

import com.example.domain.model.BarSeries
import com.example.domain.model.ContagionResult
import com.example.domain.model.NewsHeadline
import com.example.domain.model.ThreatAssessment
import com.example.domain.model.ThreatNarrativeResult
import com.example.domain.model.ThreatPositionContext
import com.example.domain.model.ThreatQuantResult
import com.example.domain.model.TradePosition
import com.example.engine.ThreatContext
import com.example.engine.ThreatEngine

/**
 * Compatibility adapter delegating to [com.example.engine.ThreatEngine].
 */
object ThreatAssessmentEngine {
    private val defaultEngine = ThreatEngine()

    fun posToContext(pos: TradePosition): ThreatPositionContext = ThreatPositionContext(
        qty = pos.qty,
        entry = pos.entry,
        initialStop = pos.stop,
        stop = pos.stop,
        upnl = pos.upnl
    )

    fun quantThreat(
        bars: BarSeries,
        priceOverride: Double? = null,
        benchBars: BarSeries? = null
    ): ThreatQuantResult? = defaultEngine
        .quantThreat(bars, ThreatContext(benchBars = benchBars, priceOverride = priceOverride))
        ?.let { ThreatEngine.toDomainQuant(it) }

    fun contagion(peerBars: List<BarSeries>): ContagionResult? =
        ThreatEngine.toDomainContagion(defaultEngine.contagion(peerBars))

    fun narrativeDegraded(
        reason: String = "No verified threat catalyst in recent headlines"
    ): ThreatNarrativeResult = ThreatEngine.toDomainNarrative(defaultEngine.narrativeDegraded(reason))

    fun classifyDemoHeadlines(
        symbol: String,
        headlines: List<NewsHeadline>
    ): ThreatNarrativeResult = ThreatEngine.toDomainNarrative(defaultEngine.classifyHeadlinesLocally(symbol, headlines))

    fun narrativeThreatScore(
        narrative: ThreatNarrativeResult,
        headlines: List<NewsHeadline>,
        nowMs: Long = System.currentTimeMillis()
    ): Double = defaultEngine.narrativeScore(
        ThreatEngine.fromDomainNarrative(narrative),
        headlines,
        nowMs
    )

    fun assess(
        symbol: String,
        bars: BarSeries,
        headlines: List<NewsHeadline>,
        narrative: ThreatNarrativeResult,
        position: ThreatPositionContext? = null,
        priceOverride: Double? = null,
        benchBars: BarSeries? = null,
        peerBars: List<BarSeries> = emptyList(),
        isLiveClassified: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): ThreatAssessment? {
        val card = defaultEngine.assessWithNarrative(
            symbol = symbol,
            bars = bars,
            headlines = headlines,
            narrative = ThreatEngine.fromDomainNarrative(narrative, isLiveClassified),
            position = ThreatEngine.fromDomainPosition(position),
            ctx = ThreatContext(
                benchBars = benchBars,
                peerBars = peerBars,
                priceOverride = priceOverride
            ),
            nowMs = nowMs
        ) ?: return null
        return ThreatEngine.toDomainAssessment(card)
    }
}
