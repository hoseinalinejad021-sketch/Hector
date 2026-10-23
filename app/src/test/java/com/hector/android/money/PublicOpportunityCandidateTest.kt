package com.hector.android.money

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicOpportunityCandidateTest {
    @Test
    fun acceptsHttpsUrlOnRegisteredProviderHost() {
        assertTrue(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "hackerone-public-directory",
                "https://hackerone.com/programs/example"
            )
        )
        assertTrue(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "bugcrowd-public-programs",
                "https://bugcrowd.com/example"
            )
        )
    }

    @Test
    fun rejectsHttpAndLookalikeHosts() {
        assertFalse(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "hackerone-public-directory",
                "http://hackerone.com/programs/example"
            )
        )
        assertFalse(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "hackerone-public-directory",
                "https://hackerone.com.attacker.example/programs/example"
            )
        )
        assertFalse(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "bugcrowd-public-programs",
                "https://bugcrowd.com.attacker.example/example"
            )
        )
    }

    @Test
    fun rejectsUnknownSourceAndMalformedUrl() {
        assertFalse(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "unknown-provider",
                "https://example.com/program"
            )
        )
        assertFalse(
            PublicOpportunityCandidate.isAllowedProgramUrl(
                "hackerone-public-directory",
                "not a url"
            )
        )
    }

    @Test
    fun candidateStartsUnreviewedAndCannotImplyIncome() {
        val candidate = PublicOpportunityCandidate(
            id = "candidate-1",
            sourceId = "hackerone-public-directory",
            title = "Example program",
            programUrl = "https://hackerone.com/programs/example",
            discoveredAtEpochMillis = 1L
        )
        assertTrue(candidate.reviewStatus == OpportunityReviewStatus.UNREVIEWED)
    }
}
