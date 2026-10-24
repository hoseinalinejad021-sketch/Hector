package com.hector.android.money

/**
 * Deterministic daily review of confirmed income. This is an analysis core, not a live data
 * connector or machine-learning model: callers must supply provider-confirmed ledger records.
 */
data class MoneyEngineDailyReview(
    val currency: String,
    val previousDayNetMinorUnits: Long,
    val currentDayNetMinorUnits: Long,
    val changeMinorUnits: Long,
    val diagnosis: String,
    val sourceChanges: List<SourceNetChange>
) {
    val improved: Boolean get() = changeMinorUnits > 0
    val hasConfirmedIncomeEvidence: Boolean get() =
        previousDayNetMinorUnits != 0L || currentDayNetMinorUnits != 0L
}

data class SourceNetChange(
    val sourceId: String,
    val previousNetMinorUnits: Long,
    val currentNetMinorUnits: Long
) {
    val changeMinorUnits: Long
        get() = Math.subtractExact(currentNetMinorUnits, previousNetMinorUnits)
}

/** Compares completed-day ledgers; never spends money or guarantees future growth. */
object MoneyEngineDailyReviewCore {
    fun review(
        previousDay: List<IncomeRecord>,
        currentDay: List<IncomeRecord>,
        currency: String
    ): MoneyEngineDailyReview {
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an uppercase currency code" }
        val previous = confirmedNetBySource(previousDay, currency)
        val current = confirmedNetBySource(currentDay, currency)
        val sourceIds = (previous.keys + current.keys).toSortedSet()
        val changes = sourceIds.map { source ->
            SourceNetChange(source, previous[source] ?: 0L, current[source] ?: 0L)
        }.filter { it.previousNetMinorUnits != it.currentNetMinorUnits }
            .sortedBy { it.changeMinorUnits }

        val previousTotal = previous.values.fold(0L) { total, value -> Math.addExact(total, value) }
        val currentTotal = current.values.fold(0L) { total, value -> Math.addExact(total, value) }
        val delta = Math.subtractExact(currentTotal, previousTotal)
        val diagnosis = when {
            previous.isEmpty() && current.isEmpty() ->
                "NO_CONFIRMED_INCOME_DATA: connect or import a permitted source before evaluating performance."
            delta > 0 ->
                "NET_INCOME_UP: compare source-level changes; this is not proof of a lasting trend."
            delta < 0 ->
                "NET_INCOME_DOWN: inspect negative source changes, fees, costs, and payment status."
            else ->
                "NET_INCOME_UNCHANGED: inspect source-level shifts; do not claim improvement without evidence."
        }
        return MoneyEngineDailyReview(currency, previousTotal, currentTotal, delta, diagnosis, changes)
    }

    private fun confirmedNetBySource(
        records: List<IncomeRecord>,
        currency: String
    ): Map<String, Long> {
        val totals = linkedMapOf<String, Long>()
        records.asSequence()
            .filter { it.currency == currency && it.countsAsRealizedIncome() }
            .forEach { record ->
                totals[record.sourceId] = Math.addExact(
                    totals[record.sourceId] ?: 0L,
                    record.netMinorUnits()
                )
            }
        return totals
    }
}
