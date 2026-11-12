// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.utils

/** Facts and links for the About and legal notices screen (see NOTICE.md, licenses/bookorbit/ADDITIONAL_TERMS.md). */
object LegalInfo {
    /** The complete Corresponding Source of this app (AGPL-3.0 section 13). */
    const val SOURCE_URL = ProjectLinks.REPO_URL
    const val BOOKORBIT_URL = "https://github.com/bookorbit/bookorbit"
    const val NOTABLE_URL = "https://github.com/Ethran/notable"
    const val ARAGONITE_URL = "https://github.com/jdkruzr/aragonite"

    /** The original author's sponsor page. Credit to the upstream project, not a link for this app's own support. */
    const val NOTABLE_SPONSOR_URL = "https://github.com/sponsors/ethran"

    /** Date of the last change to BookOrbit-derived material that was noted in NOTICE.md (ISO 8601). */
    const val MODIFIED_ON = "2026-11-12"

    /** What the build writes into `BuildConfig.GIT_SHA` when git was not available. */
    const val UNKNOWN_COMMIT = "unknown"

    private val shaPattern = Regex("[0-9a-f]{4,40}")

    /** The commit hash worth showing: a real one, or null for "unknown", blank or malformed text. */
    fun commitOrNull(sha: String?): String? = sha?.trim()?.takeIf { shaPattern.matches(it) }

    /** Link to the exact commit of this build, or null when the build does not know its commit. */
    fun commitUrl(sha: String?): String? = commitOrNull(sha)?.let { "$SOURCE_URL/commit/$it" }

    /** Link to a file of the repository on the main branch, e.g. `blobUrl("NOTICE.md")`. */
    fun blobUrl(path: String): String = "$SOURCE_URL/blob/main/${path.trimStart('/')}"
}
