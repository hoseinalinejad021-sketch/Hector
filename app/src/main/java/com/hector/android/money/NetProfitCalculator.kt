package com.hector.android.money

data class ProfitSummary(
    val currency: String,
    val realizedGrossMinorUnits: Long,
    val realizedNetMinorUnits: Long,
    val pendingCount: Int,
    val opportunityCount: Int
)

object NetProfitCalculator {
    /**
     * Only provider-confirmed receipts are included in realized totals.
     * Pending amounts and opportunities are counted separately, never presented as cash.
     */
    fun summarize(records: List<IncomeRecord>, currency: String): ProfitSummary {
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an uppercase currency code" }
        val matching = records.filter { it.currency == currency }
        val realized = matching.filter { it.countsAsRealizedIncome() }
        return ProfitSummary(
            currency = currency,
            realizedGrossMinorUnits = realized.fold(0L) { total, record ->
                Math.addExact(total, record.grossMinorUnits)
            },
            realizedNetMinorUnits = realized.fold(0L) { total, record ->
                Math.addExact(total, record.netMinorUnits())
            },
            pendingCount = matching.count { it.status == IncomeStatus.PENDING },
            opportunityCount = matching.count { it.status == IncomeStatus.OPPORTUNITY }
        )
    }
}
