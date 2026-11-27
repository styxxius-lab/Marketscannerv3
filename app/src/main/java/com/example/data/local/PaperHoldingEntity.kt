package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a locally stored simulated paper-trading holding with a user-defined
 * dummy purchase price and share quantity.
 */
@Entity(
    tableName = "holdings",
    indices = [
        Index(value = ["symbol"], unique = true),
        Index(value = ["purchasedAtMs"])
    ]
)
data class PaperHoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val shares: Double,
    val purchasePrice: Double,
    val notes: String = "",
    val purchasedAtMs: Long = System.currentTimeMillis()
)
