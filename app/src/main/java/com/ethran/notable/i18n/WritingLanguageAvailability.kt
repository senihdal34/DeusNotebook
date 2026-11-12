package com.ethran.notable.i18n

import java.io.File

/**
 * Which handwriting languages the tablet's recognizer can actually use. The Onyx service reads each language's
 * resources from a folder named after the code (`tr_TR/`): English and Chinese come with the system, others are
 * downloaded in Boox Notes into `/sdcard/.noteResource/<version>/recognition-assets/resources/`. A language
 * without its folder is not recognized.
 */
object WritingLanguageAvailability {
    private val systemResources = File("/system/recognition_assets/resources")
    private val downloadedRoot = File("/sdcard/.noteResource")

    /** Codes that have resources on this tablet; null when the folders cannot be read (then assume all work). */
    fun installed(system: File = systemResources, downloaded: File = downloadedRoot): Set<String>? {
        val found = mutableSetOf<String>()
        var readable = false
        fun add(dir: File) {
            val names = dir.list() ?: return
            readable = true
            found += names
        }
        add(system)
        downloaded.listFiles()?.forEach { add(File(it, "recognition-assets/resources")) }
        return if (readable) found else null
    }

    fun isAvailable(code: String, installed: Set<String>? = installed()): Boolean = installed == null || code in installed

    /** The language the recognizer really gets: the chosen one when installed, otherwise English. */
    fun effective(code: String, installed: Set<String>? = installed()): String =
        if (isAvailable(code, installed)) code else WritingLanguage.DEFAULT_CODE

    /**
     * The Boox apps that can be opened to download a language pack. The pack is fetched by Boox's own note editor
     * (language button, More languages); it has no launcher icon of its own, so the Boox home app (Notes tab) comes first.
     */
    private val booxPackages = listOf("com.onyx", "com.onyx.android.note")

    /**
     * If the language the user was waiting for is installed now, makes it the writing language and returns it.
     * [installed] is passed in by tests.
     */
    fun promotePending(context: android.content.Context, installed: Set<String>? = installed()): WritingLanguage? {
        val pending = LanguagePrefs.pendingWriting(context) ?: return null
        if (installed == null || pending.code !in installed) return null
        LanguagePrefs.setWriting(context, pending)
        LanguagePrefs.setPendingWriting(context, null)
        com.ethran.notable.io.OnyxHWREngine.setLanguage(pending.code)
        return pending
    }

    /** Opens Boox so the user can download the language (Notes, any note, language button, More languages). False if Boox is missing. */
    fun openBooxNotes(context: android.content.Context): Boolean {
        for (pkg in booxPackages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        return false
    }
}
