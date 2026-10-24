package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeOpportunityPortValidationTest {
    @Test
    fun rejectsNonstandardHttpsPortEvenWhenHostIsAllowed() {
        val nonstandardPort = IncomeOpportunity(
            id = "custom-port",
            sourceId = "hackerone-public-directory",
            currency = "USD",
            estimatedGrossMinorUnits = 10000,
            successProbabilityBasisPoints = 1000,
            estimatedCostsMinorUnits = 0,
            estimatedEffortMinutes = 60,
            userEligible = true,
            termsReviewed = true,
            permittedAutomation = true,
            sourceUrl = "https://hackerone.com:8443/programs/example"
        )
        val standardPort = nonstandardPort.copy(
            id = "standard-port",
            sourceUrl = "https://hackerone.com:443/programs/example"
        )

        assertEquals(
            listOf(standardPort),
            IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(
                listOf(nonstandardPort, standardPort)
            )
        )
    }
}
