package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityDiscoveryValidatorTest {
    @Test
    fun acceptsCandidatesFromExpectedRegisteredSource() {
        val candidate = PublicOpportunityCandidate(
            id = "candidate-1",
            sourceId = "hackerone-public-directory",
            title = "Example program",
            programUrl = "https://hackerone.com/programs/example",
            discoveredAtEpochMillis = 1L
        )
        val result = OpportunityDiscoveryResult.Success(listOf(candidate), 2L)

        val validated = OpportunityDiscoveryValidator.validate(
            "hackerone-public-directory",
            result
        )

        assertEquals(result, validated)
    }

    @Test
    fun rejectsCandidatesAttributedToAnotherSource() {
        val candidate = PublicOpportunityCandidate(
            id = "candidate-1",
            sourceId = "bugcrowd-public-programs",
            title = "Example program",
            programUrl = "https://bugcrowd.com/example",
            discoveredAtEpochMillis = 1L
        )
        val result = OpportunityDiscoveryResult.Success(listOf(candidate), 2L)

        val validated = OpportunityDiscoveryValidator.validate(
            "hackerone-public-directory",
            result
        )

        assertTrue(
            validated is OpportunityDiscoveryResult.Unavailable &&
                validated.reason ==
                DiscoveryUnavailableReason.PROVIDER_POLICY_REQUIRES_MANUAL_REVIEW
        )
    }

    @Test
    fun preservesUnavailableProviderState() {
        val result = OpportunityDiscoveryResult.Unavailable(
            DiscoveryUnavailableReason.AUTHENTICATION_REQUIRED
        )
        assertEquals(
            result,
            OpportunityDiscoveryValidator.validate("hackerone-public-directory", result)
        )
    }
}
