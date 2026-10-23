package com.hector.android.money

/**
 * A directory entry is a discovery starting point, not proof of eligibility or payment.
 * Keep this registry limited to official platform directories; never infer earnings from listings.
 */
data class PublicOpportunitySource(
    val id: String,
    val name: String,
    val directoryUrl: String,
    val accessMode: SourceAccessMode,
    val requiresAccountForPersonalizedData: Boolean
) {
    init {
        require(id.isNotBlank()) { "Source id must not be blank" }
        require(name.isNotBlank()) { "Source name must not be blank" }
        require(directoryUrl.startsWith("https://")) { "Source directory must use HTTPS" }
    }
}

enum class SourceAccessMode {
    PUBLIC_DIRECTORY,
    ACCOUNT_REQUIRED_FOR_PERSONALIZED_DATA
}

object PublicOpportunitySources {
    /**
     * Official public directory pages only. Hector does not scrape these pages, submit reports,
     * or claim the listed programs are eligible for this user. A future connector must use a
     * documented API or explicitly permitted public interface and re-check each program's policy.
     */
    val officialDirectories: List<PublicOpportunitySource> = listOf(
        PublicOpportunitySource(
            id = "hackerone-public-directory",
            name = "HackerOne public program directory",
            directoryUrl = "https://hackerone.com/directory/programs",
            accessMode = SourceAccessMode.PUBLIC_DIRECTORY,
            requiresAccountForPersonalizedData = true
        ),
        PublicOpportunitySource(
            id = "bugcrowd-public-programs",
            name = "Bugcrowd public program list",
            directoryUrl = "https://www.bugcrowd.com/bug-bounty-list/",
            accessMode = SourceAccessMode.PUBLIC_DIRECTORY,
            requiresAccountForPersonalizedData = true
        )
    )

    fun findById(id: String): PublicOpportunitySource? =
        officialDirectories.firstOrNull { it.id == id }
}
