package com.hector.android.money

/**
 * A ledger entry imported from a permitted income provider or entered by the user.
 * This model does not claim that an opportunity is paid until the provider confirms receipt.
 */
enum class IncomeStatus {
    OPPORTUNITY,
    PENDING,
    CONFIRMED_RECEIVED,
    WITHDRAWABLE,
    PAID_OUT,
    REJECTED
}

data class IncomeRecord(
    val id: String,
    val sourceId: String,
    val currency: String,
    val grossMinorUnits: Long,
    val platformFeeMinorUnits: Long = 0,
    val payoutFeeMinorUnits: Long = 0,
    val conversionFeeMinorUnits: Long = 0,
    val directCostMinorUnits: Long = 0,
    val status: IncomeStatus,
    val createdAtEpochMillis: Long,
    val providerReference: String? = null
) {
    init {
        require(id.isNotBlank()) { "Income record id must not be blank" }
        require(sourceId.isNotBlank()) { "Income source id must not be blank" }
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an ISO-style uppercase code" }
        require(grossMinorUnits >= 0) { "Gross amount cannot be negative" }
        require(platformFeeMinorUnits >= 0 && payoutFeeMinorUnits >= 0 &&
            conversionFeeMinorUnits >= 0 && directCostMinorUnits >= 0) {
            "Fees and direct costs cannot be negative"
        }
        require(createdAtEpochMillis >= 0) { "Timestamp cannot be negative" }
    }

    /** May be negative when the costs exceed gross income. */
    fun netMinorUnits(): Long {
        val deductions = Math.addExact(
            Math.addExact(platformFeeMinorUnits, payoutFeeMinorUnits),
            Math.addExact(conversionFeeMinorUnits, directCostMinorUnits)
        )
        return Math.subtractExact(grossMinorUnits, deductions)
    }

    fun countsAsRealizedIncome(): Boolean = status == IncomeStatus.CONFIRMED_RECEIVED ||
        status == IncomeStatus.WITHDRAWABLE || status == IncomeStatus.PAID_OUT
}
