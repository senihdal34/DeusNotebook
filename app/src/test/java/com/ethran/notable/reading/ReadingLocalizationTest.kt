package com.ethran.notable.reading

import com.ethran.notable.reading.badges.BadgeCatalogue
import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.Rarity
import com.ethran.notable.reading.board.argRuns
import com.ethran.notable.reading.board.gigabytes
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The Reading texts in the five app languages: every language has every text, and dates and numbers follow the language. */
class ReadingLocalizationTest {
    init { EnglishStrings.install() }

    @After fun backToEnglish() = EnglishStrings.install()

    private class Table(val strings: Map<String, String>, val plurals: Map<String, Set<String>>)

    private fun load(folder: String): Table {
        val file = listOf("src/main/res/$folder/strings_reading.xml", "app/src/main/res/$folder/strings_reading.xml").map(::File).first { it.isFile }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Set<String>>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = e.textContent
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
                    for (k in 0 until items.length) strings[e.getAttribute("name") + "#" + (items.item(k) as Element).getAttribute("quantity")] = items.item(k).textContent
                }
            }
        }
        return Table(strings, plurals)
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]|%[sd]").findAll(text).map { it.value.replace(Regex("^%\\d\\$"), "%") }.sorted().toList()

    private val folders = mapOf("values-tr" to "tr", "values-es" to "es", "values-zh" to "zh", "values-pl" to "pl")
    private val required = mapOf("pl" to setOf("one", "few", "many", "other"), "zh" to setOf("other"), "tr" to setOf("one", "other"), "es" to setOf("one", "other"))

    @Test fun everyLanguageHasEveryTextAndPlaceholder() {
        val en = load("values")
        for ((folder, lang) in folders) {
            val t = load(folder)
            assertEquals("$folder: texts that are missing or extra", en.strings.keys.filter { '#' !in it }.toSet(), t.strings.keys.filter { '#' !in it }.toSet())
            assertEquals("$folder: plurals that are missing or extra", en.plurals.keys, t.plurals.keys)
            for (key in en.plurals.keys) assertEquals("$folder: $key forms", required.getValue(lang), t.plurals.getValue(key))
            for ((key, text) in en.strings.filter { '#' !in it.key }) {
                assertFalse("$folder: $key is empty", t.strings.getValue(key).isBlank())
                assertEquals("$folder: $key placeholders", placeholders(text), placeholders(t.strings.getValue(key)))
            }
            // The general form of each plural shows the same numbers as English ("one" may say "a day" and drop the number).
            for (key in en.plurals.keys) {
                assertEquals("$folder: $key placeholders", placeholders(en.strings.getValue("$key#other")), placeholders(t.strings.getValue("$key#other")))
            }
        }
    }

    @Test fun everyBadgeAndEveryRarityHasAnEnglishText() {
        assertEquals(56, BadgeCatalogue.ALL.size)
        for (def in BadgeCatalogue.ALL) {
            assertTrue(def.key, def.name.isNotBlank() && def.description.isNotBlank())
        }
        assertEquals(listOf("Common", "Rare", "Epic", "Legendary"), Rarity.entries.map { it.label })
        assertEquals("Ink Initiate", BadgeCatalogue.get("books_finished_1")!!.name)
        assertEquals("Read 5,000 pages", BadgeCatalogue.get("pages_read_2")!!.description)
    }

    @Test fun everyWordOfTheDayHasAMeaning() {
        assertEquals(30, ReadingWords.WORDS.size)
        for (w in ReadingWords.WORDS) assertTrue(w.word, w.meaning.isNotBlank())
        assertEquals("the highest point", ReadingWords.WORDS.single { it.word == "zenith" }.meaning)
    }

    /** English text with another locale, for dates and numbers. */
    private fun speak(tag: String) {
        val locale = Locale.forLanguageTag(tag)
        RS.resolver = object : TextResolver by EnglishStrings {
            override val locale: Locale = locale
        }
    }

    @Test fun monthsAndWeekdaysFollowTheLanguage() {
        val saturday = LocalDate.of(2026, 11, 7)
        assertEquals("Saturday", ReadingDates.weekday(saturday.dayOfWeek, full = true))
        assertEquals("Nov", ReadingDates.month(Month.NOVEMBER))
        assertEquals("S", ReadingDates.weekdayInitial(saturday.dayOfWeek))
        speak("tr")
        assertEquals("Cumartesi", ReadingDates.weekday(saturday.dayOfWeek, full = true))
        assertEquals("Kasım", ReadingDates.month(Month.NOVEMBER, full = true))
        speak("es")
        assertEquals("sábado", ReadingDates.weekday(saturday.dayOfWeek, full = true))
        assertEquals("noviembre", ReadingDates.month(Month.NOVEMBER, full = true))
        speak("zh")
        assertEquals("11月", ReadingDates.month(Month.NOVEMBER))
        assertEquals("11月", ReadingDates.month(Month.NOVEMBER, full = true))
        assertEquals("11月", ReadingDates.monthAlone(Month.NOVEMBER))
        speak("pl")
        // Next to a day number Polish uses the genitive ("7 listopada"); alone it is "listopad".
        assertEquals("listopada", ReadingDates.month(Month.NOVEMBER, full = true))
        assertEquals("listopad", ReadingDates.monthAlone(Month.NOVEMBER))
    }

    @Test fun numbersAreGroupedForTheLanguage() {
        assertEquals("1,234", RS.count(1234))
        speak("tr")
        assertEquals("1.234", RS.count(1234))
        speak("zh")
        assertEquals("1,234", RS.count(1234))
    }

    @Test fun freeStorageUsesTheLocalesDecimalSign() {
        assertEquals("12.3", gigabytes(12.34))
        assertEquals("12", gigabytes(12.0))
        assertEquals("0.5", gigabytes(0.5))
        speak("tr")
        assertEquals("12,3", gigabytes(12.34))
        assertEquals("12", gigabytes(12.0))
        speak("pl")
        assertEquals("1,5", gigabytes(1.5))
    }

    @Test fun anEarnedDayHasALongAndAShortForm() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 11, 4).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli() + 1_000L
        val today = LocalDate.of(2026, 11, 4)
        assertEquals("Earned today", BadgeEngine.earnedLabel(at(today), now, zone))
        assertEquals("today", BadgeEngine.earnedDayLabel(at(today), now, zone))
        assertEquals("Earned yesterday", BadgeEngine.earnedLabel(at(today.minusDays(1)), now, zone))
        assertEquals("yesterday", BadgeEngine.earnedDayLabel(at(today.minusDays(1)), now, zone))
        assertEquals("Earned Sunday", BadgeEngine.earnedLabel(at(today.minusDays(3)), now, zone))
        assertEquals("Sunday", BadgeEngine.earnedDayLabel(at(today.minusDays(3)), now, zone))
        assertEquals("Earned 12 Oct", BadgeEngine.earnedLabel(at(LocalDate.of(2026, 10, 12)), now, zone))
        assertEquals("12 Oct", BadgeEngine.earnedDayLabel(at(LocalDate.of(2026, 10, 12)), now, zone))
        assertEquals(null, BadgeEngine.earnedDayLabel(null, now, zone))
    }

    @Test fun aSentenceKeepsItsBoldPartWhereverTheLanguagePutsIt() {
        val runs = argRuns("End of book in 3 h 20 min", "3 h 20 min", androidx.compose.ui.graphics.Color.Black)
        assertEquals(listOf("End of book in ", "3 h 20 min"), runs.map { it.t })
        assertEquals(listOf(false, true), runs.map { it.b })
        val middle = argRuns("Kitabın sonuna 3 sa 20 dk var", "3 sa 20 dk", androidx.compose.ui.graphics.Color.Black)
        assertEquals(listOf("Kitabın sonuna ", "3 sa 20 dk", " var"), middle.map { it.t })
        assertEquals(listOf("no number here"), argRuns("no number here", "42", androidx.compose.ui.graphics.Color.Black).map { it.t })
        assertEquals(listOf("x"), argRuns("x", "", androidx.compose.ui.graphics.Color.Black).map { it.t })
    }

    @Test fun dayMonthYearOrderFollowsThePatternOfTheLanguage() {
        val d = LocalDate.of(2026, 5, 6)
        assertEquals("6 May 2026", ReadingDates.dayMonthYear(d))
        assertEquals("Wed, 6 May 2026", ReadingDates.weekdayDayMonthYear(d))
        assertEquals("Wednesday, 6 May", ReadingDates.weekdayDayMonth(d, fullWeekday = true))
        assertEquals("Wed 6 May", ReadingDates.weekdayDayMonthPlain(d))
        assertEquals("Wed 6", ReadingDates.weekdayDay(d))
        assertEquals("May 2026", ReadingDates.monthYear(Month.MAY, 2026))
    }
}
