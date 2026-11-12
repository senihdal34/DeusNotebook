package com.ethran.notable.reading

import com.ethran.notable.R
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Small text helpers of the board, kept apart from the UI so they are unit tested. */
object ReadingText {
    fun greeting(hour: Int, name: String): String {
        val part = RS.get(when (hour) {
            in 5..11 -> R.string.reading_greeting_morning
            in 12..16 -> R.string.reading_greeting_afternoon
            in 17..21 -> R.string.reading_greeting_evening
            else -> R.string.reading_greeting_night
        })
        return if (name.isBlank()) part else RS.get(R.string.reading_greeting_name, part, name)
    }

    /** "<1m", "45m", "1h 20m", "2h". */
    fun minutes(m: Int): String = when {
        m <= 0 -> RS.get(R.string.reading_min_short, 0)
        m < 60 -> RS.get(R.string.reading_min_short, m)
        m % 60 == 0 -> RS.get(R.string.reading_hour_short, m / 60)
        else -> RS.get(R.string.reading_hour_min_short, m / 60, m % 60)
    }

    /** Books a reader with [goal] books a year should have finished by [dayOfYear] of [daysInYear]. */
    fun expectedByNow(goal: Int, dayOfYear: Int, daysInYear: Int): Double = goal * dayOfYear.toDouble() / daysInYear

    /** "Today", "Yesterday", "3 days ago", "2 weeks ago", or the date for anything older than a month; "Never" when unknown. */
    fun lastRead(lastReadMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (lastReadMs == null || lastReadMs <= 0L) return RS.get(R.string.reading_never)
        val day = ReadingStats.dayOf(lastReadMs, zone)
        val days = ChronoUnit.DAYS.between(day, ReadingStats.dayOf(nowMs, zone)).toInt()
        return when {
            days <= 0 -> RS.get(R.string.reading_today)
            days == 1 -> RS.get(R.string.reading_yesterday)
            days < 14 -> RS.plural(R.plurals.reading_days_ago, days, days)
            days < 31 -> (days / 7).let { RS.plural(R.plurals.reading_weeks_ago, it, it) }
            else -> ReadingDates.dayMonthYear(day)
        }
    }

    /** "On pace", "2 books ahead", "1 book behind". */
    fun pace(finished: Int, goal: Int, dayOfYear: Int, daysInYear: Int): String {
        if (finished >= goal) return RS.get(R.string.reading_goal_reached)
        val diff = finished - expectedByNow(goal, dayOfYear, daysInYear)
        val n = kotlin.math.abs(Math.round(diff)).toInt()
        return when {
            n == 0 -> RS.get(R.string.reading_on_pace)
            diff > 0 -> RS.plural(R.plurals.reading_books_ahead, n, n)
            else -> RS.plural(R.plurals.reading_books_behind, n, n)
        }
    }
}
