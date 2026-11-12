package com.ethran.notable.io

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultTagScannerTurkishTest {
    @Test
    fun `tags are lowercased the Turkish way and only the front matter is read`() {
        val dir = kotlin.io.path.createTempDirectory("vault").toFile()
        try {
            File(dir, "a.md").writeText("---\ntags:\n  - ISLAK\n  - İstanbul\n---\n\ntags:\n  - not-a-tag\n")
            File(dir, "b.md").writeText("---\ntags: [Irmak, #ışık]\n---\nbody")
            File(dir, "c.md").writeText("no front matter\n---\ntags:\n - x\n---\n")
            val tags = VaultTagScanner.scanTags(dir.absolutePath).toSet()
            assertEquals(setOf("ıslak", "istanbul", "ırmak", "ışık"), tags)
        } finally {
            dir.deleteRecursively()
        }
    }
}
