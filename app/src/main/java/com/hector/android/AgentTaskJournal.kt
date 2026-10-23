package com.hector.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small bounded journal of agent task outcomes. It stores summaries, never prompts, credentials,
 * raw tool arguments, or unbounded tool output. The journal is local to this app installation.
 */
class AgentTaskJournal(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    data class Entry(
        val id: String,
        val goal: String,
        val status: String,
        val summary: String,
        val updatedAtMillis: Long
    )

    @Synchronized
    fun record(goal: String, status: String, summary: String, nowMillis: Long = System.currentTimeMillis()) {
        val safeGoal = sanitize(goal, MAX_GOAL_CHARS)
        val safeSummary = sanitize(summary, MAX_SUMMARY_CHARS)
        val safeStatus = status.takeIf { it in ALLOWED_STATUSES } ?: STATUS_FAILED
        val existing = readEntries().filterNot { it.id == "$nowMillis:$safeGoal" }.toMutableList()
        existing.add(0, Entry("$nowMillis:$safeGoal", safeGoal, safeStatus, safeSummary, nowMillis))
        val array = JSONArray()
        existing.take(MAX_ENTRIES).forEach { entry ->
            array.put(JSONObject()
                .put("id", entry.id)
                .put("goal", entry.goal)
                .put("status", entry.status)
                .put("summary", entry.summary)
                .put("updatedAtMillis", entry.updatedAtMillis))
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    @Synchronized
    fun recent(limit: Int = MAX_ENTRIES): List<Entry> {
        if (limit <= 0) return emptyList()
        return readEntries().take(limit.coerceAtMost(MAX_ENTRIES))
    }

    private fun readEntries(): List<Entry> = runCatching {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val status = item.optString("status")
            if (status !in ALLOWED_STATUSES) return@mapNotNull null
            Entry(
                id = item.optString("id").take(240),
                goal = item.optString("goal").take(MAX_GOAL_CHARS),
                status = status,
                summary = item.optString("summary").take(MAX_SUMMARY_CHARS),
                updatedAtMillis = item.optLong("updatedAtMillis", 0L)
            )
        }.take(MAX_ENTRIES)
    }.getOrElse { emptyList() }

    private fun sanitize(value: String, maxChars: Int): String {
        val withoutControls = value.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        val redacted = SECRET_PATTERN.replace(withoutControls) { match ->
            val prefix = match.groupValues[1]
            "$prefix[REDACTED]"
        }
        return redacted.trim().take(maxChars)
    }

    companion object {
        private const val PREFS_NAME = "hector_agent_task_journal"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 50
        private const val MAX_GOAL_CHARS = 240
        private const val MAX_SUMMARY_CHARS = 1_000
        private const val STATUS_FAILED = "failed"
        private val ALLOWED_STATUSES = setOf("planned", "running", "succeeded", "failed", "cancelled", "needs_approval")
        private val SECRET_PATTERN = Regex(
            "(?i)(api[_-]?key|authorization|bearer|password|token|secret)(\\s*[:=]\\s*)([^\\s,;]+)"
        )
    }
}
