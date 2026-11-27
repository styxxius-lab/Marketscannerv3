package com.example.domain.engine

import com.example.data.local.PaperAccountEntity
import com.example.data.local.PaperHoldingEntity
import com.example.data.service.WatchlistQuoteSnippet
import com.example.domain.model.SignalRow
import java.util.Locale

data class HoldingPnlValuation(
    val holding: PaperHoldingEntity,
    val livePrice: Double?,
    val dayChangeDollar: Double,
    val dayChangePct: Double?,
    val priceSource: String,
    val isLivePriced: Boolean,
    val costBasis: Double,
    val marketValue: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPct: Double,
    val dayPnl: Double
)

data class PaperHoldingsPortfolioSummary(
    val valuations: List<HoldingPnlValuation> = emptyList(),
    val totalCostBasis: Double = 0.0,
    val totalMarketValue: Double = 0.0,
    val totalUnrealizedPnl: Double = 0.0,
    val totalUnrealizedPnlPct: Double = 0.0,
    val totalDayPnl: Double = 0.0,
    val winnersCount: Int = 0,
    val losersCount: Int = 0,
    val livePricedCount: Int = 0
)

data class SimulatorAllocationSlice(
    val label: String,
    val marketValue: Double,
    val weightPct: Double,
    val isCash: Boolean = false
)

data class PaperSimulatorMetrics(
    val startingCapital: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL,
    val cashBalance: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL,
    val holdingsMarketValue: Double = 0.0,
    val holdingsCostBasis: Double = 0.0,
    val totalEquity: Double = PaperAccountEntity.DEFAULT_STARTING_CAPITAL,
    val unrealizedPnl: Double = 0.0,
    val unrealizedPnlPct: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalReturnPct: Double = 0.0,
    val dayPnl: Double = 0.0,
    val dayPnlPct: Double = 0.0,
    val investedPct: Double = 0.0,
    val cashPct: Double = 100.0,
    val totalBuyOrders: Int = 0,
    val totalSellOrders: Int = 0,
    val winRatePct: Double? = null,
    val bestHolding: HoldingPnlValuation? = null,
    val worstHolding: HoldingPnlValuation? = null,
    val allocations: List<SimulatorAllocationSlice> = emptyList()
)

/**
 * Pure calculation engine that marks locally stored Room paper-trading holdings against
 * real-time Finnhub/Yahoo/Kraken quote snippets and live scanner ticker rows, and
 * aggregates total equity and profit/loss metrics for the Paper Trading Simulator.
 */
object PaperHoldingsPnlEngine {

    fun evaluate(
        holdings: List<PaperHoldingEntity>,
        quotesBySymbol: Map<String, WatchlistQuoteSnippet>,
        scannerRows: List<SignalRow>
    ): PaperHoldingsPortfolioSummary {
        if (holdings.isEmpty()) return PaperHoldingsPortfolioSummary()

        val scannerBySymbol = scannerRows.associateBy { it.symbol.trim().uppercase(Locale.US) }

        val valuations = holdings.map { holding ->
            val sym = holding.symbol.trim().uppercase(Locale.US)
            val quote = quotesBySymbol[sym]?.takeIf { it.currentPrice.isFinite() && it.currentPrice > 0.0 }
            val scannerRow = scannerBySymbol[sym]?.takeIf { it.price.isFinite() && it.price > 0.0 }

            val livePrice: Double?
            val dayChangeDollar: Double
            val dayChangePct: Double?
            val priceSource: String

            when {
                quote != null -> {
                    livePrice = quote.currentPrice
                    dayChangeDollar = quote.changeDollar
                    dayChangePct = quote.changePct
                    priceSource = quote.sourceLabel
                }
                scannerRow != null -> {
                    livePrice = scannerRow.price
                    val prev = if (scannerRow.series.size >= 2) {
                        scannerRow.series[scannerRow.series.lastIndex - 1].takeIf { it.isFinite() && it > 0.0 }
                            ?: scannerRow.price
                    } else {
                        scannerRow.price
                    }
                    dayChangeDollar = scannerRow.price - prev
                    dayChangePct = if (prev > 0.0) (dayChangeDollar / prev) * 100.0 else 0.0
                    priceSource = "SCANNER LIVE"
                }
                else -> {
                    livePrice = null
                    dayChangeDollar = 0.0
                    dayChangePct = null
                    priceSource = "PENDING"
                }
            }

            val costBasis = holding.shares * holding.purchasePrice
            val effectivePrice = livePrice ?: holding.purchasePrice
            val marketValue = holding.shares * effectivePrice
            val unrealizedPnl = marketValue - costBasis
            val unrealizedPnlPct = if (costBasis > 0.0) (unrealizedPnl / costBasis) * 100.0 else 0.0
            val dayPnl = if (livePrice != null) holding.shares * dayChangeDollar else 0.0

            HoldingPnlValuation(
                holding = holding,
                livePrice = livePrice,
                dayChangeDollar = dayChangeDollar,
                dayChangePct = dayChangePct,
                priceSource = priceSource,
                isLivePriced = livePrice != null,
                costBasis = costBasis,
                marketValue = marketValue,
                unrealizedPnl = unrealizedPnl,
                unrealizedPnlPct = unrealizedPnlPct,
                dayPnl = dayPnl
            )
        }

        val totalCostBasis = valuations.sumOf { it.costBasis }
        val totalMarketValue = valuations.sumOf { it.marketValue }
        val totalUnrealizedPnl = totalMarketValue - totalCostBasis
        val totalUnrealizedPnlPct = if (totalCostBasis > 0.0) {
            (totalUnrealizedPnl / totalCostBasis) * 100.0
        } else {
            0.0
        }
        val totalDayPnl = valuations.sumOf { it.dayPnl }

        return PaperHoldingsPortfolioSummary(
            valuations = valuations,
            totalCostBasis = totalCostBasis,
            totalMarketValue = totalMarketValue,
            totalUnrealizedPnl = totalUnrealizedPnl,
            totalUnrealizedPnlPct = totalUnrealizedPnlPct,
            totalDayPnl = totalDayPnl,
            winnersCount = valuations.count { it.isLivePriced && it.unrealizedPnl > 0.005 },
            losersCount = valuations.count { it.isLivePriced && it.unrealizedPnl < -0.005 },
            livePricedCount = valuations.count { it.isLivePriced }
        )
    }

    /**
     * Combines the Room-persisted [PaperAccountEntity] cash & realized P&L ledger with
     * the mark-to-market [PaperHoldingsPortfolioSummary] into complete simulator metrics.
     */
    fun evaluateSimulator(
        account: PaperAccountEntity,
        summary: PaperHoldingsPortfolioSummary
    ): PaperSimulatorMetrics {
        val startingCapital = account.startingCapital.coerceAtLeast(1.0)
        val cash = account.cashBalance.coerceAtLeast(0.0)
        val marketVal = summary.totalMarketValue.coerceAtLeast(0.0)
        val costBasis = summary.totalCostBasis.coerceAtLeast(0.0)
        val totalEquity = cash + marketVal
        val unrealizedPnl = summary.totalUnrealizedPnl
        val realizedPnl = account.realizedPnl
        // Total P&L combines realized gains/losses from closed simulated trades + open mark-to-market P&L.
        val totalPnl = realizedPnl + unrealizedPnl
        val totalReturnPct = (totalPnl / startingCapital) * 100.0
        val dayPnl = summary.totalDayPnl
        val prevEquity = (totalEquity - dayPnl).coerceAtLeast(1.0)
        val dayPnlPct = (dayPnl / prevEquity) * 100.0

        val investedPct = if (totalEquity > 0.0) ((marketVal / totalEquity) * 100.0).coerceIn(0.0, 100.0) else 0.0
        val cashPct = (100.0 - investedPct).coerceIn(0.0, 100.0)

        val closedCount = account.winningSells + account.losingSells
        val winRatePct = if (closedCount > 0) (account.winningSells.toDouble() / closedCount.toDouble()) * 100.0 else null

        val sortedByReturn = summary.valuations.sortedByDescending { it.unrealizedPnlPct }
        val bestHolding = sortedByReturn.firstOrNull()
        val worstHolding = if (sortedByReturn.size >= 2) sortedByReturn.lastOrNull() else null

        val allocations = buildList {
            if (totalEquity > 0.0) {
                summary.valuations
                    .sortedByDescending { it.marketValue }
                    .forEach { v ->
                        add(
                            SimulatorAllocationSlice(
                                label = v.holding.symbol,
                                marketValue = v.marketValue,
                                weightPct = (v.marketValue / totalEquity) * 100.0,
                                isCash = false
                            )
                        )
                    }
                if (cash > 0.01) {
                    add(
                        SimulatorAllocationSlice(
                            label = "CASH",
                            marketValue = cash,
                            weightPct = (cash / totalEquity) * 100.0,
                            isCash = true
                        )
                    )
                }
            }
        }

        return PaperSimulatorMetrics(
            startingCapital = startingCapital,
            cashBalance = cash,
            holdingsMarketValue = marketVal,
            holdingsCostBasis = costBasis,
            totalEquity = totalEquity,
            unrealizedPnl = unrealizedPnl,
            unrealizedPnlPct = summary.totalUnrealizedPnlPct,
            realizedPnl = realizedPnl,
            totalPnl = totalPnl,
            totalReturnPct = totalReturnPct,
            dayPnl = dayPnl,
            dayPnlPct = dayPnlPct,
            investedPct = investedPct,
            cashPct = cashPct,
            totalBuyOrders = account.totalBuyOrders,
            totalSellOrders = account.totalSellOrders,
            winRatePct = winRatePct,
            bestHolding = bestHolding,
            worstHolding = worstHolding,
            allocations = allocations
        )
    }
}
