package com.ethran.notable.io

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HwrWordCorrectorTest {
    private val glossary = HwrGlossary(
        listOf("ProjeAlfa", "ÖrnekFirma", "Orbitalis", "Veltora", "Telmaron", "ÖrnekTelekom", "LinkedIn", "Şirket", "NASA")
    )

    private fun fix(label: String, vararg candidates: String) =
        HwrWordCorrector.correctWord(HwrWord(label, candidates.toList()), glossary)

    @Test fun `one wrong letter is fixed from the glossary`() = assertEquals("ProjeAlfa", fix("ProjeAlfo"))
    @Test fun `candidate from the engine wins over a wrong label`() = assertEquals("ÖrnekFirma", fix("Ornekfirna", "ÖrnekFirma", "Ornekfirne"))
    @Test fun `case and Turkish accents are ignored`() {
        assertEquals("Şirket", fix("sirket"))
        assertEquals("LinkedIn", fix("linkedın"))
    }
    @Test fun `turkish suffix after apostrophe is kept`() = assertEquals("Veltora'ya", fix("Veltora'ya"))
    @Test fun `stem is corrected and suffix kept`() = assertEquals("Veltora'ya", fix("Veltura'ya"))
    @Test fun `punctuation around the word is kept`() = assertEquals("(ProjeAlfa),", fix("(ProjeAlfo),"))
    @Test fun `all caps stays all caps`() = assertEquals("NASA", fix("N4SA", "NASA"))
    @Test fun `ordinary words and numbers are left alone`() {
        assertEquals("hesapları", fix("hesapları", "hesaplari", "hesaplan"))
        assertEquals("27200", fix("27200"))
        assertEquals("ile", fix("ile"))
    }
    @Test fun `glossary word with a turkish suffix is not shortened`() {
        assertEquals("ÖrnekFirma'dan", fix("ÖrnekFirma'dan"))
        assertEquals("ÖrnekFirma", fix("ÖrnekFirma"))
        val g = HwrGlossary(listOf("Windows"))
        assertEquals("Windowsta", HwrWordCorrector.correctWord(HwrWord("Windowsta"), g))
    }
    @Test fun `two glossary words equally close means no change`() {
        val g = HwrGlossary(listOf("Tarama", "Parama"))
        assertEquals("Xarama", HwrWordCorrector.correctWord(HwrWord("Xarama"), g))
    }
    @Test fun `distance two needs the same first letter`() {
        assertEquals("Orbitalis", fix("Orbitelas"))        // 2 edits, same first letter, long word
        assertEquals("Urbitelas", fix("Urbitelas"))        // first letter differs: untouched
        assertEquals("Telmoran", fix("Telmoran"))          // short word: only 1 edit allowed
    }
    @Test fun `empty glossary changes nothing`() =
        assertEquals("ProjeAlfo", HwrWordCorrector.correctWord(HwrWord("ProjeAlfo"), HwrGlossary(emptyList())))

    @Test fun `rebuild keeps spaces and line breaks`() {
        val words = listOf(HwrWord("ProjeAlfo"), HwrWord(" "), HwrWord("ile"), HwrWord("\n"), HwrWord("ÖrnekFirma"))
        assertEquals("ProjeAlfa ile\nÖrnekFirma", HwrWordCorrector.rebuild(words, glossary))
    }

    @Test fun `loader reads glossary file, note titles and tags`() {
        val dir = Files.createTempDirectory("inbox").toFile()
        try {
            HwrGlossaryLoader.ensureDefaultFile(dir)
            java.io.File(dir, "Örnek Not Planı-2026-09-28-11-26-29.md")
                .writeText("---\ntags:\n  - projealfaplan\n---\nx")
            java.io.File(dir, HwrGlossaryLoader.FILE_NAME).appendText("ÖrnekYazılım # şirket adı\n")
            val g = HwrGlossaryLoader.load(dir)
            assertEquals("ÖrnekFirma", g.exact("ornekfirma"))
            assertEquals("ÖrnekYazılım", g.exact("ornekyazilim"))
            assertEquals("Planı", g.exact("plani"))
            assertEquals("projealfaplan", g.exact("projealfaplan"))
            assertTrue(g.size > 20)
        } finally { dir.deleteRecursively() }
    }
}
