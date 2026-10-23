package com.hector.android.money

/**
 * A candidate opportunity discovered from a source. Estimates are not income and must never
 * be added to the wallet balance or realized-income ledger.
 */
data class IncomeOpportunity(
    val id: String,
    val sourceId: String,
    val currency: String,
    val estimatedGrossMinorUnits: Long,
    /** Estimated probability of success, in basis points: 0..10_000. */
    val successProbabilityBasisPoints: Int,
    val estimatedCostsMinorUnits: Long,
    val estimatedEffortMinutes: Int,
    val userEligible: Boolean,
    val termsReviewed: Boolean,
    val permittedAutomation: Boolean,
    val sourceUrl: String
) {
    init {
        require(id.isNotBlank()) { "Opportunity id must not be blank" }
        require(sourceId.isNotBlank()) { "Source id must not be blank" }
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an uppercase currency code" }
        require(estimatedGrossMinorUnits >= 0) { "Estimated gross amount cannot be negative" }
        require(successProbabilityBasisPoints in 0..10_000) { "Probability must be between 0 and 10,000 basis points" }
        require(estimatedCostsMinorUnits >= 0) { "Estimated costs cannot be negative" }
        require(estimatedEffortMinutes >= 0) { "Effort cannot be negative" }
        require(sourceUrl.startsWith("https://")) { "Opportunity source must use HTTPS" }
    }

    fun isActionable(): Boolean =
        userEligible && termsReviewed && permittedAutomation &&
            successProbabilityBasisPoints > 0
}
