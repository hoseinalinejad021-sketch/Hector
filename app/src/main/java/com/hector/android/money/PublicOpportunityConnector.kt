package com.hector.android.money

/**
 * Contract for a future source integration. Implementations must use an official documented API
 * or an explicitly permitted interface; this interface intentionally performs no network access.
 */
interface PublicOpportunityConnector {
    val sourceId: String

    suspend fun discover(): OpportunityDiscoveryResult
}

sealed class OpportunityDiscoveryResult {
    data class Success(
        val candidates: List<PublicOpportunityCandidate>,
        val fetchedAtEpochMillis: Long
    ) : OpportunityDiscoveryResult() {
        init {
            require(fetchedAtEpochMillis > 0) { "Fetch timestamp must be positive" }
            require(candidates.all { it.sourceId.isNotBlank() }) {
                "Every candidate must identify its source"
            }
        }
    }

    data class Unavailable(val reason: DiscoveryUnavailableReason) : OpportunityDiscoveryResult()
}

enum class DiscoveryUnavailableReason {
    NOT_CONFIGURED,
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    NETWORK_ERROR,
    PROVIDER_POLICY_REQUIRES_MANUAL_REVIEW
}

/**
 * Shared validation for connector results. Rejects candidates attributed to a different source;
 * callers must not convert discovery results into realized income.
 */
object OpportunityDiscoveryValidator {
    fun validate(
        expectedSourceId: String,
        result: OpportunityDiscoveryResult
    ): OpportunityDiscoveryResult {
        require(expectedSourceId.isNotBlank()) { "Expected source id must not be blank" }
        if (result !is OpportunityDiscoveryResult.Success) return result

        val valid = result.candidates.all { candidate ->
            candidate.sourceId == expectedSourceId &&
                PublicOpportunitySources.findById(candidate.sourceId) != null &&
                PublicOpportunityCandidate.isAllowedProgramUrl(
                    candidate.sourceId,
                    candidate.programUrl
                )
        }
        return if (valid) result
        else OpportunityDiscoveryResult.Unavailable(
            DiscoveryUnavailableReason.PROVIDER_POLICY_REQUIRES_MANUAL_REVIEW
        )
    }
}
