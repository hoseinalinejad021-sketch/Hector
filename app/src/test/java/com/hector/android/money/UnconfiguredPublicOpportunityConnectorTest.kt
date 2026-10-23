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

        connector.discover().startCoroutine(object : Continuation<OpportunityDiscoveryResult> {
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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSourceId() {
        UnconfiguredPublicOpportunityConnector("unknown-source")
    }
}
