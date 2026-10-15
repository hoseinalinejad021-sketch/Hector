package com.hector.assistant

import kotlin.math.max

/**
 * Hector Adaptive Trading Core v0.1.
 *
 * This is a deterministic PAPER-TRADING evaluation and risk gate, not a trained
 * AI model and not an order-execution adapter. It never connects to an exchange.
 * It learns only aggregate performance statistics from explicitly supplied
 * closed paper trades. Strategy changes require separate validation and approval.
 */
class TradingLearningCore(
    private val maxDailyLossPercent: Double = 2.0,
    private val maxDrawdownPercent: Double = 8.0,
    private val maxConsecutiveLosses: Int = 4,
    private val minimumTradesBeforeEvaluation: Int = 30
) {
    data class PaperTradeResult(
        val netReturnPercent: Double,
        val feesAndSlippageIncluded: Boolean,
        val timestampUtcMillis: Long
    )

    data class Status(
        val tradesObserved: Int,
        val winRatePercent: Double?,
        val averageNetReturnPercent: Double?,
        val maxDrawdownPercent: Double,
        val consecutiveLosses: Int,
        val paused: Boolean,
        val pauseReason: String,
        val eligibleForReview: Boolean
    )

    private val results = mutableListOf<PaperTradeResult>()
    private var equity = 100.0
    private var peakEquity = 100.0
    private var maxDrawdown = 0.0
    private var consecutiveLosses = 0
    private var paused = false
    private var pauseReason = ""

    @Synchronized
    fun recordClosedPaperTrade(result: PaperTradeResult): Status {
        if (paused) return status()
        if (!result.netReturnPercent.isFinite()) return status()

        // Require realistic costs to be included; otherwise the result is not
        // accepted into the evaluation record.
        if (!result.feesAndSlippageIncluded) {
            paused = true
            pauseReason = "Evaluation paused: trade results must include fees and slippage."
            return status()
        }

        results.add(result)
        val previousEquity = equity
        equity *= (1.0 + result.netReturnPercent / 100.0)
        peakEquity = max(peakEquity, equity)
        val drawdown = if (peakEquity > 0.0) (peakEquity - equity) / peakEquity * 100.0 else 100.0
        maxDrawdown = max(maxDrawdown, drawdown)

        if (result.netReturnPercent < 0.0) consecutiveLosses++ else consecutiveLosses = 0

        val dayStart = result.timestampUtcMillis - 24L * 60L * 60L * 1000L
        val dailyReturn = results
            .filter { it.timestampUtcMillis >= dayStart && it.timestampUtcMillis <= result.timestampUtcMillis }
            .sumOf { it.netReturnPercent }

        when {
            dailyReturn <= -maxDailyLossPercent -> {
                paused = true
                pauseReason = "Daily loss limit reached; manual review required."
            }
            maxDrawdown >= maxDrawdownPercent -> {
                paused = true
                pauseReason = "Maximum drawdown limit reached; manual review required."
            }
            consecutiveLosses >= maxConsecutiveLosses -> {
                paused = true
                pauseReason = "Consecutive-loss limit reached; manual review required."
            }
            equity <= 0.0 || previousEquity <= 0.0 -> {
                paused = true
                pauseReason = "Invalid simulated equity; manual review required."
            }
        }
        return status()
    }

    /**
     * Daily review computes a transparent summary only. It does not alter
     * strategy parameters or switch live trading on.
     */
    @Synchronized
    fun dailyReview(): Status = status()

    @Synchronized
    fun isPaused(): Boolean = paused

    /**
     * A human must inspect the paper-trading results before explicitly resetting
     * a pause. This method does not activate live trading.
     */
    @Synchronized
    fun resetPaperPauseAfterManualReview() {
        paused = false
        pauseReason = ""
        consecutiveLosses = 0
    }

    @Synchronized
    private fun status(): Status {
        val wins = results.count { it.netReturnPercent > 0.0 }
        val winRate = if (results.isEmpty()) null else wins.toDouble() / results.size * 100.0
        val average = if (results.isEmpty()) null else results.map { it.netReturnPercent }.average()
        return Status(
            tradesObserved = results.size,
            winRatePercent = winRate,
            averageNetReturnPercent = average,
            maxDrawdownPercent = maxDrawdown,
            consecutiveLosses = consecutiveLosses,
            paused = paused,
            pauseReason = pauseReason,
            eligibleForReview = results.size >= minimumTradesBeforeEvaluation &&
                results.all { it.feesAndSlippageIncluded }
        )
    }
}
