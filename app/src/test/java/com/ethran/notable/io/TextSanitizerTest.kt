package com.ethran.notable.io

import org.junit.Test
import java.nio.charset.Charset
import java.text.Normalizer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Broken-character cases seen in handwriting recognition output, file names and excerpts. */
class TextSanitizerTest {

    private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)

    /** What a reader that treats UTF-8 bytes as the given single-byte charset produces. */
    private fun mojibake(s: String, charset: String) =
        String(s.toByteArray(Charsets.UTF_8), Charset.forName(charset))

    // --- (a) decomposed Unicode ---

    @Test
    fun `decomposed cedilla becomes ş`() {
        assertEquals("ş", TextSanitizer.clean("ş"))
        assertEquals("Ş", TextSanitizer.clean("Ş"))
    }

    @Test
    fun `capital I with dot above becomes İ`() {
        assertEquals("İstanbul", TextSanitizer.clean("İstanbul"))
    }

    @Test
    fun `Boox file name listed decomposed comes out composed`() {
        val decomposed = nfd("Eşitlikçilik")
        assertTrue(decomposed.length > "Eşitlikçilik".length)
        assertEquals("Eşitlikçilik", TextSanitizer.cleanLine(decomposed))
        assertEquals("Eşitlikçilik", TextSanitizer.tagFromName(decomposed))
    }

    @Test
    fun `all Turkish letters survive decomposition round trip`() {
        val letters = "İıŞşĞğÇçÖöÜü"
        assertEquals(letters, TextSanitizer.clean(nfd(letters)))
        assertEquals(letters, TextSanitizer.clean(letters))
    }

    @Test
    fun `dotted lower i left by root-locale lowercasing is repaired`() {
        val lowered = "İSTANBUL ve Işık".lowercase() // "i̇stanbul ve işık" with a stray U+0307
        assertTrue(lowered.contains('̇'))
        assertEquals("istanbul ve işık", TextSanitizer.cleanLine(lowered))
    }

    @Test
    fun `case is never changed`() {
        assertEquals("ISPARTA ışık İzmir", TextSanitizer.clean("ISPARTA ışık İzmir"))
    }

    // --- (b) replacement chars, control, invisible, private use, mojibake ---

    @Test
    fun `replacement char and control characters are removed`() {
        assertEquals("merhaba dünya", TextSanitizer.clean("mer�haba\u0000 d\u0007ünya"))
    }

    @Test
    fun `zero width and bidi marks are removed`() {
        val dirty = "a​b‌‎‏c‪d‮e⁦f⁩g﻿h­i⁠"
        assertEquals("abcdefghi", TextSanitizer.clean(dirty))
    }

    @Test
    fun `invisible char between base and combining mark does not block composition`() {
        assertEquals("ş", TextSanitizer.clean("s​̧"))
    }

    @Test
    fun `private use glyphs are removed`() {
        assertEquals("not defteri", TextSanitizer.clean("not defteri󰀀"))
    }

    @Test
    fun `unpaired surrogates are removed`() {
        assertEquals("ab", TextSanitizer.clean("a\uD83Db\uDE00"))
    }

    @Test
    fun `utf8 read as latin1 is repaired`() {
        val original = "Çağrı için şarkı söyle, öğrenci üzüm İzmir"
        val broken = mojibake(original, "ISO-8859-1")
        assertTrue(broken.contains("Ã") || broken.contains("Ä"))
        assertEquals(original, TextSanitizer.clean(broken))
    }

    @Test
    fun `utf8 read as cp1252 is repaired`() {
        val original = "Eşitlikçilik ğ Ğ ş Ş ı İ ç Ç ö Ö ü Ü"
        val broken = mojibake(original, "windows-1252")
        assertEquals(original, TextSanitizer.clean(broken))
    }

    @Test
    fun `typical mojibake pairs`() {
        assertEquals("ç", TextSanitizer.clean("Ã§"))
        assertEquals("ı", TextSanitizer.clean("Ä±"))
        assertEquals("İ", TextSanitizer.clean("Ä°"))
        assertEquals("ü", TextSanitizer.clean("Ã¼"))
        assertEquals("ö", TextSanitizer.clean("Ã¶"))
        assertEquals("ğ", TextSanitizer.clean("ÄŸ"))
        assertEquals("ş", TextSanitizer.clean("ÅŸ"))
    }

    @Test
    fun `mojibake of typographic punctuation is repaired`() {
        assertEquals("o’nun “evi – tamam… ‘x’", TextSanitizer.clean(mojibake("o’nun “evi – tamam… ‘x’", "windows-1252")))
    }

    @Test
    fun `mojibake inside a wiki link and next to a tag is repaired`() {
        val broken = mojibake("[[Eşitlikçilik]] #günlük", "windows-1252")
        assertEquals("[[Eşitlikçilik]] #günlük", TextSanitizer.clean(broken))
    }

    @Test
    fun `lookalike text that is not mojibake is left alone`() {
        // "Ã" followed by an ordinary letter does not form valid UTF-8, so nothing is "repaired".
        assertEquals("Ãbc Äx", TextSanitizer.clean("Ãbc Äx"))
        // Valid UTF-8 but not Latin text (a CJK character): not repaired either.
        assertFalse(TextSanitizer.clean(mojibake("日", "ISO-8859-1")).contains('日'))
    }

    @Test
    fun `correct Turkish text containing similar looking letters is unchanged`() {
        val text = "Ağrı Dağı'nda çok güzel bir gün: ÇĞİÖŞÜ çğıöşü"
        assertEquals(text, TextSanitizer.clean(text))
    }

    // --- (c) spacing and line-break artefacts ---

    @Test
    fun `odd spaces are collapsed`() {
        assertEquals("bir iki  üç".replace("  ", " "), TextSanitizer.clean("bir  iki  \t　üç"))
    }

    @Test
    fun `line structure is kept but stray whitespace around it is not`() {
        val dirty = "  birinci satır  \r\n   \r\n\r\n\r\n\r\nikinci üçüncü\u0085dördüncü \n"
        assertEquals("birinci satır\n\nikinci\nüçüncü\ndördüncü", TextSanitizer.clean(dirty).trimStart())
    }

    @Test
    fun `markdown indentation is preserved`() {
        val md = "- madde\n  - alt madde\n\n```\nkod\n```"
        assertEquals(md, TextSanitizer.clean(md))
    }

    @Test
    fun `cleanLine flattens newlines`() {
        assertEquals("Başlık metni", TextSanitizer.cleanLine("  Başlık\n​metni \n"))
    }

    @Test
    fun `orphan combining marks are dropped`() {
        assertEquals("a b", TextSanitizer.clean("a ̧b".replace("b", " b")).replace("  ", " "))
        assertEquals("kelime", TextSanitizer.clean("́kelime"))
    }

    @Test
    fun `null and blank input`() {
        assertEquals("", TextSanitizer.clean(null))
        assertEquals("", TextSanitizer.clean(""))
        assertEquals("", TextSanitizer.clean(" ​\n\t "))
        assertEquals("", TextSanitizer.cleanLine(null))
    }

    // --- things that must survive ---

    @Test
    fun `wiki links hashtags and markdown survive`() {
        val text = "# Başlık\n\n**kalın** _italik_ `kod` [[Eşitlikçilik]] #günlük-not [bağlantı](https://örnek.com/a_b)\n> alıntı"
        assertEquals(text, TextSanitizer.clean(text))
        assertEquals(text, TextSanitizer.clean(nfd(text)))
    }

    @Test
    fun `real emoji survive including sequences`() {
        val emoji = "tamam 👍 ❤️ 👨‍👩‍👧 🇹🇷 👍🏽 1️⃣ ✅ 🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        assertEquals(emoji, TextSanitizer.clean(emoji))
    }

    @Test
    fun `stray joiners and variation selectors outside emoji are removed`() {
        assertEquals("ab c", TextSanitizer.clean("a‍b ️c"))
    }

    @Test
    fun `emoji next to dirt stay while dirt goes`() {
        assertEquals("şu 👍 iyi", TextSanitizer.clean("şu​ 👍� i‏yi"))
    }

    // --- tag names ---

    @Test
    fun `tagFromName builds a hashtag body`() {
        assertEquals("Eşitlikçilik", TextSanitizer.tagFromName(nfd("Eşitlikçilik")))
        assertEquals("toplantı-notları", TextSanitizer.tagFromName("#toplantı  notları​"))
        assertEquals("", TextSanitizer.tagFromName(null))
    }

    // --- idempotence ---

    @Test
    fun `clean and cleanLine are idempotent`() {
        val samples = listOf(
            "ş İ �​  x\r\ny z",
            mojibake("Çağrı İçin Şarkı [[Eşitlikçilik]] #etiket", "windows-1252"),
            mojibake("Çağrı İçin Şarkı", "ISO-8859-1"),
            "i̇stanbul",
            "👍 ❤️ 👨‍👩‍👧 ‍️",
            "  - madde\n    - alt\n\n\n\nson ",
            "Ã\u0083Â§",
            nfd("Eşitlikçilik Ğ Ö"),
            ""
        )
        for (s in samples) {
            val once = TextSanitizer.clean(s)
            assertEquals(once, TextSanitizer.clean(once), "clean not idempotent for ${s.take(30)}")
            val line = TextSanitizer.cleanLine(s)
            assertEquals(line, TextSanitizer.cleanLine(line), "cleanLine not idempotent for ${s.take(30)}")
        }
    }

    // --- wiring through the export code ---

    @Test
    fun `markdown export cleans body and tags but keeps format`() {
        val md = InboxSyncEngine.generateMarkdown(
            createdDate = "2025-03-15",
            tags = listOf(nfd("Eşitlikçilik"), "​"),
            content = "İstanbul notu � [[Eşitlikçilik]] #günlük",
            baseName = "x"
        )
        assertTrue(md.contains("  - Eşitlikçilik\n"))
        assertFalse(md.contains("​"))
        assertTrue(md.contains("\nİstanbul notu [[Eşitlikçilik]] #günlük\n"))
        assertEquals(1, md.lines().count { it.startsWith("  - ") })
    }

    @Test
    fun `file names are composed before characters are replaced`() {
        assertEquals("Eşitlikçilik notu", InboxSyncEngine.sanitizeFileName(nfd("Eşitlikçilik") + "  notu"))
    }
}
