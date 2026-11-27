package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ThreatHistoryEntity::class,
        PaperHoldingEntity::class,
        DailyPnlSnapshotEntity::class,
        PaperAccountEntity::class,
        PaperTradeExecutionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MarketScanDatabase : RoomDatabase() {
    abstract fun threatHistoryDao(): ThreatHistoryDao
    abstract fun paperHoldingDao(): PaperHoldingDao
    abstract fun dailyPnlSnapshotDao(): DailyPnlSnapshotDao
    abstract fun paperAccountDao(): PaperAccountDao

    companion object {
        @Volatile
        private var INSTANCE: MarketScanDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `holdings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `shares` REAL NOT NULL,
                        `purchasePrice` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `purchasedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_holdings_symbol` ON `holdings` (`symbol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holdings_purchasedAtMs` ON `holdings` (`purchasedAtMs`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_pnl_snapshots` (
                        `dateKey` TEXT NOT NULL,
                        `epochDayMs` INTEGER NOT NULL,
                        `dailyPnl` REAL NOT NULL,
                        `dailyPnlPct` REAL NOT NULL,
                        `cumulativePnl` REAL NOT NULL,
                        `portfolioValue` REAL NOT NULL,
                        `costBasis` REAL NOT NULL,
                        `holdingsCount` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_pnl_snapshots_epochDayMs` ON `daily_pnl_snapshots` (`epochDayMs`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paper_account` (
                        `id` INTEGER NOT NULL,
                        `startingCapital` REAL NOT NULL,
                        `cashBalance` REAL NOT NULL,
                        `realizedPnl` REAL NOT NULL,
                        `totalBuyOrders` INTEGER NOT NULL,
                        `totalSellOrders` INTEGER NOT NULL,
                        `winningSells` INTEGER NOT NULL,
                        `losingSells` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paper_trade_executions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `side` TEXT NOT NULL,
                        `shares` REAL NOT NULL,
                        `executionPrice` REAL NOT NULL,
                        `notional` REAL NOT NULL,
                        `realizedPnl` REAL,
                        `notes` TEXT NOT NULL,
                        `executedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paper_trade_executions_executedAtMs` ON `paper_trade_executions` (`executedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paper_trade_executions_symbol` ON `paper_trade_executions` (`symbol`)")
            }
        }

        fun getInstance(context: Context): MarketScanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarketScanDatabase::class.java,
                    "market_scan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
