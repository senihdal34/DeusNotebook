package com.ethran.notable.io

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportedExcerptsTest {
    private fun md(body: String) = "---\ncreated: \"[[2026-10-12]]\"\npages: 1\nsource: aragonite\n---\n\n$body\n\n---\n![page 1](jpg_archive/x.jpg)\n"

    @Test fun `headline is dropped and following lines are joined`() =
        assertEquals("ilk satır ve ikinci satır burada.", ExportedExcerpts.excerptFrom(md("Örnek Rapor\nilk satır ve ikinci\nsatır burada.")).replace("  ", " "))

    @Test fun `strike marks and wiki brackets are removed`() =
        assertEquals("Örnek hesapları [x] Veltora", ExportedExcerpts.excerptFrom(md("Gün 1\n~~Örnek hesapları~~\n[x] [[Veltora]]")).replace("[x]", "[x]"))

    @Test fun `title only note has no excerpt`() =
        assertEquals("", ExportedExcerpts.excerptFrom(md("Örnek Rapor")))

    @Test fun `image trailer is never part of the excerpt`() =
        assertTrue(!ExportedExcerpts.excerptFrom(md("Başlık\nbir iki üç")).contains("jpg"))

    @Test fun `long text is cut with an ellipsis`() {
        val long = (1..60).joinToString(" ") { "kelime$it" }
        val e = ExportedExcerpts.excerptFrom(md("Başlık\n$long"))
        assertTrue(e.endsWith("…") && e.length <= 171)
    }

    @Test fun `unrecognized placeholder is ignored`() =
        assertEquals("", ExportedExcerpts.excerptFrom(md("_Handwriting could not be recognized. See the page image below._")))

    // ---- a note left by Back (recognised text) and later saved with Save & Exit (exported Markdown) ----

    private fun withPage(block: (java.io.File, com.ethran.notable.data.db.Page, String) -> Unit) {
        val dir = kotlin.io.path.createTempDirectory("vault").toFile()
        try {
            val page = com.ethran.notable.data.db.Page(notebookId = null, background = "inbox", createdAt = java.util.Date(1_700_000_000_000))
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).format(page.createdAt)
            block(dir, page, stamp)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `after Back only the recognised text gives the excerpt, without its title line`() = withPage { dir, page, _ ->
        val map = ExportedExcerpts.load(dir, listOf(page)) { "Toplantı\nbütçe ve takvim" }
        assertEquals("bütçe ve takvim", map[page.id])
    }

    @Test fun `after Save and Exit the exported Markdown wins over the recognised text, shown once`() = withPage { dir, page, stamp ->
        java.io.File(dir, "Toplantı-$stamp.md").writeText(md("Toplantı\nkesin metin"))
        val map = ExportedExcerpts.load(dir, listOf(page)) { "Toplantı\neski tanıma" }
        assertEquals(mapOf(page.id to "kesin metin"), map)
    }
}
