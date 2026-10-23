package com.hector.android

/** Converts observed postconditions into a truthful task result; model text alone is not evidence. */
object AgentTaskVerifier {
    enum class Outcome { VERIFIED_SUCCESS, INCOMPLETE, FAILED, CANCELLED }

    data class Evidence(
        val executionStarted: Boolean,
        val requiredChecks: List<Boolean>,
        val cancelled: Boolean = false,
        val executionError: String? = null
    )

    data class Result(val outcome: Outcome, val summary: String, val passedChecks: Int, val totalChecks: Int)

    fun evaluate(evidence: Evidence): Result {
        val total = evidence.requiredChecks.size
        val passed = evidence.requiredChecks.count { it }
        if (evidence.cancelled) return Result(Outcome.CANCELLED, "Task was cancelled; no success is claimed.", passed, total)
        if (!evidence.executionError.isNullOrBlank()) {
            return Result(Outcome.FAILED, "Execution failed: ${evidence.executionError.take(240)}", passed, total)
        }
        if (!evidence.executionStarted) {
            return Result(Outcome.INCOMPLETE, "Execution has not been observed; result is unverified.", passed, total)
        }
        if (total == 0 || passed != total) {
            return Result(Outcome.INCOMPLETE, "Postconditions are incomplete ($passed/$total checks passed); result is unverified.", passed, total)
        }
        return Result(Outcome.VERIFIED_SUCCESS, "All $total required postcondition checks passed.", passed, total)
    }
}
