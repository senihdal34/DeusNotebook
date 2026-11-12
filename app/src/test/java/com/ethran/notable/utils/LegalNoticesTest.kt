package com.ethran.notable.utils

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The About screen must show BookOrbit's notice word for word, and link to the exact source of this build. */
class LegalNoticesTest {
    private fun file(vararg candidates: String): File = candidates.map(::File).first { it.isFile }

    private fun legalStrings(): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file("src/main/res/values/strings_legal.xml", "app/src/main/res/values/strings_legal.xml")).documentElement
        val out = LinkedHashMap<String, String>()
        val nodes = root.getElementsByTagName("string")
        for (i in 0 until nodes.length) (nodes.item(i) as Element).let { out[it.getAttribute("name")] = it.textContent }
        return out
    }

    /** The block quote of section 1 in ADDITIONAL_TERMS.md, without markdown (bold, angle brackets around the link). */
    private fun termsNoticeLines(): List<String> {
        val text = file("../licenses/bookorbit/ADDITIONAL_TERMS.md", "licenses/bookorbit/ADDITIONAL_TERMS.md").readText()
        val section = text.substringAfter("## 1. Attribution").substringBefore("## 2.")
        return section.lines().filter { it.startsWith(">") }.map { it.removePrefix(">").trim() }.filter { it.isNotEmpty() }
            .map { it.replace("**", "").replace("<", "").replace(">", "") }
    }

    @Test fun theBookOrbitNoticeIsShownWordForWord() {
        val s = legalStrings()
        val shown = listOf(
            "legal_bookorbit_title", "legal_bookorbit_copyright", "legal_bookorbit_contributors",
            "legal_bookorbit_original", "legal_bookorbit_license"
        ).map { s.getValue(it) }
        assertEquals(termsNoticeLines(), shown)
    }

    @Test fun theModifiedVersionStatementIsShownWordForWord() {
        val terms = file("../licenses/bookorbit/ADDITIONAL_TERMS.md", "licenses/bookorbit/ADDITIONAL_TERMS.md").readText()
        val statement = terms.substringAfter("## 3. Modified Versions").lines().first { it.startsWith("> ") }.removePrefix("> ").trim()
        assertEquals(statement, legalStrings().getValue("legal_bookorbit_modified"))
    }

    @Test fun poweredByBookOrbitLinksToTheOriginalProject() {
        assertEquals("https://github.com/bookorbit/bookorbit", LegalInfo.BOOKORBIT_URL)
        assertTrue(legalStrings().getValue("legal_bookorbit_original").endsWith(LegalInfo.BOOKORBIT_URL))
    }

    @Test fun theModificationDateIsADate() {
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(LegalInfo.MODIFIED_ON))
    }

    @Test fun aCommitLinkNeedsARealHash() {
        assertEquals("https://github.com/senihdal34/DeusNotebook/commit/ab12cd3", LegalInfo.commitUrl("ab12cd3"))
        assertEquals("ab12cd3", LegalInfo.commitOrNull(" ab12cd3\n"))
        assertNull(LegalInfo.commitUrl(LegalInfo.UNKNOWN_COMMIT))
        assertNull(LegalInfo.commitUrl(""))
        assertNull(LegalInfo.commitUrl(null))
        assertNull(LegalInfo.commitUrl("not a hash"))
    }

    @Test fun repositoryFilesLinkToTheMainBranch() {
        assertEquals("https://github.com/senihdal34/DeusNotebook/blob/main/NOTICE.md", LegalInfo.blobUrl("NOTICE.md"))
        assertEquals("https://github.com/senihdal34/DeusNotebook/blob/main/licenses/GPL-3.0.txt", LegalInfo.blobUrl("/licenses/GPL-3.0.txt"))
    }
}
