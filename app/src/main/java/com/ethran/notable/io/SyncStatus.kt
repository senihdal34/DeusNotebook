package com.ethran.notable.io

import java.io.File

/** What the Sync tab shows about the notes folder: how many notes were exported and when the last one was written. */
data class ExportStatus(val folder: File, val noteCount: Int, val lastExportMillis: Long?) {
    companion object {
        /** Reads the folder; a missing or unreadable folder counts as nothing exported. */
        fun of(folder: File): ExportStatus {
            val notes = folder.listFiles { f -> f.isFile && f.extension == "md" }.orEmpty()
            return ExportStatus(folder, notes.size, notes.maxOfOrNull { it.lastModified() })
        }
    }
}

/** Where to get Syncthing and how to recognize it on the tablet. */
object SyncthingApps {
    /** Syncthing-Fork (F-Droid) and the original Syncthing app (Google Play), in the order we look for them. */
    val packages = listOf("com.github.catfriend1.syncthingfork", "com.nutomic.syncthingandroid")
    const val ANDROID_DOWNLOAD_URL = "https://f-droid.org/packages/com.github.catfriend1.syncthingfork/"
    const val COMPUTER_DOWNLOAD_URL = "https://syncthing.net/downloads/"

    fun installed(isInstalled: (String) -> Boolean): String? = packages.firstOrNull(isInstalled)
}
