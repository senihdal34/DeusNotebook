// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckTest {
    private fun v(text: String) = requireNotNull(Version.fromString(text)) { "no version: $text" }

    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false, assets: String = "[]") =
        """{"tag_name":"$tag","name":"","html_url":"https://example.test/$tag","draft":$draft,"prerelease":$prerelease,"assets":$assets}"""

    private fun list(vararg items: String) = items.joinToString(",", "[", "]")

    // --- Version parsing and comparison -------------------------------------------------------------------------

    @Test fun leadingVToleratedAndPatchOptional() {
        assertEquals(Version(0, 2, 1), v("v0.2.1"))
        assertEquals(Version(1, 4, 0), v("1.4"))
        assertNull(Version.fromString("v1"))
        assertNull(Version.fromString("latest"))
        assertNull(Version.fromString(""))
        assertNull(Version.fromString("1.2.3-beta"))
    }

    @Test fun numbersCompareAsNumbersNotAsText() {
        assertTrue(v("0.2.0") < v("v0.2.1"))
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("1.0.0") > v("0.99.99"))
        assertEquals(0, v("0.2.0").compareTo(v("v0.2.0")))
        assertEquals(v("0.2.0").compareTo(v("0.2")), 0)
    }

    @Test fun nextSuffixIsParsedAndOrderedByBuildTime() {
        val older = v("0.2.0-next-12.11.2026-09:30")
        val newer = v("0.2.0-next-12.11.2026-10:05")
        assertNotNull(older.buildTimestamp)
        assertTrue(older < newer)
        assertNull(Version.fromString("0.2.0-next-99.99.2026-09:30"))
    }

    @Test fun nextBuildIsOlderThanItsOwnStableAndNewerThanThePreviousStable() {
        val next = v("0.2.0-next-12.11.2026-09:30")
        assertTrue(next < v("0.2.0"))
        assertTrue(next > v("0.1.9"))
        assertTrue(next < v("0.2.1"))
    }

    // --- Stable channel --------------------------------------------------------------------------------------------

    @Test fun repositoryWithoutReleasesIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0", "[]"))
        assertTrue(ReleaseCheck.evaluate("0.2.0", "[]").isUpToDateOrUnknown)
    }

    @Test fun repositoryWithOnlyPrereleasesAndDraftsIsUpToDateForStable() {
        val json = list(release("v9.9.9", prerelease = true), release("v8.8.8", draft = true))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0", json))
    }

    @Test fun newerStableReleaseIsOffered() {
        val status = ReleaseCheck.evaluate("0.2.0", list(release("v0.2.1")))
        assertEquals(UpdateStatus.StableAvailable(v("0.2.0"), v("0.2.1")), status)
        assertTrue(!status.isUpToDateOrUnknown)
    }

    @Test fun equalOrOlderStableReleaseIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0", list(release("v0.2.0"))))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.1", list(release("0.2.0"))))
    }

    @Test fun highestVersionWinsWhateverTheListOrder() {
        val json = list(release("v0.2.1"), release("v0.10.0"), release("v0.3.0"))
        assertEquals(v("0.10.0"), ReleaseCheck.latestStable(ReleaseCheck.parseReleases(json).orEmpty()))
    }

    @Test fun releaseTitleIsUsedWhenThereIsNoTag() {
        val json = """[{"name":"v0.3.0","html_url":"u","prerelease":false}]"""
        assertEquals(UpdateStatus.StableAvailable(v("0.2.0"), v("0.3.0")), ReleaseCheck.evaluate("0.2.0", json))
    }

    @Test fun releaseWithUnreadableVersionIsSkipped() {
        val json = list(release("nightly"), release("v0.2.5"))
        assertEquals(UpdateStatus.StableAvailable(v("0.2.0"), v("0.2.5")), ReleaseCheck.evaluate("0.2.0", json))
    }

    // --- Failures never crash --------------------------------------------------------------------------------------

    @Test fun failedRequestIsCouldNotCheck() {
        val status = ReleaseCheck.evaluate("0.2.0", null)
        assertEquals(UpdateStatus.CouldNotCheck, status)
        assertTrue(status.isUpToDateOrUnknown)
    }

    @Test fun garbageAnswersAreCouldNotCheck() {
        for (text in listOf("", "   ", "not json", "{}", """{"message":"Not Found"}""", "[1,2,3]", "[{")) {
            assertEquals(text, UpdateStatus.CouldNotCheck, ReleaseCheck.evaluate("0.2.0", text))
        }
    }

    @Test fun unreadableOwnVersionIsCouldNotCheck() {
        assertEquals(UpdateStatus.CouldNotCheck, ReleaseCheck.evaluate(null, "[]"))
        assertEquals(UpdateStatus.CouldNotCheck, ReleaseCheck.evaluate("dev", "[]"))
    }

    @Test fun releasesWithMissingFieldsStillParse() {
        val releases = ReleaseCheck.parseReleases("""[{},{"tag_name":"v1.0.0"}]""")
        assertEquals(2, releases?.size)
    }

    // --- Preview ("-next-") channel ---------------------------------------------------------------------------------

    private fun previewAsset(updatedAt: String, name: String = ProjectLinks.NEXT_APK_NAME) =
        """[{"name":"$name","updated_at":"$updatedAt"}]"""

    @Test fun newerPreviewBuildIsOffered() {
        val json = list(release("next", prerelease = true, assets = previewAsset("2026-11-13T10:00:00Z")))
        val status = ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", json)
        assertTrue(status is UpdateStatus.PreviewAvailable)
    }

    @Test fun currentPreviewBuildIsUpToDate() {
        val json = list(release("next", prerelease = true, assets = previewAsset("2026-11-12T09:35:00Z")))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", json))
    }

    @Test fun noPreviewReleaseOrWrongAssetIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", "[]"))
        val other = list(release("next", prerelease = true, assets = previewAsset("2026-11-13T10:00:00Z", "other.apk")))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", other))
        val badDate = list(release("next", prerelease = true, assets = previewAsset("yesterday")))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", badDate))
    }

    @Test fun previewBuildIgnoresStableReleases() {
        val json = list(release("v0.2.0"))
        assertEquals(UpdateStatus.UpToDate, ReleaseCheck.evaluate("0.2.0-next-12.11.2026-09:30", json))
    }
}
