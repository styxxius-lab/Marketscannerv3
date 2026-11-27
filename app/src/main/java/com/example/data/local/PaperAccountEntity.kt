package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room singleton entity storing the simulated Paper Trading Simulator account ledger:
 * starting capital, available cash buying power, cumulative realized P&L, and order stats.
 */
@Entity(tableName = "paper_account")
data class PaperAccountEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val startingCapital: Double = DEFAULT_STARTING_CAPITAL,
    val cashBalance: Double = DEFAULT_STARTING_CAPITAL,
    val realizedPnl: Double = 0.0,
    val totalBuyOrders: Int = 0,
    val totalSellOrders: Int = 0,
    val winningSells: Int = 0,
    val losingSells: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_STARTING_CAPITAL = 25_000.0
    }
}
