package com.hector.android.money

import java.math.BigDecimal
import java.math.RoundingMode

data class RankedIncomeOpportunity(
    val opportunity: IncomeOpportunity,
    /** Probability-weighted estimate after stated costs; not guaranteed income. */
    val expectedNetMinorUnits: Long
)

object IncomeOpportunityRanker {
    /**
     * Rank only opportunities whose eligibility, terms, and automation permission are confirmed.
     * Returns positive expected-net opportunities first. Estimates are never ledger entries.
     */
    /**
     * Safe entry point for discovered candidates: validate registered source URLs, deduplicate,
     * then apply the existing eligibility and expected-net ranking rules.
     */
    fun rankDiscovered(
        candidates: List<IncomeOpportunity>,
        currency: String
    ): List<RankedIncomeOpportunity> =
        rank(IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(candidates), currency)

    fun rank(
        opportunities: List<IncomeOpportunity>,
        currency: String
    ): List<RankedIncomeOpportunity> {
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an uppercase currency code" }

        return opportunities.asSequence()
            .filter { it.currency == currency && it.isActionable() }
            .map { opportunity ->
                val expectedGross = BigDecimal.valueOf(opportunity.estimatedGrossMinorUnits)
                    .multiply(BigDecimal.valueOf(opportunity.successProbabilityBasisPoints.toLong()))
                    .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.DOWN)
                val expectedNet = expectedGross
                    .subtract(BigDecimal.valueOf(opportunity.estimatedCostsMinorUnits))
                    .longValueExact()
                RankedIncomeOpportunity(opportunity, expectedNet)
            }
            .filter { it.expectedNetMinorUnits > 0 }
            .sortedWith(
                compareByDescending<RankedIncomeOpportunity> { it.expectedNetMinorUnits }
                    .thenBy { it.opportunity.estimatedEffortMinutes }
                    .thenBy { it.opportunity.id }
            )
            .toList()
    }
}
