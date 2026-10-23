package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicOpportunitySourceTest {
    @Test
    fun registryContainsOnlyHttpsOfficialDirectories() {
        val sources = PublicOpportunitySources.officialDirectories
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.all { it.directoryUrl.startsWith("https://") })
        assertTrue(sources.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun includesHackerOneAndBugcrowdPublicDirectories() {
        assertNotNull(PublicOpportunitySources.findById("hackerone-public-directory"))
        assertNotNull(PublicOpportunitySources.findById("bugcrowd-public-programs"))
    }

    @Test
    fun unknownSourceIsNotSilentlyAccepted() {
        assertNull(PublicOpportunitySources.findById("unknown-provider"))
    }

    @Test
    fun directoryRegistryDoesNotRepresentIncomeOrPayout() {
        assertEquals(
            SourceAccessMode.PUBLIC_DIRECTORY,
            PublicOpportunitySources.findById("hackerone-public-directory")?.accessMode
        )
        assertTrue(
            PublicOpportunitySources.officialDirectories.all {
                it.requiresAccountForPersonalizedData
            }
        )
    }
}
