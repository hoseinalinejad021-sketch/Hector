package com.hector.android.money

/**
 * Evidence-based root-cause classification for the daily money review.
 *
 * This component diagnoses observed ledger state only. It never changes strategies, credentials,
 * account settings, safety limits, or performs transactions.
 */
enum class MoneyEngineRootCause {
    MISSING_CONFIRMED_DATA,
    LEDGER_CORRUPTED,
    NET_INCOME_DECLINED,
    SOURCE_MIX_SHIFT,
    NET_INCOME_UNCHANGED,
    NET_INCOME_IMPROVED
}

data class MoneyEngineRootCauseReport(
    val cause: MoneyEngineRootCause,
    val evidence: List<String>,
    val nextChecks: List<String>,
    val mayProposeExperiment: Boolean
)

object MoneyEngineRootCauseAnalyzer {
    fun analyze(review: MoneyEngineDailyReview): MoneyEngineRootCauseReport {
        val previous = review.previousDayNetMinorUnits
        val current = review.currentDayNetMinorUnits
        val changedSources = review.sourceChanges

        if (review.diagnosis.startsWith("NO_CONFIRMED_INCOME_DATA:")) {
            return MoneyEngineRootCauseReport(
                cause = MoneyEngineRootCause.MISSING_CONFIRMED_DATA,
                evidence = listOf("The daily review found no confirmed ledger entries for either comparison period."),
                nextChecks = listOf(
                    "Verify the selected date range and currency.",
                    "Check whether a permitted source is connected or ledger records were imported.",
                    "Do not infer a zero-income cause from missing records."
                ),
                mayProposeExperiment = false
            )
        }

        if (review.changeMinorUnits < 0) {
            val negativeSources = changedSources.filter { it.changeMinorUnits < 0 }
                .map { "${it.sourceId}: ${it.changeMinorUnits}" }
            return MoneyEngineRootCauseReport(
                cause = MoneyEngineRootCause.NET_INCOME_DECLINED,
                evidence = listOf(
                    "Net income changed by ${review.changeMinorUnits} ${review.currency} minor units.",
                    if (negativeSources.isEmpty()) "No negative source-level change was supplied."
                    else "Negative source changes (minor units): ${negativeSources.joinToString()}."
                ),
                nextChecks = listOf(
                    "Verify source records, payment status, refunds, fees, and direct costs.",
                    "Compare like-for-like completed periods before attributing causality.",
                    "Treat source changes as correlation, not proof of cause."
                ),
                mayProposeExperiment = true
            )
        }

        if (review.changeMinorUnits == 0L && changedSources.isNotEmpty()) {
            return MoneyEngineRootCauseReport(
                cause = MoneyEngineRootCause.SOURCE_MIX_SHIFT,
                evidence = listOf(
                    "Overall net income is unchanged, but ${changedSources.size} source(s) changed."
                ),
                nextChecks = listOf(
                    "Inspect positive and negative source changes separately.",
                    "Check whether one source replaced another without increasing total net income."
                ),
                mayProposeExperiment = true
            )
        }

        if (review.changeMinorUnits == 0L) {
            return MoneyEngineRootCauseReport(
                cause = MoneyEngineRootCause.NET_INCOME_UNCHANGED,
                evidence = listOf("Net income is unchanged at ${current} ${review.currency} minor units."),
                nextChecks = listOf(
                    "Check data completeness and whether the two periods are comparable.",
                    "Do not label an unchanged result as improvement."
                ),
                mayProposeExperiment = true
            )
        }

        return MoneyEngineRootCauseReport(
            cause = MoneyEngineRootCause.NET_INCOME_IMPROVED,
            evidence = listOf(
                "Net income changed by +${review.changeMinorUnits} ${review.currency} minor units.",
                "Previous period: $previous; current period: $current."
            ),
            nextChecks = listOf(
                "Verify the increase is confirmed and not a timing, refund, or data-import artifact.",
                "Repeat the comparison over additional completed periods before claiming a trend."
            ),
            mayProposeExperiment = false
        )
    }
}
