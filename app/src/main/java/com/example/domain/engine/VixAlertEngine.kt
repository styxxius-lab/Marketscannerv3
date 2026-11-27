package com.example.domain.engine

import com.example.domain.model.MarketRegime
import com.example.domain.model.RegimeLabel
import com.example.domain.model.VixSnapshot
import com.example.domain.model.VixSpikeAlertEvent
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class VixMonitorState(
    val armed: Boolean = true,
    val lastTriggeredAtMs: Long? = null,
    val lastTriggeredSpikePct: Double? = null,
    val recentAlerts: List<VixSpikeAlertEvent> = emptyList()
)

data class VixEvaluationResult(
    val monitorState: VixMonitorState,
    val triggeredEvent: VixSpikeAlertEvent?
)

object VixAlertEngine {

    const val DEFAULT_THRESHOLD_PCT = 10.0
    const val MIN_THRESHOLD_PCT = 2.0
    const val MAX_THRESHOLD_PCT = 100.0
    const val COOLDOWN_MS = 15L * 60L * 1000L // 15 minutes hysteresis cooldown
    private const val ACCELERATION_STEP_PCT = 5.0

    private fun round2(x: Double): Double =
        if (x.isFinite()) (x * 100.0).roundToInt() / 100.0 else 0.0

    /**
     * Computes the sudden intraday VIX spike percentage as the peak positive move
     * from either the session open, previous close, or intraday rolling low.
     */
    fun computeIntradaySpikePct(
        current: Double,
        open: Double,
        prevClose: Double,
        intradayLow: Double
    ): Double {
        if (!current.isFinite() || current <= 0.0) return 0.0
        val fromOpen = if (open > 0.0) ((current - open) / open) * 100.0 else 0.0
        val fromPrev = if (prevClose > 0.0) ((current - prevClose) / prevClose) * 100.0 else 0.0
        val fromLow = if (intradayLow > 0.0) ((current - intradayLow) / intradayLow) * 100.0 else 0.0
        return round2(max(0.0, max(fromOpen, max(fromPrev, fromLow))))
    }

    fun buildSnapshot(
        current: Double,
        open: Double,
        prevClose: Double,
        intradayLow: Double,
        intradayHigh: Double,
        intradaySeries: List<Double>,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ): VixSnapshot {
        val safeCur = current.coerceAtLeast(5.0)
        val safeOpen = if (open > 0.0) open else safeCur
        val safePrev = if (prevClose > 0.0) prevClose else safeOpen
        val safeLow = min(if (intradayLow > 0.0) intradayLow else safeCur, safeCur)
        val safeHigh = max(if (intradayHigh > 0.0) intradayHigh else safeCur, safeCur)
        val chgPct = round2(((safeCur - safePrev) / safePrev) * 100.0)
        val spikePct = computeIntradaySpikePct(safeCur, safeOpen, safePrev, safeLow)
        val cleanSeries = (if (intradaySeries.isEmpty()) listOf(safeOpen, safeCur) else intradaySeries)
            .takeLast(60)
            .map { round2(it) }

        return VixSnapshot(
            current = round2(safeCur),
            open = round2(safeOpen),
            prevClose = round2(safePrev),
            intradayLow = round2(safeLow),
            intradayHigh = round2(safeHigh),
            changeFromClosePct = chgPct,
            intradaySpikePct = spikePct,
            intradaySeries = cleanSeries,
            source = source,
            updatedAtMs = nowMs
        )
    }

    fun evaluate(
        snapshot: VixSnapshot?,
        thresholdPct: Double,
        enabled: Boolean,
        monitorState: VixMonitorState,
        nowMs: Long = System.currentTimeMillis()
    ): VixEvaluationResult {
        if (snapshot == null) {
            return VixEvaluationResult(monitorState = monitorState, triggeredEvent = null)
        }
        val safeThreshold = thresholdPct.coerceIn(MIN_THRESHOLD_PCT, MAX_THRESHOLD_PCT)
        val spikePct = snapshot.intradaySpikePct
        val rearmLevel = safeThreshold * 0.75

        // Hysteresis: automatically re-arm if volatility cools meaningfully below threshold
        val rearmedState = if (!monitorState.armed && spikePct < rearmLevel) {
            monitorState.copy(armed = true)
        } else {
            monitorState
        }

        if (!enabled || spikePct < safeThreshold) {
            return VixEvaluationResult(monitorState = rearmedState, triggeredEvent = null)
        }

        val lastAt = rearmedState.lastTriggeredAtMs
        val lastSpike = rearmedState.lastTriggeredSpikePct ?: 0.0
        val cooldownElapsed = lastAt == null || (nowMs - lastAt) >= COOLDOWN_MS
        val acceleratedLeg = !rearmedState.armed && (spikePct >= lastSpike + ACCELERATION_STEP_PCT)
        val shouldFire = rearmedState.armed || cooldownElapsed || acceleratedLeg

        if (!shouldFire) {
            return VixEvaluationResult(monitorState = rearmedState, triggeredEvent = null)
        }

        val baseline = min(
            snapshot.intradayLow.takeIf { it > 0.0 } ?: snapshot.open,
            snapshot.open.takeIf { it > 0.0 } ?: snapshot.prevClose
        ).coerceAtLeast(1.0)

        val event = VixSpikeAlertEvent(
            id = UUID.randomUUID().toString(),
            vixLevel = snapshot.current,
            baselineLevel = round2(baseline),
            spikePct = spikePct,
            changeFromClosePct = snapshot.changeFromClosePct,
            thresholdPct = round2(safeThreshold),
            source = snapshot.source,
            timestampMs = nowMs
        )

        val updatedMonitor = rearmedState.copy(
            armed = false,
            lastTriggeredAtMs = nowMs,
            lastTriggeredSpikePct = spikePct,
            recentAlerts = (listOf(event) + rearmedState.recentAlerts).take(10)
        )
        return VixEvaluationResult(monitorState = updatedMonitor, triggeredEvent = event)
    }

    fun demoBaselineFromRegime(
        regime: MarketRegime,
        nowMs: Long = System.currentTimeMillis()
    ): VixSnapshot {
        val base = when (regime.label) {
            RegimeLabel.RISK_ON -> 13.8 + regime.volPct * 4.5
            RegimeLabel.NEUTRAL -> 16.2 + regime.volPct * 5.0
            RegimeLabel.RISK_OFF -> 21.0 + regime.volPct * 7.5
            RegimeLabel.CRISIS -> 28.5 + regime.volPct * 11.0
        }.coerceIn(11.5, 55.0)
        val open = round2(base * 0.985)
        val prevClose = round2(base * 0.975)
        val low = round2(min(open, base * 0.97))
        val series = List(24) { idx ->
            val frac = idx / 23.0
            round2(open + (base - open) * frac + kotlin.math.sin(idx * 0.7) * 0.14)
        }
        return buildSnapshot(
            current = base,
            open = open,
            prevClose = prevClose,
            intradayLow = low,
            intradayHigh = max(base, open),
            intradaySeries = series,
            source = "DEMO",
            nowMs = nowMs
        )
    }

    fun stepDemoVix(
        current: VixSnapshot,
        nowMs: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): VixSnapshot {
        // Gentle intraday drift with occasional realistic volatility pulse
        val shockRoll = random.nextDouble()
        val deltaPct = when {
            shockRoll > 0.96 -> 0.025 + random.nextDouble() * 0.025 // occasional +2.5% to +5% pulse
            shockRoll < 0.04 -> -(0.02 + random.nextDouble() * 0.02)
            else -> (random.nextDouble() - 0.49) * 0.012
        }
        val nextPrice = round2((current.current * (1.0 + deltaPct)).coerceIn(10.0, 85.0))
        val nextLow = min(current.intradayLow, nextPrice)
        val nextHigh = max(current.intradayHigh, nextPrice)
        val nextSeries = (current.intradaySeries + nextPrice).takeLast(60)
        return buildSnapshot(
            current = nextPrice,
            open = current.open,
            prevClose = current.prevClose,
            intradayLow = nextLow,
            intradayHigh = nextHigh,
            intradaySeries = nextSeries,
            source = current.source,
            nowMs = nowMs
        )
    }

    fun simulateSpike(
        current: VixSnapshot,
        thresholdPct: Double,
        nowMs: Long = System.currentTimeMillis()
    ): VixSnapshot {
        val targetSpikeRatio = (thresholdPct.coerceIn(MIN_THRESHOLD_PCT, MAX_THRESHOLD_PCT) + 2.5) / 100.0
        val baseline = min(current.intradayLow, current.open).coerceAtLeast(10.0)
        val spikedLevel = round2(max(current.current * 1.03, baseline * (1.0 + targetSpikeRatio)))
        val nextHigh = max(current.intradayHigh, spikedLevel)
        val nextSeries = (current.intradaySeries + spikedLevel).takeLast(60)
        return buildSnapshot(
            current = spikedLevel,
            open = current.open,
            prevClose = current.prevClose,
            intradayLow = current.intradayLow,
            intradayHigh = nextHigh,
            intradaySeries = nextSeries,
            source = current.source,
            nowMs = nowMs
        )
    }
}
