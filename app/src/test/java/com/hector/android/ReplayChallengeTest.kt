package com.hector.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayChallengeTest {
    @Test
    fun acceptsExactPhraseOnlyOnce() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)

        assertTrue(issued.phrase.matches(Regex("[\\p{IsArabic} ]+")))
        assertTrue(challenge.consumeTranscript(issued.phrase, 2_000L))
        assertFalse(challenge.consumeTranscript(issued.phrase, 2_001L))
    }

    @Test
    fun rejectsWrongPhraseAndConsumesChallenge() {
        val challenge = ReplayChallenge()
        challenge.issue(1_000L)

        assertFalse(challenge.consumeTranscript("عبارت اشتباه است", 2_000L))
        assertFalse(challenge.consumeTranscript("آفتاب باران بهار", 2_001L))
    }

    @Test
    fun rejectsExpiredChallenge() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)

        assertFalse(challenge.consumeTranscript(issued.phrase, issued.expiresAtElapsedMs + 1L))
    }

    @Test
    fun normalizesWhitespaceAndArabicPersianCharacterVariants() {
        val challenge = ReplayChallenge()
        assertTrue(challenge.normalize("كِتاب   علي").contains("کتاب"))
        assertTrue(challenge.normalize("كِتاب   علي") == challenge.normalize("کتاب علی"))
    }

    @Test
    fun normalizesExtraWhitespace() {
        val challenge = ReplayChallenge()
        val issued = challenge.issue(1_000L)
        val normalized = issued.phrase.replace(" ", "   ")

        assertTrue(challenge.consumeTranscript(normalized, 2_000L))
    }
}
