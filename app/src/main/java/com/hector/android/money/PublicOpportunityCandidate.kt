package com.hector.android.money

import java.net.URI

/**
 * A discovered listing is not an earnings record. Only store candidates after a user or a
 * permitted provider connector has verified the listing URL against the registered source.
 */
data class PublicOpportunityCandidate(
    val id: String,
    val sourceId: String,
    val title: String,
    val programUrl: String,
    val discoveredAtEpochMillis: Long,
    val reviewStatus: OpportunityReviewStatus = OpportunityReviewStatus.UNREVIEWED
) {
    init {
        require(id.isNotBlank()) { "Candidate id must not be blank" }
        require(title.isNotBlank()) { "Candidate title must not be blank" }
        require(discoveredAtEpochMillis > 0) { "Discovery timestamp must be positive" }
        require(isAllowedProgramUrl(sourceId, programUrl)) {
            "Program URL must use HTTPS and match the registered source host"
        }
    }

    companion object {
        fun isAllowedProgramUrl(sourceId: String, url: String): Boolean {
            val source = PublicOpportunitySources.findById(sourceId) ?: return false
            return try {
                val uri = URI(url)
                val host = uri.host?.lowercase() ?: return false
                val schemeIsHttps = uri.scheme.equals("https", ignoreCase = true)
                val hasNoUserInfo = uri.rawUserInfo == null
                val hasNoFragmentCredentials = !url.contains("@")
                val allowedHost = when (sourceId) {
                    "hackerone-public-directory" ->
                        host == "hackerone.com" || host.endsWith(".hackerone.com")
                    "bugcrowd-public-programs" ->
                        host == "bugcrowd.com" || host.endsWith(".bugcrowd.com")
                    else -> false
                }
                schemeIsHttps && hasNoUserInfo && hasNoFragmentCredentials && allowedHost
            } catch (_: Exception) {
                false
            }
        }
    }
}

enum class OpportunityReviewStatus {
    UNREVIEWED,
    ELIGIBILITY_CHECK_REQUIRED,
    USER_CONFIRMED_ELIGIBLE,
    INELIGIBLE
}
