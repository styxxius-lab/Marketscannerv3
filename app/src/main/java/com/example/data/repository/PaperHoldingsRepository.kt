package com.example.data.repository

import com.example.data.local.DailyPnlSnapshotDao
import com.example.data.local.DailyPnlSnapshotEntity
import com.example.data.local.PaperAccountDao
import com.example.data.local.PaperAccountEntity
import com.example.data.local.PaperHoldingDao
import com.example.data.local.PaperHoldingEntity
import com.example.data.local.PaperTradeExecutionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Repository abstracting Room SQLite access for the local Paper Trading Simulator:
 * simulated account cash & realized P&L, simulated holdings, trade execution ledger,
 * and 30-day daily P&L snapshots.
 */
class PaperHoldingsRepository(
    private val dao: PaperHoldingDao,
    private val dailyPnlDao: DailyPnlSnapshotDao? = null,
    private val accountDao: PaperAccountDao? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    val holdingsFlow: Flow<List<PaperHoldingEntity>> = dao.observeHoldings()

    val accountFlow: Flow<PaperAccountEntity> =
        accountDao?.observeAccount()
            ?.map { it ?: PaperAccountEntity() }
            ?: flowOf(PaperAccountEntity())

    val recentTradesFlow: Flow<List<PaperTradeExecutionEntity>> =
        accountDao?.observeRecentTrades(limit = 60)
            ?: flowOf(emptyList())

    /**
     * Reactive chronological (oldest -> newest) stream of up to 30 daily simulated P&L snapshots
     * stored in Room.
     */
    val recentDailyPnlFlow: Flow<List<DailyPnlSnapshotEntity>> =
        dailyPnlDao?.observeRecentSnapshots(limit = 30)
            ?.map { list -> list.sortedBy { it.epochDayMs } }
            ?: flowOf(emptyList())

    suspend fun ensureAccountInitialized(
        startingCapital: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL
    ): PaperAccountEntity {
        val daoRef = accountDao ?: return PaperAccountEntity(
            startingCapital = startingCapital,
            cashBalance = startingCapital
        )
        val existing = daoRef.getAccount()
        if (existing != null) return existing
        val created = PaperAccountEntity(
            id = PaperAccountEntity.SINGLETON_ID,
            startingCapital = startingCapital,
            cashBalance = startingCapital,
            realizedPnl = 0.0,
            updatedAtMs = clockMs()
        )
        daoRef.upsertAccount(created)
        return created
    }

    /**
     * Executes a simulated BUY order backed by Room cash accounting:
     * validates available buying power, deducts notional cash, merges weighted-average cost into
     * `holdings`, increments buy stats, and appends a `BUY` record to `paper_trade_executions`.
     */
    suspend fun executeSimulatedBuy(
        rawSymbol: String,
        shares: Double,
        executionPrice: Double,
        notes: String = ""
    ): Result<PaperTradeExecutionEntity> {
        val symbol = normalizeSymbol(rawSymbol)
            ?: return Result.failure(IllegalArgumentException("Enter a valid ticker (e.g. NVDA, AAPL, BTC/USD)."))
        if (!shares.isFinite() || shares <= 0.0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than 0."))
        }
        if (!executionPrice.isFinite() || executionPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Execution price must be greater than $0."))
        }

        val notional = round2(shares * executionPrice)
        val account = ensureAccountInitialized()
        if (notional > account.cashBalance + 0.01) {
            return Result.failure(
                IllegalArgumentException(
                    "Insufficient buying power: order is $${String.format(Locale.US, "%.2f", notional)} " +
                        "but cash is $${String.format(Locale.US, "%.2f", account.cashBalance)}."
                )
            )
        }

        val addResult = addOrMergeHolding(
            rawSymbol = symbol,
            shares = shares,
            purchasePrice = executionPrice,
            notes = notes.ifBlank { "Simulated BUY" }
        )
        if (addResult.isFailure) {
            return Result.failure(addResult.exceptionOrNull() ?: IllegalStateException("Buy failed."))
        }

        val now = clockMs()
        val updatedCash = (account.cashBalance - notional).coerceAtLeast(0.0)
        val updatedAccount = account.copy(
            cashBalance = round2(updatedCash),
            totalBuyOrders = account.totalBuyOrders + 1,
            updatedAtMs = now
        )
        accountDao?.upsertAccount(updatedAccount)

        val execution = PaperTradeExecutionEntity(
            symbol = symbol,
            side = "BUY",
            shares = shares,
            executionPrice = executionPrice,
            notional = notional,
            realizedPnl = null,
            notes = notes.trim(),
            executedAtMs = now
        )
        val id = accountDao?.insertTrade(execution) ?: 0L
        return Result.success(execution.copy(id = id))
    }

    /**
     * Executes a simulated SELL order against an existing Room holding:
     * credits proceeds to `cashBalance`, realizes P&L against average cost basis, updates or
     * removes the holding, updates win/loss stats, and appends a `SELL` execution record.
     */
    suspend fun executeSimulatedSell(
        rawSymbol: String,
        sharesToSell: Double,
        executionPrice: Double,
        notes: String = ""
    ): Result<PaperTradeExecutionEntity> {
        val symbol = normalizeSymbol(rawSymbol)
            ?: return Result.failure(IllegalArgumentException("Enter a valid ticker symbol to sell."))
        if (!sharesToSell.isFinite() || sharesToSell <= 0.0) {
            return Result.failure(IllegalArgumentException("Sell quantity must be greater than 0."))
        }
        if (!executionPrice.isFinite() || executionPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Sell price must be greater than $0."))
        }

        val existing = dao.findBySymbol(symbol)
            ?: return Result.failure(IllegalArgumentException("No open simulated position in $symbol."))
        if (sharesToSell > existing.shares + 1e-6) {
            return Result.failure(
                IllegalArgumentException(
                    "Cannot sell ${formatTrimmed(sharesToSell)} $symbol — you only hold ${formatTrimmed(existing.shares)}."
                )
            )
        }

        val effectiveSellQty = sharesToSell.coerceAtMost(existing.shares)
        val proceeds = round2(effectiveSellQty * executionPrice)
        val costRemoved = effectiveSellQty * existing.purchasePrice
        val realized = round2(proceeds - costRemoved)
        val remainingQty = existing.shares - effectiveSellQty
        val now = clockMs()

        if (remainingQty <= 1e-6) {
            dao.deleteById(existing.id)
        } else {
            dao.updateHolding(
                existing.copy(
                    shares = remainingQty,
                    purchasedAtMs = now
                )
            )
        }

        val account = ensureAccountInitialized()
        val isWin = realized > 0.005
        val isLoss = realized < -0.005
        val updatedAccount = account.copy(
            cashBalance = round2(account.cashBalance + proceeds),
            realizedPnl = round2(account.realizedPnl + realized),
            totalSellOrders = account.totalSellOrders + 1,
            winningSells = account.winningSells + if (isWin) 1 else 0,
            losingSells = account.losingSells + if (isLoss) 1 else 0,
            updatedAtMs = now
        )
        accountDao?.upsertAccount(updatedAccount)

        val execution = PaperTradeExecutionEntity(
            symbol = symbol,
            side = "SELL",
            shares = effectiveSellQty,
            executionPrice = executionPrice,
            notional = proceeds,
            realizedPnl = realized,
            notes = notes.trim(),
            executedAtMs = now
        )
        val id = accountDao?.insertTrade(execution) ?: 0L
        return Result.success(execution.copy(id = id))
    }

    suspend fun depositCash(amount: Double): Result<PaperAccountEntity> {
        if (!amount.isFinite() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Deposit amount must be greater than $0."))
        }
        val account = ensureAccountInitialized()
        val updated = account.copy(
            startingCapital = round2(account.startingCapital + amount),
            cashBalance = round2(account.cashBalance + amount),
            updatedAtMs = clockMs()
        )
        accountDao?.upsertAccount(updated)
        return Result.success(updated)
    }

    suspend fun resetSimulatorAccount(
        startingCapital: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL
    ): PaperAccountEntity {
        val cleanCapital = startingCapital.coerceAtLeast(100.0)
        dao.clearAll()
        dailyPnlDao?.clearAll()
        accountDao?.clearTrades()
        val fresh = PaperAccountEntity(
            id = PaperAccountEntity.SINGLETON_ID,
            startingCapital = round2(cleanCapital),
            cashBalance = round2(cleanCapital),
            realizedPnl = 0.0,
            totalBuyOrders = 0,
            totalSellOrders = 0,
            winningSells = 0,
            losingSells = 0,
            updatedAtMs = clockMs()
        )
        accountDao?.upsertAccount(fresh)
        return fresh
    }

    /**
     * Resets the simulator to a clean $25,000 account and executes 3 realistic simulated buys
     * (equities or crypto pairs depending on [isCryptoMode]) so cash, holdings, total equity,
     * and trade history reconcile accurately in Room.
     */
    suspend fun seedSimulatorStarterPortfolio(
        livePricesBySymbol: Map<String, Double>,
        isCryptoMode: Boolean = false
    ) {
        val startCap = PaperAccountEntity.DEFAULT_STARTING_CAPITAL
        resetSimulatorAccount(startCap)
        val specs = if (isCryptoMode) {
            listOf(
                Triple("BTC/USD", 0.08, (livePricesBySymbol["BTC/USD"]?.takeIf { it > 0.0 } ?: 68_000.0) * 0.97),
                Triple("ETH/USD", 1.5, (livePricesBySymbol["ETH/USD"]?.takeIf { it > 0.0 } ?: 3_400.0) * 0.985),
                Triple("SOL/USD", 20.0, (livePricesBySymbol["SOL/USD"]?.takeIf { it > 0.0 } ?: 175.0) * 1.015)
            )
        } else {
            listOf(
                Triple("NVDA", 35.0, (livePricesBySymbol["NVDA"]?.takeIf { it > 0.0 } ?: 135.0) * 0.96),
                Triple("AAPL", 22.0, (livePricesBySymbol["AAPL"]?.takeIf { it > 0.0 } ?: 225.0) * 1.015),
                Triple("MSFT", 12.0, (livePricesBySymbol["MSFT"]?.takeIf { it > 0.0 } ?: 420.0) * 0.975)
            )
        }
        specs.forEach { (sym, qty, rawPx) ->
            val roundedPx = round2(rawPx)
            executeSimulatedBuy(
                rawSymbol = sym,
                shares = qty,
                executionPrice = roundedPx,
                notes = "Starter simulator lot"
            )
        }
    }

    suspend fun replaceDailyPnlWindow(snapshots: List<DailyPnlSnapshotEntity>) {
        dailyPnlDao?.replaceAll(snapshots)
    }

    suspend fun clearDailyPnlSnapshots() {
        dailyPnlDao?.clearAll()
    }

    /**
     * Inserts a new paper holding or merges into an existing holding for the same symbol
     * using a weighted-average purchase price.
     */
    suspend fun addOrMergeHolding(
        rawSymbol: String,
        shares: Double,
        purchasePrice: Double,
        notes: String = ""
    ): Result<PaperHoldingEntity> {
        val symbol = normalizeSymbol(rawSymbol)
            ?: return Result.failure(IllegalArgumentException("Enter a valid ticker symbol (e.g. AAPL, NVDA)."))
        if (!shares.isFinite() || shares <= 0.0) {
            return Result.failure(IllegalArgumentException("Shares must be greater than 0."))
        }
        if (!purchasePrice.isFinite() || purchasePrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Purchase price must be greater than $0."))
        }

        val cleanNotes = notes.trim()
        val now = clockMs()
        val existing = dao.findBySymbol(symbol)
        return if (existing != null) {
            val totalShares = existing.shares + shares
            val weightedAvgPrice = ((existing.shares * existing.purchasePrice) + (shares * purchasePrice)) / totalShares
            val updated = existing.copy(
                shares = totalShares,
                purchasePrice = weightedAvgPrice,
                notes = cleanNotes.ifBlank { existing.notes },
                purchasedAtMs = now
            )
            dao.updateHolding(updated)
            Result.success(updated)
        } else {
            val entity = PaperHoldingEntity(
                symbol = symbol,
                shares = shares,
                purchasePrice = purchasePrice,
                notes = cleanNotes,
                purchasedAtMs = now
            )
            val id = dao.insertHolding(entity)
            Result.success(entity.copy(id = id))
        }
    }

    /**
     * Updates an existing holding's share quantity, dummy purchase price, or notes in place.
     */
    suspend fun updateHolding(
        id: Long,
        shares: Double,
        purchasePrice: Double,
        notes: String
    ): Result<PaperHoldingEntity> {
        if (!shares.isFinite() || shares <= 0.0) {
            return Result.failure(IllegalArgumentException("Shares must be greater than 0."))
        }
        if (!purchasePrice.isFinite() || purchasePrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Purchase price must be greater than $0."))
        }
        val existing = dao.findById(id)
            ?: return Result.failure(IllegalStateException("Holding not found."))
        val updated = existing.copy(
            shares = shares,
            purchasePrice = purchasePrice,
            notes = notes.trim(),
            purchasedAtMs = clockMs()
        )
        dao.updateHolding(updated)
        return Result.success(updated)
    }

    suspend fun deleteHolding(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    /**
     * Inserts 3 starter dummy paper lots using live prices when available (with realistic
     * entry offsets) so users can immediately inspect simulated P&L against real-time quotes.
     */
    suspend fun seedDummyHoldings(livePricesBySymbol: Map<String, Double>) {
        val now = clockMs()
        val specs = listOf(
            Triple("NVDA", 12.0, (livePricesBySymbol["NVDA"]?.takeIf { it > 0.0 } ?: 135.0) * 0.96),
            Triple("AAPL", 8.0, (livePricesBySymbol["AAPL"]?.takeIf { it > 0.0 } ?: 225.0) * 1.015),
            Triple("MSFT", 5.0, (livePricesBySymbol["MSFT"]?.takeIf { it > 0.0 } ?: 420.0) * 0.975)
        )
        specs.forEachIndexed { idx, (sym, qty, dummyPrice) ->
            val existing = dao.findBySymbol(sym)
            if (existing == null) {
                val roundedPrice = round2(dummyPrice)
                dao.insertHolding(
                    PaperHoldingEntity(
                        symbol = sym,
                        shares = qty,
                        purchasePrice = roundedPrice,
                        notes = "Dummy paper lot",
                        purchasedAtMs = now - (idx * 60_000L)
                    )
                )
            }
        }
    }

    companion object {
        private val SYMBOL_REGEX = Regex("^[A-Z][A-Z0-9./-]{0,11}$")

        fun normalizeSymbol(raw: String): String? {
            val cleaned = raw.trim().removePrefix("$").uppercase(Locale.US)
            return cleaned.takeIf { SYMBOL_REGEX.matches(it) }
        }

        private fun round2(value: Double): Double = round(value * 100.0) / 100.0

        private fun formatTrimmed(value: Double): String {
            return if (abs(value - value.toLong()) < 1e-6) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
            }
        }
    }
}
