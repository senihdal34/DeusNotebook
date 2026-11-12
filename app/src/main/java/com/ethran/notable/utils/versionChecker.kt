package com.ethran.notable.utils

import android.content.Context
import android.content.pm.PackageManager
import com.ethran.notable.BuildConfig
import com.ethran.notable.R
import com.ethran.notable.i18n.AppStrings
import com.ethran.notable.ui.showHint
import io.shipbook.shipbooksdk.ShipBook
import java.net.HttpURLConnection
import java.net.URL

private val log = ShipBook.getLogger("versionChecker")

private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 8_000

/** The releases of this project's repository as JSON text, or null on any network or HTTP failure. */
fun fetchReleasesJson(apiUrl: String = ProjectLinks.RELEASES_API_URL): String? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "${ProjectLinks.REPO_NAME}/${BuildConfig.VERSION_NAME}")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            log.i("Releases request answered ${connection.responseCode}")
            null
        } else {
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        log.i("Failed to fetch releases: ${e.message}")
        null
    } finally {
        connection?.disconnect()
    }
}

fun getCurrentVersionName(context: Context): String? {
    try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return null
}

// cache
var isLatestVersion: Boolean? = null

/**
 * True when the app is current, or when that cannot be told (no releases yet, no network, an unreadable answer).
 * Never throws. Only a real answer is cached, so a failed check can be repeated with the button.
 */
fun isLatestVersion(context: Context, force: Boolean = false): Boolean {
    if (!force) isLatestVersion?.let { return it }

    val status = try {
        ReleaseCheck.evaluate(getCurrentVersionName(context), fetchReleasesJson())
    } catch (e: Exception) {
        log.i("Update check failed: ${e.message}")
        UpdateStatus.CouldNotCheck
    }
    log.i("Update check: $status")

    when (status) {
        is UpdateStatus.StableAvailable -> showHint(
            AppStrings.get(R.string.update_stable_available, status.current.toString(), status.latest.toString()),
            duration = 5000
        )
        is UpdateStatus.PreviewAvailable -> showHint(
            AppStrings.get(R.string.update_preview_available, status.current.toString(), status.latest.toString()),
            duration = 5000
        )
        UpdateStatus.UpToDate, UpdateStatus.CouldNotCheck -> Unit
    }
    if (status != UpdateStatus.CouldNotCheck) isLatestVersion = status.isUpToDateOrUnknown
    return status.isUpToDateOrUnknown
}

const val isNext = BuildConfig.IS_NEXT
