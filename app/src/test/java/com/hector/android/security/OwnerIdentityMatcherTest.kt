package com.hector.android.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerIdentityMatcherTest {
    private val face = OwnerFaceMatcher(threshold = 0.80f)
    private val voice = OwnerVoiceMatcher(threshold = 0.80f)
    private val owner = floatArrayOf(1f, 0f, 0f)

    @Test fun faceRejectsMissingEmbeddingsAndFailedLiveness() {
        assertFalse(face.matches(null, owner, true))
        assertFalse(face.matches(owner, null, true))
        assertFalse(face.matches(owner, owner, false))
    }

    @Test fun faceAcceptsMatchingEmbeddingOnlyWhenLive() {
        assertTrue(face.matches(owner, floatArrayOf(1f, 0f, 0f), true))
        assertFalse(face.matches(owner, floatArrayOf(0f, 1f, 0f), true))
    }

    @Test fun faceRejectsMalformedEmbeddings() {
        assertFalse(face.matches(floatArrayOf(0f, 0f), owner, true))
        assertFalse(face.matches(floatArrayOf(Float.NaN), floatArrayOf(1f), true))
        assertFalse(face.matches(floatArrayOf(0f), floatArrayOf(0f), true))
    }

    @Test fun voiceRequiresFreshChallengeAndAntiReplay() {
        assertFalse(voice.matches(owner, owner, false, true))
        assertFalse(voice.matches(owner, owner, true, false))
        assertTrue(voice.matches(owner, owner, true, true))
    }

    @Test fun voiceRejectsMissingOrMalformedEmbeddings() {
        assertFalse(voice.matches(null, owner, true, true))
        assertFalse(voice.matches(owner, floatArrayOf(1f, 0f), true, true))
        assertFalse(voice.matches(floatArrayOf(Float.POSITIVE_INFINITY), floatArrayOf(1f), true, true))
    }
}
