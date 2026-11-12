package com.ethran.notable.planner

import com.ethran.notable.R
import com.ethran.notable.i18n.AppLanguage
import com.ethran.notable.i18n.AppStrings
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Names the planner gives its notebook and pages. They follow the app language, so changing the language renames
 * them; pages and the notebook are found by their `background` key and by a name in any language, never by the
 * current name alone.
 */
object PlannerNames {
    fun notebookTitle(language: AppLanguage = AppStrings.language) = AppStrings.getIn(language, R.string.planner_notebook)

    fun weekTitle(monday: LocalDate, language: AppLanguage = AppStrings.language) =
        AppStrings.getIn(language, R.string.planner_week_title, PlannerWeeks.weekOfYear(monday))

    fun dayTitle(day: LocalDate, language: AppLanguage = AppStrings.language): String {
        val locale = language.locale
        return "${PlannerWeeks.dayFull(day.dayOfWeek.value - 1, locale)} ${PlannerWeeks.shortDate(day, locale)}"
    }

    /** "November 2026", "Kasım 2026", "2026年11月". Pure text from the language's calendar names, no string resources. */
    fun monthTitle(month: YearMonth, language: AppLanguage = AppStrings.language): String {
        val locale = language.locale
        return if (locale.language == "zh") "${month.year}年${month.monthValue}月" else "${monthLabel(month, locale)} ${month.year}"
    }

    /** "Q4 2026"; Chinese "2026年第4季度". */
    fun quarterTitle(year: Int, quarter: Int, language: AppLanguage = AppStrings.language): String =
        if (language.locale.language == "zh") "${year}年第${quarter}季度" else "Q$quarter $year"

    fun yearTitle(year: Int): String = year.toString()

    /** Full month name on its own: "November", "listopad" (not "listopada"), "11月". */
    fun monthLabel(month: YearMonth, locale: Locale = AppStrings.locale): String =
        if (locale.language == "zh") "${month.monthValue}月"
        else month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    /** Short month name for the year page: "Nov", "Kas", "11月". */
    fun monthShort(month: YearMonth, locale: Locale = AppStrings.locale): String =
        if (locale.language == "zh") "${month.monthValue}月"
        else month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }

    /** Name of the notebook that holds all pages of one level. */
    fun levelNotebookTitle(level: PlannerLevel, language: AppLanguage = AppStrings.language): String =
        AppStrings.getIn(language, levelNotebookRes(level))

    fun allLevelNotebookTitles(level: PlannerLevel): List<String> = AppLanguage.entries.map { levelNotebookTitle(level, it) }.distinct()

    private fun levelNotebookRes(level: PlannerLevel) = when (level) {
        PlannerLevel.Month -> R.string.planner_months_notebook
        PlannerLevel.Quarter -> R.string.planner_quarters_notebook
        PlannerLevel.Year -> R.string.planner_years_notebook
    }

    /** The notebook's name in every language (and the name before the app was translated). */
    fun allNotebookTitles(): List<String> = AppLanguage.entries.map { notebookTitle(it) }.distinct() + LEGACY_NOTEBOOK

    /** What [title] of a planner page should read now, or null when it is already right or was typed by the user. */
    fun renamed(background: String, title: String, language: AppLanguage = AppStrings.language): String? {
        val wanted: String
        val known: Set<String>
        val week = PlannerWeeks.parseWeek(background)
        val day = PlannerWeeks.parseDay(background)
        val month = PlannerWeeks.parseMonth(background)
        val quarter = PlannerWeeks.parseQuarter(background)
        val year = PlannerWeeks.parseYear(background)
        when {
            month != null -> {
                wanted = monthTitle(month, language)
                known = AppLanguage.entries.map { monthTitle(month, it) }.toSet()
            }
            quarter != null -> {
                wanted = quarterTitle(quarter.year, quarter.quarter, language)
                known = AppLanguage.entries.map { quarterTitle(quarter.year, quarter.quarter, it) }.toSet()
            }
            year != null -> {
                wanted = yearTitle(year)
                known = setOf(wanted)
            }
            week != null -> {
                wanted = weekTitle(week, language)
                known = AppLanguage.entries.map { weekTitle(week, it) }.toSet()
            }
            day != null -> {
                wanted = dayTitle(day, language)
                known = AppLanguage.entries.map { dayTitle(day, it) }.toSet()
            }
            else -> return null
        }
        return decide(title, wanted, known, legacyWeek = week != null)
    }

    /**
     * The rename rule: a title the app generated (in any language, a blank one, or the first Turkish version's
     * "Hafta 44") becomes [wanted]; a title that is already right, or that the user typed, stays.
     */
    internal fun decide(title: String, wanted: String, known: Set<String>, legacyWeek: Boolean): String? {
        if (title == wanted) return null
        val generated = title in known || title.isBlank() || (legacyWeek && LEGACY_WEEK.matches(title))
        return if (generated) wanted else null
    }

    private const val LEGACY_NOTEBOOK = "Planlayıcı"
    private val LEGACY_WEEK = Regex("""Hafta \d+(/\d+)?""")
}
