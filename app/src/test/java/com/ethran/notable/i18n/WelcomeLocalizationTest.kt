package com.ethran.notable.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

/** The welcome page texts exist in all five languages with the same placeholders, and none hard-codes the app name. */
class WelcomeLocalizationTest {
    private fun welcomeStrings(folder: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (name in listOf("strings_welcome.xml", "strings_sync.xml")) {
            val file = listOf("src/main/res/$folder/$name", "app/src/main/res/$folder/$name").map(::File).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                if (e.tagName == "string" && e.getAttribute("name").startsWith("welcome_")) result[e.getAttribute("name")] = e.textContent
            }
        }
        return result
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun everyLanguageHasEveryWelcomeTextWithTheSamePlaceholders() {
        val en = welcomeStrings("values")
        for (folder in listOf("values-tr", "values-es", "values-pl", "values-zh")) {
            val t = welcomeStrings(folder)
            assertEquals("$folder: missing or extra texts", en.keys, t.keys)
            for ((key, text) in en) {
                assertFalse("$folder: $key is empty", t.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.getValue(key)))
            }
        }
    }

    @Test fun noWelcomeTextNamesTheAppByHand() {
        for (folder in listOf("values", "values-tr", "values-es", "values-pl", "values-zh")) {
            for ((key, text) in welcomeStrings(folder)) {
                assertFalse("$folder: $key", text.contains("DeusNote", ignoreCase = true))
            }
        }
    }
}
