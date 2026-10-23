package com.hector.android.money

/**
 * Safe default until a provider's documented API and access permissions are configured.
 * This implementation deliberately performs no network requests and never fabricates results.
 */
class UnconfiguredPublicOpportunityConnector(
    override val sourceId: String
) : PublicOpportunityConnector {
    init {
        require(PublicOpportunitySources.findById(sourceId) != null) {
            "Source must be present in the official source registry"
        }
    }

    override suspend fun discover(): OpportunityDiscoveryResult =
        OpportunityDiscoveryResult.Unavailable(DiscoveryUnavailableReason.NOT_CONFIGURED)
}
