package com.ethran.notable.activity

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

/** The Writing activity texts exist in all five languages with the same placeholders. */
class ActivityLocalizationTest {
    /** name (plurals as "name/quantity") to text. */
    private fun texts(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings_activity.xml", "app/src/main/res/$folder/strings_activity.xml").map(::File).first { it.isFile }
        val out = LinkedHashMap<String, String>()
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> out[e.getAttribute("name")] = e.textContent
                "plurals" -> for (j in 0 until e.childNodes.length) {
                    val item = e.childNodes.item(j) as? Element ?: continue
                    out[e.getAttribute("name") + "/" + item.getAttribute("quantity")] = item.textContent
                }
            }
        }
        return out
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun everyLanguageHasEveryTextWithTheSamePlaceholders() {
        val en = texts("values").filterKeys { !it.contains('/') }
        val enSubtitle = placeholders(texts("values").getValue("activity_subtitle/other"))
        for (folder in listOf("values-tr", "values-es", "values-pl", "values-zh")) {
            val t = texts(folder)
            assertEquals("$folder: missing or extra texts", en.keys, t.filterKeys { !it.contains('/') }.keys)
            for ((key, text) in en) {
                assertFalse("$folder: $key is empty", t.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.getValue(key)))
            }
            val forms = t.filterKeys { it.startsWith("activity_subtitle/") }
            assertFalse("$folder: no subtitle forms", forms.isEmpty())
            for ((key, text) in forms) assertEquals("$folder: $key placeholders", enSubtitle, placeholders(text))
        }
    }

    @Test fun polishHasEveryPluralForm() {
        assertEquals(setOf("one", "few", "many", "other"), texts("values-pl").keys.filter { it.startsWith("activity_subtitle/") }.map { it.substringAfter('/') }.toSet())
    }
}
