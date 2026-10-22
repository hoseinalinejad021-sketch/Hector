package com.hector.android

/**
 * Keeps the client-side payload limits aligned with firestore.rules.
 * Byte limits are stricter than the Firestore character limits and leave
 * substantial room for document keys and Firestore encoding overhead.
 */
internal object CloudSyncPayloadLimits {
    const val MAX_FIELD_CHARS = 300_000
    const val MAX_FIELD_UTF8_BYTES = 300_000
    const val MAX_TOTAL_UTF8_BYTES = 600_000

    fun isWithinLimits(memory: String, history: String): Boolean {
        if (memory.length > MAX_FIELD_CHARS || history.length > MAX_FIELD_CHARS) return false

        val memoryBytes = memory.toByteArray(Charsets.UTF_8).size
        val historyBytes = history.toByteArray(Charsets.UTF_8).size
        return memoryBytes <= MAX_FIELD_UTF8_BYTES &&
            historyBytes <= MAX_FIELD_UTF8_BYTES &&
            memoryBytes.toLong() + historyBytes.toLong() <= MAX_TOTAL_UTF8_BYTES
    }
}
