package com.hector.android.money

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnconfiguredPublicOpportunityConnectorTest {
    @Test
    fun reportsNotConfiguredWithoutInventingCandidates() {
        val connector = UnconfiguredPublicOpportunityConnector("hackerone-public-directory")
        var result: OpportunityDiscoveryResult? = null

        val discovery: suspend () -> OpportunityDiscoveryResult = { connector.discover() }
        discovery.startCoroutine(object : Continuation<OpportunityDiscoveryResult> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<OpportunityDiscoveryResult>) {
                result = value.getOrThrow()
            }
        })

        assertTrue(result is OpportunityDiscoveryResult.Unavailable)
        assertEquals(
            DiscoveryUnavailableReason.NOT_CONFIGURED,
            (result as OpportunityDiscoveryResult.Unavailable).reason
        )
    }

    @Test
    fun validatorRejectsCandidateAttributedToDifferentConnector() {
        val candidate = PublicOpportunityCandidate(
            id = "program-1",
            sourceId = "hackerone-public-directory",
            title = "Example public program",
            programUrl = "https://hackerone.com/directory/programs",
            discoveredAtEpochMillis = 1L
        )
        val result = OpportunityDiscoveryResult.Success(
            candidates = listOf(candidate),
            fetchedAtEpochMillis = 2L
        )

        val validated = OpportunityDiscoveryValidator.validate(
            expectedSourceId = "bugcrowd-public-programs",
            result = result
        )

        assertEquals(
            OpportunityDiscoveryResult.Unavailable(
                DiscoveryUnavailableReason.PROVIDER_POLICY_REQUIRES_MANUAL_REVIEW
            ),
            validated
        )
    }

    @Test
    fun validatorPreservesUnavailableProviderState() {
        val unavailable = OpportunityDiscoveryResult.Unavailable(
            DiscoveryUnavailableReason.AUTHENTICATION_REQUIRED
        )

        assertEquals(
            unavailable,
            OpportunityDiscoveryValidator.validate(
                expectedSourceId = "hackerone-public-directory",
                result = unavailable
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSourceId() {
        UnconfiguredPublicOpportunityConnector("unknown-source")
    }
}
