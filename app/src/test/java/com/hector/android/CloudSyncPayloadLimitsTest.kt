package com.hector.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncPayloadLimitsTest {
    @Test
    fun acceptsPayloadAtPerFieldAndCombinedByteLimits() {
        val memory = "m".repeat(CloudSyncPayloadLimits.MAX_FIELD_UTF8_BYTES)
        val history = "h".repeat(CloudSyncPayloadLimits.MAX_FIELD_UTF8_BYTES)

        assertTrue(CloudSyncPayloadLimits.isWithinLimits(memory, history))
    }

    @Test
    fun rejectsAnyFieldOverCharacterLimit() {
        val tooLong = "x".repeat(CloudSyncPayloadLimits.MAX_FIELD_CHARS + 1)

        assertFalse(CloudSyncPayloadLimits.isWithinLimits(tooLong, ""))
        assertFalse(CloudSyncPayloadLimits.isWithinLimits("", tooLong))
    }

    @Test
    fun rejectsUnicodeFieldThatExceedsUtf8ByteLimit() {
        // Each emoji occupies four UTF-8 bytes, despite using two UTF-16 code units.
        val tooManyBytes = "🙂".repeat(CloudSyncPayloadLimits.MAX_FIELD_UTF8_BYTES / 4 + 1)

        assertFalse(CloudSyncPayloadLimits.isWithinLimits(tooManyBytes, ""))
    }

    @Test
    fun acceptsEmptyPayload() {
        assertTrue(CloudSyncPayloadLimits.isWithinLimits("", ""))
    }
}
