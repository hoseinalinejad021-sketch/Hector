package com.hector.android.money

/**
 * Non-configurable safety defaults for the first money-engine milestone.
 *
 * These flags describe what Hector must NOT be able to do in this milestone.
 * They do not connect an income provider, monitor a wallet, or create income by themselves.
 */
object MoneyEngineGuardrails {
    const val RECEIVE_ONLY_WALLET = true
    const val WITHDRAWALS_ENABLED = false
    const val TRANSACTION_SIGNING_ENABLED = false
    const val AUTOMATIC_SPENDING_ENABLED = false
    const val REGION_RESTRICTION_BYPASS_ENABLED = false

    /** A product goal, not a promise that income will increase every calendar day. */
    const val OPTIMIZE_FOR_DAILY_NET_INCOME_GROWTH = true
    const val GUARANTEED_DAILY_GROWTH = false
}

/**
 * Compares confirmed net income for two completed days.
 * Values must use the same currency and minor unit (for example, USD cents).
 */
data class DailyIncomeComparison(
    val previousDayNetMinorUnits: Long,
    val currentDayNetMinorUnits: Long
) {
    val changeMinorUnits: Long
        get() = Math.subtractExact(currentDayNetMinorUnits, previousDayNetMinorUnits)

    val improved: Boolean
        get() = changeMinorUnits > 0
}
