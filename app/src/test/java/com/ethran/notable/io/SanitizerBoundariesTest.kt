package com.ethran.notable.io

import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The sanitizer wired into the real boundaries: excerpts, Boox names, glossary, corrector, vault tags. */
class SanitizerBoundariesTest {
    private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
    private fun md(body: String) =
        "---\ncreated: \"[[2026-10-12]]\"\npages: 1\nsource: aragonite\n---\n\n$body\n\n---\n![page 1](jpg_archive/x.jpg)\n"

    // --- ExportedExcerpts ---

    @Test
    fun `excerpt drops replacement chars and zero-width marks`() {
        val body = "Başlık\nbu​ not� çok  güzel‏\nikinci‪ satır"
        assertEquals("bu not çok güzel ikinci satır", ExportedExcerpts.excerptFrom(md(body)).replace("  ", " "))
    }

    @Test
    fun `excerpt composes decomposed Turkish letters`() {
        val excerpt = ExportedExcerpts.excerptFrom(md("Başlık\n" + nfd("Eşitlikçilik Doğaya İstanbul")))
        assertEquals("Eşitlikçilik Doğaya İstanbul", excerpt)
    }

    @Test
    fun `excerpt repairs mojibake and unwraps wiki links`() {
        val broken = String("Çağrı [[Doğaya]]".toByteArray(Charsets.UTF_8), charset("windows-1252"))
        val excerpt = ExportedExcerpts.excerptFrom(md("Başlık\n$broken"))
        assertEquals("Çağrı Doğaya", excerpt)
    }

    @Test
    fun `excerpt keeps real emoji`() {
        assertEquals("tamam 👍 ❤️", ExportedExcerpts.excerptFrom(md("Başlık\ntamam 👍\u200B ❤️")))
    }

    @Test
    fun `cut excerpt never ends in half an emoji`() {
        val body = "Başlık\n" + "a".repeat(169) + "😀 son"
        val excerpt = ExportedExcerpts.excerptFrom(md(body))
        assertTrue(excerpt.endsWith("…"))
        // a lone surrogate would not survive a UTF-8 round trip
        assertEquals(excerpt, String(excerpt.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    // --- BooxNoteNames ---

    @Test
    fun `Boox note names become NFC hashtags`() {
        assertEquals("Eşitlikçilik-Doğaya", BooxNoteNames.tag(nfd("Eşitlikçilik Doğaya")))
        assertEquals("Eşitlikçilik-Doğaya", BooxNoteNames.tag("Eşitlikçilik Doğaya"))
        assertEquals("İstanbul-notları", BooxNoteNames.tag("İstanbul​ notları"))
        assertEquals(null, BooxNoteNames.tag("​ �"))
        assertEquals(null, BooxNoteNames.tag("#"))
    }

    @Test
    fun `decomposed and composed names are listed once`() {
        val root = Files.createTempDirectory("note").toFile()
        File(root, nfd("Eşitlikçilik Doğaya")).mkdirs()
        File(root, "Eşitlikçilik Doğaya.note").writeText("x")
        val tags = BooxNoteNames.scan(root)
        assertEquals(listOf("Eşitlikçilik-Doğaya"), tags)
    }

    // --- HwrGlossaryLoader / HwrGlossary / HwrWordCorrector ---

    @Test
    fun `glossary words from file lines, note names and tags are NFC`() {
        val d = Files.createTempDirectory("glossary").toFile()
        File(d, "glossary.txt").writeText(nfd("Eşitlikçilik") + "​  Doğaya�\n")
        File(d, nfd("Çalışkanlık-notu-2026-10-12-10-00-00") + ".md")
            .writeText("---\ntags:\n  - " + nfd("Yüzleşme") + "\n---\n")
        val g = HwrGlossaryLoader.load(d)
        assertEquals("Eşitlikçilik", g.exact("Esitlikcilik"))
        assertEquals("Doğaya", g.exact("dogaya"))
        assertEquals("Çalışkanlık", g.exact("caliskanlik"))
        assertEquals("Yüzleşme", g.exact("yuzlesme"))
    }

    @Test
    fun `glossary canonical spelling is composed even from decomposed words`() {
        val g = HwrGlossary(listOf(nfd("Şirketler")))
        assertEquals("Şirketler", g.exact("sirketler"))
    }

    @Test
    fun `corrected word keeps composed glossary spelling`() {
        val g = HwrGlossary(listOf(nfd("Eşitlikçilik")))
        assertEquals("Eşitlikçilik", HwrWordCorrector.correctWord(HwrWord("Esitlikcilik"), g))
    }

    // --- VaultTagScanner / markdown tags ---

    @Test
    fun `vault tags from front matter are composed and lowercased without stray dots`() {
        val d = Files.createTempDirectory("vault").toFile()
        File(d, "a.md").writeText("---\ntags:\n  - " + nfd("İstanbul") + "\n  - Eşitlikçilik​\n---\nx\n")
        val tags = VaultTagScanner.scanTags(d.absolutePath)
        assertTrue("istanbul" in tags, "got $tags")
        assertTrue("eşitlikçilik" in tags, "got $tags")
        assertTrue(tags.none { it.contains('̇') || it.contains('​') })
        assertNotNull(tags.firstOrNull())
    }
}
