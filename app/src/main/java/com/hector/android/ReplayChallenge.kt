package com.hector.android

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

/**
 * One-use, short-lived challenge state for an on-device speech recognizer.
 *
 * IMPORTANT: this is only the challenge-state component. It is not a liveness detector
 * by itself. The app must pass a transcript produced by a trusted on-device recognizer
 * from the SAME captured utterance used for speaker embedding, then independently verify
 * the speaker. Do not authorize sensitive actions based on this class.
 */
class ReplayChallenge {
    data class Issued(val phrase: String, val expiresAtElapsedMs: Long)

    private val random = SecureRandom()
    private var activePhrase: String? = null
    private var expiresAtElapsedMs: Long = 0L
    private var consumed = true

    @Synchronized
    fun issue(nowElapsedMs: Long): Issued {
        val words = WORDS.toMutableList()
        val chosen = ArrayList<String>(WORD_COUNT)
        repeat(WORD_COUNT) {
            val index = random.nextInt(words.size)
            chosen += words.removeAt(index)
        }
        val phrase = chosen.joinToString(" ")
        activePhrase = phrase
        expiresAtElapsedMs = nowElapsedMs + CHALLENGE_TTL_MS
        consumed = false
        return Issued(phrase, expiresAtElapsedMs)
    }

    /**
     * Consumes the challenge on the first verification attempt, whether it succeeds or
     * fails. Call only with a transcript from a trusted on-device recognizer. Confidence
     * must be a recognizer-provided value in [0,1], otherwise pass 0 and fail closed.
     */
    @Synchronized
    fun consumeTranscript(
        transcript: String?,
        confidence: Float,
        nowElapsedMs: Long
    ): Boolean {
        val expected = activePhrase
        val expires = expiresAtElapsedMs
        if (consumed || expected == null) return false

        // Burn it before evaluating input: no retries against the same challenge.
        consumed = true
        activePhrase = null
        expiresAtElapsedMs = 0L

        if (nowElapsedMs > expires || confidence < MIN_CONFIDENCE ||
            !confidence.isFinite() || confidence !in 0f..1f
        ) return false

        return normalize(transcript.orEmpty()) == normalize(expected)
    }

    @Synchronized
    fun clear() {
        activePhrase = null
        expiresAtElapsedMs = 0L
        consumed = true
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    companion object {
        private const val WORD_COUNT = 3
        private const val CHALLENGE_TTL_MS = 15_000L
        private const val MIN_CONFIDENCE = 0.80f

        // Avoid homophones and very similar words; use only simple, distinct words.
        private val WORDS = listOf(
            "amber", "basket", "candle", "delta", "garden", "harbor",
            "lemon", "marble", "pencil", "river", "silver", "window",
            "yellow", "button", "forest", "planet", "ticket", "velvet",
            "coffee", "ladder", "magnet", "rabbit", "summer", "tunnel"
        )
    }
}
