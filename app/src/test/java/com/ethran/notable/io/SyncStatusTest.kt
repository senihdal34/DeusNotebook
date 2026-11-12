package com.ethran.notable.io

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SyncStatusTest {
    private fun dir() = createTempDirectory("notes").toFile()

    @Test fun `counts only markdown files and reports the newest one`() {
        val d = dir()
        File(d, "a.md").apply { writeText("a"); setLastModified(1_000_000) }
        File(d, "b.md").apply { writeText("b"); setLastModified(3_000_000) }
        File(d, "glossary.txt").apply { writeText("x"); setLastModified(9_000_000) }
        File(d, "jpg_archive").mkdirs()
        File(d, "folder.md").mkdirs()          // a directory named like a note is not a note
        val s = ExportStatus.of(d)
        assertEquals(2, s.noteCount)
        assertEquals(3_000_000L, s.lastExportMillis)
        d.deleteRecursively()
    }

    @Test fun `a missing or empty folder means nothing exported`() {
        val missing = ExportStatus.of(File(dir(), "nope"))
        assertEquals(0, missing.noteCount)
        assertNull(missing.lastExportMillis)
        val empty = dir()
        assertEquals(0, ExportStatus.of(empty).noteCount)
        empty.deleteRecursively()
    }

    @Test fun `syncthing is found by either known package`() {
        assertEquals("com.github.catfriend1.syncthingfork", SyncthingApps.installed { it == "com.github.catfriend1.syncthingfork" })
        assertEquals("com.nutomic.syncthingandroid", SyncthingApps.installed { it == "com.nutomic.syncthingandroid" })
        assertNull(SyncthingApps.installed { false })
        assertTrue(SyncthingApps.ANDROID_DOWNLOAD_URL.startsWith("https://"))
        assertTrue(SyncthingApps.COMPUTER_DOWNLOAD_URL.startsWith("https://"))
    }
}
