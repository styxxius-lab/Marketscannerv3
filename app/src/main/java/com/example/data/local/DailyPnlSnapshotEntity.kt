package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing one day's simulated Paper Trading portfolio P&L snapshot over a rolling
 * 30-day window derived from the locally persisted [PaperHoldingEntity] positions.
 */
@Entity(
    tableName = "daily_pnl_snapshots",
    indices = [
        Index(value = ["epochDayMs"])
    ]
)
data class DailyPnlSnapshotEntity(
    @PrimaryKey val dateKey: String,
    val epochDayMs: Long,
    val dailyPnl: Double,
    val dailyPnlPct: Double,
    val cumulativePnl: Double,
    val portfolioValue: Double,
    val costBasis: Double,
    val holdingsCount: Int,
    val updatedAtMs: Long = System.currentTimeMillis()
)
