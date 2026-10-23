package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeOpportunityRankerTest {
    private fun opportunity(
        id: String,
        gross: Long = 10_000,
        probability: Int = 8_000,
        costs: Long = 1_000,
        effort: Int = 30,
        eligible: Boolean = true,
        reviewed: Boolean = true,
        automationAllowed: Boolean = true,
        currency: String = "USD"
    ) = IncomeOpportunity(
        id = id,
        sourceId = "official-source",
        currency = currency,
        estimatedGrossMinorUnits = gross,
        successProbabilityBasisPoints = probability,
        estimatedCostsMinorUnits = costs,
        estimatedEffortMinutes = effort,
        userEligible = eligible,
        termsReviewed = reviewed,
        permittedAutomation = automationAllowed,
        sourceUrl = "https://example.org/opportunities/$id"
    )

    @Test
    fun ranksByExpectedNetAndNeverTreatsEstimatesAsRealizedIncome() {
        val ranked = IncomeOpportunityRanker.rank(
            listOf(opportunity("lower", gross = 5_000), opportunity("higher", gross = 20_000)),
            "USD"
        )
        assertEquals(listOf("higher", "lower"), ranked.map { it.opportunity.id })
        assertEquals(15_000L, ranked.first().expectedNetMinorUnits)
    }

    @Test
    fun excludesUnverifiedIneligibleOrNotPermittedOpportunities() {
        val ranked = IncomeOpportunityRanker.rank(
            listOf(
                opportunity("eligible"),
                opportunity("not-eligible", eligible = false),
                opportunity("terms-unreviewed", reviewed = false),
                opportunity("automation-forbidden", automationAllowed = false)
            ),
            "USD"
        )
        assertEquals(listOf("eligible"), ranked.map { it.opportunity.id })
    }

    @Test
    fun excludesNonPositiveExpectedNetAndOtherCurrencies() {
        val ranked = IncomeOpportunityRanker.rank(
            listOf(
                opportunity("loss", gross = 100, probability = 100, costs = 100),
                opportunity("other-currency", currency = "EUR")
            ),
            "USD"
        )
        assertTrue(ranked.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpsSourceUrl() {
        opportunity("unsafe-url").copy(sourceUrl = "http://example.org")
    }
}
