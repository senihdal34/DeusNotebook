package com.ethran.notable.io

import android.os.Environment
import java.io.File

/**
 * Names of the notebooks and files in the Boox Notes app (its exports live in /sdcard/note), as hashtags.
 * "To do" is left out. Subfolder names count too; a notebook with the same name as its folder appears once.
 */
object BooxNoteNames {
    private val skipped = setOf("todo", "to-do", "to do", "yapilacaklar", "yapılacaklar")

    fun scan(root: File = File(Environment.getExternalStorageDirectory(), "note")): List<String> {
        if (!root.isDirectory) return emptyList()
        val names = LinkedHashSet<String>()
        root.walkTopDown().maxDepth(4).filter { it != root }.forEach { f ->
            val raw = if (f.isDirectory) f.name else f.nameWithoutExtension
            tag(raw)?.let { names.add(it) }
        }
        return names.toList()
    }

    /** "Örnek cüzdan" -> "Örnek-cüzdan"; null for To do, empty or hidden names. */
    fun tag(raw: String): String? {
        // File names come back decomposed from some file systems ("s" + U+0327): compose to NFC first.
        val name = TextSanitizer.cleanLine(raw)
        if (name.isEmpty() || name.startsWith(".")) return null
        if (name.lowercase(java.util.Locale("tr", "TR")) in skipped || name.lowercase() in skipped) return null
        return TextSanitizer.tagFromName(name).ifEmpty { null }
    }
}
