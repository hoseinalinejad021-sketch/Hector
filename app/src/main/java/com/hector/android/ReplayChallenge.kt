package com.hector.android

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

/**
 * One-use, short-lived spoken challenge state.
 * This validates recognized text only; it does not prove biological liveness.
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
            chosen += words.removeAt(random.nextInt(words.size))
        }
        val phrase = chosen.joinToString(" ")
        activePhrase = phrase
        expiresAtElapsedMs = nowElapsedMs + CHALLENGE_TTL_MS
        consumed = false
        return Issued(phrase, expiresAtElapsedMs)
    }

    /** The first attempt consumes the challenge whether it succeeds or fails. */
    @Synchronized
    fun consumeTranscript(transcript: String?, nowElapsedMs: Long): Boolean {
        val expected = activePhrase
        val expires = expiresAtElapsedMs
        if (consumed || expected == null) return false

        consumed = true
        activePhrase = null
        expiresAtElapsedMs = 0L

        if (nowElapsedMs > expires) return false
        val actual = normalize(transcript.orEmpty())
        return actual.isNotBlank() && actual == normalize(expected)
    }

    @Synchronized
    fun clear() {
        activePhrase = null
        expiresAtElapsedMs = 0L
        consumed = true
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('\u0640', ' ')
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace('\u200c', ' ')
            .replace(Regex("[^\\p{IsArabic}a-z ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    companion object {
        private const val WORD_COUNT = 3
        private const val CHALLENGE_TTL_MS = 15_000L
        private val WORDS = listOf(
            "آفتاب", "باران", "بهار", "دریا", "پنجره", "کتاب",
            "ستاره", "پرنده", "درخت", "لبخند", "آسمان", "گلستان",
            "امید", "روشن", "جنگل", "خورشید", "سپیده", "ماهتاب",
            "رودخانه", "آرامش", "دوستی", "زندگی", "آواز", "پروانه"
        )
    }
}
