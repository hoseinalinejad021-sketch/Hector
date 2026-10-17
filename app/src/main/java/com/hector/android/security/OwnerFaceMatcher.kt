package com.hector.android.security

import kotlin.math.sqrt

/**
 * Owner-face embedding comparison.
 *
 * The camera/ML model must produce an embedding for both enrollment and the
 * live capture. Face detection or head movement alone is NOT identity matching.
 * Keep this check fail-closed until a validated face-embedding model is wired in.
 */
class OwnerFaceMatcher(
    private val threshold: Float = DEFAULT_THRESHOLD
) {
    init { require(threshold in -1f..1f) }

    fun matches(
        enrolledOwnerEmbedding: FloatArray?,
        liveFaceEmbedding: FloatArray?,
        livenessPassed: Boolean
    ): Boolean {
        if (!livenessPassed) return false
        if (enrolledOwnerEmbedding == null || liveFaceEmbedding == null) return false
        if (enrolledOwnerEmbedding.isEmpty() ||
            enrolledOwnerEmbedding.size != liveFaceEmbedding.size) return false
        if (enrolledOwnerEmbedding.any { !it.isFinite() } ||
            liveFaceEmbedding.any { !it.isFinite() }) return false

        val similarity = cosineSimilarity(enrolledOwnerEmbedding, liveFaceEmbedding)
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
