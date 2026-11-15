package com.ethran.notable.io

import java.io.File

/**
 * The recognised text of a note, kept in the app's private storage (one small text file per page) so the Home list has
 * a title and an excerpt after leaving a note without exporting it. The vault is not touched. When the note is
 * exported later, the exported Markdown is the preferred source of the excerpt.
 */
object RecognisedTextStore {
    /** Set once at app start (and by the sync on first use); null in JVM tests unless a test sets it. */
    @Volatile
    var dir: File? = null

    private fun file(pageId: String): File? =
        dir?.let { File(it, pageId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt") }

    fun save(pageId: String, text: String) {
        try {
            val f = file(pageId) ?: return
            f.parentFile?.mkdirs()
            f.writeText(text)
        } catch (_: Exception) { /* the title is already saved; only the excerpt is lost */ }
    }

    fun read(pageId: String): String? =
        try { file(pageId)?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
}
