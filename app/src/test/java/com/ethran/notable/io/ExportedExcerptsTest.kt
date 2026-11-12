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
}
