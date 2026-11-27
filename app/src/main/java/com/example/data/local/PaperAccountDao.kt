package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperAccountDao {

    @Query("SELECT * FROM paper_account WHERE id = :id LIMIT 1")
    fun observeAccount(id: Int = PaperAccountEntity.SINGLETON_ID): Flow<PaperAccountEntity?>

    @Query("SELECT * FROM paper_account WHERE id = :id LIMIT 1")
    suspend fun getAccount(id: Int = PaperAccountEntity.SINGLETON_ID): PaperAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: PaperAccountEntity)

    @Query("SELECT * FROM paper_trade_executions ORDER BY executedAtMs DESC LIMIT :limit")
    fun observeRecentTrades(limit: Int = 50): Flow<List<PaperTradeExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: PaperTradeExecutionEntity): Long

    @Query("DELETE FROM paper_trade_executions")
    suspend fun clearTrades()
}
