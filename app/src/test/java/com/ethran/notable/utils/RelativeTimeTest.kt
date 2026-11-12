package com.ethran.notable.utils

import com.ethran.notable.utils.RelativeTime.Age
import com.ethran.notable.utils.RelativeTime.Part
import com.ethran.notable.utils.RelativeTime.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RelativeTimeTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun age(y: Int, m: Int, d: Int) = RelativeTime.age(LocalDate.of(y, m, d), today)
    private fun ago(vararg parts: Pair<Span, Int>) = Age.Ago(parts.map { Part(it.first, it.second) })

    /** English words, the way the English resources read; the app resolves the same pieces from `rel_*` resources. */
    private object English : RelativeTime.Words {
        override fun today() = "today"
        override fun yesterday() = "yesterday"
        override fun unit(span: Span, count: Int): String {
            val name = when (span) { Span.Year -> "year"; Span.Month -> "month"; Span.Week -> "week"; Span.Day -> "day" }
            return "$count $name" + if (count == 1) "" else "s"
        }
        override fun join(first: String, second: String) = "$first $second"
        override fun ago(parts: String) = "$parts ago"
    }

    private fun text(y: Int, m: Int, d: Int) = RelativeTime.format(age(y, m, d), English)

    @Test fun today_and_yesterday() {
        assertEquals(Age.Today, age(2026, 10, 30))
        assertEquals(Age.Yesterday, age(2026, 10, 29))
        assertEquals(Age.Today, age(2026, 10, 31))   // clock skew
    }

    @Test fun days_and_weeks() {
        assertEquals(ago(Span.Day to 2), age(2026, 10, 28))
        assertEquals(ago(Span.Day to 6), age(2026, 10, 24))
        assertEquals(ago(Span.Week to 1), age(2026, 10, 23))
        assertEquals(ago(Span.Week to 1, Span.Day to 2), age(2026, 10, 21))
        assertEquals(ago(Span.Week to 3, Span.Day to 6), age(2026, 10, 3))
    }

    @Test fun months_with_exact_days() {
        assertEquals(ago(Span.Month to 1), age(2026, 9, 30))
        assertEquals(ago(Span.Month to 1, Span.Day to 5), age(2026, 9, 25))
        assertEquals(ago(Span.Month to 1, Span.Day to 16), age(2026, 9, 14))
        assertEquals(ago(Span.Month to 6, Span.Day to 2), age(2026, 4, 28))
    }

    @Test fun years() {
        assertEquals(ago(Span.Year to 1), age(2025, 10, 30))
        assertEquals(ago(Span.Year to 1, Span.Month to 2), age(2025, 8, 30))
        assertEquals(ago(Span.Year to 2, Span.Month to 3), age(2024, 7, 15))
    }

    @Test fun english_text() {
        assertEquals("today", text(2026, 10, 30))
        assertEquals("yesterday", text(2026, 10, 29))
        assertEquals("2 days ago", text(2026, 10, 28))
        assertEquals("1 week ago", text(2026, 10, 23))
        assertEquals("1 week 2 days ago", text(2026, 10, 21))
        assertEquals("1 month 5 days ago", text(2026, 9, 25))
        assertEquals("6 months 2 days ago", text(2026, 4, 28))
        assertEquals("1 year ago", text(2025, 10, 30))
        assertEquals("2 years 3 months ago", text(2024, 7, 15))
    }
}
