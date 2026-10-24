package com.hector.android.money

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

/**
 * Daily diagnostic job. It analyzes only locally stored ledger entries and never fetches a source,
 * changes strategies, or executes transactions.
 */
class MoneyEngineDailyReviewWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val store = MoneyEngineLedgerStore(applicationContext)
        val today = LocalDate.now()
        val currentDay = today.minusDays(1)
        val previousDay = today.minusDays(2)
        val previous = store.recordsForDay(previousDay)
        val current = store.recordsForDay(currentDay)
        val currencies = (previous + current).map { it.currency }.distinct().sorted()

        if (currencies.isEmpty()) {
            store.saveLatestReport(
                MoneyEngineRootCauseReport(
                    cause = MoneyEngineRootCause.MISSING_CONFIRMED_DATA,
                    evidence = listOf("No ledger records are stored for either completed comparison day."),
                    nextChecks = listOf(
                        "Import provider-confirmed ledger records or enter them through an approved app flow.",
                        "Verify the source is permitted before connecting it.",
                        "No income or improvement is inferred from missing data."
                    ),
                    mayProposeExperiment = false
                ),
                currentDay
            )
        } else {
            currencies.forEach { currency ->
                val review = MoneyEngineDailyReviewCore.review(
                    previous.filter { it.currency == currency },
                    current.filter { it.currency == currency },
                    currency
                )
                store.saveLatestReport(MoneyEngineRootCauseAnalyzer.analyze(review), currentDay, currency)
            }
        }
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )
}
