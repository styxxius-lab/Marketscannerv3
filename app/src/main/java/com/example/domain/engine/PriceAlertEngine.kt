package com.example.domain.engine

import com.example.domain.model.AlertCondition
import com.example.domain.model.PriceAlert
import com.example.domain.model.SignalAction
import com.example.domain.model.SignalAlertMode
import com.example.domain.model.SignalChangeAlert
import com.example.domain.model.SignalRow
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

data class AlertEvaluationResult(
    val updatedAlerts: List<PriceAlert>,
    val newlyTriggered: List<PriceAlert>
)

data class SignalChangeTriggerEvent(
    val alertId: String,
    val symbol: String,
    val previousAction: SignalAction,
    val newAction: SignalAction,
    val price: Double,
    val conviction: Double,
    val reason: String,
    val triggeredAtMs: Long
)

data class SignalAlertEvaluationResult(
    val updatedAlerts: List<SignalChangeAlert>,
    val triggeredEvents: List<SignalChangeTriggerEvent>
)

object PriceAlertEngine {

    private fun round2(x: Double): Double = (x * 100.0).roundToInt() / 100.0

    fun createAlert(
        symbol: String,
        targetPrice: Double,
        condition: AlertCondition,
        nowMs: Long = System.currentTimeMillis()
    ): PriceAlert {
        return PriceAlert(
            id = UUID.randomUUID().toString(),
            symbol = symbol.trim().uppercase(Locale.US),
            targetPrice = round2(targetPrice),
            condition = condition,
            createdAtMs = nowMs,
            active = true,
            triggeredAtMs = null,
            triggeredPrice = null
        )
    }

    fun evaluate(
        alerts: List<PriceAlert>,
        prices: Map<String, Double>,
        nowMs: Long = System.currentTimeMillis()
    ): AlertEvaluationResult {
        if (alerts.isEmpty() || prices.isEmpty()) {
            return AlertEvaluationResult(alerts, emptyList())
        }
        val triggered = mutableListOf<PriceAlert>()
        val next = alerts.map { alert ->
            if (!alert.active) {
                alert
            } else {
                val px = prices[alert.symbol]
                val crossed = px != null && px > 0.0 && when (alert.condition) {
                    AlertCondition.ABOVE -> px >= alert.targetPrice
                    AlertCondition.BELOW -> px <= alert.targetPrice
                }
                if (crossed && px != null) {
                    val fired = alert.copy(
                        active = false,
                        triggeredAtMs = nowMs,
                        triggeredPrice = round2(px)
                    )
                    triggered.add(fired)
                    fired
                } else {
                    alert
                }
            }
        }
        return AlertEvaluationResult(updatedAlerts = next, newlyTriggered = triggered)
    }

    fun toggle(alerts: List<PriceAlert>, id: String): List<PriceAlert> = alerts.map {
        if (it.id == id) {
            val nextActive = !it.active
            if (nextActive) {
                it.copy(active = true, triggeredAtMs = null, triggeredPrice = null)
            } else {
                it.copy(active = false)
            }
        } else {
            it
        }
    }

    fun rearm(alerts: List<PriceAlert>, id: String): List<PriceAlert> = alerts.map {
        if (it.id == id) it.copy(active = true, triggeredAtMs = null, triggeredPrice = null) else it
    }

    fun remove(alerts: List<PriceAlert>, id: String): List<PriceAlert> =
        alerts.filterNot { it.id == id }

    fun clearTriggered(alerts: List<PriceAlert>): List<PriceAlert> =
        alerts.filter { it.active || it.triggeredAtMs == null }

    fun upsertSignalAlert(
        alerts: List<SignalChangeAlert>,
        symbol: String,
        enabled: Boolean,
        mode: SignalAlertMode? = null,
        currentAction: SignalAction? = null,
        nowMs: Long = System.currentTimeMillis()
    ): List<SignalChangeAlert> {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (cleanSym.isEmpty()) return alerts
        val existing = alerts.firstOrNull { it.symbol.equals(cleanSym, ignoreCase = true) }
        return if (existing != null) {
            alerts.map { item ->
                if (item.id == existing.id) {
                    item.copy(
                        enabled = enabled,
                        mode = mode ?: item.mode,
                        lastKnownAction = currentAction ?: item.lastKnownAction
                    )
                } else {
                    item
                }
            }
        } else {
            val created = SignalChangeAlert(
                id = UUID.randomUUID().toString(),
                symbol = cleanSym,
                mode = mode ?: SignalAlertMode.ANY_CHANGE,
                lastKnownAction = currentAction,
                enabled = enabled,
                createdAtMs = nowMs
            )
            listOf(created) + alerts
        }
    }

    fun removeSignalAlert(alerts: List<SignalChangeAlert>, id: String): List<SignalChangeAlert> =
        alerts.filterNot { it.id == id }

    fun evaluateSignalChanges(
        signalAlerts: List<SignalChangeAlert>,
        rows: List<SignalRow>,
        nowMs: Long = System.currentTimeMillis()
    ): SignalAlertEvaluationResult {
        if (signalAlerts.isEmpty() || rows.isEmpty()) {
            return SignalAlertEvaluationResult(signalAlerts, emptyList())
        }
        val rowsBySymbol = rows.associateBy { it.symbol.uppercase(Locale.US) }
        val events = mutableListOf<SignalChangeTriggerEvent>()

        val updated = signalAlerts.map { alert ->
            val row = rowsBySymbol[alert.symbol.uppercase(Locale.US)] ?: return@map alert
            val prevAction = alert.lastKnownAction
            if (prevAction == null) {
                alert.copy(lastKnownAction = row.action)
            } else if (!alert.enabled) {
                if (prevAction != row.action) alert.copy(lastKnownAction = row.action) else alert
            } else if (prevAction != row.action) {
                val matchesMode = when (alert.mode) {
                    SignalAlertMode.ANY_CHANGE -> true
                    SignalAlertMode.BUY_ONLY -> row.action == SignalAction.BUY
                    SignalAlertMode.SELL_ONLY -> row.action == SignalAction.SELL
                }
                if (matchesMode) {
                    val transition = "${prevAction.name} → ${row.action.name}"
                    val summaryReason = row.keyCatalyst.ifBlank {
                        row.reasons.firstOrNull().orEmpty().ifBlank { "Quant + AI signal updated" }
                    }
                    events.add(
                        SignalChangeTriggerEvent(
                            alertId = alert.id,
                            symbol = alert.symbol,
                            previousAction = prevAction,
                            newAction = row.action,
                            price = round2(row.price),
                            conviction = row.conviction,
                            reason = summaryReason,
                            triggeredAtMs = nowMs
                        )
                    )
                    alert.copy(
                        lastKnownAction = row.action,
                        lastTriggeredAtMs = nowMs,
                        lastTransitionLabel = transition
                    )
                } else {
                    alert.copy(lastKnownAction = row.action)
                }
            } else {
                alert
            }
        }
        return SignalAlertEvaluationResult(updatedAlerts = updated, triggeredEvents = events)
    }
}
