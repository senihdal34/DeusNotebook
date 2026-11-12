package com.ethran.notable.editor.rail

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Every text of the tool rail exists in all five languages, with the same placeholders. */
class ToolbarLocalizationTest {
    private val folders = listOf("values", "values-tr", "values-es", "values-pl", "values-zh")

    private fun strings(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings_toolbar.xml", "app/src/main/res/$folder/strings_toolbar.xml").map(::File).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        val result = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.tagName == "string") result[e.getAttribute("name")] = e.textContent
        }
        return result
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    /** Labels shown by a long press on a rail button (also their accessibility descriptions). */
    private val tooltipKeys = listOf(
        "rail_back", "rail_pen", "rail_eraser", "rail_highlighter", "rail_lasso", "rail_line", "rail_undo", "rail_redo",
        "rail_page_of", "rail_pages", "rail_prev_page", "rail_next_page", "rail_new_page",
        "rail_favorite_add", "rail_favorite_remove", "rail_more", "rail_show",
    )

    @Test fun everyTooltipLabelExistsInAllFiveLocales() {
        for (folder in folders) {
            val t = strings(folder)
            for (key in tooltipKeys) assertTrue("$folder: $key missing", t[key]?.isNotBlank() == true)
        }
    }

    @Test fun allLocalesHaveTheSameKeysAndPlaceholders() {
        val en = strings("values")
        for (folder in folders.drop(1)) {
            val t = strings(folder)
            assertEquals("$folder: missing or extra texts", en.keys, t.keys)
            for ((key, text) in en) {
                assertFalse("$folder: $key is empty", t.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.getValue(key)))
            }
        }
    }

    @Test fun settingsTextsExist() {
        for (folder in folders) {
            val t = strings(folder)
            for (key in listOf("settings_toolbar_side", "settings_toolbar_side_left", "settings_toolbar_side_right", "settings_toolbar_auto_hide")) {
                assertTrue("$folder: $key", t[key]?.isNotBlank() == true)
            }
        }
    }

    @Test fun translationsAreRealTranslations() {
        val en = strings("values")
        for (folder in folders.drop(1)) {
            val t = strings(folder)
            // "Undo" and "Pen"-like words may match in a language by chance; the long texts must not.
            for (key in listOf("rail_more_reset_view", "settings_toolbar_auto_hide", "rail_new_page")) {
                assertFalse("$folder: $key is still English", t.getValue(key) == en.getValue(key))
            }
        }
    }
}
