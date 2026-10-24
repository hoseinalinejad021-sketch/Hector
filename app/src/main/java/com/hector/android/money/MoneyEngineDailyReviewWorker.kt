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
    override suspend fun doWork(): Result {
        val store = MoneyEngineLedgerStore(applicationContext)
        val today = LocalDate.now()
        val currentDay = today.minusDays(1)
        return try {
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
            Result.success()
        } catch (corruption: LedgerCorruptionException) {
            // A known corrupt ledger is not a transient failure: report it and stop retrying.
            runCatching {
                store.saveLatestReport(
                    MoneyEngineRootCauseReport(
                        cause = MoneyEngineRootCause.LEDGER_CORRUPTED,
                        evidence = listOf(
                            "Stored income ledger is invalid. Original records were preserved; no empty ledger was substituted.",
                            "Diagnostic: ${corruption.message ?: "ledger validation failed"}"
                        ),
                        nextChecks = listOf(
                            "Back up the app data before attempting recovery.",
                            "Restore or repair the ledger from a trusted backup; do not overwrite it with an empty ledger.",
                            "Review the original parsing/validation cause before resuming daily analysis."
                        ),
                        mayProposeExperiment = false
                    ),
                    currentDay
                )
            }
            Result.failure()
        } catch (_: Exception) {
            // Unknown failures may be transient, so preserve WorkManager retry behavior.
            Result.retry()
        }
    }
}
