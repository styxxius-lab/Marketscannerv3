package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing each simulated BUY or SELL order executed inside the
 * Paper Trading Simulator for auditable realized P&L and trade history.
 */
@Entity(
    tableName = "paper_trade_executions",
    indices = [
        Index(value = ["executedAtMs"]),
        Index(value = ["symbol"])
    ]
)
data class PaperTradeExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val shares: Double,
    val executionPrice: Double,
    val notional: Double,
    val realizedPnl: Double? = null,
    val notes: String = "",
    val executedAtMs: Long = System.currentTimeMillis()
)
