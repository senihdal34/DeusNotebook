package com.ethran.notable.planner

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.w3c.dom.Element

/** The labels of the planner pill exist in all five languages. */
class PlannerNavLocalizationTest {
    private fun texts(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings_planner_nav.xml", "app/src/main/res/$folder/strings_planner_nav.xml").map(::File).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun `every language has every label`() {
        val en = texts("values")
        assertEquals(setOf("planner_nav_previous", "planner_nav_next", "planner_this_month", "planner_this_quarter", "planner_this_year"), en.keys)
        for (folder in listOf("values-tr", "values-es", "values-pl", "values-zh")) {
            val t = texts(folder)
            assertEquals(en.keys, t.keys, folder)
            for ((k, v) in t) assertFalse(v.isBlank(), "$folder: $k is empty")
        }
    }
}
