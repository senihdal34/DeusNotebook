// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectLinksTest {
    @Test fun everyProductLinkPointsAtThisRepository() {
        assertEquals("https://github.com/senihdal34/DeusNotebook", ProjectLinks.REPO_URL)
        assertEquals("https://github.com/senihdal34/DeusNotebook/releases", ProjectLinks.RELEASES_URL)
        assertEquals("https://api.github.com/repos/senihdal34/DeusNotebook/releases", ProjectLinks.RELEASES_API_URL)
        assertEquals(ProjectLinks.REPO_URL, LegalInfo.SOURCE_URL)
        for (url in listOf(ProjectLinks.REPO_URL, ProjectLinks.RELEASES_URL, ProjectLinks.ISSUES_NEW_URL, ProjectLinks.RELEASES_API_URL)) {
            assertFalse(url, url.contains("ethran", ignoreCase = true))
            assertFalse(url, url.contains("jdkruzr", ignoreCase = true))
        }
    }

    @Test fun newIssueLinkCarriesTitleAndBody() {
        assertEquals(
            "https://github.com/senihdal34/DeusNotebook/issues/new?title=Bug%3A+x&body=a%0Ab",
            ProjectLinks.newIssueUrl("Bug%3A+x", "a%0Ab")
        )
    }

    @Test fun theUpstreamSponsorLinkIsCreditAndStaysUpstream() {
        assertEquals("https://github.com/sponsors/ethran", LegalInfo.NOTABLE_SPONSOR_URL)
    }
}
