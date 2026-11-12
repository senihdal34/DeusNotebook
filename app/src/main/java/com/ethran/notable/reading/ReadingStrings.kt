package com.ethran.notable.reading

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.ethran.notable.R
import com.ethran.notable.i18n.AppStrings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Where the reading logic gets its words from. The app uses [AppStrings]; the unit tests plug in an English table. */
interface TextResolver {
    val locale: Locale
    fun string(@StringRes id: Int, args: Array<out Any>): String
    fun plural(@PluralsRes id: Int, count: Int, args: Array<out Any>): String
}

/** The app language: resource text and the locale for dates and numbers. */
object AppTextResolver : TextResolver {
    override val locale: Locale get() = AppStrings.locale
    override fun string(id: Int, args: Array<out Any>): String = AppStrings.get(id, *args)
    override fun plural(id: Int, count: Int, args: Array<out Any>): String = AppStrings.plural(id, count, *args)
}

/**
 * Text for code that has no composition (the board's data builders, badges, labels). Every user-visible word of the
 * Reading feature comes through here or through `stringResource`, so it follows the in-app language. The [resolver] is
 * swapped only in unit tests, which run without Android resources.
 */
object RS {
    @Volatile var resolver: TextResolver = AppTextResolver

    val locale: Locale get() = resolver.locale

    fun get(@StringRes id: Int, vararg args: Any): String = resolver.string(id, args)

    /** [count] picks the form; give the count again in [args] when the text shows it (`%d`). */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String = resolver.plural(id, count, args)

    /** "7,000" in English, "7.000" in Turkish: a whole number grouped for the app language. */
    fun count(n: Int): String = "%,d".format(locale, n)
}

/** Month and weekday names and day-month patterns in the app language (the order of day, month and year differs by language). */
object ReadingDates {
    private fun zh() = RS.locale.language == "zh"

    /** "Nov" (short) or "November" (full) next to a day number; Chinese is "11月" either way. */
    fun month(m: Month, full: Boolean = false): String =
        if (zh()) "${m.value}月" else m.getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, RS.locale)

    /** The month on its own (a heading), which some languages spell differently from the month next to a day. */
    fun monthAlone(m: Month, full: Boolean = true): String =
        if (zh()) "${m.value}月" else m.getDisplayName(if (full) TextStyle.FULL_STANDALONE else TextStyle.SHORT_STANDALONE, RS.locale)

    fun weekday(d: DayOfWeek, full: Boolean = false): String = d.getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, RS.locale)

    /** One letter for a column head (S M T W T F S in English). */
    fun weekdayInitial(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW_STANDALONE, RS.locale)

    /** "7 Nov". */
    fun dayMonth(d: LocalDate, fullMonth: Boolean = false): String = RS.get(R.string.reading_fmt_day_month, d.dayOfMonth, month(d.month, fullMonth))

    /** "6 May 2026". */
    fun dayMonthYear(d: LocalDate): String = RS.get(R.string.reading_fmt_day_month_year, d.dayOfMonth, month(d.month), d.year)

    /** "Saturday, 7 Nov" with a full weekday and short month, or full month when [fullMonth]. */
    fun weekdayDayMonth(d: LocalDate, fullWeekday: Boolean, fullMonth: Boolean = false): String =
        RS.get(R.string.reading_fmt_wd_day_month, weekday(d.dayOfWeek, fullWeekday), d.dayOfMonth, month(d.month, fullMonth))

    /** "Sat 7 Nov", no comma. */
    fun weekdayDayMonthPlain(d: LocalDate): String =
        RS.get(R.string.reading_fmt_wd_day_month_plain, weekday(d.dayOfWeek), d.dayOfMonth, month(d.month))

    /** "Sat, 31 Oct 2025". */
    fun weekdayDayMonthYear(d: LocalDate): String =
        RS.get(R.string.reading_fmt_wd_day_month_year, weekday(d.dayOfWeek), d.dayOfMonth, month(d.month), d.year)

    /** "Sun 22". */
    fun weekdayDay(d: LocalDate): String = RS.get(R.string.reading_fmt_wd_day, weekday(d.dayOfWeek), d.dayOfMonth)

    /** "Oct 2025". */
    fun monthYear(m: Month, year: Int): String = RS.get(R.string.reading_fmt_month_year, month(m), year)
}
