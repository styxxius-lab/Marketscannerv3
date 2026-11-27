package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPnlSnapshotDao {

    @Query("SELECT * FROM daily_pnl_snapshots ORDER BY epochDayMs DESC LIMIT :limit")
    fun observeRecentSnapshots(limit: Int = 30): Flow<List<DailyPnlSnapshotEntity>>

    @Query("SELECT * FROM daily_pnl_snapshots ORDER BY epochDayMs ASC")
    suspend fun getAllSnapshots(): List<DailyPnlSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: DailyPnlSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<DailyPnlSnapshotEntity>)

    @Query("DELETE FROM daily_pnl_snapshots")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(snapshots: List<DailyPnlSnapshotEntity>) {
        clearAll()
        if (snapshots.isNotEmpty()) {
            upsertAll(snapshots)
        }
    }
}
