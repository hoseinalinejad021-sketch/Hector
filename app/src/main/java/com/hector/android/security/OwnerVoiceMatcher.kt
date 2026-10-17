package com.hector.android.security

import kotlin.math.sqrt

/**
 * Owner-speaker embedding comparison.
 *
 * A validated speaker-embedding model must produce both embeddings. The caller
 * must also pass an independently verified fresh challenge/anti-replay result.
 * Speech-to-text phrase equality is not speaker identity verification.
 */
class OwnerVoiceMatcher(
    private val threshold: Float = DEFAULT_THRESHOLD
) {
    init { require(threshold in -1f..1f) }

    fun matches(
        enrolledOwnerEmbedding: FloatArray?,
        liveSpeakerEmbedding: FloatArray?,
        freshChallengePassed: Boolean,
        antiReplayPassed: Boolean
    ): Boolean {
        if (!freshChallengePassed || !antiReplayPassed) return false
        if (enrolledOwnerEmbedding == null || liveSpeakerEmbedding == null) return false
        if (enrolledOwnerEmbedding.isEmpty() ||
            enrolledOwnerEmbedding.size != liveSpeakerEmbedding.size) return false
        if (enrolledOwnerEmbedding.any { !it.isFinite() } ||
            liveSpeakerEmbedding.any { !it.isFinite() }) return false

        val similarity = cosineSimilarity(enrolledOwnerEmbedding, liveSpeakerEmbedding)
            ?: return false
        return similarity >= threshold
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float? {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        if (normA <= 0.0 || normB <= 0.0) return null
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    companion object {
        // Must be calibrated on the selected model and real-device test data.
        const val DEFAULT_THRESHOLD = 0.80f
    }
}
