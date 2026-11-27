package com.example.domain.engine

import com.example.domain.model.BarSeries
import com.example.domain.model.BotState
import com.example.domain.model.CryptoGuardVerdict
import com.example.domain.model.EquityPoint
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalRow
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PaperBotEngine {
    const val RISK_PCT = 0.01
    const val DAILY_LOSS_LIMIT = 0.03
    const val MAX_TRADES_PER_DAY = 3
    const val MAX_POSITIONS = 1
    const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    const val THIRTY_DAY_WINDOW_DAYS = 30

    enum class LogExportFormat(
        val label: String,
        val shortLabel: String,
        val extension: String,
        val mimeType: String
    ) {
        CSV("CSV (.csv)", "CSV", "csv", "text/csv"),
        TSV("Tab Text (.txt)", "TXT", "txt", "text/plain")
    }

    data class EquityCurveSummary(
        val points: List<EquityPoint>,
        val high: Double,
        val low: Double,
        val maxDrawdownPct: Double
    )

    data class DailyEquityPoint(
        val dayKey: String,
        val timestampMs: Long,
        val equity: Double,
        val returnPct: Double,
        val hasActivity: Boolean
    )

    data class ThirtyDayPerformanceSummary(
        val points: List<DailyEquityPoint>,
        val high: Double,
        val low: Double,
        val maxDrawdownPct: Double,
        val periodReturnPct: Double,
        val periodPnl: Double,
        val activeDaysCount: Int
    )

    private fun round6(x: Double): Double = kotlin.math.round(x * 1_000_000.0) / 1_000_000.0
    private fun round4(x: Double): Double = (x * 10000.0).roundToInt() / 10000.0
    private fun round2(x: Double): Double = (x * 100.0).roundToInt() / 100.0
    private fun roundPrice(x: Double, symbol: String): Double =
        if (symbol.contains("/") && x < 5.0) round4(x) else round2(x)
    private fun roundQty(x: Double): Double =
        if (x < 0.01) round6(x) else round4(x)

    fun todayKey(nowMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))

    fun totalMarketValue(state: BotState): Double =
        round4(state.positions.sumOf { it.qty * it.last })

    fun totalUnrealizedPnl(state: BotState): Double =
        round4(state.positions.sumOf { it.upnl })

    fun liveEquity(state: BotState): Double =
        round4(state.cash + state.positions.sumOf { it.qty * it.last })

    fun detectNewLogEntries(before: BotState, after: BotState): List<TradeLogEntry> {
        if (after.log === before.log || after.log.isEmpty()) return emptyList()
        val prevHead = before.log.firstOrNull() ?: return after.log.reversed()
        val idx = after.log.indexOf(prevHead)
        val count = if (idx >= 0) idx else after.log.size
        return after.log.take(count).reversed()
    }

    fun buildThirtyDayEquitySeries(
        state: BotState,
        nowMs: Long = System.currentTimeMillis()
    ): EquityCurveSummary {
        val base = if (state.initialEquity > 0.0) state.initialEquity else 10.0
        val cutoff = nowMs - THIRTY_DAYS_MS
        val inWindow = state.equityHistory
            .filter { it.timestampMs >= cutoff }
            .sortedBy { it.timestampMs }
            .toMutableList()

        if (inWindow.isEmpty()) {
            val anchorEq = state.equityHistory.lastOrNull()?.equity ?: base
            inWindow.add(EquityPoint(nowMs - 60_000L, anchorEq))
        }

        val currentLive = liveEquity(state)
        val lastPt = inWindow.lastOrNull()
        if (lastPt == null || abs(lastPt.equity - currentLive) >= 0.0001 || nowMs > lastPt.timestampMs) {
            inWindow.add(EquityPoint(nowMs, currentLive))
        }

        var peak = inWindow.first().equity
        var maxDd = 0.0
        var high = inWindow.first().equity
        var low = inWindow.first().equity
        for (pt in inWindow) {
            if (pt.equity > high) high = pt.equity
            if (pt.equity < low) low = pt.equity
            if (pt.equity > peak) peak = pt.equity
            if (peak > 0.0) {
                val dd = ((peak - pt.equity) / peak) * 100.0
                if (dd > maxDd) maxDd = dd
            }
        }

        return EquityCurveSummary(
            points = inWindow,
            high = round2(high),
            low = round2(low),
            maxDrawdownPct = round2(maxDd)
        )
    }

    fun buildDailyThirtyDayPerformance(
        state: BotState,
        nowMs: Long = System.currentTimeMillis()
    ): ThirtyDayPerformanceSummary {
        val base = if (state.initialEquity > 0.0) state.initialEquity else 10.0
        val currentLive = liveEquity(state)
        val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dayFormatter.format(Date(nowMs))

        val daySlots = ArrayList<Pair<String, Long>>(THIRTY_DAY_WINDOW_DAYS)
        val cal = Calendar.getInstance()
        for (offset in (THIRTY_DAY_WINDOW_DAYS - 1) downTo 0) {
            cal.timeInMillis = nowMs
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val ts = if (offset == 0) nowMs else cal.timeInMillis
            daySlots.add(dayFormatter.format(Date(ts)) to ts)
        }

        val windowFirstKey = daySlots.first().first
        val sortedHistory = state.equityHistory.sortedBy { it.timestampMs }
        val historyByDay = sortedHistory.groupBy { dayFormatter.format(Date(it.timestampMs)) }
        val logDays = state.log.map { dayFormatter.format(Date(it.timestampMs)) }.toSet()

        val priorPoint = sortedHistory.lastOrNull { dayFormatter.format(Date(it.timestampMs)) < windowFirstKey }
        var carryEquity = priorPoint?.equity ?: base
        var hasStarted = priorPoint != null

        val dailyPoints = ArrayList<DailyEquityPoint>(THIRTY_DAY_WINDOW_DAYS)
        for ((key, ts) in daySlots) {
            val dayHistory = historyByDay[key]
            val eq = when {
                key == today -> {
                    hasStarted = true
                    currentLive
                }
                !dayHistory.isNullOrEmpty() -> {
                    hasStarted = true
                    dayHistory.last().equity
                }
                else -> {
                    if (logDays.contains(key)) hasStarted = true
                    carryEquity
                }
            }
            carryEquity = eq
            val retPct = if (base > 0.0) ((eq - base) / base) * 100.0 else 0.0
            dailyPoints.add(
                DailyEquityPoint(
                    dayKey = key,
                    timestampMs = ts,
                    equity = round2(eq),
                    returnPct = round2(retPct),
                    hasActivity = hasStarted
                )
            )
        }

        val activeSummary = buildThirtyDayEquitySeries(state, nowMs)
        val periodPnl = round2(currentLive - base)
        val periodReturnPct = if (base > 0.0) round2(((currentLive - base) / base) * 100.0) else 0.0

        return ThirtyDayPerformanceSummary(
            points = dailyPoints,
            high = activeSummary.high,
            low = activeSummary.low,
            maxDrawdownPct = activeSummary.maxDrawdownPct,
            periodReturnPct = periodReturnPct,
            periodPnl = periodPnl,
            activeDaysCount = dailyPoints.count { it.hasActivity }
        )
    }

    private fun appendEquityPoint(
        history: List<EquityPoint>,
        equity: Double,
        nowMs: Long
    ): List<EquityPoint> {
        val last = history.lastOrNull()
        if (last != null && abs(last.equity - equity) < 0.0001 && (nowMs - last.timestampMs) < 15_000L) {
            return history
        }
        val cutoff = nowMs - THIRTY_DAYS_MS
        return (history.filter { it.timestampMs >= cutoff } + EquityPoint(nowMs, equity)).takeLast(360)
    }

    fun tick(
        state: BotState,
        rows: List<SignalRow>,
        nowMs: Long = System.currentTimeMillis(),
        advisorVerdicts: Map<String, AdvisorVetoVerdict> = emptyMap(),
        advisorEnabled: Boolean = false,
        regimeLabel: String = "",
        cryptoGuards: Map<String, CryptoGuardVerdict> = emptyMap(),
        pegBreached: Boolean = false,
        edgeDecayed: Boolean = false,
        heatBreached: Boolean = false,
        barsBySymbol: Map<String, BarSeries> = emptyMap()
    ): BotState {
        var s = state
        val wasOpenAtStart = s.positions.isNotEmpty()
        val today = todayKey(nowMs)
        if (s.dayKey != today) {
            s = s.copy(dayKey = today, tradesToday = 0, halted = false)
        }
        if (s.equityHistory.isEmpty()) {
            s = s.copy(equityHistory = listOf(EquityPoint(nowMs, liveEquity(s))))
        }

        // Mark-to-market & stop/target exit checks for every held position
        if (s.positions.isNotEmpty()) {
            val rowMap = rows.associateBy { it.symbol }
            val currentPositions = s.positions
            val remaining = ArrayList<TradePosition>(currentPositions.size)
            for (pos in currentPositions) {
                val q = rowMap[pos.symbol]
                if (q != null) {
                    val upnl = round4((q.price - pos.entry) * pos.qty)
                    val highPx = max(pos.highestPrice, q.price)
                    val age = pos.sessionAge + 1
                    val risk = if (pos.initialRisk > 0.0) pos.initialRisk else abs(pos.entry - pos.stop).coerceAtLeast(0.01)
                    val rMult = round2((q.price - pos.entry) / risk)

                    var workingPos = pos.copy(
                        last = q.price,
                        upnl = upnl,
                        highestPrice = highPx,
                        initialRisk = risk,
                        rMultiple = rMult,
                        sessionAge = age
                    )

                    // 1. Check exit triggers first (target, stop, or time stop)
                    val timeStopTriggered = s.running && workingPos.sessionAge >= 15 && workingPos.rMultiple < 0.3
                    val hit = when {
                        q.price <= workingPos.stop -> if (workingPos.isTrailing) "trailing stop" else "stop"
                        q.price >= workingPos.target -> "target"
                        timeStopTriggered -> "time stop (15 sessions < 0.3R)"
                        else -> null
                    }

                    if (hit != null) {
                        val fillPx = when (hit) {
                            "target" -> workingPos.target
                            "trailing stop", "stop" -> workingPos.stop
                            else -> q.price
                        }
                        s = closeTrade(s, workingPos, hit, fillPx, nowMs)
                    } else {
                        // 2. Automated Partial Profit Taking at +1.0R (Trim 50% & move stop to Breakeven)
                        if (s.running && rMult >= 1.0 && !workingPos.partialTaken && workingPos.qty >= 0.0002) {
                            val trimQty = round4(workingPos.qty * 0.5)
                            val remainQty = round4(workingPos.qty - trimQty)
                            if (trimQty > 0.0001 && remainQty > 0.0001) {
                                val realizedPnl = round4((q.price - workingPos.entry) * trimQty)
                                val remainUpnl = round4((q.price - workingPos.entry) * remainQty)
                                val breakevenStop = max(workingPos.stop, workingPos.entry)
                                workingPos = workingPos.copy(
                                    qty = remainQty,
                                    upnl = remainUpnl,
                                    stop = breakevenStop,
                                    partialTaken = true,
                                    isTrailing = true
                                )
                                val trimLog = TradeLogEntry(
                                    timestampMs = nowMs,
                                    type = "TRIM",
                                    symbol = workingPos.symbol,
                                    qty = trimQty,
                                    price = round2(q.price),
                                    pnl = realizedPnl,
                                    note = "+1.0R reached · 50% trim & stop to breakeven",
                                    llmVerdict = workingPos.llmVerdict.ifBlank { "off" },
                                    regime = workingPos.entryRegime.ifBlank { regimeLabel },
                                    rMultiple = rMult
                                )
                                s = s.copy(
                                    equity = round4(s.equity + realizedPnl),
                                    cash = round4(s.cash + trimQty * q.price),
                                    log = listOf(trimLog) + s.log.take(49)
                                )
                            }
                        }

                        // 3. Trailing Stop Ratchet (2.0 x ATR trailing below highest high)
                        if (workingPos.isTrailing) {
                            val atrVal = if (q.atrPct > 0.0) (q.price * q.atrPct / 100.0) else (risk * 0.75)
                            val trailStop = round2(workingPos.highestPrice - 2.0 * atrVal)
                            if (trailStop > workingPos.stop && trailStop < q.price) {
                                workingPos = workingPos.copy(stop = trailStop)
                            }
                        }

                        remaining.add(workingPos)
                    }
                } else {
                    remaining.add(pos)
                }
            }
            s = s.copy(positions = remaining)
            s = s.copy(equityHistory = appendEquityPoint(s.equityHistory, liveEquity(s), nowMs))
        }

        val base = if (s.initialEquity > 0.0) s.initialEquity else 10.0
        if (s.equity <= base * (1.0 - DAILY_LOSS_LIMIT)) {
            s = s.copy(halted = true)
        }

        val entryBlockedByMacroGuard = pegBreached || edgeDecayed || heatBreached
        if (s.running && !s.halted && !wasOpenAtStart && s.positions.isEmpty() && s.tradesToday < MAX_TRADES_PER_DAY) {
            if (entryBlockedByMacroGuard) {
                val guardWhy = when {
                    pegBreached -> "stablecoin peg breach (>1.5% dev) — entries paused"
                    edgeDecayed -> "edge decay guard triggered (t <= -1.0) — entries halted"
                    else -> "portfolio heat cap (>6.0%) reached — entries paused"
                }
                val alreadyLoggedMacroToday = s.log.any { entry ->
                    entry.type.equals("GUARD", ignoreCase = true) &&
                        entry.symbol == "BOOK" &&
                        todayKey(entry.timestampMs) == today
                }
                if (!alreadyLoggedMacroToday) {
                    val macroLog = TradeLogEntry(
                        timestampMs = nowMs,
                        type = "GUARD",
                        symbol = "BOOK",
                        qty = 0.0,
                        price = liveEquity(s),
                        pnl = null,
                        note = guardWhy,
                        llmVerdict = "guard",
                        regime = regimeLabel
                    )
                    s = s.copy(log = listOf(macroLog) + s.log.take(49))
                }
                return s
            }
            val eligible = rows.filter { r ->
                r.action == SignalAction.BUY &&
                    r.size > 0.0 &&
                    (r.cost == null || r.cost.viable) &&
                    !(r.event?.blocked == true)
            }
            val heldSyms = s.positions.map { it.symbol }.toSet()
            var chosen: SignalRow? = null
            var chosenGuard: CryptoGuardVerdict? = null
            for (r in eligible) {
                if (heldSyms.isNotEmpty() && barsBySymbol.isNotEmpty() &&
                    !CryptoTraderEngine.isCorrelationOk(r.symbol, heldSyms, barsBySymbol)
                ) {
                    continue
                }
                val guard = cryptoGuards[r.symbol]
                if (guard != null && guard.blocked) {
                    // CryptoPanic + Kraken Futures Guard: blocks new entries only (never panic-sells held positions)
                    val alreadyLoggedGuardToday = s.log.any { entry ->
                        entry.type.equals("GUARD", ignoreCase = true) &&
                            entry.symbol == r.symbol &&
                            todayKey(entry.timestampMs) == today
                    }
                    if (!alreadyLoggedGuardToday) {
                        val reasonText = guard.notes.joinToString("; ")
                            .ifBlank { "adverse crypto news/funding" }
                        val guardLog = TradeLogEntry(
                            timestampMs = nowMs,
                            type = "GUARD",
                            symbol = r.symbol,
                            qty = 0.0,
                            price = r.price,
                            pnl = null,
                            note = "entry blocked · $reasonText".take(180),
                            llmVerdict = "guard",
                            regime = regimeLabel
                        )
                        s = s.copy(log = listOf(guardLog) + s.log.take(49))
                    }
                    continue
                }
                val verdict = advisorVerdicts[r.symbol]
                if (verdict != null && verdict.isVeto) {
                    // Rule #2 (Veto only) & Rule #3 (Log verdict): skip entry and record VETO once per symbol/day
                    val alreadyLoggedToday = s.log.any { entry ->
                        entry.type.equals("VETO", ignoreCase = true) &&
                            entry.symbol == r.symbol &&
                            todayKey(entry.timestampMs) == today
                    }
                    if (!alreadyLoggedToday) {
                        val vetoNote = buildString {
                            append("veto: ")
                            append(verdict.category.ifBlank { "adverse" })
                            if (verdict.why.isNotBlank()) {
                                append(" · ").append(verdict.why)
                            }
                        }.take(180)
                        val vetoLog = TradeLogEntry(
                            timestampMs = nowMs,
                            type = "VETO",
                            symbol = r.symbol,
                            qty = 0.0,
                            price = r.price,
                            pnl = null,
                            note = vetoNote,
                            llmVerdict = "veto",
                            regime = regimeLabel
                        )
                        s = s.copy(log = listOf(vetoLog) + s.log.take(49))
                    }
                    continue
                }
                chosen = r
                chosenGuard = guard
                break
            }
            if (chosen != null) {
                val llmTag = resolveLlmTag(chosen.symbol, advisorVerdicts, advisorEnabled)
                val sizeMult = chosenGuard?.sizeMult?.coerceIn(0.25, 1.0) ?: 1.0
                val guardSuffix = if (chosenGuard != null && sizeMult < 0.99) {
                    " · guard ${"%.2f".format(Locale.US, sizeMult)}x"
                } else {
                    ""
                }
                s = openPositionInternal(
                    state = s,
                    cand = chosen,
                    note = "conviction ${"%.2f".format(Locale.US, chosen.conviction)}$guardSuffix",
                    nowMs = nowMs,
                    llmVerdict = llmTag,
                    regimeLabel = regimeLabel,
                    sizeMult = sizeMult
                )
            }
        }
        return s
    }

    private fun resolveLlmTag(
        symbol: String,
        advisorVerdicts: Map<String, AdvisorVetoVerdict>,
        advisorEnabled: Boolean
    ): String {
        val verdict = advisorVerdicts[symbol]
        return when {
            verdict?.isVeto == true -> "veto"
            verdict != null -> verdict.llmTag.ifBlank { if (advisorEnabled) "clear" else "off" }
            advisorEnabled -> "clear"
            else -> "off"
        }
    }

    fun openManual(
        state: BotState,
        signal: SignalRow,
        nowMs: Long = System.currentTimeMillis(),
        customQty: Double? = null,
        llmVerdict: String = "off",
        regimeLabel: String = ""
    ): BotState {
        var s = state
        val today = todayKey(nowMs)
        if (s.dayKey != today) {
            s = s.copy(dayKey = today, tradesToday = 0, halted = false)
        }
        if (s.halted || signal.price <= 0.0 || s.cash <= 0.01) {
            return s
        }
        if (s.positions.any { it.symbol == signal.symbol }) {
            return s
        }
        val notePrefix = if (customQty != null) "sized manual" else "manual"
        return openPositionInternal(
            state = s,
            cand = signal,
            note = "$notePrefix · conv ${"%.2f".format(Locale.US, signal.conviction)}",
            nowMs = nowMs,
            customQty = customQty,
            llmVerdict = llmVerdict.ifBlank { "off" },
            regimeLabel = regimeLabel
        )
    }

    private fun openPositionInternal(
        state: BotState,
        cand: SignalRow,
        note: String,
        nowMs: Long,
        customQty: Double? = null,
        llmVerdict: String = "off",
        regimeLabel: String = "",
        sizeMult: Double = 1.0
    ): BotState {
        val base = if (state.initialEquity > 0.0) state.initialEquity else 10.0
        val stopPrice = if (cand.stop < cand.price) cand.stop else roundPrice(cand.price * 0.97, cand.symbol)
        val targetPrice = if (cand.target > cand.price) cand.target else roundPrice(cand.price * 1.06, cand.symbol)
        val risk = abs(cand.price - stopPrice).let { if (it <= 0.0) 1.0 else it }
        val maxAffordable = state.cash * 0.95 / cand.price
        val rawQty = if (customQty != null && customQty > 0.0) {
            min(customQty, maxAffordable)
        } else {
            min(base * RISK_PCT / risk, maxAffordable) * sizeMult.coerceIn(0.25, 1.0)
        }
        val minQtyFloor = if (cand.symbol.contains("/")) 0.00001 else 0.0001
        if (rawQty <= minQtyFloor) return state

        val cleanVerdict = llmVerdict.trim().lowercase(Locale.US).ifBlank { "off" }
        val roundedQty = roundQty(rawQty)
        val newPos = TradePosition(
            symbol = cand.symbol,
            qty = roundedQty,
            entry = cand.price,
            stop = stopPrice,
            target = targetPrice,
            openedAtMs = nowMs,
            upnl = 0.0,
            last = cand.price,
            highestPrice = cand.price,
            initialRisk = risk,
            rMultiple = 0.0,
            partialTaken = false,
            isTrailing = false,
            sessionAge = 0,
            llmVerdict = cleanVerdict,
            entryRegime = regimeLabel
        )
        val entryLog = TradeLogEntry(
            timestampMs = nowMs,
            type = "OPEN",
            symbol = cand.symbol,
            qty = roundedQty,
            price = cand.price,
            note = note,
            llmVerdict = cleanVerdict,
            regime = regimeLabel
        )
        val next = state.copy(
            positions = state.positions + newPos,
            cash = round4(state.cash - roundedQty * cand.price),
            tradesToday = state.tradesToday + 1,
            log = listOf(entryLog) + state.log.take(49)
        )
        return next.copy(
            equityHistory = appendEquityPoint(next.equityHistory, liveEquity(next), nowMs)
        )
    }

    fun closeManual(
        state: BotState,
        symbol: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): BotState {
        val targetPos = if (symbol != null) {
            state.positions.find { it.symbol == symbol }
        } else {
            state.positions.firstOrNull()
        } ?: return state
        val remaining = state.positions.filterNot { it.symbol == targetPos.symbol }
        return closeTrade(state.copy(positions = remaining), targetPos, "manual", targetPos.last, nowMs)
    }

    fun trimManual(
        state: BotState,
        symbol: String,
        fraction: Double = 0.5,
        reason: String = "threat trim",
        nowMs: Long = System.currentTimeMillis()
    ): BotState {
        val pos = state.positions.find { it.symbol == symbol } ?: return state
        val safeFraction = fraction.coerceIn(0.05, 1.0)
        if (safeFraction >= 0.99) {
            return closeTrade(state, pos, reason, pos.last, nowMs)
        }
        val trimQty = round4(pos.qty * safeFraction)
        val remainQty = round4(pos.qty - trimQty)
        if (trimQty <= 0.0001 || remainQty <= 0.0001) {
            return closeTrade(state, pos, reason, pos.last, nowMs)
        }
        val px = pos.last
        val realizedPnl = round4((px - pos.entry) * trimQty)
        val remainUpnl = round4((px - pos.entry) * remainQty)
        val updatedPos = pos.copy(
            qty = remainQty,
            upnl = remainUpnl,
            partialTaken = true,
            isTrailing = true
        )
        val remaining = state.positions.map { if (it.symbol == pos.symbol) updatedPos else it }
        val risk = if (pos.initialRisk > 0.0) pos.initialRisk else abs(pos.entry - pos.stop).coerceAtLeast(0.01)
        val rMult = round2((px - pos.entry) / risk)
        val trimLog = TradeLogEntry(
            timestampMs = nowMs,
            type = "TRIM",
            symbol = pos.symbol,
            qty = trimQty,
            price = round2(px),
            pnl = realizedPnl,
            note = reason,
            llmVerdict = pos.llmVerdict.ifBlank { "off" },
            regime = pos.entryRegime,
            rMultiple = rMult
        )
        val next = state.copy(
            equity = round4(state.equity + realizedPnl),
            cash = round4(state.cash + trimQty * px),
            positions = remaining,
            log = listOf(trimLog) + state.log.take(49)
        )
        return next.copy(
            equityHistory = appendEquityPoint(next.equityHistory, liveEquity(next), nowMs)
        )
    }

    fun updatePositionStop(
        state: BotState,
        symbol: String,
        newStop: Double
    ): BotState {
        if (!newStop.isFinite() || newStop <= 0.0) return state
        val roundedStop = round2(newStop)
        var changed = false
        val updated = state.positions.map { pos ->
            if (pos.symbol == symbol && roundedStop < pos.last && abs(roundedStop - pos.stop) >= 0.01) {
                changed = true
                pos.copy(stop = roundedStop)
            } else {
                pos
            }
        }
        return if (changed) state.copy(positions = updated) else state
    }

    private fun closeTrade(
        state: BotState,
        pos: TradePosition,
        reason: String,
        px: Double,
        nowMs: Long
    ): BotState {
        val pnl = round4((px - pos.entry) * pos.qty)
        val risk = if (pos.initialRisk > 0.0) pos.initialRisk else abs(pos.entry - pos.stop).coerceAtLeast(0.01)
        val rMult = round2((px - pos.entry) / risk)
        val closeLog = TradeLogEntry(
            timestampMs = nowMs,
            type = "CLOSE",
            symbol = pos.symbol,
            qty = pos.qty,
            price = round2(px),
            pnl = pnl,
            note = reason,
            llmVerdict = pos.llmVerdict.ifBlank { "off" },
            regime = pos.entryRegime,
            rMultiple = rMult
        )
        val nextEquity = round4(state.equity + pnl)
        val remaining = state.positions.filterNot { it.symbol == pos.symbol }
        val next = state.copy(
            equity = nextEquity,
            cash = round4(state.cash + pos.qty * px),
            positions = remaining,
            log = listOf(closeLog) + state.log.take(49)
        )
        return next.copy(
            equityHistory = appendEquityPoint(next.equityHistory, liveEquity(next), nowMs)
        )
    }

    fun reset(accountSize: Double = 10.0, nowMs: Long = System.currentTimeMillis()): BotState = BotState(
        equity = accountSize,
        cash = accountSize,
        initialEquity = accountSize,
        positions = emptyList(),
        log = emptyList(),
        equityHistory = listOf(EquityPoint(nowMs, accountSize)),
        dayKey = todayKey(nowMs),
        tradesToday = 0,
        halted = false,
        running = false
    )

    fun suggestedExportFileName(
        format: LogExportFormat,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        return "bot_trade_log_$datePart.${format.extension}"
    }

    fun exportTradeLog(
        state: BotState,
        format: LogExportFormat = LogExportFormat.CSV,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val delim = if (format == LogExportFormat.CSV) "," else "\t"
        val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            this.timeZone = timeZone
        }

        fun escapeCell(raw: String): String {
            val cleaned = raw.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
            return if (format == LogExportFormat.CSV) {
                if (cleaned.contains(',') || cleaned.contains('"')) {
                    "\"${cleaned.replace("\"", "\"\"")}\""
                } else {
                    cleaned
                }
            } else {
                cleaned.replace("\t", " ")
            }
        }

        val sb = StringBuilder()
        val headers = listOf(
            "Timestamp",
            "Action",
            "Symbol",
            "Qty",
            "Price",
            "Notional",
            "Realized_PnL",
            "Cumulative_PnL",
            "Note"
        )
        sb.append(headers.joinToString(delim)).append('\n')

        val chronological = state.log.sortedBy { it.timestampMs }
        var cumPnl = 0.0
        for (entry in chronological) {
            val notional = entry.qty * entry.price
            if (entry.pnl != null) {
                cumPnl += entry.pnl
            }
            val rowCells = listOf(
                tsFmt.format(Date(entry.timestampMs)),
                entry.type.uppercase(Locale.US),
                entry.symbol.uppercase(Locale.US),
                "%.4f".format(Locale.US, entry.qty),
                "%.2f".format(Locale.US, entry.price),
                "%.2f".format(Locale.US, notional),
                if (entry.pnl != null) "%.4f".format(Locale.US, entry.pnl) else "",
                "%.4f".format(Locale.US, cumPnl),
                escapeCell(entry.note)
            )
            sb.append(rowCells.joinToString(delim)).append('\n')
        }
        return sb.toString()
    }
}
