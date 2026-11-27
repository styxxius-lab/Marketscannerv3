package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperHoldingDao {

    @Query("SELECT * FROM holdings ORDER BY purchasedAtMs DESC")
    fun observeHoldings(): Flow<List<PaperHoldingEntity>>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): PaperHoldingEntity?

    @Query("SELECT * FROM holdings WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PaperHoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: PaperHoldingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holdings: List<PaperHoldingEntity>)

    @Update
    suspend fun updateHolding(holding: PaperHoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM holdings")
    suspend fun clearAll()
}
