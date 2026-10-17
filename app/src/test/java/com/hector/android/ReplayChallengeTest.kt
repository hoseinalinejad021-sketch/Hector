package com.hector.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayChallengeTest {
    @Test
    fun acceptsExactPhraseOnlyOnce() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)

        assertTrue(challenge.consumeTranscript(issued.phrase, 2_000L))
        assertFalse(challenge.consumeTranscript(issued.phrase, 2_001L))
    }

    @Test
    fun rejectsWrongPhraseAndConsumesChallenge() {
        val challenge = ReplayChallenge()
        challenge.issue(1_000L)

        assertFalse(challenge.consumeTranscript("wrong words here", 2_000L))
        assertFalse(challenge.consumeTranscript("amber basket candle", 2_001L))
    }

    @Test
    fun rejectsExpiredChallenge() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)

        assertFalse(challenge.consumeTranscript(issued.phrase, issued.expiresAtElapsedMs + 1L))
    }

    @Test
    fun normalizesCaseAndWhitespace() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)
        val normalized = issued.phrase.uppercase().replace(" ", "   ")

        assertTrue(challenge.consumeTranscript(normalized, 2_000L))
    }
}
