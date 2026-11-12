package com.ethran.notable.i18n

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test fun `english is the default for both languages`() {
        assertEquals(AppLanguage.English, AppLanguage.Default)
        assertEquals(AppLanguage.English, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("xx"))
        assertEquals("en_US", WritingLanguage.DEFAULT_CODE)
        assertEquals("en_US", WritingLanguage.fromCode(null).code)
        assertEquals("en_US", WritingLanguage.fromCode("nope").code)
    }

    @Test fun `app languages are English Turkish Polish Spanish and Chinese`() {
        assertEquals(listOf("en", "tr", "pl", "es", "zh"), AppLanguage.entries.map { it.tag })
        assertEquals(AppLanguage.Turkish, AppLanguage.fromTag("tr"))
        assertEquals("zh", AppLanguage.Chinese.locale.language)
        assertEquals("CN", AppLanguage.Chinese.locale.country)
    }

    @Test fun `writing languages have unique codes and include Turkish`() {
        val codes = WritingLanguage.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals("Türkçe", WritingLanguage.fromCode("tr_TR").nativeName)
        assertTrue(codes.containsAll(listOf("en_US", "tr_TR", "es_ES", "zh_CN", "pl_PL")))
    }
}

class WritingLanguageAvailabilityTest {
    @Test fun `a language without resources falls back to English`() {
        val installed = setOf("en_US", "zh_CN", "tr_TR", "mul")
        assertEquals("tr_TR", WritingLanguageAvailability.effective("tr_TR", installed))
        assertEquals("en_US", WritingLanguageAvailability.effective("es_ES", installed))
        assertTrue(WritingLanguageAvailability.isAvailable("zh_CN", installed))
        assertTrue(!WritingLanguageAvailability.isAvailable("pl_PL", installed))
    }

    @Test fun `unreadable resource folders assume every language works`() {
        assertEquals("es_ES", WritingLanguageAvailability.effective("es_ES", null))
    }

    @Test fun `folders on the tablet are scanned`() {
        val root = kotlin.io.path.createTempDirectory("res").toFile()
        val system = java.io.File(root, "system").apply { java.io.File(this, "en_US").mkdirs() }
        val downloaded = java.io.File(root, ".noteResource")
        java.io.File(downloaded, "2-3/recognition-assets/resources/tr_TR").mkdirs()
        java.io.File(downloaded, "additional_res/pl_PL").mkdirs()   // extra words, not a recognizer language
        assertEquals(setOf("en_US", "tr_TR"), WritingLanguageAvailability.installed(system, downloaded))
        assertEquals(null, WritingLanguageAvailability.installed(java.io.File(root, "none"), java.io.File(root, "none2")))
        root.deleteRecursively()
    }
}
