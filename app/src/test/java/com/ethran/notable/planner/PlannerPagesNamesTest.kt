package com.ethran.notable.planner

import com.ethran.notable.i18n.AppLanguage
import java.io.File
import java.time.YearMonth
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PlannerPagesNamesTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun `titles in every language`() {
        assertEquals("November 2026", PlannerNames.monthTitle(nov, AppLanguage.English))
        assertEquals("Kasım 2026", PlannerNames.monthTitle(nov, AppLanguage.Turkish))
        assertEquals("Noviembre 2026", PlannerNames.monthTitle(nov, AppLanguage.Spanish))
        assertEquals("Listopad 2026", PlannerNames.monthTitle(nov, AppLanguage.Polish))
        assertEquals("2026年11月", PlannerNames.monthTitle(nov, AppLanguage.Chinese))
        assertEquals("Q4 2026", PlannerNames.quarterTitle(2026, 4, AppLanguage.English))
        assertEquals("Q4 2026", PlannerNames.quarterTitle(2026, 4, AppLanguage.Turkish))
        assertEquals("2026年第4季度", PlannerNames.quarterTitle(2026, 4, AppLanguage.Chinese))
        assertEquals("2026", PlannerNames.yearTitle(2026))
    }

    @Test fun `short month names for the year page`() {
        assertEquals("Nov", PlannerNames.monthShort(nov, AppLanguage.English.locale))
        assertEquals("Kas", PlannerNames.monthShort(nov, AppLanguage.Turkish.locale))
        assertEquals("11月", PlannerNames.monthShort(nov, AppLanguage.Chinese.locale))
        assertFalse(PlannerNames.monthShort(YearMonth.of(2026, 5), AppLanguage.Spanish.locale).endsWith("."))
    }

    @Test fun `a generated title in any language is renamed to the current language`() {
        val bgs = listOf(PlannerWeeks.monthBackground(nov), PlannerWeeks.quarterBackground(2026, 4), PlannerWeeks.yearBackground(2026))
        for (bg in bgs) {
            val titles = AppLanguage.entries.map { lang ->
                when {
                    bg.startsWith(PlannerWeeks.MONTH_PREFIX) -> PlannerNames.monthTitle(nov, lang)
                    bg.startsWith(PlannerWeeks.QUARTER_PREFIX) -> PlannerNames.quarterTitle(2026, 4, lang)
                    else -> PlannerNames.yearTitle(2026)
                }
            }
            for (to in AppLanguage.entries) {
                val wanted = titles[AppLanguage.entries.indexOf(to)]
                for (from in titles) {
                    val result = PlannerNames.renamed(bg, from, to)
                    if (from == wanted) assertNull("$bg $from -> ${to.tag}", result) else assertEquals("$bg $from -> ${to.tag}", wanted, result)
                }
                assertEquals("blank title of $bg", wanted, PlannerNames.renamed(bg, "", to))
            }
        }
    }

    @Test fun `a title the user typed is kept`() {
        assertNull(PlannerNames.renamed(PlannerWeeks.monthBackground(nov), "Launch month", AppLanguage.Turkish))
        assertNull(PlannerNames.renamed(PlannerWeeks.quarterBackground(2026, 4), "Planım", AppLanguage.English))
        assertNull(PlannerNames.renamed(PlannerWeeks.yearBackground(2026), "My year", AppLanguage.Polish))
    }

    @Test fun `unknown planner pages are never renamed`() {
        assertNull(PlannerNames.renamed("planner-month:broken", "x", AppLanguage.English))
        assertNull(PlannerNames.renamed("planner-future:1", "", AppLanguage.English))
    }

    @Test fun `week and day renaming still works`() {
        val monday = java.time.LocalDate.of(2026, 10, 26)
        assertEquals(PlannerNames.dayTitle(monday, AppLanguage.Turkish), PlannerNames.renamed(PlannerWeeks.dayBackground(monday), PlannerNames.dayTitle(monday, AppLanguage.English), AppLanguage.Turkish))
    }

    // ---- the new string files -----------------------------------------------------------------------------------------

    private fun strings(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings_planner_pages.xml", "app/src/main/res/$folder/strings_planner_pages.xml").map(::File).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        val result = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.tagName == "string") result[e.getAttribute("name")] = e.textContent
        }
        return result
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test fun `every language has every planner page text with the same placeholders`() {
        val en = strings("values")
        assertTrue(en.size >= 10)
        for (folder in listOf("values-tr", "values-es", "values-pl", "values-zh")) {
            val t = strings(folder)
            assertEquals("$folder: missing or extra texts", en.keys, t.keys)
            for ((key, text) in en) {
                assertFalse("$folder: $key is empty", t.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.getValue(key)))
            }
        }
    }

    @Test fun `the three notebook names are different in every language and from the week notebook`() {
        for (folder in listOf("values", "values-tr", "values-es", "values-pl", "values-zh")) {
            val s = strings(folder)
            val names = listOf("planner_months_notebook", "planner_quarters_notebook", "planner_years_notebook").map { s.getValue(it) }
            assertEquals(folder, 3, names.toSet().size)
        }
    }
}
