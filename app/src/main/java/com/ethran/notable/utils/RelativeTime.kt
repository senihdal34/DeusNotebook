package com.ethran.notable.utils

import com.ethran.notable.R
import com.ethran.notable.i18n.AppStrings
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Date

/**
 * How old a note is, in exact calendar units: today, yesterday, "5 days ago", "1 week 2 days ago",
 * "1 month 5 days ago", "1 year 2 months ago". Zero parts are left out.
 *
 * [age] is the pure part (units and counts, no words). [format] turns it into text with a [Words] set; the
 * default [AppWords] follows the app language, a test can pass its own.
 */
object RelativeTime {
    enum class Span { Year, Month, Week, Day }

    data class Part(val span: Span, val count: Int)

    sealed interface Age {
        data object Today : Age
        data object Yesterday : Age
        data class Ago(val parts: List<Part>) : Age
    }

    /** The words of one language. [join] puts two parts together, [ago] adds the "ago" frame around them. */
    interface Words {
        fun today(): String
        fun yesterday(): String
        fun unit(span: Span, count: Int): String
        fun join(first: String, second: String): String
        fun ago(parts: String): String
    }

    /** The words in the app language (resources `rel_*`). */
    object AppWords : Words {
        override fun today() = AppStrings.get(R.string.rel_today)
        override fun yesterday() = AppStrings.get(R.string.rel_yesterday)
        override fun unit(span: Span, count: Int): String = AppStrings.plural(
            when (span) {
                Span.Year -> R.plurals.rel_year
                Span.Month -> R.plurals.rel_month
                Span.Week -> R.plurals.rel_week
                Span.Day -> R.plurals.rel_day
            }, count, count
        )
        override fun join(first: String, second: String) = AppStrings.get(R.string.rel_join, first, second)
        override fun ago(parts: String) = AppStrings.get(R.string.rel_ago, parts)
    }

    fun age(date: Date, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Age =
        age(Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate(), today)

    fun age(day: LocalDate, today: LocalDate): Age {
        if (!day.isBefore(today)) return Age.Today      // clock skew never shows "-1 day"
        val p = Period.between(day, today)
        val parts = when {
            p.years > 0 -> listOf(Part(Span.Year, p.years), Part(Span.Month, p.months))
            p.months > 0 -> listOf(Part(Span.Month, p.months), Part(Span.Day, p.days))
            p.days >= 7 -> listOf(Part(Span.Week, p.days / 7), Part(Span.Day, p.days % 7))
            p.days == 1 -> return Age.Yesterday
            else -> listOf(Part(Span.Day, p.days))
        }
        return Age.Ago(parts.filter { it.count > 0 })
    }

    fun format(age: Age, words: Words = AppWords): String = when (age) {
        Age.Today -> words.today()
        Age.Yesterday -> words.yesterday()
        is Age.Ago -> words.ago(age.parts.map { words.unit(it.span, it.count) }.reduce(words::join))
    }

    fun format(date: Date, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(), words: Words = AppWords): String =
        format(age(date, today, zone), words)
}
