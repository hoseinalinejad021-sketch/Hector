package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeOpportunityDiscoveryPipelineTest {
    private fun opportunity(
        id: String,
        sourceId: String = "hackerone-public-directory",
        url: String = "https://hackerone.com/programs/example"
    ) = IncomeOpportunity(
        id = id,
        sourceId = sourceId,
        currency = "USD",
        estimatedGrossMinorUnits = 10_000,
        successProbabilityBasisPoints = 1_000,
        estimatedCostsMinorUnits = 0,
        estimatedEffortMinutes = 60,
        userEligible = true,
        termsReviewed = true,
        permittedAutomation = true,
        sourceUrl = url
    )

    @Test
    fun keepsOnlyRegisteredSourcesAndTheirHttpsHosts() {
        val result = IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(
            listOf(
                opportunity("valid"),
                opportunity("unknown", sourceId = "unknown-provider"),
                opportunity("wrong-host", url = "https://evil.example/program")
            )
        )
        assertEquals(listOf("valid"), result.map { it.id })
    }

    @Test
    fun removesDuplicatesIgnoringTrackingQueryAndTrailingSlash() {
        val result = IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(
            listOf(
                opportunity("first", url = "https://hackerone.com/programs/example?ref=one"),
                opportunity("duplicate", url = "https://www.hackerone.com/programs/example/")
            )
        )
        assertEquals(listOf("first"), result.map { it.id })
    }

    @Test
    fun rejectsInsecureUrlsAndUrlsWithEmbeddedCredentials() {
        val result = IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(
            listOf(
                opportunity("http", url = "http://hackerone.com/programs/example"),
                opportunity("credentials", url = "https://user:pass@hackerone.com/programs/example")
            )
        )
        assertEquals(emptyList<IncomeOpportunity>(), result)
    }

    @Test
    fun permitsRegisteredBugcrowdHostAndSubdomains() {
        val result = IncomeOpportunityDiscoveryPipeline.normalizeAndDeduplicate(
            listOf(
                opportunity(
                    "bugcrowd",
                    sourceId = "bugcrowd-public-programs",
                    url = "https://bugcrowd.com/programs/example"
                )
            )
        )
        assertEquals(listOf("bugcrowd"), result.map { it.id })
    }

    @Test
    fun rankDiscoveredValidatesAndDeduplicatesBeforeRanking() {
        val candidates = listOf(
            opportunity("valid", url = "https://hackerone.com/programs/valid"),
            opportunity("duplicate", url = "https://www.hackerone.com/programs/valid/?tracking=1"),
            opportunity("untrusted", url = "https://not-hackerone.example/programs/fake")
        )
        val ranked = IncomeOpportunityRanker.rankDiscovered(candidates, "USD")
        assertEquals(listOf("valid"), ranked.map { it.opportunity.id })
    }
}
