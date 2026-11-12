package com.ethran.notable.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HwrGlossaryLoaderTest {
    private fun dir() = Files.createTempDirectory("glossary").toFile().also { it.deleteOnExit() }

    @Test fun multiWordLineAddsEachWord() {
        val d = dir()
        File(d, "glossary.txt").writeText("# firmalar\nProje Beta   # ekip\nÖrnekYazılım\n")
        val g = HwrGlossaryLoader.load(d)
        assertEquals("Proje", g.exact("proje"))
        assertEquals("Beta", g.exact("BETA"))
        assertEquals("ÖrnekYazılım", g.exact("ornekyazilim"))
    }

    @Test fun starterFileKnowsExampleNamesAndDevTerms() {
        val d = dir()
        HwrGlossaryLoader.ensureDefaultFile(d)
        val g = HwrGlossaryLoader.load(d)
        for (w in listOf("ÖrnekYazılım", "ÖrnekFirma", "ÖrnekAtölye", "ProjeAlfa", "ProjeBeta", "deployment", "backend", "Kubernetes")) {
            assertTrue("$w missing", g.exact(w) != null)
        }
        assertNull("common Turkish word must not be rewritten", g.exact("git"))
        assertEquals("Kubernetes", HwrWordCorrector.correctWord(HwrWord("Kuberentes"), g))
    }
}
