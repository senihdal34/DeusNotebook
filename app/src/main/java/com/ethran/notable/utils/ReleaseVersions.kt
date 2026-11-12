// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.utils

import kotlinx.serialization.json.Json
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

/** One entry of GitHub's `GET /repos/{owner}/{repo}/releases`. Every field has a default: a missing one must not crash. */
@Suppress("PropertyName")
@kotlinx.serialization.Serializable
data class GitHubRelease(
    val name: String? = null,
    val tag_name: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList()
) {
    @kotlinx.serialization.Serializable
    data class Asset(
        val name: String = "",
        val updated_at: String = ""
    )

    /** The version text of this release: the tag, or the title when there is no tag. */
    val versionText: String? get() = (tag_name?.takeIf { it.isNotBlank() } ?: name)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * A version such as `0.2.0`, `v0.2.1`, `1.4` or `0.2.0-next-12.11.2026-09:30`. The leading `v` is optional, a missing
 * patch number counts as 0. Ordering: major, minor, patch; for equal numbers a build with a "-next-" suffix is older
 * than the stable release with the same number, and two "-next-" builds are ordered by build time.
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val buildTimestamp: Date? = null
) : Comparable<Version> {
    companion object {
        private val versionRegex = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-next-(\d{2}\.\d{2}\.\d{4}-\d{2}:\d{2}))?$""")
        private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm")

        fun fromString(versionString: String): Version? {
            val match = versionRegex.matchEntire(versionString.trim()) ?: return null
            val (major, minor, patch, dateStr) = match.destructured
            val numbers = listOf(major, minor, patch.ifEmpty { "0" }).map { it.toIntOrNull() ?: return null }
            if (dateStr.isEmpty()) return Version(numbers[0], numbers[1], numbers[2])
            return try {
                val instant = LocalDateTime.parse(dateStr, dateFormatter).atOffset(ZoneOffset.UTC).toInstant()
                Version(numbers[0], numbers[1], numbers[2], Timestamp.from(instant))
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun compareTo(other: Version): Int {
        val byNumber = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
        if (byNumber != 0) return byNumber
        val mine = buildTimestamp
        val theirs = other.buildTimestamp
        return when {
            mine != null && theirs != null -> mine.compareTo(theirs)
            mine != null -> -1 // a "-next-" build is older than the stable release of the same number
            theirs != null -> 1
            else -> 0
        }
    }

    override fun toString(): String = if (buildTimestamp != null) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault()).format(buildTimestamp.toInstant())
    } else {
        "v$major.$minor.$patch"
    }
}

/** What the update check found. Nothing here is an error: a failed check is [CouldNotCheck] and the UI says "up to date". */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data object CouldNotCheck : UpdateStatus
    data class StableAvailable(val current: Version, val latest: Version) : UpdateStatus
    data class PreviewAvailable(val current: Version, val latest: Version) : UpdateStatus

    /** True when the app should NOT show the "newer version" prompt. */
    val isUpToDateOrUnknown: Boolean get() = this is UpToDate || this is CouldNotCheck
}

/** The pure part of the update check: JSON text in, [UpdateStatus] out. No network, no Android classes. */
object ReleaseCheck {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The releases of a repository; an empty list for `[]`, null when [text] is not a list of releases. */
    fun parseReleases(text: String?): List<GitHubRelease>? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString<List<GitHubRelease>>(text)
        } catch (_: Exception) {
            null
        }
    }

    /** The highest published (not draft, not prerelease) version. GitHub lists by date, so the list order is not trusted. */
    fun latestStable(releases: List<GitHubRelease>): Version? =
        releases.asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { r -> r.versionText?.let { Version.fromString(it) } }
            .filter { it.buildTimestamp == null }
            .maxOrNull()

    /** Upload time of the preview APK of the newest prerelease, minus 15 minutes for the build time (as before). */
    fun latestPreviewMillis(releases: List<GitHubRelease>, apkName: String = ProjectLinks.NEXT_APK_NAME): Long? =
        releases.asSequence()
            .filter { !it.draft && it.prerelease }
            .mapNotNull { r -> r.assets.firstOrNull { it.name == apkName } }
            .mapNotNull { asset ->
                try {
                    ZonedDateTime.parse(asset.updated_at, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            .maxOrNull()
            ?.minus(PREVIEW_BUILD_TIME_MILLIS)

    private const val PREVIEW_BUILD_TIME_MILLIS = 900_000L

    /**
     * [currentName] is the app's `versionName`, [releasesJson] the answer of the releases endpoint (null when the
     * request failed). A repository without releases is [UpdateStatus.UpToDate]; anything unreadable is [UpdateStatus.CouldNotCheck].
     */
    fun evaluate(currentName: String?, releasesJson: String?): UpdateStatus {
        val current = currentName?.let { Version.fromString(it) } ?: return UpdateStatus.CouldNotCheck
        val releases = parseReleases(releasesJson) ?: return UpdateStatus.CouldNotCheck
        return if (current.buildTimestamp != null) {
            val latestMillis = latestPreviewMillis(releases) ?: return UpdateStatus.UpToDate
            val latest = Version(current.major, current.minor, current.patch, Timestamp(latestMillis))
            if (current < latest) UpdateStatus.PreviewAvailable(current, latest) else UpdateStatus.UpToDate
        } else {
            val latest = latestStable(releases) ?: return UpdateStatus.UpToDate
            if (current < latest) UpdateStatus.StableAvailable(current, latest) else UpdateStatus.UpToDate
        }
    }
}
