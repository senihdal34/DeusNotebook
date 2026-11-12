// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.utils

/**
 * Every link that points users at THIS project: its source, releases, issues and the update check. One place, so no
 * screen can drift back to an upstream address. Links to the projects this app is based on are credit, not product
 * links, and live in [LegalInfo] (`NOTABLE_URL`, `ARAGONITE_URL`, `NOTABLE_SPONSOR_URL`).
 */
object ProjectLinks {
    const val REPO_OWNER = "senihdal34"
    const val REPO_NAME = "DeusNotebook"

    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val ISSUES_NEW_URL = "$REPO_URL/issues/new"

    /** GitHub REST endpoint the "Check for newer version" button reads. */
    const val RELEASES_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"

    /** File name of the preview ("next") APK that .github/workflows/preview.yml uploads to the "next" prerelease. */
    const val NEXT_APK_NAME = "deusnotebook-next.apk"

    /** New-issue link with a prefilled [title] and [body] (both URL-encoded by the caller). */
    fun newIssueUrl(encodedTitle: String, encodedBody: String): String =
        "$ISSUES_NEW_URL?title=$encodedTitle&body=$encodedBody"
}
