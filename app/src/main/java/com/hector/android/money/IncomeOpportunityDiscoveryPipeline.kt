package com.hector.android.money

import java.net.URI
import java.util.Locale

/**
 * Safety boundary between a permitted source connector and ranking.
 *
 * This class does not fetch pages, scrape websites, verify legal eligibility, or prove a payout.
 * A connector must independently follow the source's documented API/public-interface rules.
 */
object IncomeOpportunityDiscoveryPipeline {

    /**
     * Keeps only opportunities tied to a registered official source and its HTTPS host,
     * then removes duplicate listings by normalized source URL.
     *
     * Eligibility, reviewed terms and permitted automation are still checked by the ranker.
     */
    fun normalizeAndDeduplicate(
        candidates: List<IncomeOpportunity>
    ): List<IncomeOpportunity> {
        val seen = LinkedHashSet<String>()
        return candidates.filter { opportunity ->
            val source = PublicOpportunitySources.findById(opportunity.sourceId)
                ?: return@filter false
            val candidateUri = parseHttpsUri(opportunity.sourceUrl)
                ?: return@filter false
            val directoryUri = parseHttpsUri(source.directoryUrl)
                ?: return@filter false

            val candidateHost = normalizeHost(candidateUri.host)
            val sourceHost = normalizeHost(directoryUri.host)
            if (candidateHost != sourceHost && !candidateHost.endsWith(".$sourceHost")) {
                return@filter false
            }

            val path = candidateUri.rawPath.orEmpty().trimEnd('/').ifBlank { "/" }
            val key = "${source.id}|${candidateHost}|${path}"
            seen.add(key)
        }
    }

    private fun parseHttpsUri(value: String): URI? = runCatching {
        URI(value).takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                !it.host.isNullOrBlank() &&
                it.userInfo == null
        }
    }.getOrNull()

    private fun normalizeHost(host: String?): String =
        host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.")
}
