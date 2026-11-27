package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatHistoryDao {

    @Query("SELECT * FROM threat_history ORDER BY timestampMs DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<ThreatHistoryEntity>>

    @Query("SELECT * FROM threat_history WHERE symbol = :symbol ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestForSymbol(symbol: String): ThreatHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ThreatHistoryEntity): Long

    @Query("DELETE FROM threat_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM threat_history WHERE symbol = :symbol")
    suspend fun deleteForSymbol(symbol: String)

    @Query("DELETE FROM threat_history")
    suspend fun clearAll()
}
