package com.ethran.notable.ui.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The All notes texts exist in all five languages with the same placeholders. */
class HomeAllNotesLocalizationTest {
    private val folders = listOf("values", "values-tr", "values-es", "values-pl", "values-zh")

    private fun strings(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings_home_all.xml", "app/src/main/res/$folder/strings_home_all.xml").map(::File).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        val result = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { if (it.tagName == "string") result[it.getAttribute("name")] = it.textContent }
        return result
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun sameKeysAndPlaceholdersEverywhere() {
        val en = strings("values")
        assertTrue(en.size >= 9)
        for (folder in folders.drop(1)) {
            val t = strings(folder)
            assertEquals("$folder keys", en.keys, t.keys)
            for ((key, text) in en) {
                assertFalse("$folder: $key empty", t.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.getValue(key)))
            }
        }
    }
}
